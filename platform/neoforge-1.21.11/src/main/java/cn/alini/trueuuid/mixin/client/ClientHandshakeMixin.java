package cn.alini.trueuuid.mixin.client;

import cn.alini.trueuuid.protocol.AcceptanceHooks;
import cn.alini.trueuuid.net.AuthAnswerPayload;
import cn.alini.trueuuid.net.AuthPayload;
import cn.alini.trueuuid.client.ClientAccountStatus;
import cn.alini.trueuuid.client.ClientAuthDiagnostics;
import cn.alini.trueuuid.client.ClientYggdrasilEndpoint;
import cn.alini.trueuuid.net.NetIds;
import cn.alini.trueuuid.Trueuuid;
import cn.alini.trueuuid.protocol.AuthMessages;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket;
import net.minecraft.network.protocol.login.custom.CustomQueryPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Mixin(ClientHandshakePacketListenerImpl.class)
// 1.21.9+ record-era copy: the session service moved to
// Minecraft.services().sessionService(). See build.gradle's recordEraSources.
abstract class ClientHandshakeMixin {
    @Shadow private Connection connection;
    @Shadow private Consumer<Component> updateStatus;
    @Unique private String trueuuid$lastHasJoinedUrl = "";

    @Inject(method = "handleCustomQuery", at = @At("HEAD"), cancellable = true)
    private void trueuuid$answer(ClientboundCustomQueryPacket packet, CallbackInfo callback) {
        CustomQueryPayload payload = packet.payload();
        if (!NetIds.AUTH.equals(payload.id()) || !(payload instanceof AuthPayload query)) return;

        Minecraft minecraft = Minecraft.getInstance();
        User user = minecraft.getUser();
        String token = user.getAccessToken();
        Connection loginConnection = connection;
        int transactionId = packet.transactionId();
        Trueuuid.acceptance("phase=client_query_received migrationAvailable={} migrationConfirm={}",
                query.message().migrationAvailable(), NetIds.MIGRATION_CONFIRM_SERVER_ID.equals(query.message().nonce()));
        if (query.message().migrationAvailable()
                && NetIds.MIGRATION_CONFIRM_SERVER_ID.equals(query.message().nonce())) {
            Trueuuid.acceptance("phase=client_migration_query_received offlineUuid={}", query.message().offlineUuid());
            trueuuid$confirmOfflinePlayerDataUpgrade(minecraft, query.message().offlineUuid(), query.message().summary(),
                    trueuuid$lastHasJoinedUrl, loginConnection, transactionId);
            callback.cancel();
            return;
        }
        if (token == null || token.isBlank() || "0".equals(token)) {
            Trueuuid.debug("TrueUUID client session token is absent or is a development placeholder");
            Trueuuid.acceptance("phase=client_missing_session_token");
            ClientAccountStatus.markOffline();
            trueuuid$send(loginConnection, transactionId, false, "", false, true);
            callback.cancel();
            return;
        }

        // Resolve the skin-site endpoint before joinServer: the join assertion
        // is one-shot, and an authlib-injector session must be verified by the
        // server against the same Yggdrasil service (subject to its allowlist).
        String hasJoinedUrl = ClientYggdrasilEndpoint.resolveHasJoinedUrl();
        this.trueuuid$lastHasJoinedUrl = hasJoinedUrl;
        updateStatus.accept(Component.translatable("connect.authorizing"));
        CompletableFuture.supplyAsync(() -> {
                    try {
                        // The access token stays on the client. The server only
                        // receives a later hasJoined assertion through its safe verifier.
                        minecraft.services().sessionService().joinServer(user.getProfileId(), token, query.message().nonce());
                        Trueuuid.debug("TrueUUID joinServer completed successfully");
                        Trueuuid.acceptance("phase=client_joinserver_ok");
                        return true;
                    } catch (Throwable failure) {
                        Trueuuid.debug("TrueUUID joinServer failed: {}", ClientAuthDiagnostics.failureCategory(failure));
                        Trueuuid.acceptance("phase=client_joinserver_failed category={}", ClientAuthDiagnostics.failureCategory(failure));
                        return false;
                    }
                })
                .orTimeout(25, TimeUnit.SECONDS)
                .exceptionally(error -> false)
                .thenAccept(joined -> {
                    if (joined) ClientAccountStatus.markPremium();
                    else ClientAccountStatus.markOffline();
                    if (joined && query.message().migrationAvailable()) {
                        trueuuid$confirmOfflinePlayerDataUpgrade(minecraft, query.message().offlineUuid(),
                                query.message().summary(), hasJoinedUrl, loginConnection, transactionId);
                    } else {
                        trueuuid$send(loginConnection, transactionId, joined, hasJoinedUrl, false, false);
                    }
                });
        callback.cancel();
    }

    @Unique private static void trueuuid$send(Connection connection, int transactionId, boolean joined,
                                              String hasJoinedUrl, boolean migrationConfirmed, boolean missingToken) {
        Trueuuid.acceptance("phase=client_answer_send joined={} migrationConfirmed={} missingToken={}",
                joined, migrationConfirmed, missingToken);
        connection.send(new ServerboundCustomQueryAnswerPacket(transactionId,
                new AuthAnswerPayload(new AuthMessages.Answer(joined, hasJoinedUrl, migrationConfirmed, missingToken))));
    }

    @Unique private static void trueuuid$confirmOfflinePlayerDataUpgrade(Minecraft minecraft, String offlineUuid,
                                                                         String summary, String hasJoinedUrl,
                                                                         Connection connection, int transactionId) {
        if (trueuuid$testAutoConfirmMigration()) {
            Trueuuid.acceptance("phase=client_migration_auto_confirm offlineUuid={}", offlineUuid);
            trueuuid$send(connection, transactionId, true, hasJoinedUrl, true, false);
            return;
        }
        Trueuuid.acceptance("phase=client_migration_prompt_shown offlineUuid={}", offlineUuid);
        minecraft.execute(() -> minecraft.setScreen(new ConfirmScreen(
                confirmed -> {
                    Trueuuid.acceptance("phase=client_migration_prompt_answer confirmed={}", confirmed);
                    trueuuid$send(connection, transactionId, true, hasJoinedUrl, confirmed, false);
                    minecraft.setScreen(null);
                },
                Component.translatable("trueuuid.confirm.offline_player.title"),
                Component.translatable("trueuuid.confirm.offline_player.message",
                        trueuuid$authSourceComponent(hasJoinedUrl), offlineUuid, summary),
                Component.translatable("trueuuid.confirm.migrate_join"),
                Component.translatable("trueuuid.confirm.exit_admin")
        )));
    }

    @Unique private static boolean trueuuid$testAutoConfirmMigration() {
        return AcceptanceHooks.autoConfirmMigration();
    }

    @Unique private static Component trueuuid$authSourceComponent(String hasJoinedUrl) {
        return hasJoinedUrl == null || hasJoinedUrl.isBlank()
                ? Component.translatable("trueuuid.auth_source.premium")
                : Component.translatable("trueuuid.auth_source.skin_site");
    }
}
