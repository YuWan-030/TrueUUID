package cn.alini.trueuuid.protocol;

import cn.alini.trueuuid.api.AccountStatus;

import java.util.Objects;
import java.util.Optional;

/** Stable one-byte server-to-client hybrid authentication status contract. */
public final class HybridStatusWireCodec {
    public static byte encode(AccountStatus status) {
        Objects.requireNonNull(status, "status");
        return switch (status) {
            case PREMIUM_VERIFIED -> 1;
            case OFFLINE_FALLBACK -> 2;
            default -> throw new IllegalArgumentException("status is not a verified hybrid-login outcome");
        };
    }

    public static Optional<AccountStatus> decode(int wireId) {
        return switch (wireId) {
            case 1 -> Optional.of(AccountStatus.PREMIUM_VERIFIED);
            case 2 -> Optional.of(AccountStatus.OFFLINE_FALLBACK);
            default -> Optional.empty();
        };
    }

    private HybridStatusWireCodec() {
    }
}
