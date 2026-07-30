package cn.alini.trueuuid.server;

import cn.alini.trueuuid.net.NetIds;
import cn.alini.trueuuid.protocol.AuthMessages;
import cn.alini.trueuuid.protocol.AuthWireCodec;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;

final class LoginPacketCodec {
    static AuthMessages.Answer decode(FriendlyByteBuf data) {
        if (data == null) throw new IllegalArgumentException("missing login answer payload");
        byte[] payload = new byte[data.readableBytes()];
        data.readBytes(payload);
        return AuthWireCodec.decodeAnswer(payload);
    }

    static ClientboundCustomQueryPacket initial(int transactionId, String nonce) {
        return query(transactionId, new AuthMessages.Query(nonce, false, "", ""));
    }

    static ClientboundCustomQueryPacket migration(int transactionId, PlayerDataMigration.OfflineData data) {
        return query(transactionId, new AuthMessages.Query(NetIds.MIGRATION_CONFIRM_SERVER_ID, true,
                data.offlineUuid().toString(), data.summary()));
    }

    private static ClientboundCustomQueryPacket query(int transactionId, AuthMessages.Query query) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeBytes(AuthWireCodec.encodeQuery(query));
        return new ClientboundCustomQueryPacket(transactionId, NetIds.AUTH, buffer);
    }

    private LoginPacketCodec() {}
}
