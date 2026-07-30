package cn.alini.trueuuid.net;

import cn.alini.trueuuid.protocol.AuthMessages;
import cn.alini.trueuuid.protocol.AuthWireCodec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.login.custom.CustomQueryPayload;
import net.minecraft.resources.ResourceLocation;

/** FriendlyByteBuf payload shared by Forge 48 through 55. */
public record ForgeAuthPayload(AuthMessages.Query message) implements CustomQueryPayload {
    public ForgeAuthPayload(FriendlyByteBuf buffer) { this(AuthWireCodec.decodeQuery(read(buffer))); }
    @Override public ResourceLocation id() { return ForgeNetIds.AUTH; }
    @Override public void write(FriendlyByteBuf buffer) { buffer.writeBytes(AuthWireCodec.encodeQuery(message)); }
    private static byte[] read(FriendlyByteBuf buffer) {
        if (buffer.readableBytes() > 10_000) throw new IllegalArgumentException("TrueUUID query is too large");
        byte[] result = new byte[buffer.readableBytes()];
        buffer.readBytes(result);
        return result;
    }
}
