package cn.alini.trueuuid.server;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

/** Deterministic bounded alias allocation. The server, never the client, owns aliases. */
public final class OfflineAliasAllocator {
    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private static final int HASH_CHARACTERS = 5;
    private static final int MAX_ATTEMPTS = 32;

    public String allocate(
            String requestedName,
            UUID offlineUuid,
            String prefix,
            Predicate<String> effectiveNameUnavailable
    ) {
        String requested = MinecraftNames.requireValid(requestedName);
        Objects.requireNonNull(offlineUuid, "offlineUuid");
        ServerConfiguration.Aliases aliases = new ServerConfiguration.Aliases(prefix);
        Objects.requireNonNull(effectiveNameUnavailable, "effectiveNameUnavailable");

        int readableLength = 16 - aliases.prefix().length();
        String readable = aliases.prefix()
                + requested.substring(0, Math.min(readableLength, requested.length()));
        MinecraftNames.requireValidEffective(readable);
        if (!effectiveNameUnavailable.test(readable.toLowerCase(Locale.ROOT))) return readable;

        int fragmentLength = readableLength - HASH_CHARACTERS;
        String fragment = requested.substring(0, Math.min(fragmentLength, requested.length()));
        for (int counter = 0; counter < MAX_ATTEMPTS; counter++) {
            String hash = hashSuffix(requested, offlineUuid, counter);
            String candidate = aliases.prefix() + fragment + hash;
            MinecraftNames.requireValidEffective(candidate);
            if (!effectiveNameUnavailable.test(candidate.toLowerCase(Locale.ROOT))) return candidate;
        }
        throw new IllegalStateException("could not allocate a unique offline alias after "
                + MAX_ATTEMPTS + " deterministic attempts");
    }

    private static String hashSuffix(String requestedName, UUID uuid, int counter) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(MinecraftNames.normalize(requestedName).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(ByteBuffer.allocate(20)
                    .putLong(uuid.getMostSignificantBits())
                    .putLong(uuid.getLeastSignificantBits())
                    .putInt(counter)
                    .array());
            byte[] value = digest.digest();
            StringBuilder encoded = new StringBuilder(HASH_CHARACTERS);
            int buffer = 0;
            int bits = 0;
            for (byte item : value) {
                buffer = (buffer << 8) | (item & 0xff);
                bits += 8;
                while (bits >= 5 && encoded.length() < HASH_CHARACTERS) {
                    bits -= 5;
                    encoded.append(BASE32[(buffer >>> bits) & 31]);
                }
                if (encoded.length() == HASH_CHARACTERS) return encoded.toString();
            }
            throw new IllegalStateException("SHA-256 did not produce enough alias entropy");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
