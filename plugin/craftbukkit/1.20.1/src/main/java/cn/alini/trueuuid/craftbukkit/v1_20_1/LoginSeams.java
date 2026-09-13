package cn.alini.trueuuid.craftbukkit.v1_20_1;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Every server and library member the 1.20.1 login bridge touches, with its exact descriptor.
 *
 * <p>This table is the single source of truth. {@link LoginBridge} resolves handles only from it,
 * and the runtime contract test checks the same entries against real Spigot and Paper server JARs.
 * Any compatible CraftBukkit v1_20_R1 build is accepted when every entry resolves. A missing or
 * changed member refuses startup and names the seam, so compatibility never silently widens.
 */
final class LoginSeams {
    enum Kind { VIRTUAL, STATIC, CONSTRUCTOR, GETTER, SETTER, STATIC_GETTER }

    record Seam(String owner, Kind kind, String name, String descriptor, String purpose) {
        Seam {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(descriptor, "descriptor");
            Objects.requireNonNull(purpose, "purpose");
        }

        /** Spigot-mapped Minecraft members are the fragile, obfuscated part of the contract. */
        boolean serverMember() {
            return owner.startsWith("net.minecraft.");
        }

        boolean field() {
            return kind == Kind.GETTER || kind == Kind.SETTER || kind == Kind.STATIC_GETTER;
        }

        String describe() {
            return owner + "." + name + " " + descriptor + " (" + purpose + ")";
        }
    }

    /** Startup refusal that lists every unresolved seam, not only the first. */
    static final class UnavailableException extends Exception {
        private final List<Seam> missing;

        UnavailableException(List<Seam> missing, Throwable firstCause) {
            super(message(missing), firstCause);
            this.missing = List.copyOf(missing);
        }

        List<Seam> missing() {
            return missing;
        }

        private static String message(List<Seam> missing) {
            StringBuilder text = new StringBuilder("this server does not provide ")
                    .append(missing.size()).append(" required login seam(s):");
            for (Seam seam : missing) text.append(System.lineSeparator()).append("  - ").append(seam.describe());
            return text.toString();
        }
    }

    static final String NETWORK_MANAGER = "net.minecraft.network.NetworkManager";
    static final String LOGIN_LISTENER = "net.minecraft.server.network.LoginListener";
    static final String LOGIN_STATE = LOGIN_LISTENER + "$EnumProtocolState";
    static final String MINECRAFT_SERVER = "net.minecraft.server.MinecraftServer";
    static final String LOGIN_START = "net.minecraft.network.protocol.login.PacketLoginInStart";
    static final String ENCRYPTION_REQUEST = "net.minecraft.network.protocol.login.PacketLoginOutEncryptionBegin";
    static final String QUERY_REQUEST = "net.minecraft.network.protocol.login.PacketLoginOutCustomPayload";
    static final String QUERY_ANSWER = "net.minecraft.network.protocol.login.PacketLoginInCustomPayload";
    static final String RESOURCE_KEY = "net.minecraft.resources.MinecraftKey";
    static final String PACKET_BUFFER = "net.minecraft.network.PacketDataSerializer";
    static final String GAME_PROFILE = "com.mojang.authlib.GameProfile";
    static final String PROPERTY = "com.mojang.authlib.properties.Property";
    static final String PROFILE_REPOSITORY = "com.mojang.authlib.GameProfileRepository";
    static final String AGENT = "com.mojang.authlib.Agent";
    static final String PROFILE_LOOKUP_CALLBACK = "com.mojang.authlib.ProfileLookupCallback";
    static final String PROFILE_NOT_FOUND = "com.mojang.authlib.yggdrasil.ProfileNotFoundException";

    private static final String D_LOGIN_STATE = "L" + LOGIN_STATE.replace('.', '/') + ";";
    private static final String D_GAME_PROFILE = "Lcom/mojang/authlib/GameProfile;";
    private static final String D_PACKET_BUFFER = "Lnet/minecraft/network/PacketDataSerializer;";

