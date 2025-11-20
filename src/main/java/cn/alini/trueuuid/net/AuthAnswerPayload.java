package cn.alini.trueuuid.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.login.custom.CustomQueryAnswerPayload;

public record AuthAnswerPayload(boolean ok) implements CustomQueryAnswerPayload {
    private static final int CURRENT_VERSION = 1;
    
    public AuthAnswerPayload(FriendlyByteBuf buf) {
        this(buf.readBoolean());
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(ok);
    }
}