package cn.alini.trueuuid.bukkit;

import cn.alini.trueuuid.api.AccountStatus;
import cn.alini.trueuuid.server.AuthenticatedIdentity;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HybridJoinFeedbackTest {
    @Test void rendersPremiumFeedbackForEnglishAndChineseClients() {
        assertEquals("[TrueUUID] Premium account verified.",
                HybridJoinFeedback.messages(AccountStatus.PREMIUM_VERIFIED, "en_us").chat());
        assertEquals("TrueUUID · Premium verified",
                HybridJoinFeedback.messages(AccountStatus.PREMIUM_VERIFIED, null).actionBar());
        assertEquals("[TrueUUID] 正版账号验证成功。",
                HybridJoinFeedback.messages(AccountStatus.PREMIUM_VERIFIED, "zh_cn").chat());
    }

    @Test void rendersExplicitOfflineFallbackWithoutCallingItPremium() {
        assertEquals("[TrueUUID] Offline fallback accepted. This account was not premium-verified.",
                HybridJoinFeedback.messages(AccountStatus.OFFLINE_FALLBACK, "en_gb").chat());
        assertEquals("TrueUUID · 离线兜底",
                HybridJoinFeedback.messages(AccountStatus.OFFLINE_FALLBACK, "zh_tw").actionBar());
    }

    @Test void refusesToRenderUnknownOrOnlineModeAsACompletedHybridLogin() {
        assertThrows(IllegalArgumentException.class,
                () -> HybridJoinFeedback.messages(AccountStatus.UNKNOWN, "en_us"));
        assertThrows(IllegalArgumentException.class,
                () -> HybridJoinFeedback.messages(AccountStatus.ONLINE_MODE, "en_us"));
    }

    @Test void aliasedPlayerIsExplicitlyToldRequestedAndEffectiveNames() {
        AuthenticatedIdentity identity = new AuthenticatedIdentity(
                UUID.fromString("76bcf6ba-e081-3976-aa3a-38db4da0a066"),
                AuthenticatedIdentity.Kind.OFFLINE, "FixGOD", "-FixGOD",
                AuthenticatedIdentity.Authority.OFFLINE, true);
        HybridJoinFeedback.Messages messages = HybridJoinFeedback.messages(
                AccountStatus.OFFLINE_FALLBACK, "en_us", Optional.of(identity));
        assertEquals("[TrueUUID] Offline identity FixGOD is using the safe alias -FixGOD.",
                messages.chat());
        assertEquals("TrueUUID · Offline alias -FixGOD", messages.actionBar());
    }
}
