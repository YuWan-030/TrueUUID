package cn.alini.trueuuid.net;

import cn.alini.trueuuid.protocol.AuthMessages;
import cn.alini.trueuuid.protocol.AuthWireCodec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.login.custom.CustomQueryAnswerPayload;

public record ForgeAuthAnswerPayload(AuthMessages.Answer message) implements CustomQueryAnswerPayload {
    public ForgeAuthAnswerPayload(FriendlyByteBuf buffer) { this(AuthWireCodec.decodeAnswer(read(buffer))); }
    @Override public void write(FriendlyByteBuf buffer) { buffer.writeBytes(AuthWireCodec.encodeAnswer(message)); }
    private static byte[] read(FriendlyByteBuf buffer) {
        if (buffer.readableBytes() > 10_000) throw new IllegalArgumentException("TrueUUID answer is too large");
        byte[] result = new byte[buffer.readableBytes()];
        buffer.readBytes(result);
        return result;
    }
}
