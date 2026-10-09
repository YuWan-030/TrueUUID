package cn.alini.trueuuid.protocol;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClientSessionTest {
    private static final UUID PROFILE = UUID.fromString("d64da409-52f6-4ce8-a082-73b9a5d303bd");

    @Test void pclOfflineUuidTokenRequestsOfflineWithEitherUuidFormat() {
        assertTrue(ClientSession.isMissing(PROFILE, PROFILE.toString()));
        assertTrue(ClientSession.isMissing(PROFILE, PROFILE.toString().replace("-", "")));
        assertTrue(ClientSession.isMissing(PROFILE, PROFILE.toString().toUpperCase(Locale.ROOT)));
        assertTrue(ClientSession.isMissing(PROFILE,
                PROFILE.toString().replace("-", "").toUpperCase(Locale.ROOT)));
        assertTrue(OfflineClientResponse.isExplicit(ClientSession.offlineAnswer()));
    }

    @Test void absentSessionAndDevelopmentPlaceholdersRequestOffline() {
        assertTrue(ClientSession.isMissing(null, "opaque-session-token"));
        for (String token : new String[]{null, "", " \t", "0"}) {
            assertTrue(ClientSession.isMissing(PROFILE, token));
        }
    }

    @Test void opaqueAndOtherUuidTokensStillRequirePremiumProof() {
        assertFalse(ClientSession.isMissing(PROFILE, "opaque-session-token"));
        UUID other = UUID.fromString("0cb0953b-1c06-49bc-8a4f-dfcada519032");
        assertFalse(ClientSession.isMissing(PROFILE, other.toString()));
        assertFalse(ClientSession.isMissing(PROFILE, other.toString().replace("-", "")));
        assertFalse(ClientSession.isMissing(PROFILE, " " + PROFILE));
    }

    @Test void failedProofCannotSelectOfflineEvenWithoutACustomEndpoint() {
        for (String endpoint : new String[]{"", "https://example.invalid/hasJoined"}) {
            AuthMessages.Answer failure = ClientSession.proofAnswer(false, endpoint);
            assertFalse(failure.missingSessionToken());
            assertFalse(OfflineClientResponse.isExplicit(failure));
            assertFalse(OfflineClientResponse.isExplicit(ClientSession.proofAnswer(true, endpoint)));
        }
    }

    @Test void offlinePlaceholderStillObeysServerNameProtectionAfterWireDecoding() {
        AuthMessages.Answer answer = AuthWireCodec.decodeAnswer(
                AuthWireCodec.encodeAnswer(ClientSession.offlineAnswer()));
        boolean explicitOffline = OfflineClientResponse.isExplicit(answer);
        assertEquals(AuthPolicy.Decision.OFFLINE, AuthPolicy.decide(
                new AuthPolicy.Input(false, explicitOffline, false, false, true, true, true)));
        assertEquals(AuthPolicy.Decision.DENY, AuthPolicy.decide(
                new AuthPolicy.Input(true, explicitOffline, false, false, true, true, true)));

        AuthMessages.Answer rejectedProof = AuthWireCodec.decodeAnswer(
                AuthWireCodec.encodeAnswer(ClientSession.proofAnswer(false, "")));
        assertEquals(AuthPolicy.Decision.DENY, AuthPolicy.decide(new AuthPolicy.Input(
                false, OfflineClientResponse.isExplicit(rejectedProof), true, true, false, true, false)));
    }
}