    static final Seam CONNECTION_LISTENER = new Seam(NETWORK_MANAGER, Kind.VIRTUAL, "j",
            "()Lnet/minecraft/network/PacketListener;", "read the connection's login listener");
    static final Seam CONNECTION_CHANNEL = new Seam(NETWORK_MANAGER, Kind.GETTER, "m",
            "Lio/netty/channel/Channel;", "schedule login work on the connection event loop");
    static final Seam CONNECTION_SEND = new Seam(NETWORK_MANAGER, Kind.VIRTUAL, "a",
            "(Lnet/minecraft/network/protocol/Packet;)V", "send login packets");
    static final Seam SPOOFED_UUID = new Seam(NETWORK_MANAGER, Kind.SETTER, "spoofedUUID",
            "Ljava/util/UUID;", "apply the verified UUID before player data opens");
    static final Seam SPOOFED_PROFILE = new Seam(NETWORK_MANAGER, Kind.SETTER, "spoofedProfile",
            "[Lcom/mojang/authlib/properties/Property;", "apply verified profile properties");
    static final Seam LISTENER_STATE_GET = new Seam(LOGIN_LISTENER, Kind.GETTER, "h",
            D_LOGIN_STATE, "confirm the login is still waiting for authentication");
    static final Seam LISTENER_STATE_SET = new Seam(LOGIN_LISTENER, Kind.SETTER, "h",
            D_LOGIN_STATE, "hand native premium proof to the server");
    static final Seam LISTENER_PROFILE_GET = new Seam(LOGIN_LISTENER, Kind.GETTER, "j",
            D_GAME_PROFILE, "read the natively verified profile");
    static final Seam LISTENER_PROFILE_SET = new Seam(LOGIN_LISTENER, Kind.SETTER, "j",
            D_GAME_PROFILE, "bind native premium proof to the requested name");
    static final Seam LISTENER_CHALLENGE = new Seam(LOGIN_LISTENER, Kind.GETTER, "e",
            "[B", "read the native verify token");
    static final Seam LISTENER_SERVER = new Seam(LOGIN_LISTENER, Kind.GETTER, "f",
            "Lnet/minecraft/server/MinecraftServer;", "reach the server key pair and profile repository");
    static final Seam LISTENER_DISCONNECT = new Seam(LOGIN_LISTENER, Kind.VIRTUAL, "disconnect",
            "(Ljava/lang/String;)V", "send a readable login denial");
    static final Seam LISTENER_HELLO = new Seam(LOGIN_LISTENER, Kind.VIRTUAL, "a",
            "(L" + LOGIN_START.replace('.', '/') + ";)V", "resume login with the verified profile");
    static final Seam KEY_STATE = new Seam(LOGIN_STATE, Kind.STATIC_GETTER, "b",
            D_LOGIN_STATE, "select the native encryption state");
    static final Seam SERVER_KEY_PAIR = new Seam(MINECRAFT_SERVER, Kind.VIRTUAL, "L",
            "()Ljava/security/KeyPair;", "send the native encryption request");
    static final Seam SERVER_PROFILE_REPOSITORY = new Seam(MINECRAFT_SERVER, Kind.VIRTUAL, "ao",
            "()Lcom/mojang/authlib/GameProfileRepository;", "classify names with the server's authlib");
    static final Seam START_NAME = new Seam(LOGIN_START, Kind.VIRTUAL, "a",
            "()Ljava/lang/String;", "read the requested name");
    static final Seam START_UUID_HINT = new Seam(LOGIN_START, Kind.VIRTUAL, "c",
            "()Ljava/util/Optional;", "read the untrusted client UUID hint");
    static final Seam START_NEW = new Seam(LOGIN_START, Kind.CONSTRUCTOR, "<init>",
            "(Ljava/lang/String;Ljava/util/Optional;)V", "resume with a canonical premium name");
    static final Seam ENCRYPTION_REQUEST_NEW = new Seam(ENCRYPTION_REQUEST, Kind.CONSTRUCTOR, "<init>",
            "(Ljava/lang/String;[B[B)V", "build the native encryption request");
    static final Seam QUERY_REQUEST_NEW = new Seam(QUERY_REQUEST, Kind.CONSTRUCTOR, "<init>",
            "(ILnet/minecraft/resources/MinecraftKey;" + D_PACKET_BUFFER + ")V", "build the TrueUUID login query");
    static final Seam QUERY_ANSWER_TRANSACTION = new Seam(QUERY_ANSWER, Kind.VIRTUAL, "a",
            "()I", "match a login answer to its query");
    static final Seam QUERY_ANSWER_DATA = new Seam(QUERY_ANSWER, Kind.VIRTUAL, "c",
            "()" + D_PACKET_BUFFER, "read a bounded login answer");
    static final Seam RESOURCE_KEY_NEW = new Seam(RESOURCE_KEY, Kind.CONSTRUCTOR, "<init>",
            "(Ljava/lang/String;)V", "name the trueuuid:auth query channel");
    static final Seam PACKET_BUFFER_NEW = new Seam(PACKET_BUFFER, Kind.CONSTRUCTOR, "<init>",
            "(Lio/netty/buffer/ByteBuf;)V", "wrap the login query payload");

