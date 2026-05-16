// java
package cn.alini.trueuuid.mixin.server;

import cn.alini.trueuuid.config.TrueuuidConfig;
import cn.alini.trueuuid.net.NetIds;
import cn.alini.trueuuid.server.*;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import io.netty.buffer.Unpooled;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.ServerboundCustomQueryPacket;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Mixin(targets = "net.minecraft.server.network.ServerLoginPacketListenerImpl")
public abstract class ServerLoginMixin {
    @Shadow private GameProfile gameProfile;
    @Shadow private MinecraftServer server;
    @Shadow private Connection connection; // 1.20.1 是字段 (is a field)

    @Shadow public abstract void disconnect(Component reason);
    // 握手状态 (handshake state)
    // 使用高位事务号，避免和 Forge/FML 登录握手从 0 开始分配的 CustomQuery 事务号撞车。
    @Unique private static final AtomicInteger TRUEUUID$NEXT_TX_ID = new AtomicInteger(0x4F000000);
    @Unique private int trueuuid$txId = 0;
    @Unique private String trueuuid$nonce = null;
    @Unique private long trueuuid$sentAt = 0L;


    // 新增：防止重复处理客户端认证包（同次握手只处理一次）(Added: Prevent duplicate processing of client auth packets (only process once per handshake))
    @Unique private volatile boolean trueuuid$ackHandled = false;

