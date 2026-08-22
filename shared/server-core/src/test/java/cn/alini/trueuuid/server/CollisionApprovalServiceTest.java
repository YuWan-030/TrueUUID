package cn.alini.trueuuid.server;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollisionApprovalServiceTest {
    @Test void approvalIsNameResolutionGenerationBoundAndOneUse() {
        CollisionApprovalService approvals = new CollisionApprovalService(
                Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC));
        approvals.issue("FixGOD",
                UnifiedAdmissionPolicy.CollisionResolution.ALIAS_INCOMING_OFFLINE, 7);

        assertFalse(approvals.consume("Other",
                UnifiedAdmissionPolicy.CollisionResolution.ALIAS_INCOMING_OFFLINE, 7));
        approvals.issue("FixGOD",
                UnifiedAdmissionPolicy.CollisionResolution.ALIAS_INCOMING_OFFLINE, 7);
        assertFalse(approvals.consume("fixgod",
                UnifiedAdmissionPolicy.CollisionResolution.MOVE_EXISTING_OFFLINE, 7));
        approvals.issue("FixGOD",
                UnifiedAdmissionPolicy.CollisionResolution.ALIAS_INCOMING_OFFLINE, 7);
        assertFalse(approvals.consume("FIXGOD",
                UnifiedAdmissionPolicy.CollisionResolution.ALIAS_INCOMING_OFFLINE, 8));
        approvals.issue("FixGOD",
                UnifiedAdmissionPolicy.CollisionResolution.ALIAS_INCOMING_OFFLINE, 7);
        assertTrue(approvals.consume("fixgod",
                UnifiedAdmissionPolicy.CollisionResolution.ALIAS_INCOMING_OFFLINE, 7));
        assertFalse(approvals.consume("fixgod",
                UnifiedAdmissionPolicy.CollisionResolution.ALIAS_INCOMING_OFFLINE, 7));
    }

    @Test void expiredApprovalCannotBeObservedOrConsumed() {
        MutableClock clock = new MutableClock(1_000);
        CollisionApprovalService approvals = new CollisionApprovalService(clock);
        approvals.issue("FixGOD",
                UnifiedAdmissionPolicy.CollisionResolution.ALIAS_INCOMING_OFFLINE, 7);

        clock.advance(CollisionApprovalService.LIFETIME.toMillis() + 1);

        assertTrue(approvals.pending("FixGOD").isEmpty());
        assertFalse(approvals.consume("FixGOD",
                UnifiedAdmissionPolicy.CollisionResolution.ALIAS_INCOMING_OFFLINE, 7));
    }

    private static final class MutableClock extends Clock {
        private long millis;

        private MutableClock(long millis) {
            this.millis = millis;
        }

        private void advance(long amount) {
            millis += amount;
        }

        @Override public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) throw new IllegalArgumentException("UTC only");
            return this;
        }

        @Override public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }

        @Override public long millis() {
            return millis;
        }
    }
}
