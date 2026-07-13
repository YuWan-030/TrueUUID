package cn.alini.trueuuid.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthWireCodecTest {
    @Test void queryGoldenFixtureIsStable() {
        AuthMessages.Query query = new AuthMessages.Query("nonce", true,
                "123e4567-e89b-12d3-a456-426614174000", "playerdata, stats");
        byte[] encoded = AuthWireCodec.encodeQuery(query);

        assertEquals("545555490101000000056e6f6e6365010000002431323365343536372d653839622d313264332d613435362d34323636313431373430303000000011706c61796572646174612c207374617473", hex(encoded));
        assertEquals(query, AuthWireCodec.decodeQuery(encoded));
    }

    @Test void answerGoldenFixtureIsStable() {
        AuthMessages.Answer answer = new AuthMessages.Answer(true,
                "https://skin.example/sessionserver/session/minecraft/hasJoined", false, true);
        byte[] encoded = AuthWireCodec.encodeAnswer(answer);

        assertEquals("545555490102010000003e68747470733a2f2f736b696e2e6578616d706c652f73657373696f6e7365727665722f73657373696f6e2f6d696e6563726166742f6861734a6f696e65640001", hex(encoded));
        assertEquals(answer, AuthWireCodec.decodeAnswer(encoded));
    }

    @Test void rejectsUnknownHeaderAndTrailingData() {
        byte[] encoded = AuthWireCodec.encodeAnswer(new AuthMessages.Answer(false, "", false, false));
        encoded[0] = 0;
        assertThrows(IllegalArgumentException.class, () -> AuthWireCodec.decodeAnswer(encoded));

        byte[] trailing = new byte[encoded.length + 1];
        System.arraycopy(AuthWireCodec.encodeAnswer(new AuthMessages.Answer(false, "", false, false)), 0, trailing, 0, encoded.length);
        assertThrows(IllegalArgumentException.class, () -> AuthWireCodec.decodeAnswer(trailing));
    }

    private static String hex(byte[] value) {
        StringBuilder output = new StringBuilder(value.length * 2);
        for (byte b : value) output.append(String.format("%02x", b));
        return output.toString();
    }
}
