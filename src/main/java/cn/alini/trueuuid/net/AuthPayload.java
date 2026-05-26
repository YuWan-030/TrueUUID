package cn.alini.trueuuid.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.login.custom.CustomQueryPayload;
import net.minecraft.resources.ResourceLocation;

public record AuthPayload(String serverId, boolean offlineUpgradeAvailable, String offlineUuid, String offlineDataSummary) implements CustomQueryPayload {
    public static final ResourceLocation ID = NetIds.AUTH;

    public AuthPayload(String serverId) {
        this(serverId, false, "", "");
    }

    public AuthPayload(FriendlyByteBuf buf) {
        this(
                buf.readUtf(),
                buf.isReadable() && buf.readBoolean(),
                buf.isReadable() ? buf.readUtf() : "",
                buf.isReadable() ? buf.readUtf() : ""
        );
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(serverId);
        buf.writeBoolean(offlineUpgradeAvailable);
        buf.writeUtf(offlineUuid != null ? offlineUuid : "");
        buf.writeUtf(offlineDataSummary != null ? offlineDataSummary : "");
    }
}
