package cn.alini.trueuuid.fabric.login;

import cn.alini.trueuuid.fabric.TrueuuidFabric;
import cn.alini.trueuuid.fabric.config.FabricConfig;
import cn.alini.trueuuid.protocol.AuthMessages;
import cn.alini.trueuuid.protocol.LoginStateMachine;
import cn.alini.trueuuid.protocol.VerifiedProfile;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginNetworkHandler;
import net.minecraft.text.Text;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * One transaction per native login handler. It has no static connection map,
 * so disconnect/timeout cleanup cannot retain a previous connection.
 *
 * <p>The first secure slice owns the Mojang decision and basic offline
 * fallback: packet bounds, local-client join assertion, bounded server
 * verification, profile replacement, or release of the native offline
 * profile. Loader-specific policy and migration features follow later.</p>
 */
public final class FabricLoginTransaction {
    private static final int TRANSACTION_ID = 1;

    private final LoginStateMachine state = new LoginStateMachine();
    private CompletableFuture<Void> completion;
    private CompletableFuture<?> verification;
    private String nonce;
    private boolean closed;

    public synchronized void begin(ServerLoginNetworkHandler handler, MinecraftServer server,
                                   PacketSender sender, ServerLoginNetworking.LoginSynchronizer synchronizer) {
        if (closed || completion != null) return;

        completion = new CompletableFuture<>();
        nonce = UUID.randomUUID().toString().replace("-", "");
        state.beginAuthentication(TRANSACTION_ID, nonce, System.currentTimeMillis());
        synchronizer.waitFor(completion);
        sender.sendPacket(FabricLoginNetworking.AUTH_CHANNEL,
                FabricLoginPayloads.query(new AuthMessages.Query(nonce, false, "", "")));
        GameProfile pendingProfile = ((FabricLoginStateAccess) handler).trueuuid$getProfile();
        TrueuuidFabric.LOGGER.info("TrueUUID authentication challenge sent: player={}",
                pendingProfile == null ? "<unknown>" : pendingProfile.getName());

        CompletableFuture.runAsync(() -> timeout(handler, server),
                CompletableFuture.delayedExecutor(FabricConfig.timeoutMs(), TimeUnit.MILLISECONDS));
    }

    public synchronized void answer(MinecraftServer server, ServerLoginNetworkHandler handler,
                                    boolean understood, AuthMessages.Answer answer) {
        if (closed || completion == null) return;
        TrueuuidFabric.debug("TrueUUID received authentication response: understood={}", understood);
        if (!understood || answer == null) {
            closeWithDisconnect(server, handler, "trueuuid.disconnect.auth_denied");
            return;
        }
        LoginStateMachine.AnswerResult result = state.acceptAnswer(TRANSACTION_ID, answer);
        if (result == LoginStateMachine.AnswerResult.DENY) {
            completeOfflineFallbackOrDeny(server, handler, true);
            return;
        }
        if (result != LoginStateMachine.AnswerResult.VERIFY) {
            closeWithDisconnect(server, handler, "trueuuid.disconnect.auth_denied");
            return;
        }

        // Fabric's initial implementation is intentionally Mojang-only.  A
        // client-supplied Yggdrasil endpoint is never trusted until a Fabric
        // config adapter supplies the shared allowlist and endpoint policy.
        if (!answer.customEndpoint().isBlank()) {
            closeWithDisconnect(server, handler, "trueuuid.disconnect.custom_endpoint_unsupported");
            return;
        }
        GameProfile offlineProfile = ((FabricLoginStateAccess) handler).trueuuid$getProfile();
        if (offlineProfile == null || offlineProfile.getName() == null || offlineProfile.getName().isBlank()) {
            closeWithDisconnect(server, handler, "trueuuid.disconnect.auth_denied");
            return;
        }

        verification = FabricSessionCheck.hasJoinedAsync(offlineProfile.getName(), nonce)
                .whenComplete((verified, failure) -> server.execute(() -> completeVerification(server, handler, verified, failure)));
    }

