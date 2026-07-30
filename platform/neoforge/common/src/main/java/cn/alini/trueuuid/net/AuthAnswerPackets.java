package cn.alini.trueuuid.net;

import cn.alini.trueuuid.protocol.AuthMessages;
import net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket;

/** Creates the loader-era-specific login answer packet. */
public final class AuthAnswerPackets {
    public static ServerboundCustomQueryAnswerPacket create(int transactionId, AuthMessages.Answer answer) {
        return new ServerboundCustomQueryAnswerPacket(transactionId, new AuthAnswerPayload(answer));
    }

    private AuthAnswerPackets() {}
}
