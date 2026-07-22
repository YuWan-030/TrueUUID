package cn.alini.trueuuid.fabric.login;

import net.minecraft.network.PacketByteBuf;

/** Fixed one-byte 1.20-era server-to-client account-status payload. */
final class FabricStatusPayloads {
    private FabricStatusPayloads() {}

    static void write(PacketByteBuf buffer, FabricAuthenticationSource.ClientStatus status) {
        if (status == null) throw new IllegalArgumentException("missing server status");
        buffer.writeByte(status.wireId());
    }

    /** Returns null for malformed or unknown values; unknown data is never Premium. */
    static FabricAuthenticationSource.ClientStatus read(PacketByteBuf buffer) {
        if (buffer.readableBytes() != 1) return null;
        return FabricAuthenticationSource.ClientStatus.fromWireId(buffer.readUnsignedByte());
    }
}
