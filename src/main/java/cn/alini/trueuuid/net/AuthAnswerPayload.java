package cn.alini.trueuuid.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.login.custom.CustomQueryAnswerPayload;

public record AuthAnswerPayload(boolean ok, String hasJoinedUrl) implements CustomQueryAnswerPayload {
    private static final int CURRENT_VERSION = 1;

    public AuthAnswerPayload(boolean ok) {
        this(ok, "");
    }
    
    public AuthAnswerPayload(FriendlyByteBuf buf) {
        this(buf.readBoolean(), buf.isReadable() ? buf.readUtf() : "");
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(ok);
        buf.writeUtf(hasJoinedUrl != null ? hasJoinedUrl : "");
    }
}
