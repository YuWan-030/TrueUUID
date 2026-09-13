package cn.alini.trueuuid.craftbukkit.v1_20_1;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Checks the obfuscated half of {@link LoginSeams} against a real mapped Spigot or Paper server JAR. */
@Tag("server-runtime")
class ServerRuntimeContractTest {
    @Test void everyServerSeamExistsWithItsExactDescriptorAndStaticness() throws Exception {
        Path server = Path.of(required("trueuuid.serverRuntimeJar"));
        List<String> missing = new ArrayList<>();
        try (ZipFile archive = new ZipFile(server.toFile())) {
            for (LoginSeams.Seam seam : LoginSeams.ALL) {
                if (!seam.serverMember()) continue;
                ClassNode owner = read(archive, seam.owner().replace('.', '/') + ".class");
                if (owner == null || !present(owner, seam)) missing.add(seam.describe());
            }
        }
        assertEquals(List.of(), missing, "server JAR " + server);
    }

    private static boolean present(ClassNode owner, LoginSeams.Seam seam) {
        if (seam.field()) {
            boolean wantStatic = seam.kind() == LoginSeams.Kind.STATIC_GETTER;
            return owner.fields.stream().anyMatch(field -> field.name.equals(seam.name())
                    && field.desc.equals(seam.descriptor())
                    && ((field.access & Opcodes.ACC_STATIC) != 0) == wantStatic);
        }
        boolean wantStatic = seam.kind() == LoginSeams.Kind.STATIC;
        return owner.methods.stream().anyMatch(method -> method.name.equals(seam.name())
                && method.desc.equals(seam.descriptor())
                && ((method.access & Opcodes.ACC_STATIC) != 0) == wantStatic);
    }

    private static ClassNode read(ZipFile archive, String entryName) throws IOException {
        ZipEntry entry = archive.getEntry(entryName);
        if (entry == null) return null;
        ClassNode node = new ClassNode();
        try (InputStream input = archive.getInputStream(entry)) {
            new ClassReader(input).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return node;
    }

    private static String required(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("missing system property " + name);
        return value;
    }
}
