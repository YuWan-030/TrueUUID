package cn.alini.trueuuid.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftNamesTest {
    @Test void clientAndPremiumNamesRemainCanonical() {
        assertDoesNotThrow(() -> MinecraftNames.requireValid("FixGOD_1"));
        for (String invalid : new String[]{"-FixGOD", ".FixGOD", "+FixGOD", ",FixGOD"}) {
            assertThrows(IllegalArgumentException.class, () -> MinecraftNames.requireValid(invalid));
        }
    }

    @Test void onlyCommandFriendlyLeadingAliasMarkersAreAccepted() {
        for (String alias : new String[]{"-FixGOD", ".FixGOD", "+FixGOD", "o_FixGOD"}) {
            assertDoesNotThrow(() -> MinecraftNames.requireValidEffective(alias));
        }
        for (String invalid : new String[]{",FixGOD", "*FixGOD", "Fix-GOD", "--FixGOD"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> MinecraftNames.requireValidEffective(invalid));
        }
        assertTrue(MinecraftNames.isCanonical("FixGOD"));
        assertFalse(MinecraftNames.isCanonical("-FixGOD"));
    }
}
