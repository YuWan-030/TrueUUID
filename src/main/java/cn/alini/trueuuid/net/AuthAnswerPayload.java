package cn.alini.trueuuid.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.login.custom.CustomQueryAnswerPayload;

public record AuthAnswerPayload(boolean ok, String hasJoinedUrl, boolean migrationConfirmed, boolean missingSessionToken) implements CustomQueryAnswerPayload {
    private static final int CURRENT_VERSION = 1;

    public AuthAnswerPayload(boolean ok) {
        this(ok, "", false, false);
    }

    public AuthAnswerPayload(boolean ok, String hasJoinedUrl) {
        this(ok, hasJoinedUrl, false, false);
    }

    public AuthAnswerPayload(boolean ok, String hasJoinedUrl, boolean migrationConfirmed) {
        this(ok, hasJoinedUrl, migrationConfirmed, false);
    }
    
    public AuthAnswerPayload(FriendlyByteBuf buf) {
        this(
                buf.readBoolean(),
                buf.isReadable() ? buf.readUtf() : "",
                buf.isReadable() && buf.readBoolean(),
                buf.isReadable() && buf.readBoolean()
        );
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(ok);
        buf.writeUtf(hasJoinedUrl != null ? hasJoinedUrl : "");
        buf.writeBoolean(migrationConfirmed);
        buf.writeBoolean(missingSessionToken);
    }
}
