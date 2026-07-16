package cn.alini.trueuuid.fabric.login;

import cn.alini.trueuuid.protocol.AuthMessages;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FabricLoginPayloadsTest {
    @Test void roundTripsTheSharedAnswerFormat() {
        AuthMessages.Answer expected = new AuthMessages.Answer(true,
                "https://skin.example/sessionserver/session/minecraft/hasJoined", false, false);

        assertEquals(expected, FabricLoginPayloads.readAnswer(FabricLoginPayloads.answer(expected)));
    }

    @Test void rejectsAnOversizedNativeBufferBeforeAllocation() {
        PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer(FabricLoginPayloads.MAX_LOGIN_PAYLOAD_BYTES + 1));
        buffer.writeZero(FabricLoginPayloads.MAX_LOGIN_PAYLOAD_BYTES + 1);

        assertThrows(IllegalArgumentException.class, () -> FabricLoginPayloads.readAnswer(buffer));
    }
}
