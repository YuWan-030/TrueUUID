package cn.alini.trueuuid.craftbukkit.v1_20_1;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginSeamsTest {
    @Test void everyDeclaredSeamIsResolvedExactlyOnce() throws Exception {
        Set<LoginSeams.Seam> declared = new HashSet<>();
        for (Field field : LoginSeams.class.getDeclaredFields()) {
            if (field.getType() == LoginSeams.Seam.class && Modifier.isStatic(field.getModifiers())) {
                declared.add((LoginSeams.Seam) field.get(null));
            }
        }
        assertEquals(declared, new HashSet<>(LoginSeams.ALL));
        assertEquals(LoginSeams.ALL.size(), new HashSet<>(LoginSeams.ALL).size());
    }

    @Test void descriptorsMatchTheirKind() {
        for (LoginSeams.Seam seam : LoginSeams.ALL) {
            if (seam.field()) {
                assertTrue(Type.getType(seam.descriptor()).getSort() != Type.METHOD, seam.describe());
            } else {
                Type method = Type.getMethodType(seam.descriptor());
                if (seam.kind() == LoginSeams.Kind.CONSTRUCTOR) {
                    assertEquals("<init>", seam.name(), seam.describe());
                    assertEquals(Type.VOID_TYPE, method.getReturnType(), seam.describe());
                }
            }
        }
    }

    @Test void resolverSupportsEveryKind() throws Throwable {
        ClassLoader loader = getClass().getClassLoader();
        String fixture = Fixture.class.getName();
        Fixture instance = (Fixture) LoginSeams.resolve(
                new LoginSeams.Seam(fixture, LoginSeams.Kind.CONSTRUCTOR, "<init>", "(I)V", "test"), loader)
                .invoke(7);
        MethodHandle getter = LoginSeams.resolve(
                new LoginSeams.Seam(fixture, LoginSeams.Kind.GETTER, "hidden", "I", "test"), loader);
        MethodHandle setter = LoginSeams.resolve(
                new LoginSeams.Seam(fixture, LoginSeams.Kind.SETTER, "hidden", "I", "test"), loader);
        setter.invoke(instance, 9);
        assertEquals(9, (int) getter.invoke(instance));
        assertEquals(18, (int) LoginSeams.resolve(
                new LoginSeams.Seam(fixture, LoginSeams.Kind.VIRTUAL, "doubled", "()I", "test"), loader)
                .invoke(instance));
        assertEquals("fixed", (String) LoginSeams.resolve(
                new LoginSeams.Seam(fixture, LoginSeams.Kind.STATIC_GETTER, "CONSTANT", "Ljava/lang/String;", "test"),
                loader).invoke());
        assertEquals(5, (int) LoginSeams.resolve(
                new LoginSeams.Seam(fixture, LoginSeams.Kind.STATIC, "plusOne", "(I)I", "test"), loader)
                .invoke(4));
    }

    @Test void aServerWithoutTheMinecraftClassesRefusesAndNamesEverySeam() {
        LoginSeams.UnavailableException refused = assertThrows(LoginSeams.UnavailableException.class,
                () -> LoginSeams.resolveAll(getClass().getClassLoader()));
        assertTrue(refused.missing().contains(LoginSeams.CONNECTION_LISTENER));
        assertTrue(refused.missing().containsAll(LoginSeams.ALL.stream().filter(LoginSeams.Seam::serverMember).toList()));
        assertTrue(refused.getMessage().contains("net.minecraft.server.network.LoginListener.h"));
    }

    @Test void aChangedDescriptorIsRefused() {
        assertThrows(NoSuchFieldException.class, () -> LoginSeams.resolve(
                new LoginSeams.Seam(Fixture.class.getName(), LoginSeams.Kind.GETTER, "hidden", "J", "test"),
                getClass().getClassLoader()));
    }

    static final class Fixture {
        private static final String CONSTANT = "fixed";
        private int hidden;

        private Fixture(int hidden) {
            this.hidden = hidden;
        }

        private int doubled() {
            return hidden * 2;
        }

        private static int plusOne(int value) {
            return value + 1;
        }
    }
}
