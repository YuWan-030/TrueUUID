package cn.alini.trueuuid.bukkit;

import cn.alini.trueuuid.api.AccountStatus;
import cn.alini.trueuuid.server.AuthenticatedIdentity;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Server-rendered feedback bodies for clients without translation assets.
 *
 * <p>Bodies carry no brand prefix. {@link MessageStyle} adds the platform's styled prefix, so
 * Spigot and Paper show identical words with their own colours.
 */
public final class HybridJoinFeedback {
    public record Messages(Tone tone, String chat, String actionBar) {
        public Messages {
            Objects.requireNonNull(tone, "tone");
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
                    ? new Messages(Tone.SUCCESS, "正版账号验证成功。", "正版验证成功")
                    : new Messages(Tone.SUCCESS, "Premium account verified.", "Premium verified");
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
                    ? new Messages(Tone.ERROR, "离线身份 " + value.requestedName() + " 已使用安全别名 "
                    + value.effectiveName() + "。", "离线别名 " + value.effectiveName())
                    : new Messages(Tone.ERROR, "Offline identity " + value.requestedName()
                    + " is using the safe alias " + value.effectiveName() + ".",
                    "Offline alias " + value.effectiveName());
        }
        return chinese
                ? new Messages(Tone.ERROR, "已允许离线兜底进入。该账号未通过正版验证。", "离线兜底")
                : new Messages(Tone.ERROR, "Offline fallback accepted. This account was not premium-verified.",
                "Offline fallback");
    }

    private HybridJoinFeedback() {}
}
