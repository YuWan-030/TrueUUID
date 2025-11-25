package cn.alini.trueuuid.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.login.custom.CustomQueryPayload;
import net.minecraft.resources.ResourceLocation;

public record AuthPayload(String serverId) implements CustomQueryPayload {
    public static final ResourceLocation ID = NetIds.AUTH;

    public AuthPayload(FriendlyByteBuf buf) {
        this(buf.readUtf());
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(serverId);
    }
}