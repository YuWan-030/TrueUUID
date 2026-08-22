package cn.alini.trueuuid.spigot.v1_20_1;

import cn.alini.trueuuid.protocol.VerifiedProfile;
import cn.alini.trueuuid.server.AuthorityResult;
import com.comphenix.protocol.injector.temporary.MinimalInjector;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Exact, fail-closed bridge for Spigot build 3871 only. */
final class ExactSpigot1201Bridge {
    static final String EXPECTED_SPIGOT_IMPLEMENTATION = "3871-Spigot-d2eba2c-3f9263b";

    record Connection(Object networkManager, Object loginListener, MinimalInjector injector) {
        Connection {
            Objects.requireNonNull(networkManager, "networkManager");
            Objects.requireNonNull(loginListener, "loginListener");
            Objects.requireNonNull(injector, "injector");
        }
    }

    private final Class<?> loginListenerClass;
    private final Class<?> loginStateClass;
    private final Class<?> propertyClass;
    private final MethodHandle minimalInjectorDelegate;
    private final MethodHandle injectorNetworkManager;
    private final MethodHandle packetListener;
    private final MethodHandle listenerStateGet;
    private final MethodHandle listenerStateSet;
    private final MethodHandle listenerProfileGet;
    private final MethodHandle listenerProfileSet;
    private final MethodHandle listenerDisconnect;
    private final MethodHandle listenerChallenge;
    private final MethodHandle listenerServer;
    private final MethodHandle serverKeyPair;
    private final MethodHandle serverProfileRepository;
    private final MethodHandle findProfilesByNames;
    private final MethodHandle sendEncryptionConstructor;
    private final MethodHandle gameProfileConstructor;
    private final MethodHandle profileId;
    private final MethodHandle profileName;
    private final MethodHandle profileProperties;
    private final MethodHandle propertyValues;
    private final MethodHandle propertyName;
    private final MethodHandle propertyValue;
    private final MethodHandle propertySignature;
    private final MethodHandle signedPropertyConstructor;
    private final MethodHandle unsignedPropertyConstructor;
    private final MethodHandle startName;
    private final MethodHandle startUuidHint;
    private final MethodHandle startConstructor;
    private final MethodHandle handleHello;
    private final MethodHandle spoofedUuidSet;
    private final MethodHandle spoofedProfileSet;
    private final MethodHandle customQueryConstructor;
    private final MethodHandle minecraftKeyConstructor;
    private final MethodHandle serializerConstructor;
    private final MethodHandle wrappedBuffer;
    private final MethodHandle queryTransaction;
    private final MethodHandle queryData;
    private final MethodHandle readableBytes;
    private final MethodHandle readBytes;
    private final MethodHandle networkChannel;
    private final MethodHandle eventLoop;
    private final Class<?> profileNotFoundClass;
    private final Class<?> profileLookupCallbackClass;
    private final Method profileLookupSucceeded;
    private final Method profileLookupFailed;
    private final Object minecraftAgent;
    private final Object keyState;

    static ExactSpigot1201Bridge open() throws Throwable {
        return new ExactSpigot1201Bridge();
    }

