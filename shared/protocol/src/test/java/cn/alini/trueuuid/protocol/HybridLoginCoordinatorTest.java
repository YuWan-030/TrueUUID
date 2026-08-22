package cn.alini.trueuuid.protocol;

import cn.alini.trueuuid.api.AccountStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HybridLoginCoordinatorTest {
    private static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");
    private static final VerifiedProfile PREMIUM = new VerifiedProfile(
            UUID.fromString("11111111-2222-3333-4444-555555555555"), "Alice", List.of());

    @Test void autoUsesVanillaOnlyForExplicitUnsupportedQueryAndPublishesOnce() {
        HybridLoginCoordinator coordinator = coordinator(HybridLoginCoordinator.Mode.AUTO);
        CompletableFuture<Void> work = new CompletableFuture<>();
        coordinator.own(work);

        assertInstanceOf(HybridLoginCoordinator.SendClientQuery.class, effect(coordinator.begin()));
        assertInstanceOf(HybridLoginCoordinator.StartVanillaPremiumProof.class,
                effect(coordinator.clientQueryUnsupported(42)));
        assertProfileEffect(coordinator.vanillaProof(PremiumVerificationResult.verified(PREMIUM)),
                AccountStatus.PREMIUM_VERIFIED);
        assertInstanceOf(HybridLoginCoordinator.AwaitAcceptance.class, effect(coordinator.profileApplied()));

        HybridLoginCoordinator.PublishStatus published = assertInstanceOf(
                HybridLoginCoordinator.PublishStatus.class, effect(coordinator.commitAcceptance()));
        assertEquals(AccountStatus.PREMIUM_VERIFIED, published.status());
        assertTrue(coordinator.isTerminal());
        assertTrue(work.isCancelled());
        assertTrue(coordinator.commitAcceptance().isEmpty());
        assertTrue(coordinator.disconnected().isEmpty());
    }

    @Test void clientAssistedRejectsUnsupportedAndNeverFallsBack() {
        HybridLoginCoordinator coordinator = coordinator(HybridLoginCoordinator.Mode.CLIENT_ASSISTED);
        assertInstanceOf(HybridLoginCoordinator.SendClientQuery.class, effect(coordinator.begin()));
        HybridLoginCoordinator.Deny denied = assertInstanceOf(HybridLoginCoordinator.Deny.class,
                effect(coordinator.clientQueryUnsupported(42)));
        assertEquals(HybridLoginCoordinator.DenialReason.CLIENT_QUERY_UNSUPPORTED, denied.reason());
    }

    @Test void explicitPolicyApprovedNoSessionAnswerCanSelectOfflineWithoutPublishing() {
        HybridLoginCoordinator coordinator = coordinator(HybridLoginCoordinator.Mode.AUTO);
        assertInstanceOf(HybridLoginCoordinator.SendClientQuery.class, effect(coordinator.begin()));
        HybridLoginCoordinator.RequireOfflineCredential offline = assertInstanceOf(
                HybridLoginCoordinator.RequireOfflineCredential.class,
                effect(coordinator.clientSelectOffline(42, OfflineAuthPort.Operation.ENROLL_NEW)));
        assertEquals(OfflineAuthPort.Operation.ENROLL_NEW, offline.operation());
        HybridLoginCoordinator.ApplyAuthenticatedProfile applied = assertInstanceOf(
                HybridLoginCoordinator.ApplyAuthenticatedProfile.class,
                effect(coordinator.offlineProof(new OfflineAuthPort.Accepted(
                        UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"), "o_AliceABCDE"))));
        assertEquals(AccountStatus.OFFLINE_FALLBACK, applied.status());
    }

    @Test void serverOwnedPunctuationAliasCanBeAppliedButNotRequested() {
        UUID uuid = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        OfflineAuthPort.Accepted accepted = new OfflineAuthPort.Accepted(uuid, "-Alice");
        assertEquals("-Alice", accepted.canonicalName());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new OfflineAuthPort.Request("connection", 1, "-Alice",
                        OfflineAuthPort.Operation.ENROLL_NEW));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new OfflineAuthPort.Accepted(uuid, ",Alice"));
    }

    @Test void malformedOrFailedPremiumProofDenies() {
        for (HybridIdentityPolicy.PremiumProof failure : HybridIdentityPolicy.PremiumProof.values()) {
            if (failure == HybridIdentityPolicy.PremiumProof.VERIFIED) continue;
            HybridLoginCoordinator coordinator = coordinator(HybridLoginCoordinator.Mode.CLIENT_ASSISTED);
            coordinator.begin();
            HybridLoginCoordinator.Deny denied = assertInstanceOf(HybridLoginCoordinator.Deny.class,
                    effect(coordinator.clientProof(42, "nonce", PremiumVerificationResult.failed(failure))), failure.name());
            assertEquals(HybridLoginCoordinator.DenialReason.PREMIUM_PROOF_FAILED, denied.reason());
        }
    }

    @Test void transactionAndNonceAreConnectionBound() {
        HybridLoginCoordinator wrongTransaction = coordinator(HybridLoginCoordinator.Mode.AUTO);
        wrongTransaction.begin();
        HybridLoginCoordinator.Deny transactionDenied = assertInstanceOf(HybridLoginCoordinator.Deny.class,
                effect(wrongTransaction.clientProof(43, "nonce", PremiumVerificationResult.verified(PREMIUM))));
        assertEquals(HybridLoginCoordinator.DenialReason.TRANSACTION_MISMATCH, transactionDenied.reason());

        HybridLoginCoordinator wrongNonce = coordinator(HybridLoginCoordinator.Mode.AUTO);
        wrongNonce.begin();
        HybridLoginCoordinator.Deny nonceDenied = assertInstanceOf(HybridLoginCoordinator.Deny.class,
                effect(wrongNonce.clientProof(42, "replayed", PremiumVerificationResult.verified(PREMIUM))));
        assertEquals(HybridLoginCoordinator.DenialReason.TRANSACTION_MISMATCH, nonceDenied.reason());
    }

    @Test void offlineResultCannotPublishUntilProfileAndPlatformAcceptanceComplete() {
        HybridLoginCoordinator coordinator = new HybridLoginCoordinator(token(),
                HybridIdentityPolicy.StoredIdentity.OFFLINE_ENROLLED,
                HybridIdentityPolicy.AuthorityLookup.DEFINITELY_ABSENT,
                HybridLoginCoordinator.Mode.AUTO,
                Clock.fixed(NOW, ZoneOffset.UTC));
        HybridLoginCoordinator.RequireOfflineCredential request = assertInstanceOf(
                HybridLoginCoordinator.RequireOfflineCredential.class, effect(coordinator.begin()));
        assertEquals(OfflineAuthPort.Operation.AUTHENTICATE_ENROLLED, request.operation());

        UUID offlineUuid = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        assertProfileEffect(coordinator.offlineProof(new OfflineAuthPort.Accepted(offlineUuid, "OfflineUser")),
                AccountStatus.OFFLINE_FALLBACK);
        assertInstanceOf(HybridLoginCoordinator.AwaitAcceptance.class, effect(coordinator.profileApplied()));
        HybridLoginCoordinator.PublishStatus published = assertInstanceOf(
                HybridLoginCoordinator.PublishStatus.class, effect(coordinator.commitAcceptance()));
        assertEquals(AccountStatus.OFFLINE_FALLBACK, published.status());
    }

    @Test void authorityUnavailableForUnknownNameDeniesBeforeTransportSelection() {
        HybridLoginCoordinator coordinator = new HybridLoginCoordinator(token(),
                HybridIdentityPolicy.StoredIdentity.UNKNOWN,
                HybridIdentityPolicy.AuthorityLookup.UNAVAILABLE,
                HybridLoginCoordinator.Mode.AUTO,
                Clock.fixed(NOW, ZoneOffset.UTC));
        HybridLoginCoordinator.Deny denied = assertInstanceOf(
                HybridLoginCoordinator.Deny.class, effect(coordinator.begin()));
        assertEquals(HybridLoginCoordinator.DenialReason.IDENTITY_POLICY, denied.reason());
    }

    @Test void deferredAuthorityLookupPrecedesTransportAndOfflineEnrollment() {
        HybridLoginCoordinator premium = new HybridLoginCoordinator(token(),
                HybridIdentityPolicy.StoredIdentity.UNKNOWN, HybridLoginCoordinator.Mode.AUTO,
                Clock.fixed(NOW, ZoneOffset.UTC));
        assertInstanceOf(HybridLoginCoordinator.LookupAuthority.class, effect(premium.begin()));
        assertInstanceOf(HybridLoginCoordinator.SendClientQuery.class,
                effect(premium.authorityLookup(HybridIdentityPolicy.AuthorityLookup.PREMIUM_EXISTS)));

        HybridLoginCoordinator offline = new HybridLoginCoordinator(token(),
                HybridIdentityPolicy.StoredIdentity.UNKNOWN, HybridLoginCoordinator.Mode.AUTO,
                Clock.fixed(NOW, ZoneOffset.UTC));
        assertInstanceOf(HybridLoginCoordinator.LookupAuthority.class, effect(offline.begin()));
        HybridLoginCoordinator.RequireOfflineCredential request = assertInstanceOf(
                HybridLoginCoordinator.RequireOfflineCredential.class,
                effect(offline.authorityLookup(HybridIdentityPolicy.AuthorityLookup.DEFINITELY_ABSENT)));
        assertEquals(OfflineAuthPort.Operation.ENROLL_NEW, request.operation());
    }

    @Test void repositoryCanSelectExistingOfflineBindingAfterDeferredAuthorityLookup() {
        HybridLoginCoordinator coordinator = new HybridLoginCoordinator(token(),
                HybridIdentityPolicy.StoredIdentity.UNKNOWN, HybridLoginCoordinator.Mode.AUTO,
                Clock.fixed(NOW, ZoneOffset.UTC));
        assertInstanceOf(HybridLoginCoordinator.LookupAuthority.class, effect(coordinator.begin()));
        HybridLoginCoordinator.RequireOfflineCredential request = assertInstanceOf(
                HybridLoginCoordinator.RequireOfflineCredential.class,
                effect(coordinator.authoritySelectOffline(
                        OfflineAuthPort.Operation.AUTHENTICATE_ENROLLED)));
        assertEquals(OfflineAuthPort.Operation.AUTHENTICATE_ENROLLED, request.operation());
    }

    @Test void enrolledOfflineNameCollidingWithNewPremiumNameDenies() {
        HybridLoginCoordinator coordinator = new HybridLoginCoordinator(token(),
                HybridIdentityPolicy.StoredIdentity.OFFLINE_ENROLLED, HybridLoginCoordinator.Mode.AUTO,
                Clock.fixed(NOW, ZoneOffset.UTC));
        assertInstanceOf(HybridLoginCoordinator.LookupAuthority.class, effect(coordinator.begin()));
        HybridLoginCoordinator.Deny denied = assertInstanceOf(HybridLoginCoordinator.Deny.class,
                effect(coordinator.authorityLookup(HybridIdentityPolicy.AuthorityLookup.PREMIUM_EXISTS)));
        assertEquals(HybridLoginCoordinator.DenialReason.IDENTITY_POLICY, denied.reason());
    }

    @Test void deadlineAndExplicitCancellationAreTerminalAndCancelOwnedWork() {
        Clock expired = Clock.fixed(NOW.plusSeconds(31), ZoneOffset.UTC);
        HybridLoginCoordinator timeout = new HybridLoginCoordinator(token(),
                HybridIdentityPolicy.StoredIdentity.PREMIUM_LOCKED,
                HybridIdentityPolicy.AuthorityLookup.PREMIUM_EXISTS,
                HybridLoginCoordinator.Mode.VANILLA_HYBRID, expired);
        CompletableFuture<Void> timeoutWork = new CompletableFuture<>();
        timeout.own(timeoutWork);
        HybridLoginCoordinator.Deny timeoutEffect = assertInstanceOf(
                HybridLoginCoordinator.Deny.class, effect(timeout.begin()));
        assertEquals(HybridLoginCoordinator.DenialReason.TIMEOUT, timeoutEffect.reason());
        assertTrue(timeoutWork.isCancelled());

        HybridLoginCoordinator cancelled = coordinator(HybridLoginCoordinator.Mode.AUTO);
        cancelled.begin();
        HybridLoginCoordinator.Deny cancelledEffect = assertInstanceOf(
                HybridLoginCoordinator.Deny.class, effect(cancelled.cancel()));
        assertEquals(HybridLoginCoordinator.DenialReason.CANCELLED, cancelledEffect.reason());
        assertTrue(cancelled.clientQueryUnsupported(42).isEmpty());
    }

    @Test void racingTerminalCallbacksProduceOneTerminalEffect() throws Exception {
        HybridLoginCoordinator coordinator = coordinator(HybridLoginCoordinator.Mode.VANILLA_HYBRID);
        coordinator.begin();
        coordinator.vanillaProof(PremiumVerificationResult.verified(PREMIUM));
        coordinator.profileApplied();

        var executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<Optional<HybridLoginCoordinator.Effect>>> attempts = new ArrayList<>();
            for (int index = 0; index < 32; index++) {
                attempts.add(executor.submit(index % 2 == 0
                        ? coordinator::commitAcceptance : coordinator::disconnected));
            }
            long terminals = 0;
            for (Future<Optional<HybridLoginCoordinator.Effect>> attempt : attempts) {
                if (attempt.get().isPresent()) terminals++;
            }
            assertEquals(1, terminals);
        } finally {
            executor.shutdownNow();
        }
    }

    private static HybridLoginCoordinator coordinator(HybridLoginCoordinator.Mode mode) {
        return new HybridLoginCoordinator(token(),
                HybridIdentityPolicy.StoredIdentity.PREMIUM_LOCKED,
                HybridIdentityPolicy.AuthorityLookup.UNAVAILABLE,
                mode,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static HybridLoginCoordinator.AttemptToken token() {
        return new HybridLoginCoordinator.AttemptToken("connection-1", 7, 42, "nonce",
                NOW.plusSeconds(30).toEpochMilli());
    }

    private static HybridLoginCoordinator.Effect effect(Optional<HybridLoginCoordinator.Effect> effect) {
        return effect.orElseThrow();
    }

    private static void assertProfileEffect(
            Optional<HybridLoginCoordinator.Effect> effect,
            AccountStatus status
    ) {
        HybridLoginCoordinator.ApplyAuthenticatedProfile apply = assertInstanceOf(
                HybridLoginCoordinator.ApplyAuthenticatedProfile.class, effect(effect));
        assertEquals(status, apply.status());
    }
}
