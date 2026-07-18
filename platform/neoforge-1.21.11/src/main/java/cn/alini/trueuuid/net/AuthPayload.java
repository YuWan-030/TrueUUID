package cn.alini.trueuuid.net;

import cn.alini.trueuuid.protocol.AuthMessages;
import cn.alini.trueuuid.protocol.AuthWireCodec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.login.custom.CustomQueryPayload;
import net.minecraft.resources.Identifier;

/** Native packet wrapper; the shared protocol owns every byte-level bound. */
public record AuthPayload(AuthMessages.Query message) implements CustomQueryPayload {
    public AuthPayload(FriendlyByteBuf buffer) { this(AuthWireCodec.decodeQuery(read(buffer))); }
    @Override public Identifier id() { return NetIds.AUTH; }
    @Override public void write(FriendlyByteBuf buffer) { buffer.writeBytes(AuthWireCodec.encodeQuery(message)); }

    private static byte[] read(FriendlyByteBuf buffer) {
        if (buffer.readableBytes() > 10_000) throw new IllegalArgumentException("TrueUUID query is too large");
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        return bytes;
    }
}
