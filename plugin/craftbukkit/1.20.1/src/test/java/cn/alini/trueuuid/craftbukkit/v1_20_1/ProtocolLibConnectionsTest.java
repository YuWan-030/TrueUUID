package cn.alini.trueuuid.craftbukkit.v1_20_1;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolLibConnectionsTest {
    @Test void acceptsTheTestedMinimumAndNewerFiveXReleases() {
        assertEquals(new ProtocolLibConnections.Version(5, 1, 0),
                ProtocolLibConnections.requireSupportedVersion("5.1.0"));
        assertEquals(new ProtocolLibConnections.Version(5, 2, 0),
                ProtocolLibConnections.requireSupportedVersion("5.2.0-SNAPSHOT-679"));
        assertEquals(new ProtocolLibConnections.Version(5, 4, 0),
                ProtocolLibConnections.requireSupportedVersion(" 5.4.0 "));
    }

    @Test void refusesOlderNextMajorAndUnreadableVersions() {
        for (String version : new String[]{"5.0.0", "4.8.0", "6.0.0", "5.1", "latest", ""}) {
            assertThrows(IllegalStateException.class, () -> ProtocolLibConnections.requireSupportedVersion(version),
                    version);
        }
        assertThrows(IllegalStateException.class, () -> ProtocolLibConnections.requireSupportedVersion(null));
    }

    /** Loads each verified ProtocolLib JAR in isolation and resolves its real connection shape. */
    @Test void everyVerifiedProtocolLibResolvesAKnownConnectionShape() throws Exception {
        String jars = System.getProperty("trueuuid.protocolLibJars", "");
        assertFalse(jars.isBlank(), "trueuuid.protocolLibJars must list the verified ProtocolLib JARs");
        Map<String, String> expectedShapes = Map.of(
                "5.1.0", "minimal-injector",
                "5.2.0", "minimal-injector",
                "5.3.0", "channel-injector",
                "5.4.0", "channel-injector");
        Pattern versionInName = Pattern.compile("ProtocolLib-(\\d+\\.\\d+\\.\\d+)\\.jar");
        int checked = 0;
        for (String jar : jars.split(Pattern.quote(File.pathSeparator))) {
            Path path = Path.of(jar);
            assertTrue(Files.isRegularFile(path), "missing " + path);
            Matcher matcher = versionInName.matcher(path.getFileName().toString());
            assertTrue(matcher.matches(), path.toString());
            try (URLClassLoader loader = new ProtocolLibFirstLoader(path.toUri().toURL(), getClass().getClassLoader())) {
                ProtocolLibConnections connections = ProtocolLibConnections.open(loader);
                assertEquals(expectedShapes.get(matcher.group(1)), connections.shape(), path.toString());
            }
            checked++;
        }
        assertTrue(checked >= 2, "expected at least the minimum and the newest verified ProtocolLib");
    }

    /** Child-first for ProtocolLib only, so each JAR is tested instead of the compile-time copy. */
    private static final class ProtocolLibFirstLoader extends URLClassLoader {
        ProtocolLibFirstLoader(URL jar, ClassLoader parent) {
            super(new URL[]{jar}, parent);
        }

        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith("com.comphenix.")) return super.loadClass(name, resolve);
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) loaded = findClass(name);
                if (resolve) resolveClass(loaded);
                return loaded;
            }
        }
    }
}
