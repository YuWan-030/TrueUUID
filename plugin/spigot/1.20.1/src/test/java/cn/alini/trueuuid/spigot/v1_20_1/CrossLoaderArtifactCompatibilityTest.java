package cn.alini.trueuuid.spigot.v1_20_1;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag("cross-loader-runtime")
class CrossLoaderArtifactCompatibilityTest {
    private static final String QUERY_FIXTURE = "545555490101000000056e6f6e6365010000002431323365343536372d653839622d313264332d613435362d34323636313431373430303000000011706c61796572646174612c207374617473";
    private static final String ANSWER_FIXTURE = "545555490102010000003e68747470733a2f2f736b696e2e6578616d706c652f73657373696f6e7365727665722f73657373696f6e2f6d696e6563726166742f6861734a6f696e65640001";

    @Test void builtSpigotAndFabricArtifactsUseTheSameGoldenWireContract() throws Exception {
        Path spigot = requiredJar("trueuuid.spigotCandidateJar");
        Path fabric = requiredJar("trueuuid.fabric1201Jar");
        assertEntry(spigot, "cn/alini/trueuuid/spigot/v1_20_1/TrueuuidSpigotPlugin.class");
        assertEntry(fabric, "cn/alini/trueuuid/fabric/login/FabricClientLoginNetworking.class");

        ArtifactWire spigotWire = ArtifactWire.open(spigot);
        ArtifactWire fabricWire = ArtifactWire.open(fabric);
        assertArrayEquals(HexFormat.of().parseHex(QUERY_FIXTURE), spigotWire.query());
        assertArrayEquals(spigotWire.query(), fabricWire.query());
        assertArrayEquals(HexFormat.of().parseHex(ANSWER_FIXTURE), spigotWire.answer());
        assertArrayEquals(spigotWire.answer(), fabricWire.answer());
        assertArrayEquals(new byte[]{1, 2}, spigotWire.statuses());
        assertArrayEquals(spigotWire.statuses(), fabricWire.statuses());
    }

    private static Path requiredJar(String property) {
        Path path = Path.of(System.getProperty(property, ""));
        if (!Files.isRegularFile(path)) throw new IllegalStateException("missing artifact property " + property);
        return path;
    }

    private static void assertEntry(Path jar, String entry) throws Exception {
        try (ZipFile archive = new ZipFile(jar.toFile())) {
            assertNotNull(archive.getEntry(entry), entry);
        }
    }

    private record ArtifactWire(byte[] query, byte[] answer, byte[] statuses) {
        private static ArtifactWire open(Path jar) throws Exception {
            try (URLClassLoader loader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, null)) {
                Class<?> messages = loader.loadClass("cn.alini.trueuuid.protocol.AuthMessages");
                Class<?> queryType = loader.loadClass(messages.getName() + "$Query");
                Class<?> answerType = loader.loadClass(messages.getName() + "$Answer");
                Class<?> codec = loader.loadClass("cn.alini.trueuuid.protocol.AuthWireCodec");
                Object query = queryType.getConstructor(String.class, boolean.class, String.class, String.class)
                        .newInstance("nonce", true, "123e4567-e89b-12d3-a456-426614174000", "playerdata, stats");
                Object answer = answerType.getConstructor(boolean.class, String.class, boolean.class, boolean.class)
                        .newInstance(true, "https://skin.example/sessionserver/session/minecraft/hasJoined", false, true);
                byte[] queryBytes = invokeBytes(codec.getMethod("encodeQuery", queryType), query);
                byte[] answerBytes = invokeBytes(codec.getMethod("encodeAnswer", answerType), answer);

                Class<?> statusType = loader.loadClass("cn.alini.trueuuid.api.AccountStatus");
                Class<?> statusCodec = loader.loadClass("cn.alini.trueuuid.protocol.HybridStatusWireCodec");
                Method encodeStatus = statusCodec.getMethod("encode", statusType);
                byte premium = invokeByte(encodeStatus, enumValue(statusType, "PREMIUM_VERIFIED"));
                byte offline = invokeByte(encodeStatus, enumValue(statusType, "OFFLINE_FALLBACK"));
                return new ArtifactWire(queryBytes, answerBytes, new byte[]{premium, offline});
            }
        }

        private static byte[] invokeBytes(Method method, Object value) throws Exception {
            return (byte[]) method.invoke(null, value);
        }

        private static byte invokeByte(Method method, Object value) throws Exception {
            return (byte) method.invoke(null, value);
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private static Object enumValue(Class<?> type, String value) {
            return Enum.valueOf((Class<? extends Enum>) type, value);
        }
    }
}
