package cn.alini.trueuuid.bukkit;

import cn.alini.trueuuid.api.AccountStatus;
import cn.alini.trueuuid.server.AuthenticatedIdentity;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Plain-text, server-rendered feedback for clients without translation assets. */
public final class HybridJoinFeedback {
    public record Messages(String chat, String actionBar) {
        public Messages {
            Objects.requireNonNull(chat, "chat");
            Objects.requireNonNull(actionBar, "actionBar");
        }
    }

    public static Messages messages(AccountStatus status, String clientLocale) {
        return messages(status, clientLocale, Optional.empty());
    }

    public static Messages messages(
            AccountStatus status,
            String clientLocale,
            Optional<AuthenticatedIdentity> identity
    ) {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(identity, "identity");
        boolean chinese = clientLocale != null
                && clientLocale.toLowerCase(Locale.ROOT).startsWith("zh_");
        return switch (status) {
            case PREMIUM_VERIFIED -> chinese
                    ? new Messages("[TrueUUID] 正版账号验证成功。", "TrueUUID · 正版验证成功")
                    : new Messages("[TrueUUID] Premium account verified.", "TrueUUID · Premium verified");
            case OFFLINE_FALLBACK -> offlineMessages(chinese, identity);
            default -> throw new IllegalArgumentException("no join feedback for non-final status " + status);
        };
    }

    private static Messages offlineMessages(
            boolean chinese, Optional<AuthenticatedIdentity> identity
    ) {
        if (identity.isPresent() && identity.orElseThrow().aliased()) {
            AuthenticatedIdentity value = identity.orElseThrow();
            return chinese
                    ? new Messages("[TrueUUID] 离线身份 " + value.requestedName() + " 已使用安全别名 "
                    + value.effectiveName() + "。", "TrueUUID · 离线别名 " + value.effectiveName())
                    : new Messages("[TrueUUID] Offline identity " + value.requestedName()
                    + " is using the safe alias " + value.effectiveName() + ".",
                    "TrueUUID · Offline alias " + value.effectiveName());
        }
        return chinese
                ? new Messages("[TrueUUID] 已允许离线兜底进入。该账号未通过正版验证。", "TrueUUID · 离线兜底")
                : new Messages("[TrueUUID] Offline fallback accepted. This account was not premium-verified.",
                "TrueUUID · Offline fallback");
    }

    private HybridJoinFeedback() {}
}
