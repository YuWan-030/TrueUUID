package cn.alini.trueuuid.fabric.login;

import cn.alini.trueuuid.protocol.AuthMessages;
import cn.alini.trueuuid.protocol.AuthWireCodec;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;

/**
 * Native buffer boundary for Fabric's login networking API. The Java-only
 * protocol owns the field-level bounds; this class additionally bounds the
 * raw packet before allocating an array from a client-controlled buffer.
 */
public final class FabricLoginPayloads {
    public static final int MAX_LOGIN_PAYLOAD_BYTES = 12 * 1024;

    public static PacketByteBuf query(AuthMessages.Query query) {
        return wrap(AuthWireCodec.encodeQuery(query));
    }

    public static PacketByteBuf answer(AuthMessages.Answer answer) {
        return wrap(AuthWireCodec.encodeAnswer(answer));
    }

    public static AuthMessages.Query readQuery(PacketByteBuf buffer) {
        return AuthWireCodec.decodeQuery(readBounded(buffer));
    }

    public static AuthMessages.Answer readAnswer(PacketByteBuf buffer) {
        return AuthWireCodec.decodeAnswer(readBounded(buffer));
    }

    private static PacketByteBuf wrap(byte[] bytes) {
        return new PacketByteBuf(Unpooled.wrappedBuffer(bytes));
    }

    private static byte[] readBounded(PacketByteBuf buffer) {
        int readable = buffer.readableBytes();
        if (readable < 0 || readable > MAX_LOGIN_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("TrueUUID login payload is too large");
        }
        byte[] bytes = new byte[readable];
        buffer.readBytes(bytes);
        return bytes;
    }

    private FabricLoginPayloads() {}
}
