package cn.alini.trueuuid.server;

import cn.alini.trueuuid.Trueuuid;
import cn.alini.trueuuid.config.TrueuuidConfig;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;

import java.util.UUID;
import java.util.function.Consumer;

final class LoginFailureService {
    static GameProfile handle(GameProfile current, Connection connection, String ip, String reason,
                              boolean explicitOfflineClient, Consumer<Component> disconnect) {
        String name = current == null ? "<unknown>" : current.getName();
        AuthDecider.Decision decision = AuthDecider.onFailure(name, ip, explicitOfflineClient);
        switch (decision.kind) {
            case PREMIUM_GRACE -> {
                UUID uuid = decision.premiumUuid != null ? decision.premiumUuid
                        : TrueuuidRuntime.NAME_REGISTRY.getPremiumUuid(name).orElse(null);
                if (uuid == null) {
                    TrueuuidRuntime.AUTH_STATE.markOfflineFallback(connection, AuthState.FallbackReason.FAILURE);
                    Trueuuid.acceptance("result=offline_fallback player={} reason={}", name, reason);
                    return current;
                }
                AuthState.AuthSource source = decision.graceSource == null ? AuthState.AuthSource.MOJANG : decision.graceSource;
                String display = decision.graceDisplayName == null ? "Recent same-IP grace" : decision.graceDisplayName;
                TrueuuidRuntime.AUTH_STATE.markAuthSuccess(connection, uuid, name, source, display);
                Trueuuid.acceptance("result=premium_grace player={} uuid={}", name, uuid);
                return new GameProfile(uuid, name);
            }
            case OFFLINE -> {
                TrueuuidRuntime.AUTH_STATE.markOfflineFallback(connection, AuthState.FallbackReason.FAILURE);
                Trueuuid.acceptance("result=offline_fallback player={} reason={}", name, reason);
            }
            case DENY -> {
                Trueuuid.acceptance("result=known_deny player={} reason={}", name, reason);
                disconnect.accept(decision.denyComponent != null ? decision.denyComponent
                        : Component.translatable("trueuuid.disconnect.auth_denied"));
            }
        }
        if (TrueuuidConfig.debug()) System.out.println("[TrueUUID] authentication failure: player=" + name + ", reason=" + reason);
        return current;
    }

    private LoginFailureService() {}
}
