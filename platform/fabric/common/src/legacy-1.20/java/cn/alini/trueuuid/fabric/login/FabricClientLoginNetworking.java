package cn.alini.trueuuid.fabric.login;

import cn.alini.trueuuid.fabric.TrueuuidFabric;
import cn.alini.trueuuid.protocol.ClientYggdrasilEndpoint;
import cn.alini.trueuuid.fabric.client.FabricClientStatus;
import cn.alini.trueuuid.protocol.AcceptanceHooks;
import cn.alini.trueuuid.protocol.AuthMessages;
import cn.alini.trueuuid.protocol.ClientSession;
import cn.alini.trueuuid.protocol.ClientAuthDiagnostics;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.text.Text;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** 1.20-era client login registration; dedicated servers never link this class. */
public final class FabricClientLoginNetworking {
    private static boolean registered;
    private static volatile String lastHasJoinedUrl = "";

    public static synchronized void registerClientHooks() {
        if (registered) return;
        registered = true;
        if (!ClientLoginNetworking.registerGlobalReceiver(FabricLoginNetworking.AUTH_CHANNEL,
                (client, handler, buffer, listenerAdder) -> {
                    try {
                        AuthMessages.Query query = FabricLoginPayloads.readQuery(buffer);
                        TrueuuidFabric.acceptance("phase=client_query_received migrationAvailable={} migrationConfirm={}",
                                query.migrationAvailable(), FabricLoginNetworking.MIGRATION_CONFIRM_SERVER_ID.equals(query.nonce()));
                        if (query.migrationAvailable()
                                && FabricLoginNetworking.MIGRATION_CONFIRM_SERVER_ID.equals(query.nonce())) {
                            TrueuuidFabric.acceptance("phase=client_migration_query_received offlineUuid={}", query.offlineUuid());
                            return confirmOfflinePlayerDataUpgrade(client, query.offlineUuid(), query.summary(), lastHasJoinedUrl);
                        }
                        var session = client.getSession();
                        if (ClientSession.isMissing(session.getUuidOrNull(), session.getAccessToken())) {
                            TrueuuidFabric.debug("TrueUUID client has no session or uses an offline launcher placeholder");
                            TrueuuidFabric.acceptance("phase=client_missing_session_token");
                            return CompletableFuture.completedFuture(FabricLoginPayloads.answer(ClientSession.offlineAnswer()));
                        }
                        String hasJoinedUrl = ClientYggdrasilEndpoint.resolveHasJoinedUrl();
                        lastHasJoinedUrl = hasJoinedUrl;
                        return CompletableFuture.supplyAsync(() -> {
                                    try {
                                        FabricSessionJoiner.join(client, session, query.nonce());
                                        TrueuuidFabric.debug("TrueUUID joinServer completed successfully");
                                        TrueuuidFabric.acceptance("phase=client_joinserver_ok");
                                        return true;
                                    } catch (Throwable failure) {
                                        TrueuuidFabric.debug("TrueUUID joinServer failed: {}", ClientAuthDiagnostics.failureCategory(failure));
                                        TrueuuidFabric.acceptance("phase=client_joinserver_failed category={}", ClientAuthDiagnostics.failureCategory(failure));
                                        return false;
                                    }
                                })
                                .orTimeout(30, TimeUnit.SECONDS)
                                .exceptionally(failure -> false)
                                .thenApply(joined -> {
                                    TrueuuidFabric.acceptance(
                                            "phase=client_answer_send joined={} migrationConfirmed=false missingToken={}",
                                            joined, false);
                                    return FabricLoginPayloads.answer(
                                            ClientSession.proofAnswer(joined, hasJoinedUrl));
                                });
                    } catch (IllegalArgumentException malformed) {
                        TrueuuidFabric.LOGGER.warn("Rejected malformed TrueUUID Fabric login query: {}",
                                malformed.getMessage());
                        return CompletableFuture.completedFuture(null);
                    }
                })) {
            throw new IllegalStateException("TrueUUID Fabric client login channel was already registered");
        }
        FabricClientStatusNetworking.registerReceiver();
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> FabricClientStatus.clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> FabricClientStatus.clear());
    }

    private static CompletableFuture<net.minecraft.network.PacketByteBuf> confirmOfflinePlayerDataUpgrade(
            MinecraftClient client, String offlineUuid, String summary, String hasJoinedUrl) {
        CompletableFuture<net.minecraft.network.PacketByteBuf> future = new CompletableFuture<>();
        if (testAutoConfirmMigration()) {
            TrueuuidFabric.acceptance("phase=client_migration_auto_confirm offlineUuid={}", offlineUuid);
            future.complete(FabricLoginPayloads.answer(
                    new AuthMessages.Answer(true, hasJoinedUrl, true, false)));
            return future;
        }
        client.execute(() -> client.setScreen(new ConfirmScreen(confirmed -> {
            TrueuuidFabric.acceptance("phase=client_migration_prompt_answer confirmed={}", confirmed);
            future.complete(FabricLoginPayloads.answer(
                    new AuthMessages.Answer(true, hasJoinedUrl, confirmed, false)));
            client.setScreen(null);
        }, Text.translatable("trueuuid.confirm.offline_player.title"),
                Text.translatable("trueuuid.confirm.offline_player.message",
                        authSourceText(hasJoinedUrl), offlineUuid, summary),
                Text.translatable("trueuuid.confirm.migrate_join"),
                Text.translatable("trueuuid.confirm.exit_admin"))));
        TrueuuidFabric.acceptance("phase=client_migration_prompt_shown offlineUuid={}", offlineUuid);
        return future;
    }

    private static boolean testAutoConfirmMigration() {
        return AcceptanceHooks.autoConfirmMigration();
    }

    private static Text authSourceText(String hasJoinedUrl) {
        return hasJoinedUrl == null || hasJoinedUrl.isBlank()
                ? Text.translatable("trueuuid.auth_source.premium")
                : Text.translatable("trueuuid.auth_source.skin_site");
    }

    private FabricClientLoginNetworking() {}
}
