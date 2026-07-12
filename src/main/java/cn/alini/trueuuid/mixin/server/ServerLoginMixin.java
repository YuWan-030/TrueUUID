// java
package cn.alini.trueuuid.mixin.server;

import cn.alini.trueuuid.config.TrueuuidConfig;
import cn.alini.trueuuid.net.AuthAnswerPayload;
import cn.alini.trueuuid.net.AuthPayload;
import cn.alini.trueuuid.net.AuthQueryTracker;
import cn.alini.trueuuid.net.NetIds;
import cn.alini.trueuuid.server.*;
import cn.alini.trueuuid.util.TrueuuidText;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import io.netty.buffer.Unpooled;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket;
import net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class ServerLoginMixin {
    @Shadow private GameProfile authenticatedProfile;
    @Shadow private MinecraftServer server;
    @Shadow private Connection connection;

    @Shadow public abstract void disconnect(Component reason);

    @Invoker("startClientVerification")
    public abstract void trueuuid$startClientVerification(GameProfile profile);

    @Invoker("verifyLoginAndFinishConnectionSetup")
    public abstract void trueuuid$verifyLoginAndFinishConnectionSetup(GameProfile profile);

    // 握手状态
    @Unique private static final AtomicInteger TRUEUUID$NEXT_TX_ID = new AtomicInteger(0x4F000000);
    @Unique private int trueuuid$txId = 0;
    @Unique private String trueuuid$nonce = null;
    @Unique private long trueuuid$sentAt = 0L;
    @Unique private boolean trueuuid$offlineUpgradeOffered = false;
    @Unique private boolean trueuuid$migrationConfirmation = false;
    @Unique private GameProfile trueuuid$pendingVerifiedProfile = null;
    @Unique private AuthState.AuthSource trueuuid$pendingAuthSource = null;
    @Unique private String trueuuid$pendingAuthDisplayName = null;
    @Unique private String trueuuid$pendingIp = null;
    @Unique private static final java.util.concurrent.ConcurrentHashMap<String, Long> TRUEUUID$MIGRATION_PENDING = new java.util.concurrent.ConcurrentHashMap<>();


    // 新增：防止重复处理客户端认证包（同次握手只处理一次）
    @Unique private volatile boolean trueuuid$ackHandled = false;

    @Inject(method = "handleHello", at = @At("TAIL"))
    private void trueuuid$afterHello(ServerboundHelloPacket pkt, CallbackInfo ci) {
        if (this.server.usesAuthentication() || this.authenticatedProfile == null) return;

        // 清理 ack 处理标志（新握手重新可处理）
        this.trueuuid$ackHandled = false;

        this.trueuuid$nonce = UUID.randomUUID().toString().replace("-", "");
        this.trueuuid$txId = TRUEUUID$NEXT_TX_ID.getAndIncrement();
        this.trueuuid$sentAt = System.currentTimeMillis();

        if (TrueuuidConfig.debug()) {
            System.out.println("[TrueUUID] handleHello: 开始握手, 玩家: " + (this.authenticatedProfile != null ? this.authenticatedProfile.getName() : "<unknown>"));
            System.out.println("[TrueUUID] 握手 nonce: " + this.trueuuid$nonce + ", txId: " + this.trueuuid$txId);
        }

        if (this.authenticatedProfile != null && trueuuid$isMigrationPending(this.authenticatedProfile.getName())) {
            sendDisconnectWithReason(Component.translatable("trueuuid.disconnect.migration_pending"));
            reset();
            return;
        }
        this.trueuuid$offlineUpgradeOffered = false;
        this.trueuuid$migrationConfirmation = false;

        AuthQueryTracker.mark(this.trueuuid$txId);
        this.connection.send(new ClientboundCustomQueryPacket(this.trueuuid$txId, new AuthPayload(this.trueuuid$nonce)));
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void trueuuid$onTick(CallbackInfo ci) {
        if (this.trueuuid$txId == 0 || this.trueuuid$sentAt == 0L) return;
        ci.cancel(); // 阻止原版 tick 推进登录状
        long timeoutMs = this.trueuuid$offlineUpgradeOffered ? Math.max(TrueuuidConfig.timeoutMs(), 180_000L) : TrueuuidConfig.timeoutMs();
        if (timeoutMs <= 0) return;

        long now = System.currentTimeMillis();
        if (now - this.trueuuid$sentAt < timeoutMs) return;

        if (TrueuuidConfig.debug()) {
            System.out.println("[TrueUUID] 握手超时, txId: " + this.trueuuid$txId);
        }

        if (this.trueuuid$offlineUpgradeOffered) {
            sendDisconnectWithReason(Component.translatable("trueuuid.disconnect.migration_confirm_timeout"));
            reset();
        } else if (TrueuuidConfig.allowOfflineOnTimeout()) {
            if (TrueuuidConfig.debug()) {
                System.out.println("[TrueUUID] 超时允许离线进入");
            }
            AuthState.markOfflineFallback(this.connection, AuthState.FallbackReason.TIMEOUT);
            trueuuid$resumeLogin();
            reset();
        } else {
            sendDisconnectWithReason(TrueuuidText.configComponent(
                    TrueuuidConfig.timeoutKickMessage(),
                    "trueuuid.disconnect.timeout"
            ));
            reset();
        }
    }

    @Inject(method = "handleCustomQueryPacket", at = @At("HEAD"), cancellable = true)
    private void trueuuid$onLoginCustom(ServerboundCustomQueryAnswerPacket packet, CallbackInfo ci) {
        if (this.trueuuid$txId == 0) return;
        if (packet.transactionId() != this.trueuuid$txId) return;

        String ip;
        if (this.connection.getRemoteAddress() instanceof InetSocketAddress isa) {
            ip = isa.getAddress().getHostAddress();
        } else {
            ip = null;
        }
        if (TrueuuidConfig.debug()) {
            System.out.println("[TrueUUID] 收到客户端认证包, 玩家: " + (this.authenticatedProfile != null ? this.authenticatedProfile.getName() : "<unknown>") + ", ip: " + ip);
        }

        AuthAnswerPayload payload = (AuthAnswerPayload) packet.payload();
        if (payload == null) {
            if (TrueuuidConfig.debug()) {
                System.out.println("[TrueUUID] 认证失败, 玩家: " + (this.authenticatedProfile != null ? this.authenticatedProfile.getName() : "<unknown>") + ", ip: " + ip + ", 原因: 缺少数据");
            }
            handleAuthFailure(ip, "缺少数据", false);
            reset(); ci.cancel(); return;
        }

        boolean ackOk =  payload.ok();
        String clientHasJoinedUrl = payload.hasJoinedUrl() == null ? "" : payload.hasJoinedUrl().trim();
        boolean migrationConfirmed = payload.migrationConfirmed();
        if (migrationConfirmed && this.authenticatedProfile != null) {
            trueuuid$markMigrationPending(this.authenticatedProfile.getName());
        }
        if (TrueuuidConfig.debug()) {
            System.out.println("[TrueUUID] auth answer flags: migrationConfirmed=" + migrationConfirmed
                    + ", missingSessionToken=" + payload.missingSessionToken());
        }
        if (TrueuuidConfig.debug()) {
            System.out.println("[TrueUUID] 客户端认证包ackOk: " + ackOk + ", hasJoinedUrl: " + (clientHasJoinedUrl.isEmpty() ? "(mojang default)" : clientHasJoinedUrl));
        }
        if (this.trueuuid$migrationConfirmation) {
            if (this.trueuuid$ackHandled) {
                if (TrueuuidConfig.debug()) {
                    System.out.println("[TrueUUID] duplicate migration confirmation ignored, txId=" + this.trueuuid$txId);
                }
                ci.cancel();
                return;
            }
            this.trueuuid$ackHandled = true;
            try {
                if (!ackOk || !migrationConfirmed || this.trueuuid$pendingVerifiedProfile == null || this.trueuuid$pendingAuthSource == null) {
                    trueuuid$sendDuplicateUuidDisconnect();
                    return;
                }

                trueuuid$markMigrationPending(this.trueuuid$pendingVerifiedProfile.getName());
                trueuuid$completeVerifiedLogin(
                        this.trueuuid$pendingIp != null ? this.trueuuid$pendingIp : ip,
                        this.trueuuid$pendingVerifiedProfile,
                        this.trueuuid$pendingAuthSource,
                        this.trueuuid$pendingAuthDisplayName,
                        true
                );
            } finally {
                if (this.trueuuid$pendingVerifiedProfile != null) {
                    trueuuid$clearMigrationPending(this.trueuuid$pendingVerifiedProfile.getName());
                }
                reset();
            }
            ci.cancel();
            return;
        }
        if (!ackOk) {
            if (TrueuuidConfig.debug()) {
                System.out.println("[TrueUUID] 认证失败, 玩家: " + (this.authenticatedProfile != null ? this.authenticatedProfile.getName() : "<unknown>") + ", ip: " + ip + ", 原因: 客户端拒绝");
            }
            handleAuthFailure(ip, "客户端拒绝", true);
            reset(); ci.cancel(); return;
        }

        // 白名单校验：如果配置了白名单且客户端上报了非默认 URL，检查 URL 是否匹配任一配置项。
        if (!clientHasJoinedUrl.isEmpty()) {
            var whitelist = TrueuuidConfig.apiRootWhitelist();
            if (!whitelist.isEmpty()) {
                boolean allowed = trueuuid$isWhitelistedAuthEndpoint(clientHasJoinedUrl, whitelist);
                if (!allowed) {
                    if (TrueuuidConfig.debug()) {
                        System.out.println("[TrueUUID] 客户端上报的 hasJoined URL 不在白名单中: " + clientHasJoinedUrl);
                    }
                    handleAuthFailure(ip, "不受信任的认证服务器", false);
                    reset(); ci.cancel(); return;
                }
            }
        }

        // 幂等保护：如果已经处理过本次握手的 ack，则忽略重复包
        if (this.trueuuid$ackHandled) {
            if (TrueuuidConfig.debug()) {
                System.out.println("[TrueUUID] 重复认证包忽略, txId: " + this.trueuuid$txId);
            }
            ci.cancel();
            return;
        }
        this.trueuuid$ackHandled = true;

        // 关键：使用异步 API，不在主线程阻塞
        final String hasJoinedUrl = clientHasJoinedUrl;
        try {
            // 立即取消原始调用（以免继续执行原有逻辑），但不要 reset()，保留状态直到回调完成
            ci.cancel();

            SessionCheck.hasJoinedAsync(this.authenticatedProfile.getName(), this.trueuuid$nonce, ip, hasJoinedUrl)
                    .thenCompose(resOpt -> {
                        if (resOpt.isPresent() || !hasJoinedUrl.isEmpty() || !trueuuid$isLocalAddress(ip)) {
                            return java.util.concurrent.CompletableFuture.completedFuture(resOpt);
                        }
                        if (TrueuuidConfig.debug()) {
                            System.out.println("[TrueUUID] Mojang hasJoined failed for local connection; trying signed Mojang profile fallback, player=" + this.authenticatedProfile.getName() + ", ip=" + ip);
                        }
                        return SessionCheck.lookupMojangProfileAsync(this.authenticatedProfile.getName());
                    })
                    .whenComplete((resOpt, throwable) -> {
                        // 始终在主线程处理后续逻辑
                        server.execute(() -> {
                            try {
                                if (throwable != null) {
                                    if (TrueuuidConfig.debug()) {
                                        System.out.println("[TrueUUID] 认证异步回调发生异常: " + throwable);
                                    }
                                    handleAuthFailure(ip, "服务器异常", false);
                                    return;
                                }

                                if (resOpt.isEmpty()) {
                                    if (TrueuuidConfig.debug()) {
                                        System.out.println("[TrueUUID] 认证失败, 玩家: " + (this.authenticatedProfile != null ? this.authenticatedProfile.getName() : "<unknown>") + ", ip: " + ip + ", 原因: 会话无效");
                                    }
                                    handleAuthFailure(ip, "会话无效", false);
                                    return;
                                }

                                var res = resOpt.get();

                                // 成功：记录注册表/近期 IP；替换为正版 UUID + 名称大小写矫正 + 注入皮肤
                                AuthState.AuthSource source = hasJoinedUrl.isEmpty()
                                        ? AuthState.AuthSource.MOJANG
                                        : AuthState.AuthSource.YGGDRASIL;
                                String displayName = trueuuid$authDisplayName(hasJoinedUrl);

                                GameProfile newProfile = new GameProfile(res.uuid(), res.name());
                                var propMap = newProfile.getProperties();
                                propMap.removeAll("textures");
                                for (var p : res.properties()) {
                                    if (p.signature() != null) {
                                        propMap.put(p.name(), new Property(p.name(), p.value(), p.signature()));
                                    } else {
                                        propMap.put(p.name(), new Property(p.name(), p.value()));
                                    }
                                }
                                if (!trueuuid$completeVerifiedLogin(ip, newProfile, source, displayName, migrationConfirmed)) {
                                    return;
                                }
                            } catch (Throwable t) {
                                if (TrueuuidConfig.debug()) {
                                    System.out.println("[TrueUUID] 认证异步处理时发生异常: " + t);
                                }
                                handleAuthFailure(ip, "服务器异常", false);
                            } finally {
                                if (!this.trueuuid$migrationConfirmation) {
                                    if (this.authenticatedProfile != null) {
                                        trueuuid$clearMigrationPending(this.authenticatedProfile.getName());
                                    }
                                    reset();
                                }
                            }
                        });
                    });

        } catch (Throwable t) {
            // 若构造异步调用时报错（极少见），则回退为失败处理并重置
            if (TrueuuidConfig.debug()) {
                System.out.println("[TrueUUID] 启动异步认证时出错: " + t);
            }
            handleAuthFailure(ip, "服务器异常", false);
            reset();
            this.trueuuid$ackHandled = false;
        }
    }

    @Unique
    private void handleAuthFailure(String ip, String why, boolean explicitOfflineClient) {
        String name = this.authenticatedProfile != null ? this.authenticatedProfile.getName() : "<unknown>";
        System.out.println("[TrueUUID] 认证失败, 玩家: " + name + ", ip: " + ip + ", 原因: " + why);
        if (TrueuuidConfig.debug()) {
            System.out.println("[TrueUUID] 会话无效, 玩家: " + name + ", ip: " + ip + ", 失败原因: " + why);
        }
        AuthDecider.Decision d = AuthDecider.onFailure(name, ip, explicitOfflineClient);

        switch (d.kind) {
            case PREMIUM_GRACE -> {
                UUID premium = d.premiumUuid != null ? d.premiumUuid
                        : TrueuuidRuntime.NAME_REGISTRY.getPremiumUuid(name).orElse(null);
                if (premium != null) {
                    System.out.println("[TrueUUID] 使用近期同 IP 容错按正版 UUID 放行, 玩家: " + name + ", ip: " + ip + ", uuid: " + premium);
                    this.authenticatedProfile = new GameProfile(premium, name);
                    AuthState.AuthSource cachedSource = d.graceSource != null ? d.graceSource : AuthState.AuthSource.MOJANG;
                    String cachedName = d.graceDisplayName != null ? d.graceDisplayName : "Recent same-IP grace";
                    AuthState.markAuthSuccess(this.connection, premium, name, cachedSource, cachedName);
                    trueuuid$resumeLogin();
                } else {
                    System.out.println("[TrueUUID] 容错未找到正版 UUID，改为离线兜底, 玩家: " + name + ", ip: " + ip);
                    AuthState.markOfflineFallback(this.connection, AuthState.FallbackReason.FAILURE);
                    trueuuid$resumeLogin();
                }
            }
            case OFFLINE -> {
                System.out.println("[TrueUUID] 鉴权失败后允许离线兜底, 玩家: " + name + ", ip: " + ip);
                if (TrueuuidConfig.debug()) {
                    System.out.println("[TrueUUID] 离线进入");
                }
                AuthState.markOfflineFallback(this.connection, AuthState.FallbackReason.FAILURE);
                trueuuid$resumeLogin();
            }
            case DENY -> {
                Component msg = d.denyComponent != null ? d.denyComponent
                        : d.denyMessage != null ? Component.literal(d.denyMessage)
                        : Component.translatable("trueuuid.disconnect.auth_denied");
                System.out.println("[TrueUUID] 认证被拒绝, 玩家: " + name + ", ip: " + ip + ", 原因: " + why + ", 消息: " + msg);
                if (TrueuuidConfig.debug()) {
                    System.out.println("[TrueUUID] 认证被拒绝, 玩家: " + name + ", ip: " + ip + ", 消息: " + msg);
                }
                sendDisconnectWithReason(msg);
            }
        }
    }

    @Unique
    private void sendDisconnectWithReason(Component reason) {
        // 登录监听器只能使用 LOGIN 阶段的断开流程，避免混发 PLAY 断开包导致客户端按错误协议解码。
        this.disconnect(reason);
    }

    @Unique
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

    @Unique
    private static boolean trueuuid$isLocalAddress(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        return "127.0.0.1".equals(ip)
                || "0:0:0:0:0:0:0:1".equals(ip)
                || "::1".equals(ip)
                || "localhost".equalsIgnoreCase(ip);
    }

    @Unique
    private static void trueuuid$markMigrationPending(String name) {
        if (name == null || name.isBlank()) return;
        TRUEUUID$MIGRATION_PENDING.put(name.toLowerCase(java.util.Locale.ROOT), System.currentTimeMillis());
    }

    @Unique
    private static void trueuuid$clearMigrationPending(String name) {
        if (name == null || name.isBlank()) return;
        TRUEUUID$MIGRATION_PENDING.remove(name.toLowerCase(java.util.Locale.ROOT));
    }

    @Unique
    private static boolean trueuuid$isMigrationPending(String name) {
        if (name == null || name.isBlank()) return false;
        String key = name.toLowerCase(java.util.Locale.ROOT);
        Long since = TRUEUUID$MIGRATION_PENDING.get(key);
        if (since == null) return false;
        if (System.currentTimeMillis() - since > 30_000L) {
            TRUEUUID$MIGRATION_PENDING.remove(key, since);
            return false;
        }
        return true;
    }

    @Unique
    private boolean trueuuid$completeVerifiedLogin(String ip, GameProfile verifiedProfile, AuthState.AuthSource source, String displayName, boolean migrationConfirmed) {
        if (verifiedProfile == null || verifiedProfile.getId() == null || verifiedProfile.getName() == null || verifiedProfile.getName().isBlank()) {
            sendDisconnectWithReason(Component.translatable("trueuuid.disconnect.auth_denied"));
            return false;
        }

        String name = verifiedProfile.getName();
        UUID verifiedUuid = verifiedProfile.getId();
        PlayerDataMigration.OfflineData data = PlayerDataMigration.findOfflineData(this.server, name);
        if (data != null && !data.offlineUuid().equals(verifiedUuid)) {
            if (!migrationConfirmed) {
                trueuuid$requestOfflineUpgradeConfirmation(data, verifiedProfile, source, displayName, ip);
                return false;
            }
            try {
                PlayerDataMigration.migrateOfflineToVerified(this.server, name, verifiedUuid);
                if (TrueuuidConfig.debug()) {
                    System.out.println("[TrueUUID] migrated offline data before login: player=" + name
                            + ", offlineUuid=" + data.offlineUuid() + ", verifiedUuid=" + verifiedUuid);
                }
            } catch (Exception ex) {
                System.out.println("[TrueUUID] offline player data migration failed, player=" + name + ", offlineUuid=" + data.offlineUuid()
                        + ", verifiedUuid=" + verifiedUuid + ", error=" + ex);
                sendDisconnectWithReason(Component.translatable(
                        "trueuuid.disconnect.migration_failed",
                        name,
                        data.offlineUuid(),
                        verifiedUuid,
                        ex.getMessage()
                ));
                return false;
            }
        }

        this.authenticatedProfile = verifiedProfile;
        AuthState.markAuthSuccess(this.connection, verifiedUuid, name, source, displayName);
        TrueuuidRuntime.NAME_REGISTRY.recordSuccess(name, verifiedUuid, ip, source, displayName);
        TrueuuidRuntime.IP_GRACE.record(name, ip, verifiedUuid, source, displayName);
        trueuuid$resumeLogin();
        if (TrueuuidConfig.debug()) {
            System.out.println("[TrueUUID] recorded successful authentication source=" + source + ", displayName=" + displayName);
        }
        return true;
    }

    @Unique
    private void trueuuid$requestOfflineUpgradeConfirmation(PlayerDataMigration.OfflineData data, GameProfile verifiedProfile, AuthState.AuthSource source, String displayName, String ip) {
        this.trueuuid$pendingVerifiedProfile = verifiedProfile;
        this.trueuuid$pendingAuthSource = source;
        this.trueuuid$pendingAuthDisplayName = displayName;
        this.trueuuid$pendingIp = ip;
        this.trueuuid$migrationConfirmation = true;
        this.trueuuid$offlineUpgradeOffered = true;
        this.trueuuid$ackHandled = false;
        this.trueuuid$txId = TRUEUUID$NEXT_TX_ID.getAndIncrement();
        this.trueuuid$nonce = NetIds.MIGRATION_CONFIRM_SERVER_ID;
        this.trueuuid$sentAt = System.currentTimeMillis();

        AuthQueryTracker.mark(this.trueuuid$txId);
        AuthPayload payload = new AuthPayload(
                NetIds.MIGRATION_CONFIRM_SERVER_ID,
                true,
                data.offlineUuid().toString(),
                data.summary()
        );
        this.connection.send(new ClientboundCustomQueryPacket(this.trueuuid$txId, payload));
    }

    @Unique
    private void trueuuid$sendDuplicateUuidDisconnect() {
        if (this.trueuuid$pendingVerifiedProfile == null || this.trueuuid$pendingAuthSource == null) {
            sendDisconnectWithReason(Component.translatable("trueuuid.disconnect.auth_denied"));
            return;
        }
        String name = this.trueuuid$pendingVerifiedProfile.getName();
        UUID verifiedUuid = this.trueuuid$pendingVerifiedProfile.getId();
        PlayerDataMigration.OfflineData data = PlayerDataMigration.findOfflineData(this.server, name);
        if (data == null || verifiedUuid == null) {
            sendDisconnectWithReason(Component.translatable("trueuuid.disconnect.auth_denied"));
            return;
        }

        Component sourceName = this.trueuuid$pendingAuthSource == AuthState.AuthSource.YGGDRASIL
                ? Component.translatable(
                "trueuuid.auth_source.skin_site.with_name",
                this.trueuuid$pendingAuthDisplayName == null || this.trueuuid$pendingAuthDisplayName.isBlank()
                        ? "Yggdrasil"
                        : this.trueuuid$pendingAuthDisplayName
        )
                : Component.translatable("trueuuid.auth_source.premium");
        sendDisconnectWithReason(Component.translatable(
                "trueuuid.disconnect.duplicate_uuid",
                sourceName,
                data.offlineUuid(),
                verifiedUuid,
                name
        ));
    }

    @Unique
    private static boolean trueuuid$isWhitelistedAuthEndpoint(String hasJoinedUrl, List<String> whitelist) {
        if (hasJoinedUrl == null || hasJoinedUrl.isBlank()) {
            return false;
        }
        String host;
        try {
            host = URI.create(hasJoinedUrl).getHost();
        } catch (Throwable ignored) {
            return false;
        }
        if (host == null || host.isBlank()) {
            return false;
        }
        String normalizedHost = host.toLowerCase(java.util.Locale.ROOT);
        for (String entry : whitelist) {
            String allowed = trueuuid$normalizeWhitelistHost(entry);
            if (!allowed.isEmpty() && (normalizedHost.equals(allowed) || normalizedHost.endsWith("." + allowed))) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private static String trueuuid$normalizeWhitelistHost(String entry) {
        if (entry == null || entry.isBlank()) {
            return "";
        }
        String value = entry.trim().toLowerCase(java.util.Locale.ROOT);
        try {
            if (value.contains("://")) {
                String host = URI.create(value).getHost();
                return host == null ? "" : host.toLowerCase(java.util.Locale.ROOT);
            }
        } catch (Throwable ignored) {
            return "";
        }
        while (value.startsWith(".")) {
            value = value.substring(1);
        }
        int slash = value.indexOf('/');
        if (slash >= 0) {
            value = value.substring(0, slash);
        }
        int port = value.indexOf(':');
        if (port >= 0) {
            value = value.substring(0, port);
        }
        return value;
    }

    @Unique
    private void reset() {
        if (TrueuuidConfig.debug()) {
            System.out.println("[TrueUUID] 状态重置, txId: " + this.trueuuid$txId);
        }
        AuthQueryTracker.consume(this.trueuuid$txId);
        this.trueuuid$txId = 0;
        this.trueuuid$nonce = null;
        this.trueuuid$sentAt = 0L;
        this.trueuuid$offlineUpgradeOffered = false;
        this.trueuuid$migrationConfirmation = false;
        this.trueuuid$pendingVerifiedProfile = null;
        this.trueuuid$pendingAuthSource = null;
        this.trueuuid$pendingAuthDisplayName = null;
        this.trueuuid$pendingIp = null;
    }

    @Unique
    private void trueuuid$resumeLogin() {
        if (this.authenticatedProfile == null) {
            return;
        }
        GameProfile profile = this.authenticatedProfile;
        if (TrueuuidConfig.debug()) {
            System.out.println("[TrueUUID] resume vanilla login verification: player=" + profile.getName()
                    + ", uuid=" + profile.getId());
        }
        this.server.execute(() -> {
            if (TrueuuidConfig.debug()) {
                System.out.println("[TrueUUID] finish vanilla login now: player=" + profile.getName()
                        + ", uuid=" + profile.getId());
            }
            trueuuid$verifyLoginAndFinishConnectionSetup(profile);
        });
    }

}