    static final Seam GAME_PROFILE_NEW = new Seam(GAME_PROFILE, Kind.CONSTRUCTOR, "<init>",
            "(Ljava/util/UUID;Ljava/lang/String;)V", "create the pending native profile");
    static final Seam PROFILE_ID = new Seam(GAME_PROFILE, Kind.VIRTUAL, "getId",
            "()Ljava/util/UUID;", "read a verified UUID");
    static final Seam PROFILE_NAME = new Seam(GAME_PROFILE, Kind.VIRTUAL, "getName",
            "()Ljava/lang/String;", "read a verified name");
    static final Seam PROFILE_PROPERTIES = new Seam(GAME_PROFILE, Kind.VIRTUAL, "getProperties",
            "()Lcom/mojang/authlib/properties/PropertyMap;", "read signed profile properties");
    static final Seam PROPERTY_VALUES = new Seam("com.google.common.collect.Multimap", Kind.VIRTUAL, "values",
            "()Ljava/util/Collection;", "enumerate signed profile properties");
    static final Seam PROPERTY_NAME = new Seam(PROPERTY, Kind.VIRTUAL, "getName",
            "()Ljava/lang/String;", "copy a profile property");
    static final Seam PROPERTY_VALUE = new Seam(PROPERTY, Kind.VIRTUAL, "getValue",
            "()Ljava/lang/String;", "copy a profile property");
    static final Seam PROPERTY_SIGNATURE = new Seam(PROPERTY, Kind.VIRTUAL, "getSignature",
            "()Ljava/lang/String;", "copy a profile property signature");
    static final Seam PROPERTY_NEW_UNSIGNED = new Seam(PROPERTY, Kind.CONSTRUCTOR, "<init>",
            "(Ljava/lang/String;Ljava/lang/String;)V", "apply an unsigned profile property");
    static final Seam PROPERTY_NEW_SIGNED = new Seam(PROPERTY, Kind.CONSTRUCTOR, "<init>",
            "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", "apply a signed profile property");
    static final Seam FIND_PROFILES = new Seam(PROFILE_REPOSITORY, Kind.VIRTUAL, "findProfilesByNames",
            "([Ljava/lang/String;Lcom/mojang/authlib/Agent;Lcom/mojang/authlib/ProfileLookupCallback;)V",
            "classify a name as premium, absent, or unavailable");
    static final Seam AGENT_MINECRAFT = new Seam(AGENT, Kind.STATIC_GETTER, "MINECRAFT",
            "Lcom/mojang/authlib/Agent;", "select the Minecraft profile namespace");
    static final Seam WRAPPED_BUFFER = new Seam("io.netty.buffer.Unpooled", Kind.STATIC, "wrappedBuffer",
            "([B)Lio/netty/buffer/ByteBuf;", "wrap the login query payload");
    static final Seam BUFFER_READABLE = new Seam("io.netty.buffer.ByteBuf", Kind.VIRTUAL, "readableBytes",
            "()I", "bound a login answer");
    static final Seam BUFFER_READ = new Seam("io.netty.buffer.ByteBuf", Kind.VIRTUAL, "readBytes",
            "([B)Lio/netty/buffer/ByteBuf;", "copy a login answer");
    static final Seam CHANNEL_EVENT_LOOP = new Seam("io.netty.channel.Channel", Kind.VIRTUAL, "eventLoop",
            "()Lio/netty/channel/EventLoop;", "run login work on the connection event loop");
    static final Seam CHANNEL_OPEN = new Seam("io.netty.channel.Channel", Kind.VIRTUAL, "isOpen",
            "()Z", "stop work for a closed connection");
    static final Seam CHANNEL_CLOSE = new Seam("io.netty.channel.Channel", Kind.VIRTUAL, "close",
            "()Lio/netty/channel/ChannelFuture;", "close a connection that cannot receive a denial");

