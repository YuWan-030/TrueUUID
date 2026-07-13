package cn.alini.trueuuid.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Canonical binary representation for login queries and answers.
 *
 * Native packet adapters must pass these bytes unchanged; this class owns all
 * length checks and rejects trailing bytes so payload handling cannot drift by
 * loader or Minecraft version.
 */
public final class AuthWireCodec {
    private static final int QUERY = 1;
    private static final int ANSWER = 2;

    public static byte[] encodeQuery(AuthMessages.Query query) {
        return encode(QUERY, output -> {
            writeString(output, query.nonce(), AuthMessages.MAX_NONCE_CHARS, "nonce");
            output.writeBoolean(query.migrationAvailable());
            writeString(output, query.offlineUuid(), 64, "offlineUuid");
            writeString(output, query.summary(), AuthMessages.MAX_SUMMARY_CHARS, "summary");
        });
    }

    public static AuthMessages.Query decodeQuery(byte[] payload) {
        return decode(payload, QUERY, input -> new AuthMessages.Query(
                readString(input, AuthMessages.MAX_NONCE_CHARS, "nonce"),
                input.readBoolean(),
                readString(input, 64, "offlineUuid"),
                readString(input, AuthMessages.MAX_SUMMARY_CHARS, "summary")));
    }

    public static byte[] encodeAnswer(AuthMessages.Answer answer) {
        return encode(ANSWER, output -> {
            output.writeBoolean(answer.joined());
            writeString(output, answer.customEndpoint(), AuthMessages.MAX_ENDPOINT_CHARS, "customEndpoint");
            output.writeBoolean(answer.migrationConfirmed());
            output.writeBoolean(answer.missingSessionToken());
        });
    }

    public static AuthMessages.Answer decodeAnswer(byte[] payload) {
        return decode(payload, ANSWER, input -> new AuthMessages.Answer(
                input.readBoolean(),
                readString(input, AuthMessages.MAX_ENDPOINT_CHARS, "customEndpoint"),
                input.readBoolean(), input.readBoolean()));
    }

    private static byte[] encode(int type, Encoder body) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(ProtocolVersion.MAGIC);
                output.writeByte(ProtocolVersion.CURRENT);
                output.writeByte(type);
                body.write(output);
            }
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException("in-memory encoding failed", impossible);
        }
    }

    private static <T> T decode(byte[] payload, int expectedType, Decoder<T> body) {
        if (payload == null) throw new IllegalArgumentException("missing protocol payload");
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
            if (input.readInt() != ProtocolVersion.MAGIC) throw new IllegalArgumentException("unknown TrueUUID protocol header");
            int version = input.readUnsignedByte();
            if (version != ProtocolVersion.CURRENT) throw new IllegalArgumentException("unsupported TrueUUID protocol version: " + version);
            if (input.readUnsignedByte() != expectedType) throw new IllegalArgumentException("unexpected TrueUUID protocol message type");
            T decoded = body.read(input);
            if (input.available() != 0) throw new IllegalArgumentException("trailing bytes in TrueUUID protocol payload");
            return decoded;
        } catch (IOException | IndexOutOfBoundsException ex) {
            throw new IllegalArgumentException("malformed TrueUUID protocol payload", ex);
        }
    }

    private static void writeString(DataOutputStream output, String value, int maxChars, String field) throws IOException {
        if (value == null || value.length() > maxChars) throw new IllegalArgumentException(field + " is too long");
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        int maxBytes = Math.multiplyExact(maxChars, 4);
        if (bytes.length > maxBytes) throw new IllegalArgumentException(field + " is too long");
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readString(DataInputStream input, int maxChars, String field) throws IOException {
        int length = input.readInt();
        int maxBytes = Math.multiplyExact(maxChars, 4);
        if (length < 0 || length > maxBytes) throw new IllegalArgumentException(field + " has an invalid length");
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) throw new IllegalArgumentException(field + " is truncated");
        String value = new String(bytes, StandardCharsets.UTF_8);
        if (value.length() > maxChars || value.indexOf('\uFFFD') >= 0) throw new IllegalArgumentException(field + " is invalid");
        return value;
    }

    @FunctionalInterface private interface Encoder { void write(DataOutputStream output) throws IOException; }
    @FunctionalInterface private interface Decoder<T> { T read(DataInputStream input) throws IOException; }

    private AuthWireCodec() {}
}
