package cn.alini.trueuuid.server;

import cn.alini.trueuuid.protocol.HybridLoginCoordinator;
import cn.alini.trueuuid.protocol.OfflineAdmissionMode;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** One immutable, fully validated configuration snapshot consumed by all adapters. */
public record ServerConfiguration(
        Authentication authentication,
        Admission admission,
        Aliases aliases,
        Feedback feedback,
        Permissions permissions
) {
    public ServerConfiguration {
        Objects.requireNonNull(authentication, "authentication");
        Objects.requireNonNull(admission, "admission");
        Objects.requireNonNull(aliases, "aliases");
        Objects.requireNonNull(feedback, "feedback");
        Objects.requireNonNull(permissions, "permissions");
        if (admission.mode() == AdmissionMode.FIRST_CLAIM && !admission.firstClaimRiskAccepted()) {
            throw new IllegalArgumentException(
                    "FIRST_CLAIM requires admission.first-claim-risk-accepted=true");
        }
    }

    public static ServerConfiguration freshDefaults() {
        return new ServerConfiguration(
                new Authentication(HybridLoginCoordinator.Mode.AUTO, Duration.ofSeconds(30), 64, List.of()),
                new Admission(AdmissionMode.CONSENT_REQUIRED, OfflineAdmissionMode.ALLOW_VANILLA, false),
                new Aliases("-"),
                new Feedback(true, true, false, true, 20),
                new Permissions(Provider.AUTO));
    }

    /** Preserves the previous strict behavior when an upgraded config has no admission.mode key. */
    public static AdmissionMode deriveLegacyAdmissionMode(boolean knownPremiumDenyOffline) {
        return knownPremiumDenyOffline ? AdmissionMode.PREMIUM_RESERVED : AdmissionMode.FIRST_CLAIM;
    }

    public record Authentication(
            HybridLoginCoordinator.Mode transport,
            Duration timeout,
            int maximumPendingLogins,
            List<String> customEndpointAllowlist
    ) {
        public Authentication {
            Objects.requireNonNull(transport, "transport");
            Objects.requireNonNull(timeout, "timeout");
            if (timeout.compareTo(Duration.ofSeconds(5)) < 0
                    || timeout.compareTo(Duration.ofSeconds(60)) > 0) {
                throw new IllegalArgumentException("authentication timeout must be between 5 and 60 seconds");
            }
            if (maximumPendingLogins < 1 || maximumPendingLogins > 256) {
                throw new IllegalArgumentException("maximum pending logins must be between 1 and 256");
            }
            customEndpointAllowlist = List.copyOf(customEndpointAllowlist == null
                    ? List.of() : customEndpointAllowlist);
        }
    }

    public record Admission(
            AdmissionMode mode,
            OfflineAdmissionMode offlineClient,
            boolean firstClaimRiskAccepted
    ) {
        public Admission {
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(offlineClient, "offlineClient");
        }
    }

    public record Aliases(String prefix) {
        public Aliases {
            Objects.requireNonNull(prefix, "prefix");
            if (!(prefix.matches("[A-Za-z0-9_]{1,4}") || prefix.matches("[.+-]"))) {
                throw new IllegalArgumentException(
                        "alias prefix must be one of '.', '+', '-', or match [A-Za-z0-9_]{1,4}");
            }
        }
    }

    public record Feedback(
            boolean privateChat,
            boolean vanillaActionBar,
            boolean title,
            boolean moddedOverlay,
            int vanillaActionBarDelayTicks
    ) {
        public Feedback {
            if (vanillaActionBarDelayTicks < 1 || vanillaActionBarDelayTicks > 100) {
                throw new IllegalArgumentException("action-bar delay must be between 1 and 100 ticks");
            }
        }
    }

    public record Permissions(Provider provider) {
        public Permissions { Objects.requireNonNull(provider, "provider"); }
    }

    public enum Provider { AUTO, LUCKPERMS, PLATFORM }
}
