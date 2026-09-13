package cn.alini.trueuuid.craftbukkit.v1_20_1;

import cn.alini.trueuuid.protocol.VerifiedProfile;
import cn.alini.trueuuid.server.AuthorityResult;
import org.bukkit.entity.Player;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static cn.alini.trueuuid.craftbukkit.v1_20_1.LoginSeams.*;

/**
 * Fail-closed login bridge for CraftBukkit v1_20_R1 servers, shared by the Spigot and Paper builds.
 *
 * <p>Every member comes from {@link LoginSeams}. Construction resolves the whole table, so a server
 * that lacks any seam never reaches the first login.
 */
final class LoginBridge {
    record Connection(Object networkManager, Object loginListener, Object channel) {
        Connection {
            Objects.requireNonNull(networkManager, "networkManager");
            Objects.requireNonNull(loginListener, "loginListener");
            Objects.requireNonNull(channel, "channel");
        }
    }

    private static final int MAXIMUM_ANSWER_BYTES = 1_048_576;
    private static final int MAXIMUM_PROFILE_PROPERTIES = 32;

    private final ProtocolLibConnections connections;
    private final Map<Seam, MethodHandle> handles;
    private final Class<?> networkManagerClass;
    private final Class<?> loginListenerClass;
    private final Class<?> propertyClass;
    private final Class<?> profileNotFoundClass;
    private final Class<?> profileLookupCallbackClass;
    private final Method profileLookupSucceeded;
    private final Method profileLookupFailed;
    private final Object minecraftAgent;
    private final Object keyState;

    static LoginBridge open(ClassLoader loader, ProtocolLibConnections connections) throws Throwable {
        return new LoginBridge(loader, connections);
    }

    private LoginBridge(ClassLoader loader, ProtocolLibConnections connections) throws Throwable {
        this.connections = Objects.requireNonNull(connections, "connections");
        handles = LoginSeams.resolveAll(loader);
        networkManagerClass = Class.forName(NETWORK_MANAGER, false, loader);
        loginListenerClass = Class.forName(LOGIN_LISTENER, false, loader);
        propertyClass = Class.forName(PROPERTY, false, loader);
        Class<?> gameProfileClass = Class.forName(GAME_PROFILE, false, loader);
        profileLookupCallbackClass = Class.forName(PROFILE_LOOKUP_CALLBACK, false, loader);
        profileNotFoundClass = Class.forName(PROFILE_NOT_FOUND, false, loader);
        profileLookupSucceeded = profileLookupCallbackClass.getMethod("onProfileLookupSucceeded", gameProfileClass);
        profileLookupFailed = profileLookupCallbackClass.getMethod(
                "onProfileLookupFailed", gameProfileClass, Exception.class);
        minecraftAgent = handle(AGENT_MINECRAFT).invoke();
        keyState = handle(KEY_STATE).invoke();
        if (!(keyState instanceof Enum<?> state) || !"KEY".equals(state.name())) {
            throw new NoSuchFieldException("LoginListener state field b is not KEY");
        }
    }

    Connection resolve(Player temporaryPlayer) throws Throwable {
        Object networkManager = connections.networkManager(temporaryPlayer);
        if (!networkManagerClass.isInstance(networkManager)) {
            throw new IllegalStateException("ProtocolLib returned a non-native connection "
                    + networkManager.getClass().getName());
        }
        Object listener = handle(CONNECTION_LISTENER).invoke(networkManager);
        if (listener == null || listener.getClass() != loginListenerClass) {
            throw new IllegalStateException("connection does not own the 1.20.1 LoginListener");
        }
        Object channel = handle(CONNECTION_CHANNEL).invoke(networkManager);
        if (channel == null) throw new IllegalStateException("login connection has no channel");
        return new Connection(networkManager, listener, channel);
    }

    String startName(Object packet) throws Throwable {
        return requireMinecraftName((String) handle(START_NAME).invoke(packet));
    }

    Optional<UUID> startUuidHint(Object packet) throws Throwable {
        Object value = handle(START_UUID_HINT).invoke(packet);
        if (!(value instanceof Optional<?> optional)) {
            throw new IllegalStateException("LOGIN_START UUID hint has an unexpected type");
        }
        if (optional.isEmpty()) return Optional.empty();
        if (!(optional.orElseThrow() instanceof UUID uuid)) {
            throw new IllegalStateException("LOGIN_START UUID hint is not a UUID");
        }
        return Optional.of(uuid);
    }

    String stateName(Connection connection) throws Throwable {
        return ((Enum<?>) handle(LISTENER_STATE_GET).invoke(connection.loginListener())).name();
    }

