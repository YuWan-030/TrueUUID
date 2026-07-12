package cn.alini.trueuuid.server;

import cn.alini.trueuuid.net.NetIds;
import cn.alini.trueuuid.protocol.AuthMessages;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;

final class LoginPacketCodec {
    record Answer(boolean joined, String endpoint, boolean migrationConfirmed, boolean missingSessionToken) {}

    static Answer decode(FriendlyByteBuf data) {
        if (data == null) throw new IllegalArgumentException("missing login answer payload");
        boolean joined = data.readBoolean();
        String endpoint = data.isReadable() ? data.readUtf(AuthMessages.MAX_ENDPOINT_CHARS) : "";
        boolean migration = data.isReadable() && data.readBoolean();
        boolean missingToken = data.isReadable() && data.readBoolean();
        return new Answer(joined, endpoint, migration, missingToken);
    }

    static ClientboundCustomQueryPacket initial(int transactionId, String nonce) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeUtf(nonce, AuthMessages.MAX_NONCE_CHARS);
        buffer.writeBoolean(false);
        return new ClientboundCustomQueryPacket(transactionId, NetIds.AUTH, buffer);
    }

    static ClientboundCustomQueryPacket migration(int transactionId, PlayerDataMigration.OfflineData data) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeUtf(NetIds.MIGRATION_CONFIRM_SERVER_ID, AuthMessages.MAX_NONCE_CHARS);
        buffer.writeBoolean(true);
        buffer.writeUtf(data.offlineUuid().toString(), 64);
        buffer.writeUtf(data.summary(), AuthMessages.MAX_SUMMARY_CHARS);
        return new ClientboundCustomQueryPacket(transactionId, NetIds.AUTH, buffer);
    }

    private LoginPacketCodec() {}
}
