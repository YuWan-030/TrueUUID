package cn.alini.trueuuid.protocol;

import cn.alini.trueuuid.api.AccountStatus;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One connection's fail-closed hybrid login state machine. Platform adapters
 * execute effects and return only server-observed results to this coordinator.
 */
public final class HybridLoginCoordinator {
    public enum Mode {
        CLIENT_ASSISTED,
        VANILLA_HYBRID,
        AUTO
    }

    public enum DenialReason {
        IDENTITY_POLICY,
        CLIENT_QUERY_UNSUPPORTED,
        PREMIUM_PROOF_FAILED,
        OFFLINE_AUTH_FAILED,
        TRANSACTION_MISMATCH,
        REPLAY,
        PROFILE_APPLICATION_FAILED,
        TIMEOUT,
        DISCONNECTED,
        CANCELLED,
        INTERNAL_ERROR,
        PROTOCOL_VIOLATION
    }

    public record AttemptToken(
            String connectionId,
            long generation,
            int transactionId,
            String nonce,
            long deadlineEpochMilli
    ) {
        public AttemptToken {
            connectionId = requireBounded(connectionId, 256, "connectionId");
            nonce = requireBounded(nonce, 512, "nonce");
            if (generation < 0) throw new IllegalArgumentException("generation must not be negative");
            if (transactionId < 0) throw new IllegalArgumentException("transactionId must not be negative");
            if (deadlineEpochMilli <= 0) throw new IllegalArgumentException("deadline must be positive");
        }
    }

    public sealed interface Effect permits LookupAuthority, SendClientQuery, StartVanillaPremiumProof,
            RequireOfflineCredential, ApplyAuthenticatedProfile, AwaitAcceptance, PublishStatus, Deny {
        AttemptToken token();
    }

    public record LookupAuthority(AttemptToken token) implements Effect {
        public LookupAuthority { Objects.requireNonNull(token, "token"); }
    }

    public record SendClientQuery(AttemptToken token) implements Effect {
        public SendClientQuery { Objects.requireNonNull(token, "token"); }
    }

    public record StartVanillaPremiumProof(AttemptToken token) implements Effect {
        public StartVanillaPremiumProof { Objects.requireNonNull(token, "token"); }
    }

    public record RequireOfflineCredential(AttemptToken token, OfflineAuthPort.Operation operation) implements Effect {
        public RequireOfflineCredential {
            Objects.requireNonNull(token, "token");
            Objects.requireNonNull(operation, "operation");
        }
    }

    public record ApplyAuthenticatedProfile(
            AttemptToken token,
            VerifiedProfile profile,
            AccountStatus status
    ) implements Effect {
        public ApplyAuthenticatedProfile {
            Objects.requireNonNull(token, "token");
            Objects.requireNonNull(profile, "profile");
            requireFinalStatus(status);
        }
    }

    public record AwaitAcceptance(AttemptToken token) implements Effect {
        public AwaitAcceptance { Objects.requireNonNull(token, "token"); }
    }

    public record PublishStatus(AttemptToken token, AccountStatus status) implements Effect {
        public PublishStatus {
            Objects.requireNonNull(token, "token");
            requireFinalStatus(status);
        }
    }

    public record Deny(AttemptToken token, DenialReason reason) implements Effect {
        public Deny {
            Objects.requireNonNull(token, "token");
            Objects.requireNonNull(reason, "reason");
        }
    }

    private enum Phase {
        NEW,
        WAITING_AUTHORITY_LOOKUP,
        WAITING_CLIENT_QUERY,
        WAITING_VANILLA_PROOF,
        WAITING_OFFLINE_AUTH,
        WAITING_PROFILE_APPLICATION,
        WAITING_ACCEPTANCE,
        TERMINAL
    }

    private final AttemptToken token;
    private final HybridIdentityPolicy.StoredIdentity storedIdentity;
    private HybridIdentityPolicy.LoginRoute route;
    private final Mode mode;
    private final Clock clock;
    private final AtomicBoolean terminal = new AtomicBoolean();
    private final List<Future<?>> ownedWork = new ArrayList<>();
    private Phase phase = Phase.NEW;
    private AccountStatus pendingStatus;