    private ExactSpigot1201Bridge() throws Throwable {
        ClassLoader loader = getClass().getClassLoader();
        Class<?> minimalImpl = Class.forName(
                "com.comphenix.protocol.injector.netty.channel.NettyChannelMinimalInjector", true, loader);
        Class<?> injectorClass = Class.forName(
                "com.comphenix.protocol.injector.netty.channel.NettyChannelInjector", true, loader);
        Class<?> networkManagerClass = Class.forName("net.minecraft.network.NetworkManager", true, loader);
        Class<?> packetListenerClass = Class.forName("net.minecraft.network.PacketListener", true, loader);
        Class<?> channelClass = Class.forName("io.netty.channel.Channel", true, loader);
        Class<?> eventLoopClass = Class.forName("io.netty.channel.EventLoop", true, loader);
        loginListenerClass = Class.forName("net.minecraft.server.network.LoginListener", true, loader);
        loginStateClass = Class.forName(
                "net.minecraft.server.network.LoginListener$EnumProtocolState", true, loader);
        Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile", true, loader);
        propertyClass = Class.forName("com.mojang.authlib.properties.Property", true, loader);
        Class<?> propertyMapClass = Class.forName("com.mojang.authlib.properties.PropertyMap", true, loader);
        Class<?> minecraftServerClass = Class.forName("net.minecraft.server.MinecraftServer", true, loader);
        Class<?> gameProfileRepositoryClass = Class.forName(
                "com.mojang.authlib.GameProfileRepository", true, loader);
        Class<?> agentClass = Class.forName("com.mojang.authlib.Agent", true, loader);
        profileLookupCallbackClass = Class.forName("com.mojang.authlib.ProfileLookupCallback", true, loader);
        profileNotFoundClass = Class.forName(
                "com.mojang.authlib.yggdrasil.ProfileNotFoundException", true, loader);
        Class<?> startClass = Class.forName(
                "net.minecraft.network.protocol.login.PacketLoginInStart", true, loader);
        Class<?> encryptionClass = Class.forName(
                "net.minecraft.network.protocol.login.PacketLoginOutEncryptionBegin", true, loader);
        Class<?> customOutClass = Class.forName(
                "net.minecraft.network.protocol.login.PacketLoginOutCustomPayload", true, loader);
        Class<?> customInClass = Class.forName(
                "net.minecraft.network.protocol.login.PacketLoginInCustomPayload", true, loader);
        Class<?> keyClass = Class.forName("net.minecraft.resources.MinecraftKey", true, loader);
        Class<?> serializerClass = Class.forName("net.minecraft.network.PacketDataSerializer", true, loader);
        Class<?> byteBufClass = Class.forName("io.netty.buffer.ByteBuf", true, loader);

        minimalInjectorDelegate = getter(minimalImpl, "injector", injectorClass);
        injectorNetworkManager = getter(injectorClass, "networkManager", Object.class);
        packetListener = MethodHandles.publicLookup().findVirtual(
                networkManagerClass, "j", MethodType.methodType(packetListenerClass));
        listenerStateGet = getter(loginListenerClass, "h", loginStateClass);
        listenerStateSet = setter(loginListenerClass, "h", loginStateClass);
        listenerProfileGet = getter(loginListenerClass, "j", gameProfileClass);
        listenerProfileSet = setter(loginListenerClass, "j", gameProfileClass);
        listenerDisconnect = MethodHandles.publicLookup().findVirtual(
                loginListenerClass, "disconnect", MethodType.methodType(void.class, String.class));
        listenerChallenge = getter(loginListenerClass, "e", byte[].class);
        listenerServer = getter(loginListenerClass, "f", minecraftServerClass);
        serverKeyPair = MethodHandles.publicLookup().findVirtual(
                minecraftServerClass, "L", MethodType.methodType(java.security.KeyPair.class));
        serverProfileRepository = MethodHandles.publicLookup().findVirtual(
                minecraftServerClass, "ao", MethodType.methodType(gameProfileRepositoryClass));
        findProfilesByNames = MethodHandles.publicLookup().findVirtual(gameProfileRepositoryClass,
                "findProfilesByNames", MethodType.methodType(void.class,
                        String[].class, agentClass, profileLookupCallbackClass));
        minecraftAgent = MethodHandles.publicLookup().findStaticGetter(
                agentClass, "MINECRAFT", agentClass).invoke();
        profileLookupSucceeded = profileLookupCallbackClass.getMethod(
                "onProfileLookupSucceeded", gameProfileClass);
        profileLookupFailed = profileLookupCallbackClass.getMethod(
                "onProfileLookupFailed", gameProfileClass, Exception.class);
        sendEncryptionConstructor = MethodHandles.publicLookup().findConstructor(encryptionClass,
                MethodType.methodType(void.class, String.class, byte[].class, byte[].class));
        gameProfileConstructor = MethodHandles.publicLookup().findConstructor(gameProfileClass,
                MethodType.methodType(void.class, UUID.class, String.class));
        profileId = MethodHandles.publicLookup().findVirtual(gameProfileClass, "getId", MethodType.methodType(UUID.class));
        profileName = MethodHandles.publicLookup().findVirtual(gameProfileClass, "getName", MethodType.methodType(String.class));
        profileProperties = MethodHandles.publicLookup().findVirtual(
                gameProfileClass, "getProperties", MethodType.methodType(propertyMapClass));
        propertyValues = MethodHandles.publicLookup().findVirtual(
                propertyMapClass, "values", MethodType.methodType(Collection.class));
        propertyName = MethodHandles.publicLookup().findVirtual(
                propertyClass, "getName", MethodType.methodType(String.class));
        propertyValue = MethodHandles.publicLookup().findVirtual(
                propertyClass, "getValue", MethodType.methodType(String.class));
        propertySignature = MethodHandles.publicLookup().findVirtual(
                propertyClass, "getSignature", MethodType.methodType(String.class));
        unsignedPropertyConstructor = MethodHandles.publicLookup().findConstructor(propertyClass,
                MethodType.methodType(void.class, String.class, String.class));
        signedPropertyConstructor = MethodHandles.publicLookup().findConstructor(propertyClass,
                MethodType.methodType(void.class, String.class, String.class, String.class));
        startName = MethodHandles.publicLookup().findVirtual(startClass, "a", MethodType.methodType(String.class));
        startUuidHint = MethodHandles.publicLookup().findVirtual(
                startClass, "c", MethodType.methodType(Optional.class));
        startConstructor = MethodHandles.publicLookup().findConstructor(startClass,
                MethodType.methodType(void.class, String.class, Optional.class));
        handleHello = MethodHandles.publicLookup().findVirtual(
                loginListenerClass, "a", MethodType.methodType(void.class, startClass));
        spoofedUuidSet = setter(networkManagerClass, "spoofedUUID", UUID.class);
        Class<?> propertyArrayClass = Array.newInstance(propertyClass, 0).getClass();
        spoofedProfileSet = setter(networkManagerClass, "spoofedProfile", propertyArrayClass);
        customQueryConstructor = MethodHandles.publicLookup().findConstructor(customOutClass,
                MethodType.methodType(void.class, int.class, keyClass, serializerClass));
        minecraftKeyConstructor = MethodHandles.publicLookup().findConstructor(
                keyClass, MethodType.methodType(void.class, String.class));
        serializerConstructor = MethodHandles.publicLookup().findConstructor(
                serializerClass, MethodType.methodType(void.class, byteBufClass));
        Class<?> unpooledClass = Class.forName("io.netty.buffer.Unpooled", true, loader);
        wrappedBuffer = MethodHandles.publicLookup().findStatic(
                unpooledClass, "wrappedBuffer", MethodType.methodType(byteBufClass, byte[].class));
        queryTransaction = MethodHandles.publicLookup().findVirtual(
                customInClass, "a", MethodType.methodType(int.class));
        queryData = MethodHandles.publicLookup().findVirtual(
                customInClass, "c", MethodType.methodType(serializerClass));
        readableBytes = MethodHandles.publicLookup().findVirtual(
                serializerClass, "readableBytes", MethodType.methodType(int.class));
        readBytes = MethodHandles.publicLookup().findVirtual(
                serializerClass, "readBytes", MethodType.methodType(byteBufClass, byte[].class));
        networkChannel = getter(networkManagerClass, "m", channelClass);
        eventLoop = MethodHandles.publicLookup().findVirtual(
                channelClass, "eventLoop", MethodType.methodType(eventLoopClass));
        keyState = MethodHandles.privateLookupIn(loginStateClass, MethodHandles.lookup())
                .findStaticGetter(loginStateClass, "b", loginStateClass).invoke();
        if (!"KEY".equals(((Enum<?>) keyState).name())) {
            throw new NoSuchFieldException("LoginListener state field b is not KEY");
        }
    }

