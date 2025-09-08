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
import net.minecraft.network.protocol.game.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Mixin(targets = "net.minecraft.server.network.ServerLoginPacketListenerImpl")
public abstract class ServerLoginMixin {
    @Shadow private GameProfile gameProfile;
    @Shadow private MinecraftServer server;
    @Shadow private Connection connection; // 1.20.1 是字段

    @Shadow public abstract void disconnect(Component reason);

    // 握手状态
    @Unique private static final AtomicInteger TRUEUUID$NEXT_TX_ID = new AtomicInteger(1);
    @Unique private int trueuuid$txId = 0;
    @Unique private String trueuuid$nonce = null;
    @Unique private long trueuuid$sentAt = 0L;

    @Inject(method = "handleHello", at = @At("TAIL"))
    private void trueuuid$afterHello(ServerboundHelloPacket pkt, CallbackInfo ci) {
        if (this.server.usesAuthentication() || this.gameProfile == null) return;

        this.trueuuid$nonce = UUID.randomUUID().toString().replace("-", "");
        this.trueuuid$txId = TRUEUUID$NEXT_TX_ID.getAndIncrement();
        this.trueuuid$sentAt = System.currentTimeMillis();

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUtf(this.trueuuid$nonce);

        this.connection.send(new ClientboundCustomQueryPacket(this.trueuuid$txId, NetIds.AUTH, buf));
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void trueuuid$onTick(CallbackInfo ci) {
        if (this.trueuuid$txId == 0 || this.trueuuid$sentAt == 0L) return;
        long timeoutMs = TrueuuidConfig.timeoutMs();
        if (timeoutMs <= 0) return;

        long now = System.currentTimeMillis();
        if (now - this.trueuuid$sentAt < timeoutMs) return;

        if (TrueuuidConfig.allowOfflineOnTimeout()) {
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
    private void trueuuid$onLoginCustom(ServerboundCustomQueryPacket packet, CallbackInfo ci) {
        if (this.trueuuid$txId == 0) return;
        if (packet.getTransactionId() != this.trueuuid$txId) return;

        String ip = null;
        if (this.connection.getRemoteAddress() instanceof InetSocketAddress isa) {
            ip = isa.getAddress().getHostAddress();
        }

        FriendlyByteBuf data = packet.getData();
        if (data == null) {
            handleAuthFailure(ip, "缺少数据");
            reset(); ci.cancel(); return;
        }

        boolean ackOk = false;
        try { ackOk = data.readBoolean(); } catch (Throwable ignored) {}
        if (!ackOk) {
            handleAuthFailure(ip, "客户端拒绝");
            reset(); ci.cancel(); return;
        }

        try {
            var resOpt = SessionCheck.hasJoined(this.gameProfile.getName(), this.trueuuid$nonce, ip);
            if (resOpt.isEmpty()) {
                handleAuthFailure(ip, "会话无效");
                reset(); ci.cancel(); return;
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
            this.gameProfile = newProfile;

        } catch (Throwable t) {
            handleAuthFailure(ip, "服务器异常");
        } finally {
            reset();
            ci.cancel();
        }
    }

    @Unique
    private void handleAuthFailure(String ip, String why) {
        String name = this.gameProfile != null ? this.gameProfile.getName() : "<unknown>";
        AuthDecider.Decision d = AuthDecider.onFailure(name, ip);

        switch (d.kind) {
            case PREMIUM_GRACE -> {
                UUID premium = d.premiumUuid != null ? d.premiumUuid
                        : TrueuuidRuntime.NAME_REGISTRY.getPremiumUuid(name).orElse(null);
                if (premium != null) {
                    this.gameProfile = new GameProfile(premium, name);
                } else {
                    AuthState.markOfflineFallback(this.connection, AuthState.FallbackReason.FAILURE);
                }
            }
            case OFFLINE -> {
                AuthState.markOfflineFallback(this.connection, AuthState.FallbackReason.FAILURE);
            }
            case DENY -> {
                String msg = d.denyMessage != null ? d.denyMessage
                        : "鉴权失败，已禁止离线进入以保护你的正版存档。请稍后重试。";
                sendDisconnectWithReason(Component.literal(msg));
            }
        }
    }

    @Unique
    private void sendDisconnectWithReason(Component reason) {
        // 同时发 login 与 common 的断开包，覆盖 Login/Configuration 两种状态
        try {
            this.connection.send(new ClientboundLoginDisconnectPacket(reason));
        } catch (Throwable ignored) {}
        try {
            this.connection.send(new ClientboundDisconnectPacket(reason));
        } catch (Throwable ignored) {}
        // 然后再真正断开
        this.connection.disconnect(reason);
    }

    @Unique
    private void reset() {
        this.trueuuid$txId = 0;
        this.trueuuid$nonce = null;
        this.trueuuid$sentAt = 0L;
    }
}