// java
package cn.alini.trueuuid.server;

import cn.alini.trueuuid.Trueuuid;
import cn.alini.trueuuid.config.TrueuuidConfig;
import cn.alini.trueuuid.net.NetIds;
import cn.alini.trueuuid.protocol.AuthMessages;
import cn.alini.trueuuid.protocol.LoginStateMachine;
import cn.alini.trueuuid.protocol.MigrationTransaction;
import cn.alini.trueuuid.protocol.VerifiedProfile;
import cn.alini.trueuuid.util.TrueuuidText;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.ServerboundCustomQueryPacket;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.UUID;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public final class ServerLoginController {
    public interface Access {
        GameProfile profile();
        void profile(GameProfile profile);
        MinecraftServer server();
        Connection connection();
        void disconnect(Component reason);
    }

    private final Access access;
    private final LoginStateMachine loginState = new LoginStateMachine();
    public ServerLoginController(Access access) { this.access = access; }
    // 握手状态 (handshake state)
    // 使用高位事务号，避免和 Forge/FML 登录握手从 0 开始分配的 CustomQuery 事务号撞车。
    private static final AtomicInteger TRUEUUID$NEXT_TX_ID = new AtomicInteger(0x4F000000);
    private volatile int trueuuid$txId = 0;
    private String trueuuid$nonce = null;
    private volatile long trueuuid$sentAt = 0L;
    /** Set by the Netty hello handler and consumed only after Forge has
     * completed its LOGIN negotiation on the server thread. */
    private volatile boolean trueuuid$queryPending = false;
    /** True only after this offline login has reached a final auth decision. */
    private volatile boolean trueuuid$authFinalized = false;
    private boolean trueuuid$offlineUpgradeOffered = false;
    private boolean trueuuid$migrationConfirmation = false;
    private GameProfile trueuuid$pendingVerifiedProfile = null;
    private AuthState.AuthSource trueuuid$pendingAuthSource = null;
    private String trueuuid$pendingAuthDisplayName = null;
    private String trueuuid$pendingIp = null;
    private PlayerDataMigration.OfflineData trueuuid$pendingOfflineData;
    private CompletableFuture<?> trueuuid$currentWork;


    // 新增：防止重复处理客户端认证包（同次握手只处理一次）(Added: Prevent duplicate processing of client auth packets (only process once per handshake))
    private volatile boolean trueuuid$ackHandled = false;

    public void afterHello(ServerboundHelloPacket pkt, CallbackInfo ci) {
        if (access.server().usesAuthentication()) {
            debug("skipping TrueUUID query because the server is in online mode");
            return;
        }
        if (access.profile() == null) {
            debug("skipping TrueUUID query because the login profile is unavailable");
            return;
        }

        // 清理 ack 处理标志（新握手重新可处理） (Clear ack handled flag (new handshake can be processed again))
        this.trueuuid$ackHandled = false;

        // Forge owns the LOGIN custom-payload phase. Sending another custom
        // query here races its indexed handshake replies and corrupts the
        // packet stream.  tick() will dispatch ours immediately before the
        // vanilla READY_TO_ACCEPT path, after Forge negotiation is complete.
        this.trueuuid$authFinalized = false;
        this.trueuuid$queryPending = true;
    }

    /**
     * Called immediately before vanilla accepts a READY_TO_ACCEPT login.  At
     * this point Forge's LOGIN negotiation is complete, so a separate custom
     * query cannot be mistaken for an FML indexed reply.
     */
    public void onReadyToAccept(CallbackInfo ci) {
        if (!this.trueuuid$queryPending) {
            // Forge re-enters READY_TO_ACCEPT on every server tick.  Keep the
            // final accept call blocked while a TrueUUID query is outstanding;
            // the async verifier releases it by reset() on success/fallback.
            if (this.trueuuid$txId != 0) ci.cancel();
            // Fail closed if a loader/lifecycle edge reaches READY_TO_ACCEPT
            // without our hello hook having scheduled the required query.
            if (!this.trueuuid$authFinalized && !access.server().usesAuthentication() && access.profile() != null) {
                this.trueuuid$queryPending = true;
                onReadyToAccept(ci);
            }
            return;
        }
        this.trueuuid$queryPending = false;

        if (access.server().usesAuthentication() || access.profile() == null) return;
        if (trueuuid$isMigrationPending(access.profile().getName())) {
            sendDisconnectWithReason(Component.translatable("trueuuid.disconnect.migration_pending"));
            reset();
            ci.cancel();
            return;
        }

        this.trueuuid$nonce = UUID.randomUUID().toString().replace("-", "");
        this.trueuuid$txId = TRUEUUID$NEXT_TX_ID.getAndIncrement();
        this.trueuuid$sentAt = System.currentTimeMillis();
        this.loginState.reset();
        this.loginState.beginAuthentication(this.trueuuid$txId, this.trueuuid$nonce, this.trueuuid$sentAt);
        this.trueuuid$offlineUpgradeOffered = false;
        this.trueuuid$migrationConfirmation = false;
        debug("sending authentication query for " + access.profile().getName());
        Trueuuid.acceptance("phase=auth_query_sent player={}", access.profile().getName());

        access.connection().send(LoginPacketCodec.initial(this.trueuuid$txId, this.trueuuid$nonce));
        // Do not let vanilla accept the offline profile while authentication
        // is still in progress. onTick releases this once the state resets.
        ci.cancel();
    }

    public void onTick(CallbackInfo ci) {
        if (this.trueuuid$txId == 0 || this.trueuuid$sentAt == 0L) return;
        if (!access.connection().isConnected()) {
            TrueuuidRuntime.AUTH_STATE.remove(access.connection());
            reset();
            return;
        }
        long timeoutMs = this.trueuuid$offlineUpgradeOffered ? Math.max(TrueuuidConfig.timeoutMs(), 180_000L) : TrueuuidConfig.timeoutMs();
        if (timeoutMs <= 0) {
            // TrueUUID 自定义认证还没有结束时暂停原版登录推进，避免离线档案先进入世界后又被正版档案二次接受。
            ci.cancel();
            return;
        }

        long now = System.currentTimeMillis();
        if (now - this.trueuuid$sentAt < timeoutMs) {
            // 等待客户端认证或异步会话校验完成期间，Forge 的 NEGOTIATING 状态不能继续推进到 READY_TO_ACCEPT。
            ci.cancel();
            return;
        }

        if (TrueuuidConfig.debug()) {
            System.out.println("[TrueUUID] 握手超时, txId: " + this.trueuuid$txId);
        }

        if (this.trueuuid$offlineUpgradeOffered) {
            Trueuuid.acceptance("result=migration_timeout player={}", access.profile() == null ? "<unknown>" : access.profile().getName());
            sendDisconnectWithReason(Component.translatable("trueuuid.disconnect.migration_confirm_timeout"));
            reset();
        } else if (TrueuuidConfig.allowOfflineOnTimeout()) {
            if (TrueuuidConfig.debug()) {
                System.out.println("[TrueUUID] 超时允许离线进入");
            }
            TrueuuidRuntime.AUTH_STATE.markOfflineFallback(access.connection(), AuthState.FallbackReason.TIMEOUT);
            reset();
        } else {
            sendDisconnectWithReason(TrueuuidText.configComponent(
                    TrueuuidConfig.timeoutKickMessage(),
                    "trueuuid.disconnect.timeout"
            ));
            reset();
            ci.cancel();
        }
    }

    public void onLoginCustom(ServerboundCustomQueryPacket packet, CallbackInfo ci) {
        if (this.trueuuid$txId == 0) return;
        if (packet.getTransactionId() != this.trueuuid$txId) {
            debug("ignoring login query response for transaction " + packet.getTransactionId());
            return;
        }
        debug("received authentication response for " + access.profile().getName());
        Trueuuid.acceptance("phase=auth_answer_received player={} migrationPhase={}",
                access.profile().getName(), this.trueuuid$migrationConfirmation);

        String ip;
        if (access.connection().getRemoteAddress() instanceof InetSocketAddress isa) {
            ip = isa.getAddress().getHostAddress();
        } else {
            ip = null;
        }
        if (TrueuuidConfig.debug()) {
            System.out.println("[TrueUUID] 收到客户端认证包, 玩家: " + (access.profile() != null ? access.profile().getName() : "<unknown>") + ", ip: " + ip);
        }

        AuthMessages.Answer answer;
        try {
            answer = LoginPacketCodec.decode(packet.getData());
        } catch (Throwable malformed) {
            debug("received malformed TrueUUID answer: " + malformed.getClass().getName());
            handleAuthFailure(ip, "缺少数据");
            reset(); ci.cancel(); return;
        }
        LoginStateMachine.AnswerResult stateResult = loginState.acceptAnswer(packet.getTransactionId(), answer);
        if (stateResult == LoginStateMachine.AnswerResult.IGNORE) {
            ci.cancel();
            return;
        }
        boolean ackOk = answer.joined();
        String clientHasJoinedUrl = answer.customEndpoint();
        final boolean finalMigrationConfirmed = answer.migrationConfirmed();
        if (this.trueuuid$migrationConfirmation && finalMigrationConfirmed && access.profile() != null) {
            trueuuid$markMigrationPending(access.profile().getName());
        }

        if (TrueuuidConfig.debug()) {
            System.out.println("[TrueUUID] auth answer flags: migrationConfirmed=" + finalMigrationConfirmed
                    + ", missingSessionToken=" + answer.missingSessionToken());
        }

        if (TrueuuidConfig.debug()) {
            System.out.println("[TrueUUID] 客户端认证包ackOk: " + ackOk + ", hasJoinedUrl: " + (clientHasJoinedUrl.isEmpty() ? "(mojang default)" : clientHasJoinedUrl));
        }
        if (this.trueuuid$migrationConfirmation) {
            if (this.trueuuid$ackHandled) {
                if (TrueuuidConfig.debug()) {
                    System.out.println("[TrueUUID] 重复迁移确认包忽略, txId: " + this.trueuuid$txId);
                }
                ci.cancel();
                return;
            }
            this.trueuuid$ackHandled = true;
            try {
                if (!ackOk || !finalMigrationConfirmed || this.trueuuid$pendingVerifiedProfile == null
                        || this.trueuuid$pendingAuthSource == null || this.trueuuid$pendingOfflineData == null) {
                    Trueuuid.acceptance("result=migration_rejected player={} joined={} confirmed={}",
                            access.profile() == null ? "<unknown>" : access.profile().getName(), ackOk, finalMigrationConfirmed);
                    trueuuid$sendDuplicateUuidDisconnect();
                    reset();
                    return;
                }

                String migrationName = this.trueuuid$pendingVerifiedProfile.getName();
                Trueuuid.acceptance("phase=migration_answer_accepted player={} uuid={}",
                        migrationName, this.trueuuid$pendingVerifiedProfile.getId());
                trueuuid$markMigrationPending(migrationName);
                int activeTx = this.trueuuid$txId;
                this.trueuuid$currentWork = TrueuuidRuntime.MIGRATIONS.forServer(access.server()).migrate(
                        migrationName, this.trueuuid$pendingVerifiedProfile.getId())
                        .whenComplete((ignored, failure) -> access.server().execute(() -> {
                            if (this.trueuuid$txId != activeTx) return;
                            try {
                                if (failure != null) {
                                    Trueuuid.acceptance("result=migration_failed player={}", migrationName);
                                    sendDisconnectWithReason(Component.translatable("trueuuid.disconnect.migration_failed",
                                            migrationName, this.trueuuid$pendingOfflineData.offlineUuid(),
                                            this.trueuuid$pendingVerifiedProfile.getId(), Component.translatable("trueuuid.error.internal")));
                                } else {
                                    trueuuid$finalizeVerifiedLogin(this.trueuuid$pendingIp != null ? this.trueuuid$pendingIp : ip,
                                            this.trueuuid$pendingVerifiedProfile, this.trueuuid$pendingAuthSource,
                                            this.trueuuid$pendingAuthDisplayName);
                                    Trueuuid.acceptance("result=migration_complete player={} uuid={}",
                                            migrationName, this.trueuuid$pendingVerifiedProfile.getId());
                                }
                            } finally {
                                trueuuid$clearMigrationPending(migrationName);
                                reset();
                            }
                        }));
            } catch (Throwable failure) {
                handleAuthFailure(ip, "服务器异常", false);
                reset();
            }
            ci.cancel();
            return;
        }
        if (!ackOk) {
            if (TrueuuidConfig.debug()) {
                System.out.println("[TrueUUID] 认证失败, 玩家: " + (access.profile() != null ? access.profile().getName() : "<unknown>") + ", ip: " + ip + ", 原因: 客户端拒绝");
            }
            handleAuthFailure(ip, "客户端拒绝", true);
            reset(); ci.cancel(); return;
        }

        // 幂等保护：如果已经处理过本次握手的 ack，则忽略重复包 (Idempotency protection: If ack for this handshake has been processed, ignore duplicate packets)
        if (this.trueuuid$ackHandled) {
            if (TrueuuidConfig.debug()) {
                System.out.println("[TrueUUID] 重复认证包忽略, txId: " + this.trueuuid$txId);
            }
            ci.cancel();
            return;
        }
        this.trueuuid$ackHandled = true;

        // 关键：使用异步 API，不在主线程阻塞 (Key: Use async API, do not block main thread)
        final String hasJoinedUrl = clientHasJoinedUrl;
        try {
            // 立即取消原始调用（以免继续执行原有逻辑），但不要 reset()，保留状态直到回调完成
            // (Immediately cancel the original call (to avoid executing original logic), but do not reset(); keep state until callback completes)
            ci.cancel();

            int activeTx = this.trueuuid$txId;
            CompletableFuture<Optional<VerifiedProfile>> auth = SessionCheck.hasJoinedAsync(access.profile().getName(),
                    this.trueuuid$nonce, SessionCheck.publicClientIpOrEmpty(ip), hasJoinedUrl);
            this.trueuuid$currentWork = auth.thenCompose(resOpt -> {
                        if (resOpt.isEmpty()) return CompletableFuture.completedFuture(new AuthLookup(resOpt, null));
                        return TrueuuidRuntime.MIGRATIONS.forServer(access.server()).find(resOpt.get().name())
                                .thenApply(dataFound -> new AuthLookup(resOpt, dataFound.map(offer ->
                                        new PlayerDataMigration.OfflineData(offer.offlineUuid(), offer.summary())).orElse(null)));
                    }).whenComplete((lookup, throwable) -> {
                        // 始终在主线程处理后续逻辑 (Always process subsequent logic on main thread)
                        access.server().execute(() -> {
                            if (this.trueuuid$txId != activeTx) return;
                            try {
                                if (throwable != null) {
                                    if (TrueuuidConfig.debug()) {
                                        System.out.println("[TrueUUID] 认证异步回调发生异常: " + throwable);
                                    }
                                    handleAuthFailure(ip, "服务器异常", false);
                                    return;
                                }

                                if (lookup.result().isEmpty()) {
                                    if (TrueuuidConfig.debug()) {
                                        System.out.println("[TrueUUID] 认证失败, 玩家: " + (access.profile() != null ? access.profile().getName() : "<unknown>") + ", ip: " + ip + ", 原因: 会话无效");
                                    }
                                    handleAuthFailure(ip, "会话无效", false);
                                    return;
                                }

                                var res = lookup.result().get();

                                // 成功：记录注册表/近期 IP；替换为正版 UUID + 名称大小写矫正 + 注入皮肤 (Success: Record registry/recent IP; replace with premium UUID + name case correction + inject skin)
                                AuthState.AuthSource source = hasJoinedUrl.isEmpty()
                                        ? AuthState.AuthSource.MOJANG
                                        : AuthState.AuthSource.YGGDRASIL;
                                String displayName = trueuuid$authDisplayName(hasJoinedUrl);

                                GameProfile newProfile = VerifiedProfileService.create(res);
                                if (!trueuuid$completeVerifiedLogin(ip, newProfile, source, displayName, lookup.offlineData())) {
                                    return;
                                }
                                // 认证成功后只替换档案并释放暂停，后续由 Forge 原版登录 tick 继续完成协商和放入世界。
                            } catch (Throwable t) {
                                if (TrueuuidConfig.debug()) {
                                    System.out.println("[TrueUUID] 认证异步处理时发生异常: " + t);
                                }
                                handleAuthFailure(ip, "服务器异常", false);
                            } finally {
                                if (!this.trueuuid$migrationConfirmation) {
                                    if (access.profile() != null) {
                                        trueuuid$clearMigrationPending(access.profile().getName());
                                    }
                                    reset();
                                }
                            }
                        });
                    });

        } catch (Throwable t) {
            // 若构造异步调用时报错（极少见），则回退为失败处理并重置
            // (If an error occurs when constructing the async call (very rare), fall back to failure handling and reset)
            if (TrueuuidConfig.debug()) {
                System.out.println("[TrueUUID] 启动异步认证时出错: " + t);
            }
            handleAuthFailure(ip, "服务器异常", false);
            reset();
            this.trueuuid$ackHandled = false;
        }
    }

    public void onDisconnect() {
        TrueuuidRuntime.AUTH_STATE.remove(access.connection());
        if (this.trueuuid$pendingVerifiedProfile != null) {
            trueuuid$clearMigrationPending(this.trueuuid$pendingVerifiedProfile.getName());
        }
        reset();
    }

    private void handleAuthFailure(String ip, String why) {
        handleAuthFailure(ip, why, false);
    }

    private void handleAuthFailure(String ip, String why, boolean explicitOfflineClient) {
        access.profile(LoginFailureService.handle(access.profile(), access.connection(), ip, why,
                explicitOfflineClient, this::sendDisconnectWithReason));
    }

    private void sendDisconnectWithReason(Component reason) {
        // 登录监听器只能使用 LOGIN 阶段的断开流程，避免混发 PLAY 断开包导致客户端按错误协议解码。
        access.disconnect(reason);
    }

    private static void debug(String message) {
        if (TrueuuidConfig.debug()) {
            Trueuuid.LOGGER.info("[TrueUUID] {}", message);
        }
    }

    private String trueuuid$authDisplayName(String hasJoinedUrl) {
        if (hasJoinedUrl == null || hasJoinedUrl.isBlank()) {
            return "Mojang";
        }
        try {
            URI uri = URI.create(hasJoinedUrl);
            String host = uri.getHost();
            if (host != null && !host.isBlank()) {
                return host;
            }
        } catch (Throwable ignored) {}
        return "Yggdrasil skin site";
    }

    private static void trueuuid$markMigrationPending(String name) {
        TrueuuidRuntime.MIGRATION_LOCKS.mark(name);
    }

    private static void trueuuid$clearMigrationPending(String name) {
        TrueuuidRuntime.MIGRATION_LOCKS.clear(name);
    }

    private static boolean trueuuid$isMigrationPending(String name) {
        return TrueuuidRuntime.MIGRATION_LOCKS.contains(name);
    }

    private boolean trueuuid$completeVerifiedLogin(String ip, GameProfile verifiedProfile, AuthState.AuthSource source,
                                                   String displayName, PlayerDataMigration.OfflineData data) {
        if (verifiedProfile == null || verifiedProfile.getId() == null || verifiedProfile.getName() == null || verifiedProfile.getName().isBlank()) {
            sendDisconnectWithReason(Component.translatable("trueuuid.disconnect.auth_denied"));
            return false;
        }

        String name = verifiedProfile.getName();
        UUID verifiedUuid = verifiedProfile.getId();
        if (data != null && !data.offlineUuid().equals(verifiedUuid)) {
            Trueuuid.acceptance("phase=migration_needed player={} offlineUuid={} verifiedUuid={}",
                    name, data.offlineUuid(), verifiedUuid);
            trueuuid$requestOfflineUpgradeConfirmation(data, verifiedProfile, source, displayName, ip);
            return false;
        }

        trueuuid$finalizeVerifiedLogin(ip, verifiedProfile, source, displayName);
        return true;
    }

    private void trueuuid$finalizeVerifiedLogin(String ip, GameProfile verifiedProfile, AuthState.AuthSource source, String displayName) {
        String name = verifiedProfile.getName();
        UUID verifiedUuid = verifiedProfile.getId();
        access.profile(verifiedProfile);
        VerifiedProfileService.record(access.connection(), verifiedProfile, ip, source, displayName);
        Trueuuid.acceptance("result=premium_ready player={} uuid={} source={}", name, verifiedUuid, source);
        if (TrueuuidConfig.debug()) {
            System.out.println("[TrueUUID] 记录认证成功来源: " + source + ", displayName=" + displayName);
        }
    }

    private void trueuuid$requestOfflineUpgradeConfirmation(PlayerDataMigration.OfflineData data, GameProfile verifiedProfile, AuthState.AuthSource source, String displayName, String ip) {
        this.trueuuid$pendingVerifiedProfile = verifiedProfile;
        this.trueuuid$pendingAuthSource = source;
        this.trueuuid$pendingAuthDisplayName = displayName;
        this.trueuuid$pendingIp = ip;
        this.trueuuid$pendingOfflineData = data;
        this.trueuuid$migrationConfirmation = true;
        this.trueuuid$offlineUpgradeOffered = true;
        this.trueuuid$ackHandled = false;
        this.trueuuid$txId = TRUEUUID$NEXT_TX_ID.getAndIncrement();
        this.trueuuid$nonce = NetIds.MIGRATION_CONFIRM_SERVER_ID;
        this.trueuuid$sentAt = System.currentTimeMillis();
        this.loginState.beginMigration(this.trueuuid$txId,
                new MigrationTransaction.Offer(data.offlineUuid(), data.summary()), this.trueuuid$sentAt);

        Trueuuid.acceptance("phase=migration_query_sent player={} offlineUuid={} verifiedUuid={}",
                verifiedProfile.getName(), data.offlineUuid(), verifiedProfile.getId());
        access.connection().send(LoginPacketCodec.migration(this.trueuuid$txId, data));
    }

    private void trueuuid$sendDuplicateUuidDisconnect() {
        if (this.trueuuid$pendingVerifiedProfile == null || this.trueuuid$pendingAuthSource == null) {
            sendDisconnectWithReason(Component.translatable("trueuuid.disconnect.auth_denied"));
            return;
        }
        String name = this.trueuuid$pendingVerifiedProfile.getName();
        UUID verifiedUuid = this.trueuuid$pendingVerifiedProfile.getId();
        PlayerDataMigration.OfflineData data = this.trueuuid$pendingOfflineData;
        if (data == null || verifiedUuid == null) {
            sendDisconnectWithReason(Component.translatable("trueuuid.disconnect.auth_denied"));
            return;
        }

        Component sourceName = this.trueuuid$pendingAuthSource == AuthState.AuthSource.YGGDRASIL
                ? Component.translatable("trueuuid.auth_source.skin_site.with_name",
                this.trueuuid$pendingAuthDisplayName == null || this.trueuuid$pendingAuthDisplayName.isBlank() ? "Yggdrasil" : this.trueuuid$pendingAuthDisplayName)
                : Component.translatable("trueuuid.auth_source.premium");
        sendDisconnectWithReason(Component.translatable(
                "trueuuid.disconnect.duplicate_uuid",
                sourceName,
                data.offlineUuid(),
                verifiedUuid,
                name
        ));
    }

    private void reset() {
        if (TrueuuidConfig.debug()) {
            System.out.println("[TrueUUID] 状态重置, txId: " + this.trueuuid$txId);
        }
        this.trueuuid$txId = 0;
        this.trueuuid$nonce = null;
        this.trueuuid$sentAt = 0L;
        this.trueuuid$queryPending = false;
        this.trueuuid$authFinalized = true;
        this.trueuuid$offlineUpgradeOffered = false;
        this.trueuuid$migrationConfirmation = false;
        this.trueuuid$pendingVerifiedProfile = null;
        this.trueuuid$pendingAuthSource = null;
        this.trueuuid$pendingAuthDisplayName = null;
        this.trueuuid$pendingIp = null;
        this.trueuuid$pendingOfflineData = null;
        CompletableFuture<?> work = this.trueuuid$currentWork;
        this.trueuuid$currentWork = null;
        if (work != null && !work.isDone()) work.cancel(true);
        this.loginState.reset();
    }

    private record AuthLookup(Optional<VerifiedProfile> result,
                              PlayerDataMigration.OfflineData offlineData) {}
}
