package cn.alini.trueuuid.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.login.custom.CustomQueryAnswerPayload;

public record AuthAnswerPayload(boolean ok, int version, long timestamp) implements CustomQueryAnswerPayload {
    private static final int CURRENT_VERSION = 1;
    
    // 便捷构造器，只需要传入ok状态
    public AuthAnswerPayload(boolean ok) {
        this(ok, CURRENT_VERSION, System.currentTimeMillis());
    }
    
    public AuthAnswerPayload(FriendlyByteBuf buf) {
        this(buf.readBoolean(), buf.readInt(), buf.readLong());
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(ok);
        buf.writeInt(version);
        buf.writeLong(timestamp);
    }
}