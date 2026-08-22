package cn.alini.trueuuid.fabric.login;

import cn.alini.trueuuid.api.AccountStatus;
import cn.alini.trueuuid.protocol.HybridStatusWireCodec;
import net.minecraft.network.PacketByteBuf;

/** Fixed one-byte 1.20-era server-to-client account-status payload. */
final class FabricStatusPayloads {
    private FabricStatusPayloads() {}

    static void write(PacketByteBuf buffer, FabricAuthenticationSource.ClientStatus status) {
        if (status == null) throw new IllegalArgumentException("missing server status");
        AccountStatus publicStatus = status == FabricAuthenticationSource.ClientStatus.PREMIUM
                ? AccountStatus.PREMIUM_VERIFIED : AccountStatus.OFFLINE_FALLBACK;
        buffer.writeByte(HybridStatusWireCodec.encode(publicStatus));
    }

    /** Returns null for malformed or unknown values; unknown data is never Premium. */
    static FabricAuthenticationSource.ClientStatus read(PacketByteBuf buffer) {
        if (buffer.readableBytes() != 1) return null;
        return HybridStatusWireCodec.decode(buffer.readUnsignedByte())
                .map(status -> status == AccountStatus.PREMIUM_VERIFIED
                        ? FabricAuthenticationSource.ClientStatus.PREMIUM
                        : FabricAuthenticationSource.ClientStatus.OFFLINE)
                .orElse(null);
    }
}