    public HybridLoginCoordinator(
            AttemptToken token,
            HybridIdentityPolicy.StoredIdentity storedIdentity,
            HybridIdentityPolicy.AuthorityLookup authorityLookup,
            Mode mode
    ) {
        this(token, storedIdentity, authorityLookup, mode, Clock.systemUTC());
    }

    /** Defers unknown/offline classification until the platform returns an authoritative result. */
    public HybridLoginCoordinator(
            AttemptToken token,
            HybridIdentityPolicy.StoredIdentity storedIdentity,
            Mode mode
    ) {
        this(token, storedIdentity, mode, Clock.systemUTC());
    }

    HybridLoginCoordinator(
            AttemptToken token,
            HybridIdentityPolicy.StoredIdentity storedIdentity,
            HybridIdentityPolicy.AuthorityLookup authorityLookup,
            Mode mode,
            Clock clock
    ) {
        this.token = Objects.requireNonNull(token, "token");
        this.storedIdentity = Objects.requireNonNull(storedIdentity, "storedIdentity");
        this.route = HybridIdentityPolicy.route(this.storedIdentity,
                Objects.requireNonNull(authorityLookup, "authorityLookup"));
        this.mode = Objects.requireNonNull(mode, "mode");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    HybridLoginCoordinator(
            AttemptToken token,
            HybridIdentityPolicy.StoredIdentity storedIdentity,
            Mode mode,
            Clock clock
    ) {
        this.token = Objects.requireNonNull(token, "token");
        this.storedIdentity = Objects.requireNonNull(storedIdentity, "storedIdentity");
        this.route = storedIdentity == HybridIdentityPolicy.StoredIdentity.PREMIUM_LOCKED
                ? HybridIdentityPolicy.route(storedIdentity, HybridIdentityPolicy.AuthorityLookup.UNAVAILABLE)
                : null;
        this.mode = Objects.requireNonNull(mode, "mode");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized Optional<Effect> begin() {
        if (terminal.get()) return Optional.empty();
        Optional<Effect> expired = expireIfNecessary();
        if (expired.isPresent()) return expired;
        if (phase != Phase.NEW) return finishDenied(DenialReason.REPLAY);

        if (route == null) {
            phase = Phase.WAITING_AUTHORITY_LOOKUP;
            return Optional.of(new LookupAuthority(token));
        }
        return beginRoute();
    }

    public synchronized Optional<Effect> authorityLookup(HybridIdentityPolicy.AuthorityLookup result) {
        if (terminal.get()) return Optional.empty();
        Optional<Effect> gate = requirePhase(Phase.WAITING_AUTHORITY_LOOKUP);
        if (gate.isPresent()) return gate;
        route = HybridIdentityPolicy.route(storedIdentity, Objects.requireNonNull(result, "result"));
        return beginRoute();
    }

    /**
     * Applies a server-policy-approved offline route after authoritative name
     * classification. The repository, never a client UUID hint, selects
     * enrollment versus authentication of an existing binding.
     */
    public synchronized Optional<Effect> authoritySelectOffline(OfflineAuthPort.Operation operation) {
        if (terminal.get()) return Optional.empty();
        Optional<Effect> gate = requirePhase(Phase.WAITING_AUTHORITY_LOOKUP);
        if (gate.isPresent()) return gate;
        Objects.requireNonNull(operation, "operation");
        phase = Phase.WAITING_OFFLINE_AUTH;
        return Optional.of(new RequireOfflineCredential(token, operation));
    }

    private Optional<Effect> beginRoute() {
        return switch (Objects.requireNonNull(route, "route")) {
            case DENY -> finishDenied(DenialReason.IDENTITY_POLICY);
            case REQUIRE_OFFLINE_CREDENTIAL -> {
                phase = Phase.WAITING_OFFLINE_AUTH;
                yield Optional.of(new RequireOfflineCredential(token, OfflineAuthPort.Operation.AUTHENTICATE_ENROLLED));
            }
            case ALLOW_OFFLINE_ENROLLMENT -> {
                phase = Phase.WAITING_OFFLINE_AUTH;
                yield Optional.of(new RequireOfflineCredential(token, OfflineAuthPort.Operation.ENROLL_NEW));
            }
            case REQUIRE_PREMIUM_PROOF -> startPremiumProof();
        };
    }

    public synchronized Optional<Effect> clientQueryUnsupported(int transactionId) {
        if (terminal.get()) return Optional.empty();
        Optional<Effect> gate = requirePhase(Phase.WAITING_CLIENT_QUERY);
        if (gate.isPresent()) return gate;
        if (transactionId != token.transactionId()) return finishDenied(DenialReason.TRANSACTION_MISMATCH);
        if (mode != Mode.AUTO) return finishDenied(DenialReason.CLIENT_QUERY_UNSUPPORTED);
        phase = Phase.WAITING_VANILLA_PROOF;
        return Optional.of(new StartVanillaPremiumProof(token));
    }

    public synchronized Optional<Effect> clientProof(
            int transactionId,
            String nonce,
            PremiumVerificationResult result
    ) {
        if (terminal.get()) return Optional.empty();
        Optional<Effect> gate = requirePhase(Phase.WAITING_CLIENT_QUERY);
        if (gate.isPresent()) return gate;
        if (transactionId != token.transactionId() || !token.nonce().equals(nonce)) {
            return finishDenied(DenialReason.TRANSACTION_MISMATCH);
        }
        return acceptPremiumResult(result);
    }

    /**
     * Allows a platform policy to treat only an explicitly decoded no-session
     * answer as offline intent. The adapter must already have authoritatively
     * decided that this route is allowed; malformed/non-null deception cannot
     * call this transition.
     */
    public synchronized Optional<Effect> clientSelectOffline(
            int transactionId, OfflineAuthPort.Operation operation
    ) {
        if (terminal.get()) return Optional.empty();
        Optional<Effect> gate = requirePhase(Phase.WAITING_CLIENT_QUERY);
        if (gate.isPresent()) return gate;
        if (transactionId != token.transactionId()) {
            return finishDenied(DenialReason.TRANSACTION_MISMATCH);
        }
        phase = Phase.WAITING_OFFLINE_AUTH;
        return Optional.of(new RequireOfflineCredential(token,
                Objects.requireNonNull(operation, "operation")));
    }

    public synchronized Optional<Effect> vanillaProof(PremiumVerificationResult result) {
        if (terminal.get()) return Optional.empty();
        Optional<Effect> gate = requirePhase(Phase.WAITING_VANILLA_PROOF);
        if (gate.isPresent()) return gate;
        return acceptPremiumResult(result);
    }

    public synchronized Optional<Effect> offlineProof(OfflineAuthPort.Result result) {
        if (terminal.get()) return Optional.empty();
        Optional<Effect> gate = requirePhase(Phase.WAITING_OFFLINE_AUTH);
        if (gate.isPresent()) return gate;
        Objects.requireNonNull(result, "result");
        if (result instanceof OfflineAuthPort.Denied) {
            return finishDenied(DenialReason.OFFLINE_AUTH_FAILED);
        }
        OfflineAuthPort.Accepted accepted = (OfflineAuthPort.Accepted) result;
        return requestProfileApplication(
                new VerifiedProfile(accepted.uuid(), accepted.canonicalName(), List.of()),
                AccountStatus.OFFLINE_FALLBACK);
    }

    public synchronized Optional<Effect> profileApplied() {
        if (terminal.get()) return Optional.empty();
        Optional<Effect> gate = requirePhase(Phase.WAITING_PROFILE_APPLICATION);
        if (gate.isPresent()) return gate;
        phase = Phase.WAITING_ACCEPTANCE;
        return Optional.of(new AwaitAcceptance(token));
    }

    public synchronized Optional<Effect> profileApplicationFailed() {
        if (terminal.get()) return Optional.empty();
        return phase == Phase.WAITING_PROFILE_APPLICATION
                ? finishDenied(DenialReason.PROFILE_APPLICATION_FAILED)
                : requirePhase(Phase.WAITING_PROFILE_APPLICATION);
    }

    /** Called only after platform pre-login checks allow the applied profile. */
    public synchronized Optional<Effect> commitAcceptance() {
        if (terminal.get()) return Optional.empty();
        Optional<Effect> gate = requirePhase(Phase.WAITING_ACCEPTANCE);
        if (gate.isPresent()) return gate;
        AccountStatus status = Objects.requireNonNull(pendingStatus, "pendingStatus");
        return finish(new PublishStatus(token, status));
    }

    public synchronized Optional<Effect> timeout() {
        return finishDenied(DenialReason.TIMEOUT);
    }

    public synchronized Optional<Effect> disconnected() {
        return finishDenied(DenialReason.DISCONNECTED);
    }

    public synchronized Optional<Effect> cancel() {
        return finishDenied(DenialReason.CANCELLED);
    }

    public synchronized Optional<Effect> internalError() {
        return finishDenied(DenialReason.INTERNAL_ERROR);
    }

    /** Registers login-owned asynchronous work for cancellation at any terminal outcome. */
    public synchronized void own(Future<?> future) {
        Objects.requireNonNull(future, "future");
        if (terminal.get()) {
            future.cancel(true);
        } else {
            ownedWork.add(future);
        }
    }

    public boolean isTerminal() {
        return terminal.get();
    }

    public AttemptToken token() {
        return token;
    }

    private Optional<Effect> startPremiumProof() {
        if (mode == Mode.VANILLA_HYBRID) {
            phase = Phase.WAITING_VANILLA_PROOF;
            return Optional.of(new StartVanillaPremiumProof(token));
        }
        phase = Phase.WAITING_CLIENT_QUERY;
        return Optional.of(new SendClientQuery(token));
    }

    private Optional<Effect> acceptPremiumResult(PremiumVerificationResult result) {
        Objects.requireNonNull(result, "result");
        if (result instanceof PremiumVerificationResult.Failed) {
            return finishDenied(DenialReason.PREMIUM_PROOF_FAILED);
        }
        PremiumVerificationResult.Verified verified = (PremiumVerificationResult.Verified) result;
        return requestProfileApplication(verified.profile(), AccountStatus.PREMIUM_VERIFIED);
    }

    private Optional<Effect> requestProfileApplication(VerifiedProfile profile, AccountStatus status) {
        pendingStatus = status;
        phase = Phase.WAITING_PROFILE_APPLICATION;
        return Optional.of(new ApplyAuthenticatedProfile(token, profile, status));
    }

    private Optional<Effect> requirePhase(Phase expected) {
        Optional<Effect> expired = expireIfNecessary();
        if (expired.isPresent() || terminal.get()) return expired;
        if (phase != expected) return finishDenied(DenialReason.PROTOCOL_VIOLATION);
        return Optional.empty();
    }

    private Optional<Effect> expireIfNecessary() {
        if (!terminal.get() && clock.millis() >= token.deadlineEpochMilli()) {
            return finishDenied(DenialReason.TIMEOUT);
        }
        return Optional.empty();
    }

    private Optional<Effect> finishDenied(DenialReason reason) {
        return finish(new Deny(token, reason));
    }

    private Optional<Effect> finish(Effect effect) {
        if (!terminal.compareAndSet(false, true)) return Optional.empty();
        phase = Phase.TERMINAL;
        List.copyOf(ownedWork).forEach(future -> future.cancel(true));
        ownedWork.clear();
        return Optional.of(effect);
    }

    private static void requireFinalStatus(AccountStatus status) {
        Objects.requireNonNull(status, "status");
        if (status != AccountStatus.PREMIUM_VERIFIED && status != AccountStatus.OFFLINE_FALLBACK) {
            throw new IllegalArgumentException("status is not a hybrid-login terminal status");
        }
    }

    private static String requireBounded(String value, int maximum, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || value.length() > maximum) {
            throw new IllegalArgumentException(field + " is blank or too long");
        }
        return value;
    }
}
