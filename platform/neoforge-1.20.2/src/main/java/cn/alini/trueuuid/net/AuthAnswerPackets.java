package cn.alini.trueuuid.net;

import cn.alini.trueuuid.protocol.AuthMessages;
import cn.alini.trueuuid.protocol.AuthWireCodec;
import net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket;
import net.neoforged.neoforge.network.custom.payload.SimpleQueryPayload;

/** NeoForge 20.2's patched login packet only accepts its native wrapper. */
public final class AuthAnswerPackets {
    public static ServerboundCustomQueryAnswerPacket create(int transactionId, AuthMessages.Answer answer) {
        return new ServerboundCustomQueryAnswerPacket(transactionId,
                SimpleQueryPayload.outbound(AuthWireCodec.encodeAnswer(answer), transactionId, NetIds.AUTH));
    }

    private AuthAnswerPackets() {}
}
