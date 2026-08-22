package cn.alini.trueuuid.server;

import cn.alini.trueuuid.protocol.OfflineAdmissionMode;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UnifiedAdmissionPolicyTest {
    private static final UUID PREMIUM = UUID.fromString("d64da409-52f6-4ce8-a082-73b9a5d303bd");
    private final UnifiedAdmissionPolicy policy = new UnifiedAdmissionPolicy();

    @Test void safeParallelUsesHintOnlyToChooseProofOrExplicitOfflineAlias() {
        ServerConfiguration settings = configuration(AdmissionMode.SAFE_PARALLEL,
                OfflineAdmissionMode.ALLOW_VANILLA);
        AuthorityResult authority = new AuthorityResult.PremiumProfile(PREMIUM, "FixGOD");
        UnifiedAdmissionPolicy.StoredBindings none = bindings(false, false, false);

        assertInstanceOf(UnifiedAdmissionPolicy.RequirePremiumProof.class,
                policy.decide(settings, "FixGOD", Optional.of(PREMIUM), authority, none));
        UnifiedAdmissionPolicy.AllowOffline missingHint = assertInstanceOf(
                UnifiedAdmissionPolicy.AllowOffline.class,
                policy.decide(settings, "FixGOD", Optional.empty(), authority, none));
        assertTrue(missingHint.aliasRequired());
        UnifiedAdmissionPolicy.AllowOffline forgedMismatch = assertInstanceOf(
                UnifiedAdmissionPolicy.AllowOffline.class,
                policy.decide(settings, "FixGOD", Optional.of(UUID.randomUUID()), authority, none));
        assertTrue(forgedMismatch.aliasRequired());
    }

    @Test void forgedMatchingHintNeverAuthenticatesItOnlyRequiresPremiumProof() {
        UnifiedAdmissionPolicy.Decision decision = policy.decide(ServerConfiguration.freshDefaults(),
                "FixGOD", Optional.of(PREMIUM), new AuthorityResult.PremiumProfile(PREMIUM, "FixGOD"),
                bindings(false, false, false));
        assertInstanceOf(UnifiedAdmissionPolicy.RequirePremiumProof.class, decision);
    }

    @Test void authorityFailureAlwaysDeniesEveryMode() {
        for (AdmissionMode mode : AdmissionMode.values()) {
            ServerConfiguration settings = configuration(mode, OfflineAdmissionMode.ALLOW_VANILLA);
            UnifiedAdmissionPolicy.Deny denied = assertInstanceOf(UnifiedAdmissionPolicy.Deny.class,
                    policy.decide(settings, "FixGOD", Optional.empty(),
                            new AuthorityResult.Unavailable(AuthorityResult.Failure.TIMEOUT),
                            bindings(false, false, false)));
            assertEquals(UnifiedAdmissionPolicy.Reason.AUTHORITY_UNAVAILABLE, denied.reason());
        }
    }

    @Test void premiumReservedRejectsOfflineIntentAndOfflineDenyIsIndependent() {
        ServerConfiguration strict = configuration(AdmissionMode.PREMIUM_RESERVED,
                OfflineAdmissionMode.ALLOW_VANILLA);
        assertEquals(UnifiedAdmissionPolicy.Reason.PREMIUM_RESERVED,
                assertInstanceOf(UnifiedAdmissionPolicy.Deny.class,
                        policy.decide(strict, "FixGOD", Optional.empty(),
                                new AuthorityResult.PremiumProfile(PREMIUM, "FixGOD"),
                                bindings(false, false, false))).reason());

        ServerConfiguration noOffline = configuration(AdmissionMode.SAFE_PARALLEL,
                OfflineAdmissionMode.DENY);
        assertEquals(UnifiedAdmissionPolicy.Reason.OFFLINE_DISABLED,
                assertInstanceOf(UnifiedAdmissionPolicy.Deny.class,
                        policy.decide(noOffline, "UnusedName", Optional.empty(),
                                new AuthorityResult.DefinitelyAbsent("unusedname"),
                                bindings(false, false, false))).reason());
    }

    @Test void firstClaimLocksIdentityKindAndRequiresConfiguredRiskAcceptance() {
        ServerConfiguration settings = configuration(AdmissionMode.FIRST_CLAIM,
                OfflineAdmissionMode.ALLOW_VANILLA);
        AuthorityResult premium = new AuthorityResult.PremiumProfile(PREMIUM, "FixGOD");
        assertEquals(UnifiedAdmissionPolicy.Reason.FIRST_CLAIM_OWNED_BY_OFFLINE,
                assertInstanceOf(UnifiedAdmissionPolicy.Deny.class,
                        policy.decide(settings, "FixGOD", Optional.of(PREMIUM), premium,
                                bindings(false, true, false))).reason());
        assertEquals(UnifiedAdmissionPolicy.Reason.FIRST_CLAIM_OWNED_BY_PREMIUM,
                assertInstanceOf(UnifiedAdmissionPolicy.Deny.class,
                        policy.decide(settings, "FixGOD", Optional.empty(), premium,
                                bindings(true, false, false))).reason());
    }

    @Test void consentDefaultNeverSilentlyMutatesEitherClaimant() {
        ServerConfiguration settings = ServerConfiguration.freshDefaults();
        AuthorityResult premium = new AuthorityResult.PremiumProfile(PREMIUM, "FixGOD");

        UnifiedAdmissionPolicy.AllowOffline firstOffline = assertInstanceOf(
                UnifiedAdmissionPolicy.AllowOffline.class,
                policy.decide(settings, "FixGOD", Optional.empty(), premium,
                        bindings(false, false, false)));
        assertFalse(firstOffline.aliasRequired());

        assertEquals(UnifiedAdmissionPolicy.CollisionResolution.MOVE_EXISTING_OFFLINE,
                assertInstanceOf(UnifiedAdmissionPolicy.RequireCollisionConsent.class,
                        policy.decide(settings, "FixGOD", Optional.of(PREMIUM), premium,
                                bindings(false, true, false))).resolution());
        assertEquals(UnifiedAdmissionPolicy.CollisionResolution.ALIAS_INCOMING_OFFLINE,
                assertInstanceOf(UnifiedAdmissionPolicy.RequireCollisionConsent.class,
                        policy.decide(settings, "FixGOD", Optional.empty(), premium,
                                bindings(true, false, false))).resolution());

        assertInstanceOf(UnifiedAdmissionPolicy.RequirePremiumProof.class,
                policy.decide(settings, "FixGOD", Optional.of(PREMIUM), premium,
                        bindings(false, true, true)));
        UnifiedAdmissionPolicy.AllowOffline acceptedAlias = assertInstanceOf(
                UnifiedAdmissionPolicy.AllowOffline.class,
                policy.decide(settings, "FixGOD", Optional.empty(), premium,
                        bindings(true, true, true)));
        assertTrue(acceptedAlias.aliasRequired());
    }

    private static UnifiedAdmissionPolicy.StoredBindings bindings(
            boolean premium, boolean offline, boolean offlineAliased
    ) {
        return new UnifiedAdmissionPolicy.StoredBindings(premium, offline, offlineAliased, false);
    }

    private static ServerConfiguration configuration(AdmissionMode mode, OfflineAdmissionMode offline) {
        ServerConfiguration defaults = ServerConfiguration.freshDefaults();
        return new ServerConfiguration(defaults.authentication(),
                new ServerConfiguration.Admission(mode, offline, mode == AdmissionMode.FIRST_CLAIM),
                defaults.aliases(), defaults.feedback(), defaults.permissions());
    }
}
