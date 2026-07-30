package cn.alini.trueuuid.server;

import cn.alini.trueuuid.net.ForgeAuthAnswerPayload;
import cn.alini.trueuuid.protocol.AuthMessages;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgePacketEnvelopeTest {
    @Test
    void vanillaStreamCodecWritesTheNullableEnvelopeBeforeTheTrueuuidWirePayload() {
        AuthMessages.Answer answer = new AuthMessages.Answer(true, "", false, false);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        ServerboundCustomQueryAnswerPacket.STREAM_CODEC.encode(buffer,
                new ServerboundCustomQueryAnswerPacket(22, new ForgeAuthAnswerPayload(answer)));

        assertEquals(22, buffer.readVarInt());
        assertTrue(buffer.readBoolean());
        assertEquals(answer, new ForgeAuthAnswerPayload(buffer).message());
        assertEquals(0, buffer.readableBytes());
    }
}
