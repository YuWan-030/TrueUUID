package cn.alini.trueuuid.craftbukkit.v1_20_1;

import org.bukkit.entity.Player;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the native connection behind ProtocolLib's temporary login player.
 *
 * <p>ProtocolLib 5.1 and 5.2 return a {@code NettyChannelMinimalInjector} wrapper. ProtocolLib
 * 5.3 and later return the {@code NettyChannelInjector} itself. Both paths end at the same
 * private {@code networkManager} field. The shape is probed once at startup, and any other shape
 * refuses startup instead of guessing.
 */
final class ProtocolLibConnections {
    static final Version MINIMUM_VERSION = new Version(5, 1, 0);
    static final String FACTORY = "com.comphenix.protocol.injector.temporary.TemporaryPlayerFactory";
    static final String FACTORY_METHOD = "getInjectorFromPlayer";
    static final String CHANNEL_INJECTOR = "com.comphenix.protocol.injector.netty.channel.NettyChannelInjector";
    static final String CHANNEL_INJECTOR_NETWORK_MANAGER = "networkManager";
    static final String MINIMAL_INJECTOR = "com.comphenix.protocol.injector.netty.channel.NettyChannelMinimalInjector";
    static final String MINIMAL_INJECTOR_DELEGATE = "injector";

    private static final Pattern VERSION = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(?:[-+].*)?$");

    record Version(int major, int minor, int patch) implements Comparable<Version> {
        @Override public int compareTo(Version other) {
            if (major != other.major) return Integer.compare(major, other.major);
            if (minor != other.minor) return Integer.compare(minor, other.minor);
            return Integer.compare(patch, other.patch);
        }

        @Override public String toString() {
            return major + "." + minor + "." + patch;
        }
    }

    private final MethodHandle injectorFromPlayer;
    private final Class<?> channelInjector;
    private final MethodHandle networkManager;
    private final Class<?> minimalInjector;
    private final MethodHandle minimalDelegate;

    private ProtocolLibConnections(MethodHandle injectorFromPlayer, Class<?> channelInjector,
                                   MethodHandle networkManager, Class<?> minimalInjector,
                                   MethodHandle minimalDelegate) {
        this.injectorFromPlayer = injectorFromPlayer;
        this.channelInjector = channelInjector;
        this.networkManager = networkManager;
        this.minimalInjector = minimalInjector;
        this.minimalDelegate = minimalDelegate;
    }

    /** Accepts ProtocolLib 5.x from the tested minimum. Member probes still decide compatibility. */
    static Version requireSupportedVersion(String reported) {
        Matcher matcher = VERSION.matcher(Objects.requireNonNullElse(reported, "").trim());
        if (!matcher.matches()) {
            throw new IllegalStateException("ProtocolLib reported an unreadable version: " + reported);
        }
        Version version = new Version(Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
        if (version.major() != MINIMUM_VERSION.major() || version.compareTo(MINIMUM_VERSION) < 0) {
            throw new IllegalStateException("TrueUUID requires ProtocolLib " + MINIMUM_VERSION
                    + " or a newer 5.x release, but found " + reported);
        }
        return version;
    }

    static ProtocolLibConnections open(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> factory = Class.forName(FACTORY, false, loader);
        Method lookupMethod = factory.getMethod(FACTORY_METHOD, Player.class);
        if (!Modifier.isStatic(lookupMethod.getModifiers())) {
            throw new NoSuchMethodException(FACTORY + "." + FACTORY_METHOD + " is not static");
        }
        Class<?> channelInjector = Class.forName(CHANNEL_INJECTOR, false, loader);
        MethodHandle networkManager = MethodHandles.privateLookupIn(channelInjector, MethodHandles.lookup())
                .findGetter(channelInjector, CHANNEL_INJECTOR_NETWORK_MANAGER, Object.class)
                .asType(MethodType.methodType(Object.class, Object.class));

        Class<?> minimalInjector = null;
        MethodHandle minimalDelegate = null;
        if (!lookupMethod.getReturnType().isAssignableFrom(channelInjector)) {
            // Older ProtocolLib wraps the channel injector. Require the exact wrapper.
            minimalInjector = Class.forName(MINIMAL_INJECTOR, false, loader);
            if (!lookupMethod.getReturnType().isAssignableFrom(minimalInjector)) {
                throw new NoSuchMethodException(FACTORY_METHOD + " returns an unknown injector type "
                        + lookupMethod.getReturnType().getName());
            }
            minimalDelegate = MethodHandles.privateLookupIn(minimalInjector, MethodHandles.lookup())
                    .findGetter(minimalInjector, MINIMAL_INJECTOR_DELEGATE, channelInjector)
                    .asType(MethodType.methodType(Object.class, Object.class));
        }
        MethodHandle injectorFromPlayer = MethodHandles.publicLookup().unreflect(lookupMethod)
                .asType(MethodType.methodType(Object.class, Player.class));
        return new ProtocolLibConnections(injectorFromPlayer, channelInjector, networkManager,
                minimalInjector, minimalDelegate);
    }

    /** Returns the native NetworkManager for a held login connection. */
    Object networkManager(Player temporaryPlayer) throws Throwable {
        Objects.requireNonNull(temporaryPlayer, "temporaryPlayer");
        Object injector = injectorFromPlayer.invoke(temporaryPlayer);
        if (injector == null) {
            throw new IllegalStateException("ProtocolLib did not expose a login connection injector");
        }
        if (minimalInjector != null && minimalInjector.isInstance(injector)) {
            injector = minimalDelegate.invoke(injector);
        }
        if (!channelInjector.isInstance(injector)) {
            throw new IllegalStateException("unexpected ProtocolLib injector " + injector.getClass().getName());
        }
        Object manager = networkManager.invoke(injector);
        if (manager == null) throw new IllegalStateException("ProtocolLib injector has no network manager");
        return manager;
    }

    String shape() {
        return minimalInjector == null ? "channel-injector" : "minimal-injector";
    }
}
