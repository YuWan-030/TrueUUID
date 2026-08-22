package cn.alini.trueuuid.spigot.v1_20_1;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("exact-runtime")
class ExactRuntimeContractTest {
    @Test void exactSpigotAndProtocolLibDescriptorsRemainAvailable() throws Exception {
        Path spigot = Path.of(required("trueuuid.spigotRuntimeJar"));
        Path protocolLib = Path.of(required("trueuuid.protocolLibRuntimeJar"));

        ClassNode listener = read(spigot, "net/minecraft/server/network/LoginListener.class");
        assertField(listener, "e", "[B", Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL);
        assertField(listener, "f", "Lnet/minecraft/server/MinecraftServer;", Opcodes.ACC_FINAL);
        assertField(listener, "h", "Lnet/minecraft/server/network/LoginListener$EnumProtocolState;", 0);
        assertField(listener, "j", "Lcom/mojang/authlib/GameProfile;", 0);
        assertMethod(listener, "a", "(Lnet/minecraft/network/protocol/login/PacketLoginInStart;)V");
        assertMethod(listener, "a", "(Lnet/minecraft/network/protocol/login/PacketLoginInEncryptionBegin;)V");
        assertMethod(listener, "disconnect", "(Ljava/lang/String;)V");

        ClassNode minecraftServer = read(spigot, "net/minecraft/server/MinecraftServer.class");
        assertMethod(minecraftServer, "ao", "()Lcom/mojang/authlib/GameProfileRepository;");

        ClassNode state = read(spigot, "net/minecraft/server/network/LoginListener$EnumProtocolState.class");
        assertField(state, "b", "Lnet/minecraft/server/network/LoginListener$EnumProtocolState;",
                Opcodes.ACC_STATIC | Opcodes.ACC_FINAL);

        ClassNode network = read(spigot, "net/minecraft/network/NetworkManager.class");
        assertField(network, "m", "Lio/netty/channel/Channel;", Opcodes.ACC_PUBLIC);
        assertField(network, "spoofedUUID", "Ljava/util/UUID;", Opcodes.ACC_PUBLIC);
        assertField(network, "spoofedProfile", "[Lcom/mojang/authlib/properties/Property;", Opcodes.ACC_PUBLIC);
        assertMethod(network, "j", "()Lnet/minecraft/network/PacketListener;");

        ClassNode customResponse = read(spigot,
                "net/minecraft/network/protocol/login/PacketLoginInCustomPayload.class");
        assertMethod(customResponse, "a", "()I");
        assertMethod(customResponse, "c", "()Lnet/minecraft/network/PacketDataSerializer;");

        ClassNode loginStart = read(spigot,
                "net/minecraft/network/protocol/login/PacketLoginInStart.class");
        assertMethod(loginStart, "a", "()Ljava/lang/String;");
        assertMethod(loginStart, "c", "()Ljava/util/Optional;");
        assertMethod(loginStart, "<init>", "(Ljava/lang/String;Ljava/util/Optional;)V");

        ClassNode minimal = read(protocolLib,
                "com/comphenix/protocol/injector/netty/channel/NettyChannelMinimalInjector.class");
        assertField(minimal, "injector",
                "Lcom/comphenix/protocol/injector/netty/channel/NettyChannelInjector;",
                Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL);
        ClassNode injector = read(protocolLib,
                "com/comphenix/protocol/injector/netty/channel/NettyChannelInjector.class");
        assertField(injector, "networkManager", "Ljava/lang/Object;", Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL);
    }

    private static ClassNode read(Path jar, String entryName) throws IOException {
        try (ZipFile archive = new ZipFile(jar.toFile())) {
            var entry = archive.getEntry(entryName);
            assertNotNull(entry, entryName);
            ClassNode node = new ClassNode();
            try (var input = archive.getInputStream(entry)) {
                new ClassReader(input).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
            return node;
        }
    }

    private static void assertField(ClassNode owner, String name, String descriptor, int requiredAccess) {
        FieldNode field = owner.fields.stream()
                .filter(candidate -> candidate.name.equals(name) && candidate.desc.equals(descriptor))
                .findFirst().orElseThrow(() -> new AssertionError(owner.name + "." + name + descriptor));
        assertEquals(requiredAccess, field.access & requiredAccess, owner.name + "." + name);
    }

    private static void assertMethod(ClassNode owner, String name, String descriptor) {
        assertTrue(owner.methods.stream().map(MethodNode.class::cast)
                .anyMatch(method -> method.name.equals(name) && method.desc.equals(descriptor)),
                owner.name + "." + name + descriptor);
    }

    private static String required(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("missing system property " + name);
        return value;
    }
}
