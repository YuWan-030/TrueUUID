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
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket;
import net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.network.protocol.login.custom.CustomQueryAnswerPayload;
import net.minecraft.network.protocol.login.custom.CustomQueryPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Mixin(targets = "net.minecraft.server.network.ServerLoginPacketListenerImpl")
public abstract class ServerLoginMixin {
    @Shadow private GameProfile authenticatedProfile;
    @Shadow private MinecraftServer server;
    @Shadow private Connection connection;

    @Shadow public abstract void disconnect(Component reason);

    // 握手状态
    @Unique private static final AtomicInteger TRUEUUID$NEXT_TX_ID = new AtomicInteger(1);
    @Unique private int trueuuid$txId = 0;
    @Unique private String trueuuid$nonce = null;
    @Unique private long trueuuid$sentAt = 0L;


    // 新增：防止重复处理客户端认证包（同次握手只处理一次）
    @Unique private volatile boolean trueuuid$ackHandled = false;

    @Inject(method = "handleHello", at = @At("TAIL"))
    private void trueuuid$afterHello(ServerboundHelloPacket pkt, CallbackInfo ci) {
        if (this.server.usesAuthentication() || this.authenticatedProfile == null) return;

        // 若开启 nomojang，则直接使用本地策略，不向客户端发送会话认证包
        if (TrueuuidConfig.nomojangEnabled()) {
            String name = this.authenticatedProfile.getName();
            String ip;
            if (this.connection.getRemoteAddress() instanceof InetSocketAddress isa) {
                ip = isa.getAddress().getHostAddress();
            } else {
                ip = null;
            }
            if (TrueuuidConfig.debug()) {
                System.out.println("[TrueUUID] nomojang 模式：跳过 Mojang 会话认证, 玩家: " + (name != null ? name : "<unknown>") + ", ip: " + ip);
            }

            // 尝试同 IP 的近期容错命中 -> 视为正版
            if (TrueuuidConfig.recentIpGraceEnabled() && ip != null) {
                var pOpt = TrueuuidRuntime.IP_GRACE.tryGrace(name, ip, TrueuuidConfig.recentIpGraceTtlSeconds());
                if (pOpt.isPresent()) {
                    UUID premium = pOpt.get();
                    if (premium != null) {
                        if (TrueuuidConfig.debug()) {
                            System.out.println("[TrueUUID] nomojang: 找到同IP正版记录，按正版处理, uuid=" + premium);
                        }
                        GameProfile newProfile = new GameProfile(premium, name);
                        this.authenticatedProfile = newProfile;
                        // 记录成功（保持注册表/缓存一致）
                        TrueuuidRuntime.NAME_REGISTRY.recordSuccess(name, premium, ip);
                        TrueuuidRuntime.IP_GRACE.record(name, ip, premium);
                        return; // 直接返回，按正版处理完毕
                    }
                }
            }

            // 其余情况：直接按离线处理（不阻止进入）
            if (TrueuuidConfig.debug()) {
                System.out.println("[TrueUUID] nomojang: 未命中同IP正版记录，按离线方式放行");
            }
            // 不发送自定义认证包，保持默认的离线行为
            return;
        }


        // 清理 ack 处理标志（新握手重新可处理）
        this.trueuuid$ackHandled = false;

        this.trueuuid$nonce = UUID.randomUUID().toString().replace("-", "");
        this.trueuuid$txId = TRUEUUID$NEXT_TX_ID.getAndIncrement();
        this.trueuuid$sentAt = System.currentTimeMillis();

        if (TrueuuidConfig.debug()) {
            System.out.println("[TrueUUID] handleHello: 开始握手, 玩家: " + (this.authenticatedProfile != null ? this.authenticatedProfile.getName() : "<unknown>"));
            System.out.println("[TrueUUID] 握手 nonce: " + this.trueuuid$nonce + ", txId: " + this.trueuuid$txId);
        }

        // 创建匿名 payload，包装你的 nonce
        final String nonce = this.trueuuid$nonce;
        CustomQueryPayload payload = new CustomQueryPayload() {
            @Override
            public ResourceLocation id() {
                return NetIds.AUTH;
            }

            @Override
            public void write(FriendlyByteBuf buf) {
                buf.writeUtf(nonce);
            }
        };

        this.connection.send(new ClientboundCustomQueryPacket(this.trueuuid$txId, payload));
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void trueuuid$onTick(CallbackInfo ci) {
        if (this.trueuuid$txId == 0 || this.trueuuid$sentAt == 0L) return;
        long timeoutMs = TrueuuidConfig.timeoutMs();
        if (timeoutMs <= 0) return;

        long now = System.currentTimeMillis();
        if (now - this.trueuuid$sentAt < timeoutMs) return;

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

        CustomQueryAnswerPayload payload = packet.payload();
        if (payload == null) {
            if (TrueuuidConfig.debug()) {
                System.out.println("[TrueUUID] 认证失败, 玩家: " + (this.authenticatedProfile != null ? this.authenticatedProfile.getName() : "<unknown>") + ", ip: " + ip + ", 原因: 缺少数据");
            }
            handleAuthFailure(ip, "缺少数据");
            reset(); ci.cancel(); return;
        }

        boolean ackOk = false;
        try {
            // 从 payload 中读取 boolean
            FriendlyByteBuf tempBuf = new FriendlyByteBuf(Unpooled.buffer());
            payload.write(tempBuf);
            ackOk = tempBuf.readBoolean();
            tempBuf.release();} catch (Throwable ignored) {}
        if (TrueuuidConfig.debug()) {
            System.out.println("[TrueUUID] 客户端认证包ackOk: " + ackOk);
        }
        if (!ackOk) {
            if (TrueuuidConfig.debug()) {
                System.out.println("[TrueUUID] 认证失败, 玩家: " + (this.authenticatedProfile != null ? this.authenticatedProfile.getName() : "<unknown>") + ", ip: " + ip + ", 原因: 客户端拒绝");
            }
            handleAuthFailure(ip, "客户端拒绝");
            reset(); ci.cancel(); return;
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
        try {
            // 立即取消原始调用（以免继续执行原有逻辑），但不要 reset()，保留状态直到回调完成
            ci.cancel();

            SessionCheck.hasJoinedAsync(this.authenticatedProfile.getName(), this.trueuuid$nonce, ip)
                    .whenComplete((resOpt, throwable) -> {
                        // 始终在主线程处理后续逻辑
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
                                        System.out.println("[TrueUUID] 认证失败, 玩家: " + (this.authenticatedProfile != null ? this.authenticatedProfile.getName() : "<unknown>") + ", ip: " + ip + ", 原因: 会话无效");
                                    }
                                    handleAuthFailure(ip, "会话无效");
                                    return;
                                }

                                var res = resOpt.get();

                                // 成功：记录注册表/近期 IP；替换为正版 UUID + 名称大小写矫正 + 注入皮肤
                                TrueuuidRuntime.NAME_REGISTRY.recordSuccess(res.name(), res.uuid(), ip);
                                TrueuuidRuntime.IP_GRACE.record(res.name(), ip, res.uuid());

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
                                this.authenticatedProfile = newProfile;
                                try {
                                    Method method = this.getClass().getDeclaredMethod("m_10055_");
                                    method.setAccessible(true);
                                    method.invoke(this);
                                } catch (Exception e) {
                                    if (TrueuuidConfig.debug()) {
                                        System.out.println("[TrueUUID] 调用失败: " + e);
                                    }
                                    disconnect(Component.literal("服务器错误，请稍后重试"));
                                }
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
        String name = this.authenticatedProfile != null ? this.authenticatedProfile.getName() : "<unknown>";
        if (TrueuuidConfig.debug()) {
            System.out.println("[TrueUUID] 会话无效, 玩家: " + name + ", ip: " + ip + ", 失败原因: " + why);
        }
        AuthDecider.Decision d = AuthDecider.onFailure(name, ip);

        switch (d.kind) {
            case PREMIUM_GRACE -> {
                UUID premium = d.premiumUuid != null ? d.premiumUuid
                        : TrueuuidRuntime.NAME_REGISTRY.getPremiumUuid(name).orElse(null);
                if (premium != null) {
                    this.authenticatedProfile = new GameProfile(premium, name);
                } else {
                    AuthState.markOfflineFallback(this.connection, AuthState.FallbackReason.FAILURE);
                }
            }
            case OFFLINE -> {
                if (TrueuuidConfig.debug()) {
                    System.out.println("[TrueUUID] 离线进入");
                }
                AuthState.markOfflineFallback(this.connection, AuthState.FallbackReason.FAILURE);
            }
            case DENY -> {
                String msg = d.denyMessage != null ? d.denyMessage
                        : "鉴权失败，已禁止离线进入以保护你的正版存档。请稍后重试。";
                if (TrueuuidConfig.debug()) {
                    System.out.println("[TrueUUID] 认证被拒绝, 玩家: " + name + ", ip: " + ip + ", 消息: " + msg);
                }
                sendDisconnectWithReason(Component.literal(msg));
            }
        }
    }

    @Unique
    private void sendDisconnectWithReason(Component reason) {
        // 异步断开，避免主线程卡死
        new Thread(() -> {
            try {
                this.connection.send(new ClientboundLoginDisconnectPacket(reason));
                this.connection.send(new ClientboundDisconnectPacket(reason));
            } catch (Throwable ignored) {}
            this.connection.disconnect(reason);
        }, "TrueUUID-AsyncDisconnect").start();
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