    Connection resolve(MinimalInjector minimal) throws Throwable {
        Objects.requireNonNull(minimal, "minimal");
        Object injector = minimalInjectorDelegate.invoke(minimal);
        Object networkManager = injectorNetworkManager.invoke(injector);
        Object listener = packetListener.invoke(networkManager);
        if (listener == null || listener.getClass() != loginListenerClass) {
            throw new IllegalStateException("connection does not own the exact Spigot 1.20.1 LoginListener");
        }
        return new Connection(networkManager, listener, minimal);
    }

    String startName(Object packet) throws Throwable {
        return requireMinecraftName((String) startName.invoke(packet));
    }

    Optional<UUID> startUuidHint(Object packet) throws Throwable {
        Object value = startUuidHint.invoke(packet);
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
        return ((Enum<?>) listenerStateGet.invoke(connection.loginListener())).name();
    }

    Object customQuery(int transactionId, byte[] payload) throws Throwable {
        Objects.requireNonNull(payload, "payload");
        Object buffer = wrappedBuffer.invoke(payload);
        Object serializer = serializerConstructor.invoke(buffer);
        Object key = minecraftKeyConstructor.invoke("trueuuid:auth");
        return customQueryConstructor.invoke(transactionId, key, serializer);
    }

    int queryTransaction(Object packet) throws Throwable {
        return (int) queryTransaction.invoke(packet);
    }

    byte[] queryPayload(Object packet) throws Throwable {
        Object data = queryData.invoke(packet);
        if (data == null) return null;
        int length = (int) readableBytes.invoke(data);
        if (length < 0 || length > 1_048_576) throw new IllegalArgumentException("login answer is too large");
        byte[] payload = new byte[length];
        readBytes.invoke(data, payload);
        return payload;
    }

