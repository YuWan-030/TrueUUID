package cn.alini.trueuuid.server;

import java.util.Objects;
import java.util.UUID;

/** Final server-authenticated identity after policy and alias allocation. */
public record AuthenticatedIdentity(
        UUID uuid,
        Kind kind,
        String requestedName,
        String effectiveName,
        Authority authority,
        boolean aliased
) {
    public AuthenticatedIdentity {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(kind, "kind");
        requestedName = MinecraftNames.requireValid(requestedName);
        effectiveName = MinecraftNames.requireValidEffective(effectiveName);
        Objects.requireNonNull(authority, "authority");
        if (aliased == requestedName.equalsIgnoreCase(effectiveName)) {
            throw new IllegalArgumentException("alias flag does not match the effective name");
        }
        if (kind == Kind.PREMIUM && aliased) {
            throw new IllegalArgumentException("premium identities may not be aliased");
        }
        if (kind == Kind.PREMIUM && authority == Authority.OFFLINE) {
            throw new IllegalArgumentException("premium identity requires a verified authority");
        }
        if (kind == Kind.OFFLINE && authority != Authority.OFFLINE) {
            throw new IllegalArgumentException("offline identity must use offline authority");
        }
    }

    public enum Kind { PREMIUM, OFFLINE }

    public enum Authority { MOJANG, ALLOWLISTED_YGGDRASIL, OFFLINE }
}