    /**
     * Consults the persistent offline policy before releasing the native
     * offline profile, mirroring the other adapters: a name that has already
     * completed a verified premium login may not be taken by an unverified
     * client. On authentication failure (not timeout) one same-name, same-IP
     * reconnect inside the grace window keeps the verified identity instead.
     */
    private void completeOfflineFallbackOrDeny(MinecraftServer server, ServerLoginNetworkHandler handler, boolean allowGrace) {
        GameProfile offlineProfile = ((FabricLoginStateAccess) handler).trueuuid$getProfile();
        String name = offlineProfile == null ? null : offlineProfile.getName();
        if (allowGrace && name != null) {
            var grace = FabricAdapterRuntime.tryGraceLogin(name, clientIp(handler));
            if (grace.isPresent()) {
                TrueuuidFabric.LOGGER.info("TrueUUID recent same-IP grace login: player={}, uuid={}", name, grace.get());
                completeWithProfile(server, handler, new GameProfile(grace.get(), name));
                return;
            }
        }
        if (!FabricAdapterRuntime.canUseOfflineFallback(name)) {
            TrueuuidFabric.LOGGER.warn("TrueUUID offline fallback denied for previously verified name: player={}", name);
            closeWithDisconnect(server, handler, "trueuuid.disconnect.bound_premium");
            return;
        }
        completeOfflineFallback(server, name);
    }

    public synchronized void cancel() {
        closed = true;
        if (verification != null) verification.cancel(true);
        if (completion != null) completion.completeExceptionally(new IllegalStateException("login disconnected"));
        state.reset();
    }

    private void timeout(ServerLoginNetworkHandler handler, MinecraftServer server) {
        synchronized (this) {
            if (closed || completion == null || completion.isDone()) return;
        }
        TrueuuidFabric.debug("TrueUUID authentication timed out after {} ms", FabricConfig.timeoutMs());
        if (FabricConfig.allowOfflineOnTimeout()) {
            // The offline policy still applies: a timeout must not hand a
            // previously verified name to a client that never answered. No
            // grace either, matching 1.20.1: grace covers failures, not silence.
            completeOfflineFallbackOrDeny(server, handler, false);
        } else {
            closeWithDisconnect(server, handler, "trueuuid.disconnect.timeout");
        }
    }

    /** The reason is a translation key from platform/common-assets, never inline text. */
    private void closeWithDisconnect(MinecraftServer server, ServerLoginNetworkHandler handler, String reasonKey) {
        server.execute(() -> {
            synchronized (FabricLoginTransaction.this) {
                if (closed) return;
                closed = true;
                if (completion != null) completion.completeExceptionally(new IllegalStateException(reasonKey));
                state.reset();
            }
            handler.disconnect(Text.translatable(reasonKey));
        });
    }

    private void completeVerification(MinecraftServer server, ServerLoginNetworkHandler handler,
                                      VerifiedProfile verified, Throwable failure) {
        synchronized (this) {
            if (closed || completion == null || completion.isDone()) return;
            if (failure == null && verified != null) {
                FabricAdapterRuntime.recordVerifiedProfile(verified, clientIp(handler));
                ((FabricLoginStateAccess) handler).trueuuid$setProfile(FabricVerifiedProfiles.create(verified));
                // The English audit line for administrators and log tooling,
                // matching the Forge adapters' vocabulary; player-facing text
                // stays localized. Without this line a fully successful
                // premium run leaves no server-side evidence at all
                // (found during the first real two-sided run, 2026-07-17).
                TrueuuidFabric.LOGGER.info("TrueUUID session-verified premium login: player={}, uuid={}",
                        verified.name(), verified.uuid());
                state.reset();
                completion.complete(null);
                return;
            }
        }
        completeOfflineFallbackOrDeny(server, handler, true);
    }

    /** Grace acceptance: keep the previously verified UUID for this reconnect. */
    private void completeWithProfile(MinecraftServer server, ServerLoginNetworkHandler handler, GameProfile profile) {
        server.execute(() -> {
            synchronized (FabricLoginTransaction.this) {
                if (closed || completion == null || completion.isDone()) return;
                ((FabricLoginStateAccess) handler).trueuuid$setProfile(profile);
                state.reset();
                completion.complete(null);
            }
        });
    }

    private static String clientIp(ServerLoginNetworkHandler handler) {
        var connection = ((FabricLoginStateAccess) handler).trueuuid$getConnection();
        return connection != null && connection.getAddress() instanceof java.net.InetSocketAddress address
                && address.getAddress() != null ? address.getAddress().getHostAddress() : "";
    }

    private void completeOfflineFallback(MinecraftServer server, String name) {
        // This retains the profile generated by vanilla's offline-mode login.
        // It is intentionally distinct from a verified profile replacement.
        server.execute(() -> {
            synchronized (FabricLoginTransaction.this) {
                if (closed || completion == null || completion.isDone()) return;
                TrueuuidFabric.LOGGER.info("TrueUUID offline fallback accepted: player={}", name);
                state.reset();
                completion.complete(null);
            }
        });
    }
}