    Object customQuery(int transactionId, byte[] payload) throws Throwable {
        Objects.requireNonNull(payload, "payload");
        Object buffer = handle(WRAPPED_BUFFER).invoke(payload);
        Object serializer = handle(PACKET_BUFFER_NEW).invoke(buffer);
        Object key = handle(RESOURCE_KEY_NEW).invoke("trueuuid:auth");
        return handle(QUERY_REQUEST_NEW).invoke(transactionId, key, serializer);
    }

    int queryTransaction(Object packet) throws Throwable {
        return (int) handle(QUERY_ANSWER_TRANSACTION).invoke(packet);
    }

    byte[] queryPayload(Object packet) throws Throwable {
        Object data = handle(QUERY_ANSWER_DATA).invoke(packet);
        if (data == null) return null;
        int length = (int) handle(BUFFER_READABLE).invoke(data);
        if (length < 0 || length > MAXIMUM_ANSWER_BYTES) {
            throw new IllegalArgumentException("login answer is too large");
        }
        byte[] payload = new byte[length];
        handle(BUFFER_READ).invoke(data, payload);
        return payload;
    }

    /** Sends a native packet. Call only from {@link #runOnEventLoop}. */
    void send(Connection connection, Object packet) throws Throwable {
        handle(CONNECTION_SEND).invoke(connection.networkManager(), packet);
    }

    void startNativePremium(Connection connection, String requestedName) throws Throwable {
        requireHello(connection);
        Object listener = connection.loginListener();
        handle(LISTENER_PROFILE_SET).invoke(listener,
                handle(GAME_PROFILE_NEW).invoke((UUID) null, requireMinecraftName(requestedName)));
        handle(LISTENER_STATE_SET).invoke(listener, keyState);
        byte[] challenge = ((byte[]) handle(LISTENER_CHALLENGE).invoke(listener)).clone();
        KeyPair keys = (KeyPair) handle(SERVER_KEY_PAIR).invoke(handle(LISTENER_SERVER).invoke(listener));
        send(connection, handle(ENCRYPTION_REQUEST_NEW).invoke("", keys.getPublic().getEncoded(), challenge));
    }

