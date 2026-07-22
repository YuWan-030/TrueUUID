package cn.alini.trueuuid.fabric.login;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** One-byte typed play payload used by Minecraft 1.20.5+ networking. */
record FabricStatusPayload(FabricAuthenticationSource.ClientStatus status) implements CustomPayload {
    static final Id<FabricStatusPayload> ID = new Id<>(FabricLoginNetworking.STATUS_CHANNEL);
    static final PacketCodec<RegistryByteBuf, FabricStatusPayload> CODEC =
            CustomPayload.codecOf(FabricStatusPayload::write, FabricStatusPayload::new);

    private FabricStatusPayload(RegistryByteBuf buffer) {
        this(FabricStatusPayloads.read(buffer));
    }

    private void write(RegistryByteBuf buffer) {
        FabricStatusPayloads.write(buffer, status);
    }

    @Override
    public Id<FabricStatusPayload> getId() {
        return ID;
    }
}