    void startNativePremium(Connection connection, String requestedName) throws Throwable {
        requireHello(connection);
        listenerProfileSet.invoke(connection.loginListener(),
                gameProfileConstructor.invoke((UUID) null, requireMinecraftName(requestedName)));
        listenerStateSet.invoke(connection.loginListener(), keyState);
        byte[] challenge = ((byte[]) listenerChallenge.invoke(connection.loginListener())).clone();
        java.security.KeyPair keys = (java.security.KeyPair) serverKeyPair.invoke(
                listenerServer.invoke(connection.loginListener()));
        connection.injector().sendServerPacket(
                sendEncryptionConstructor.invoke("", keys.getPublic().getEncoded(), challenge), null, false);
    }

    /** Uses exact authlib 4.0.43 semantics: not-found is distinct from service failure. */
    AuthorityResult lookupMojangProfile(Connection connection, String requestedName)
            throws Throwable {
        String name = requireMinecraftName(requestedName);
        AtomicInteger callbackCount = new AtomicInteger();
        AtomicReference<AuthorityResult> outcome = new AtomicReference<>();
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
                            case "toString" -> "TrueUUID exact profile lookup callback";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == arguments[0];
                            default -> null;
                        };
                    }
                    throw new IllegalStateException("unexpected authlib callback method " + method);
                });
        Object server = listenerServer.invoke(connection.loginListener());
        Object repository = serverProfileRepository.invoke(server);
        findProfilesByNames.invoke(repository, new String[]{name}, minecraftAgent, callback);
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
        spoofedUuidSet.invoke(connection.networkManager(), profile.uuid());
        Object properties = Array.newInstance(propertyClass, profile.properties().size());
        for (int index = 0; index < profile.properties().size(); index++) {
            VerifiedProfile.Property property = profile.properties().get(index);
            Object value = property.signature() == null
                    ? unsignedPropertyConstructor.invoke(property.name(), property.value())
                    : signedPropertyConstructor.invoke(property.name(), property.value(), property.signature());
            Array.set(properties, index, value);
        }
        spoofedProfileSet.invoke(connection.networkManager(), properties);
        Object effectiveStart = startName(originalStart).equals(profile.name())
                ? originalStart
                : startConstructor.invoke(profile.name(), Optional.of(profile.uuid()));
        handleHello.invoke(connection.loginListener(), effectiveStart);
    }

    VerifiedProfile verifiedProfile(Connection connection) throws Throwable {
        Object profile = listenerProfileGet.invoke(connection.loginListener());
        if (profile == null) throw new IllegalStateException("native login has no profile");
        UUID uuid = (UUID) profileId.invoke(profile);
        String name = (String) profileName.invoke(profile);
        Collection<?> values = (Collection<?>) propertyValues.invoke(profileProperties.invoke(profile));
        if (values.size() > 32) throw new IllegalArgumentException("native profile has too many properties");
        List<VerifiedProfile.Property> properties = new ArrayList<>(values.size());
        for (Object value : values) {
            if (value == null || value.getClass() != propertyClass) {
                throw new IllegalArgumentException("native profile contains an unexpected property");
            }
            properties.add(new VerifiedProfile.Property(
                    (String) propertyName.invoke(value),
                    (String) propertyValue.invoke(value),
                    (String) propertySignature.invoke(value)));
        }
        return new VerifiedProfile(uuid, name, properties);
    }

    void runOnEventLoop(Connection connection, Runnable work) throws Throwable {
        Executor executor = (Executor) eventLoop.invoke(networkChannel.invoke(connection.networkManager()));
        executor.execute(work);
    }

    /** Sends the native login disconnect packet before closing the channel. */
    void disconnect(Connection connection, String message) throws Throwable {
        Objects.requireNonNull(message, "message");
        listenerDisconnect.invoke(connection.loginListener(), message);
    }

    String implementationVersion() {
        return loginListenerClass.getPackage().getImplementationVersion();
    }

    private void requireHello(Connection connection) throws Throwable {
        String state = stateName(connection);
        if (!"HELLO".equals(state)) throw new IllegalStateException("expected HELLO login state, found " + state);
    }

    private static MethodHandle getter(Class<?> owner, String field, Class<?> type) throws Throwable {
        return MethodHandles.privateLookupIn(owner, MethodHandles.lookup()).findGetter(owner, field, type);
    }

    private static MethodHandle setter(Class<?> owner, String field, Class<?> type) throws Throwable {
        return MethodHandles.privateLookupIn(owner, MethodHandles.lookup()).findSetter(owner, field, type);
    }

    private static String requireMinecraftName(String value) {
        Objects.requireNonNull(value, "name");
        if (value.isBlank() || value.length() > 16 || !value.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("invalid Minecraft name");
        }
        return value;
    }
}