    /** Uses authlib 4.0.43 semantics: not-found is distinct from service failure. */
    AuthorityResult lookupMojangProfile(Connection connection, String requestedName) throws Throwable {
        String name = requireMinecraftName(requestedName);
        AtomicInteger callbackCount = new AtomicInteger();
        AtomicReference<AuthorityResult> outcome = new AtomicReference<>();
        MethodHandle profileId = handle(PROFILE_ID);
        MethodHandle profileName = handle(PROFILE_NAME);
        Object callback = Proxy.newProxyInstance(profileLookupCallbackClass.getClassLoader(),
                new Class<?>[]{profileLookupCallbackClass}, (proxy, method, arguments) -> {
                    if (method.equals(profileLookupSucceeded)) {
                        int count = callbackCount.incrementAndGet();
                        Object profile = arguments[0];
                        UUID uuid = profile == null ? null : (UUID) profileId.invoke(profile);
                        String canonicalName = profile == null ? null : (String) profileName.invoke(profile);
                        outcome.set(count == 1
                                ? classifyProfileLookup(name, uuid, canonicalName, false)
                                : new AuthorityResult.Unavailable(AuthorityResult.Failure.MALFORMED_RESPONSE));
                        return null;
                    }
                    if (method.equals(profileLookupFailed)) {
                        int count = callbackCount.incrementAndGet();
                        Object profile = arguments[0];
                        String failedName = profile == null ? null : (String) profileName.invoke(profile);
                        Throwable failure = arguments[1] instanceof Throwable value ? value : null;
                        outcome.set(count == 1 && profileNotFoundClass.isInstance(failure)
                                ? classifyProfileLookup(name, null, failedName, true)
                                : new AuthorityResult.Unavailable(AuthorityResult.Failure.INTERNAL_ERROR));
                        return null;
                    }
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "TrueUUID profile lookup callback";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == arguments[0];
                            default -> null;
                        };
                    }
                    throw new IllegalStateException("unexpected authlib callback method " + method);
                });
        Object server = handle(LISTENER_SERVER).invoke(connection.loginListener());
        Object repository = handle(SERVER_PROFILE_REPOSITORY).invoke(server);
        handle(FIND_PROFILES).invoke(repository, new String[]{name}, minecraftAgent, callback);
        if (callbackCount.get() != 1 || outcome.get() == null) {
            return new AuthorityResult.Unavailable(AuthorityResult.Failure.MALFORMED_RESPONSE);
        }
        return outcome.get();
    }

    static AuthorityResult classifyProfileLookup(
            String requestedName, UUID uuid, String returnedName, boolean definitelyNotFound) {
        String requested = requireMinecraftName(requestedName);
        if (returnedName == null || !requested.equalsIgnoreCase(returnedName)) {
            return new AuthorityResult.Unavailable(AuthorityResult.Failure.NAME_MISMATCH);
        }
        if (definitelyNotFound) {
            return uuid == null
                    ? new AuthorityResult.DefinitelyAbsent(requested)
                    : new AuthorityResult.Unavailable(AuthorityResult.Failure.MALFORMED_RESPONSE);
        }
        return uuid != null
                ? new AuthorityResult.PremiumProfile(uuid, returnedName)
                : new AuthorityResult.Unavailable(AuthorityResult.Failure.MALFORMED_RESPONSE);
    }

    void continueAssisted(Connection connection, Object originalStart, VerifiedProfile profile) throws Throwable {
        requireHello(connection);
        handle(SPOOFED_UUID).invoke(connection.networkManager(), profile.uuid());
        Object properties = Array.newInstance(propertyClass, profile.properties().size());
        for (int index = 0; index < profile.properties().size(); index++) {
            VerifiedProfile.Property property = profile.properties().get(index);
            Object value = property.signature() == null
                    ? handle(PROPERTY_NEW_UNSIGNED).invoke(property.name(), property.value())
                    : handle(PROPERTY_NEW_SIGNED).invoke(property.name(), property.value(), property.signature());
            Array.set(properties, index, value);
        }
        handle(SPOOFED_PROFILE).invoke(connection.networkManager(), properties);
        Object effectiveStart = startName(originalStart).equals(profile.name())
                ? originalStart
                : handle(START_NEW).invoke(profile.name(), Optional.of(profile.uuid()));
        handle(LISTENER_HELLO).invoke(connection.loginListener(), effectiveStart);
    }

    VerifiedProfile verifiedProfile(Connection connection) throws Throwable {
        Object profile = handle(LISTENER_PROFILE_GET).invoke(connection.loginListener());
        if (profile == null) throw new IllegalStateException("native login has no profile");
        UUID uuid = (UUID) handle(PROFILE_ID).invoke(profile);
        String name = (String) handle(PROFILE_NAME).invoke(profile);
        Collection<?> values = (Collection<?>) handle(PROPERTY_VALUES).invoke(handle(PROFILE_PROPERTIES).invoke(profile));
        if (values.size() > MAXIMUM_PROFILE_PROPERTIES) {
            throw new IllegalArgumentException("native profile has too many properties");
        }
        List<VerifiedProfile.Property> properties = new ArrayList<>(values.size());
        for (Object value : values) {
            if (value == null || value.getClass() != propertyClass) {
                throw new IllegalArgumentException("native profile contains an unexpected property");
            }
            properties.add(new VerifiedProfile.Property(
                    (String) handle(PROPERTY_NAME).invoke(value),
                    (String) handle(PROPERTY_VALUE).invoke(value),
                    (String) handle(PROPERTY_SIGNATURE).invoke(value)));
        }
        return new VerifiedProfile(uuid, name, properties);
    }

    void runOnEventLoop(Connection connection, Runnable work) throws Throwable {
        Executor executor = (Executor) handle(CHANNEL_EVENT_LOOP).invoke(connection.channel());
        executor.execute(work);
    }

    boolean isOpen(Connection connection) throws Throwable {
        return (boolean) handle(CHANNEL_OPEN).invoke(connection.channel());
    }

    /** Last-resort close when a readable denial cannot be sent. */
    void close(Connection connection) throws Throwable {
        handle(CHANNEL_CLOSE).invoke(connection.channel());
    }

    /** Sends the native login disconnect packet before closing the channel. */
    void disconnect(Connection connection, String message) throws Throwable {
        Objects.requireNonNull(message, "message");
        handle(LISTENER_DISCONNECT).invoke(connection.loginListener(), message);
    }

    String implementationVersion() {
        return Objects.requireNonNullElse(loginListenerClass.getPackage().getImplementationVersion(), "unknown");
    }

    String protocolLibShape() {
        return connections.shape();
    }

    private MethodHandle handle(Seam seam) {
        MethodHandle handle = handles.get(seam);
        if (handle == null) throw new IllegalStateException("login seam was not resolved: " + seam.describe());
        return handle;
    }

    private void requireHello(Connection connection) throws Throwable {
        String state = stateName(connection);
        if (!"HELLO".equals(state)) throw new IllegalStateException("expected HELLO login state, found " + state);
    }

    private static String requireMinecraftName(String value) {
        Objects.requireNonNull(value, "name");
        if (value.isBlank() || value.length() > 16 || !value.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("invalid Minecraft name");
        }
        return value;
    }
}