    static final List<Seam> ALL = List.of(
            CONNECTION_LISTENER, CONNECTION_CHANNEL, CONNECTION_SEND, SPOOFED_UUID, SPOOFED_PROFILE,
            LISTENER_STATE_GET, LISTENER_STATE_SET, LISTENER_PROFILE_GET, LISTENER_PROFILE_SET,
            LISTENER_CHALLENGE, LISTENER_SERVER, LISTENER_DISCONNECT, LISTENER_HELLO, KEY_STATE,
            SERVER_KEY_PAIR, SERVER_PROFILE_REPOSITORY, START_NAME, START_UUID_HINT, START_NEW,
            ENCRYPTION_REQUEST_NEW, QUERY_REQUEST_NEW, QUERY_ANSWER_TRANSACTION, QUERY_ANSWER_DATA,
            RESOURCE_KEY_NEW, PACKET_BUFFER_NEW,
            GAME_PROFILE_NEW, PROFILE_ID, PROFILE_NAME, PROFILE_PROPERTIES, PROPERTY_VALUES,
            PROPERTY_NAME, PROPERTY_VALUE, PROPERTY_SIGNATURE, PROPERTY_NEW_UNSIGNED, PROPERTY_NEW_SIGNED,
            FIND_PROFILES, AGENT_MINECRAFT, WRAPPED_BUFFER, BUFFER_READABLE, BUFFER_READ,
            CHANNEL_EVENT_LOOP, CHANNEL_OPEN, CHANNEL_CLOSE);

    /** Resolves the complete table or reports every seam this server lacks. */
    static Map<Seam, MethodHandle> resolveAll(ClassLoader loader) throws UnavailableException {
        Map<Seam, MethodHandle> handles = new LinkedHashMap<>();
        List<Seam> missing = new ArrayList<>();
        Throwable firstCause = null;
        for (Seam seam : ALL) {
            try {
                handles.put(seam, resolve(seam, loader));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
                missing.add(seam);
                if (firstCause == null) firstCause = unavailable;
            }
        }
        if (!missing.isEmpty()) throw new UnavailableException(missing, firstCause);
        return handles;
    }

    static MethodHandle resolve(Seam seam, ClassLoader loader) throws ReflectiveOperationException {
        Class<?> owner = Class.forName(seam.owner(), false, loader);
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(owner, MethodHandles.lookup());
        if (seam.field()) {
            Class<?> type = MethodType.fromMethodDescriptorString("()" + seam.descriptor(), loader).returnType();
            return switch (seam.kind()) {
                case GETTER -> lookup.findGetter(owner, seam.name(), type);
                case SETTER -> lookup.findSetter(owner, seam.name(), type);
                case STATIC_GETTER -> lookup.findStaticGetter(owner, seam.name(), type);
                default -> throw new IllegalStateException("not a field seam: " + seam.kind());
            };
        }
        MethodType type = MethodType.fromMethodDescriptorString(seam.descriptor(), loader);
        return switch (seam.kind()) {
            case VIRTUAL -> lookup.findVirtual(owner, seam.name(), type);
            case STATIC -> lookup.findStatic(owner, seam.name(), type);
            case CONSTRUCTOR -> lookup.findConstructor(owner, type);
            default -> throw new IllegalStateException("not a method seam: " + seam.kind());
        };
    }

    private LoginSeams() {
    }
}
