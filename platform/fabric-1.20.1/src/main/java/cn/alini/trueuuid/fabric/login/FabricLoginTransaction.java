package cn.alini.trueuuid.fabric.login;

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
    private static final long TIMEOUT_SECONDS = 30;

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

        CompletableFuture.runAsync(() -> timeout(handler, server),
                CompletableFuture.delayedExecutor(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    public synchronized void answer(MinecraftServer server, ServerLoginNetworkHandler handler,
                                    boolean understood, AuthMessages.Answer answer) {
        if (closed || completion == null) return;
        if (!understood || answer == null) {
            closeWithDisconnect(server, handler, "TrueUUID authentication response was invalid");
            return;
        }
        LoginStateMachine.AnswerResult result = state.acceptAnswer(TRANSACTION_ID, answer);
        if (result == LoginStateMachine.AnswerResult.DENY) {
            completeOfflineFallback(server);
            return;
        }
        if (result != LoginStateMachine.AnswerResult.VERIFY) {
            closeWithDisconnect(server, handler, "TrueUUID authentication response was invalid");
            return;
        }

        // Fabric's initial implementation is intentionally Mojang-only.  A
        // client-supplied Yggdrasil endpoint is never trusted until a Fabric
        // config adapter supplies the shared allowlist and endpoint policy.
        if (!answer.customEndpoint().isBlank()) {
            closeWithDisconnect(server, handler, "TrueUUID custom endpoints are not enabled on Fabric yet");
            return;
        }
        GameProfile offlineProfile = ((FabricLoginStateAccess) handler).trueuuid$getProfile();
        if (offlineProfile == null || offlineProfile.getName() == null || offlineProfile.getName().isBlank()) {
            closeWithDisconnect(server, handler, "TrueUUID could not read the login profile");
            return;
        }

        verification = FabricSessionCheck.hasJoinedAsync(offlineProfile.getName(), nonce)
                .whenComplete((verified, failure) -> server.execute(() -> completeVerification(server, handler, verified, failure)));
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
        closeWithDisconnect(server, handler, "TrueUUID authentication timed out");
    }

    private void closeWithDisconnect(MinecraftServer server, ServerLoginNetworkHandler handler, String reason) {
        server.execute(() -> {
            synchronized (FabricLoginTransaction.this) {
                if (closed) return;
                closed = true;
                if (completion != null) completion.completeExceptionally(new IllegalStateException(reason));
                state.reset();
            }
            handler.disconnect(Text.literal(reason));
        });
    }

    private void completeVerification(MinecraftServer server, ServerLoginNetworkHandler handler,
                                      VerifiedProfile verified, Throwable failure) {
        synchronized (this) {
            if (closed || completion == null || completion.isDone()) return;
            if (failure == null && verified != null) {
                ((FabricLoginStateAccess) handler).trueuuid$setProfile(FabricVerifiedProfiles.create(verified));
                state.reset();
                completion.complete(null);
                return;
            }
        }
        completeOfflineFallback(server);
    }

    private void completeOfflineFallback(MinecraftServer server) {
        // This retains the profile generated by vanilla's offline-mode login.
        // It is intentionally distinct from a verified profile replacement.
        server.execute(() -> {
            synchronized (FabricLoginTransaction.this) {
                if (closed || completion == null || completion.isDone()) return;
                state.reset();
                completion.complete(null);
            }
        });
    }
}