    @Inject(method = "handleHello", at = @At("TAIL"))
    private void trueuuid$afterHello(ServerboundHelloPacket pkt, CallbackInfo ci) {
        if (this.server.usesAuthentication() || this.gameProfile == null) return;

        // 若开启 nomojang，则直接使用本地策略，不向客户端发送会话认证包 (If nomojang is enabled, use local policy directly, do not send session auth packet to client)
        if (TrueuuidConfig.nomojangEnabled()) {
            String name = this.gameProfile.getName();
            String ip;
            if (this.connection.getRemoteAddress() instanceof InetSocketAddress isa) {
                ip = isa.getAddress().getHostAddress();
            } else {
                ip = null;
            }
            if (TrueuuidConfig.debug()) {
                System.out.println("[TrueUUID] nomojang 模式：跳过 Mojang 会话认证, 玩家: " + (name != null ? name : "<unknown>") + ", ip: " + ip);
            }

            // 尝试同 IP 的近期容错命中 -> 视为正版 (Try recent same IP grace hit -> Treat as premium)
            if (TrueuuidConfig.recentIpGraceEnabled() && ip != null) {
                var pOpt = TrueuuidRuntime.IP_GRACE.tryGraceResult(name, ip, TrueuuidConfig.recentIpGraceTtlSeconds());
                if (pOpt.isPresent()) {
                    UUID premium = pOpt.get().premiumUuid();
                    if (premium != null) {
                        if (TrueuuidConfig.debug()) {
                            System.out.println("[TrueUUID] nomojang: 找到同IP正版记录，按正版处理, uuid=" + premium);
                        }
                        GameProfile newProfile = new GameProfile(premium, name);
                        this.gameProfile = newProfile;
                        AuthState.AuthSource source = pOpt.get().source() == AuthState.AuthSource.YGGDRASIL
                                ? AuthState.AuthSource.YGGDRASIL
                                : AuthState.AuthSource.MOJANG;
                        AuthState.markAuthSuccess(this.connection, source, pOpt.get().displayName());
                        // 记录成功（保持注册表/缓存一致） (Record success (keep registry/cache consistent))
                        TrueuuidRuntime.NAME_REGISTRY.recordSuccess(name, premium, ip, source, pOpt.get().displayName());
                        return; // 直接返回，按正版处理完毕 (Return directly, premium processing complete)
                    }
                }
            }

            // 其余情况：直接按离线处理（不阻止进入） (Other cases: Treat as offline directly (do not block entry))
            if (TrueuuidConfig.debug()) {
                System.out.println("[TrueUUID] nomojang: 未命中同IP正版记录，按离线方式放行");
            }
            // 不发送自定义认证包，保持默认的离线行为 (Do not send custom auth packet, keep default offline behavior)
            return;
        }


        // 清理 ack 处理标志（新握手重新可处理） (Clear ack handled flag (new handshake can be processed again))
        this.trueuuid$ackHandled = false;

        this.trueuuid$nonce = UUID.randomUUID().toString().replace("-", "");
        this.trueuuid$txId = TRUEUUID$NEXT_TX_ID.getAndIncrement();
        this.trueuuid$sentAt = System.currentTimeMillis();

        if (TrueuuidConfig.debug()) {
            System.out.println("[TrueUUID] handleHello: 开始握手, 玩家: " + (this.gameProfile != null ? this.gameProfile.getName() : "<unknown>"));
            System.out.println("[TrueUUID] 握手 nonce: " + this.trueuuid$nonce + ", txId: " + this.trueuuid$txId);
        }

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUtf(this.trueuuid$nonce);

        this.connection.send(new ClientboundCustomQueryPacket(this.trueuuid$txId, NetIds.AUTH, buf));
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void trueuuid$onTick(CallbackInfo ci) {
        if (this.trueuuid$txId == 0 || this.trueuuid$sentAt == 0L) return;
        long timeoutMs = TrueuuidConfig.timeoutMs();
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

        if (TrueuuidConfig.allowOfflineOnTimeout()) {
            if (TrueuuidConfig.debug()) {
                System.out.println("[TrueUUID] 超时允许离线进入");
            }
            AuthState.markOfflineFallback(this.connection, AuthState.FallbackReason.TIMEOUT);
            reset();
        } else {
            String msg = TrueuuidConfig.timeoutKickMessage();
            Component reason = Component.literal(msg != null ? msg : "登录超时，未完成账号校验");
            sendDisconnectWithReason(reason);
            reset();
            ci.cancel();
        }
    }

    @Inject(method = "handleCustomQueryPacket", at = @At("HEAD"), cancellable = true)
    private void trueuuid$onLoginCustom(ServerboundCustomQueryPacket packet, CallbackInfo ci) {
        if (this.trueuuid$txId == 0) return;
        if (packet.getTransactionId() != this.trueuuid$txId) return;

        String ip;
        if (this.connection.getRemoteAddress() instanceof InetSocketAddress isa) {
            ip = isa.getAddress().getHostAddress();
        } else {
            ip = null;
        }
        if (TrueuuidConfig.debug()) {
            System.out.println("[TrueUUID] 收到客户端认证包, 玩家: " + (this.gameProfile != null ? this.gameProfile.getName() : "<unknown>") + ", ip: " + ip);
        }

        FriendlyByteBuf data = packet.getData();
        if (data == null) {
            if (TrueuuidConfig.debug()) {
                System.out.println("[TrueUUID] 认证失败, 玩家: " + (this.gameProfile != null ? this.gameProfile.getName() : "<unknown>") + ", ip: " + ip + ", 原因: 缺少数据");
            }
            handleAuthFailure(ip, "缺少数据");
            reset(); ci.cancel(); return;
        }

        boolean ackOk = false;
        try { ackOk = data.readBoolean(); } catch (Throwable ignored) {}

        // 读取客户端附带的 hasJoined URL（authlib-injector 皮肤站支持）
        // 空字符串或读取失败 = 使用 Mojang 默认
        String clientHasJoinedUrl = "";
        try {
            if (data.isReadable()) {
                clientHasJoinedUrl = data.readUtf();
            }
        } catch (Throwable ignored) {}

        if (TrueuuidConfig.debug()) {
            System.out.println("[TrueUUID] 客户端认证包ackOk: " + ackOk + ", hasJoinedUrl: " + (clientHasJoinedUrl.isEmpty() ? "(mojang default)" : clientHasJoinedUrl));
        }
        if (!ackOk) {
            if (TrueuuidConfig.debug()) {
                System.out.println("[TrueUUID] 认证失败, 玩家: " + (this.gameProfile != null ? this.gameProfile.getName() : "<unknown>") + ", ip: " + ip + ", 原因: 客户端拒绝");
            }
            handleAuthFailure(ip, "客户端拒绝");
            reset(); ci.cancel(); return;
        }

        // 白名单校验：如果配置了白名单且客户端上报了非默认 URL，检查域名是否在白名单中
        if (!clientHasJoinedUrl.isEmpty()) {
            var whitelist = TrueuuidConfig.apiRootWhitelist();
            if (!whitelist.isEmpty()) {
                boolean allowed = false;
                for (String entry : whitelist) {
                    if (clientHasJoinedUrl.contains(entry)) {
                        allowed = true;
                        break;
                    }
                }
                if (!allowed) {
                    if (TrueuuidConfig.debug()) {
                        System.out.println("[TrueUUID] 客户端上报的 hasJoined URL 不在白名单中: " + clientHasJoinedUrl);
                    }
                    handleAuthFailure(ip, "不受信任的认证服务器");
                    reset(); ci.cancel(); return;
                }
            }
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

            SessionCheck.hasJoinedAsync(this.gameProfile.getName(), this.trueuuid$nonce, ip, hasJoinedUrl)
                    .whenComplete((resOpt, throwable) -> {
                        // 始终在主线程处理后续逻辑 (Always process subsequent logic on main thread)
                        server.execute(() -> {
                            try {
                                if (throwable != null) {
                                    if (TrueuuidConfig.debug()) {
                                        System.out.println("[TrueUUID] 认证异步回调发生异常: " + throwable);
                                    }
                                    handleAuthFailure(ip, "服务器异常");
                                    return;
                                }

                                if (resOpt.isEmpty()) {
                                    if (TrueuuidConfig.debug()) {
                                        System.out.println("[TrueUUID] 认证失败, 玩家: " + (this.gameProfile != null ? this.gameProfile.getName() : "<unknown>") + ", ip: " + ip + ", 原因: 会话无效");
                                    }
                                    handleAuthFailure(ip, "会话无效");
                                    return;
                                }

                                var res = resOpt.get();

                                // 成功：记录注册表/近期 IP；替换为正版 UUID + 名称大小写矫正 + 注入皮肤 (Success: Record registry/recent IP; replace with premium UUID + name case correction + inject skin)
                                AuthState.AuthSource source = hasJoinedUrl.isEmpty()
                                        ? AuthState.AuthSource.MOJANG
                                        : AuthState.AuthSource.YGGDRASIL;
                                String displayName = trueuuid$authDisplayName(hasJoinedUrl);

                                TrueuuidRuntime.NAME_REGISTRY.recordSuccess(res.name(), res.uuid(), ip, source, displayName);
                                TrueuuidRuntime.IP_GRACE.record(res.name(), ip, res.uuid(), source, displayName);

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
                                this.gameProfile = newProfile;
                                AuthState.markAuthSuccess(this.connection, res.uuid(), res.name(), source, displayName);
                                if (TrueuuidConfig.debug()) {
                                    System.out.println("[TrueUUID] 记录认证成功来源: " + source + ", displayName=" + displayName);
                                }
                                // 认证成功后只替换档案并释放暂停，后续由 Forge 原版登录 tick 继续完成协商和放入世界。
                            } catch (Throwable t) {
                                if (TrueuuidConfig.debug()) {
                                    System.out.println("[TrueUUID] 认证异步处理时发生异常: " + t);
                                }
                                handleAuthFailure(ip, "服务器异常");
                            } finally {
                                reset();
                            }
                        });
                    });

        } catch (Throwable t) {
            // 若构造异步调用时报错（极少见），则回退为失败处理并重置
            // (If an error occurs when constructing the async call (very rare), fall back to failure handling and reset)
            if (TrueuuidConfig.debug()) {
                System.out.println("[TrueUUID] 启动异步认证时出错: " + t);
            }
            handleAuthFailure(ip, "服务器异常");
            reset();
            this.trueuuid$ackHandled = false;
        }
    }

    @Unique
    private void handleAuthFailure(String ip, String why) {
        String name = this.gameProfile != null ? this.gameProfile.getName() : "<unknown>";
        System.out.println("[TrueUUID] 认证失败, 玩家: " + name + ", ip: " + ip + ", 原因: " + why);
        if (TrueuuidConfig.debug()) {
            System.out.println("[TrueUUID] 会话无效, 玩家: " + name + ", ip: " + ip + ", 失败原因: " + why);
        }
        AuthDecider.Decision d = AuthDecider.onFailure(name, ip);

        switch (d.kind) {
            case PREMIUM_GRACE -> {
                UUID premium = d.premiumUuid != null ? d.premiumUuid
                        : TrueuuidRuntime.NAME_REGISTRY.getPremiumUuid(name).orElse(null);
                if (premium != null) {
                    System.out.println("[TrueUUID] 使用近期同 IP 容错按正版 UUID 放行, 玩家: " + name + ", ip: " + ip + ", uuid: " + premium);
                    this.gameProfile = new GameProfile(premium, name);
                    AuthState.AuthSource cachedSource = d.graceSource != null ? d.graceSource : AuthState.AuthSource.MOJANG;
                    String cachedName = d.graceDisplayName != null ? d.graceDisplayName : "近期同IP容错";
                    AuthState.markAuthSuccess(this.connection, premium, name, cachedSource, cachedName);
                } else {
                    System.out.println("[TrueUUID] 容错未找到正版 UUID，改为离线兜底, 玩家: " + name + ", ip: " + ip);
                    AuthState.markOfflineFallback(this.connection, AuthState.FallbackReason.FAILURE);
                }
            }
            case OFFLINE -> {
                System.out.println("[TrueUUID] 鉴权失败后允许离线兜底, 玩家: " + name + ", ip: " + ip);
                if (TrueuuidConfig.debug()) {
                    System.out.println("[TrueUUID] 离线进入");
                }
                AuthState.markOfflineFallback(this.connection, AuthState.FallbackReason.FAILURE);
            }
            case DENY -> {
                String msg = d.denyMessage != null ? d.denyMessage
                        : "鉴权失败，已禁止离线进入以保护你的正版存档。请稍后重试。";
                System.out.println("[TrueUUID] 认证被拒绝, 玩家: " + name + ", ip: " + ip + ", 原因: " + why + ", 消息: " + msg);
                if (TrueuuidConfig.debug()) {
                    System.out.println("[TrueUUID] 认证被拒绝, 玩家: " + name + ", ip: " + ip + ", 消息: " + msg);
                }
                sendDisconnectWithReason(Component.literal(msg));
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
        return "Yggdrasil 皮肤站";
    }

    @Unique
    private void reset() {
        if (TrueuuidConfig.debug()) {
            System.out.println("[TrueUUID] 状态重置, txId: " + this.trueuuid$txId);
        }
        this.trueuuid$txId = 0;
        this.trueuuid$nonce = null;
        this.trueuuid$sentAt = 0L;
    }
}
