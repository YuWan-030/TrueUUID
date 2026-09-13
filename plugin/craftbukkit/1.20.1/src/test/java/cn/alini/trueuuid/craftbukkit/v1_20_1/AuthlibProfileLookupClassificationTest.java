package cn.alini.trueuuid.craftbukkit.v1_20_1;

import cn.alini.trueuuid.server.AuthorityResult;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class AuthlibProfileLookupClassificationTest {
    @Test void acceptsOnlyMatchingCompletePremiumProfiles() {
        AuthorityResult.PremiumProfile premium = assertInstanceOf(AuthorityResult.PremiumProfile.class,
                LoginBridge.classifyProfileLookup(
                        "FixGOD", UUID.fromString("d64da409-52f6-4ce8-a082-73b9a5d303bd"), "FixGOD", false));
        assertEquals("FixGOD", premium.canonicalName());
        assertInstanceOf(AuthorityResult.Unavailable.class,
                LoginBridge.classifyProfileLookup("FixGOD", null, "FixGOD", false));
        assertInstanceOf(AuthorityResult.Unavailable.class,
                LoginBridge.classifyProfileLookup("FixGOD", UUID.randomUUID(), "DifferentName", false));
    }

    @Test void onlyExactAuthlibNotFoundShapeMeansAbsent() {
        assertInstanceOf(AuthorityResult.DefinitelyAbsent.class,
                LoginBridge.classifyProfileLookup("1233", null, "1233", true));
        assertInstanceOf(AuthorityResult.Unavailable.class,
                LoginBridge.classifyProfileLookup("1233", UUID.randomUUID(), "1233", true));
        assertInstanceOf(AuthorityResult.Unavailable.class,
                LoginBridge.classifyProfileLookup("1233", null, "other", true));
    }
}
