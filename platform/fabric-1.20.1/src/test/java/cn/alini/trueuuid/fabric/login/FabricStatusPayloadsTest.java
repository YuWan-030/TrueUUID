package cn.alini.trueuuid.fabric.login;

import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FabricStatusPayloadsTest {
    @Test
    void roundTripsOnlyServerDefinedStatuses() {
        PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());
        FabricStatusPayloads.write(buffer, FabricAuthenticationSource.ClientStatus.PREMIUM);
        assertEquals(FabricAuthenticationSource.ClientStatus.PREMIUM, FabricStatusPayloads.read(buffer));
    }

    @Test
    void malformedDataCannotBecomePremium() {
        PacketByteBuf unknown = new PacketByteBuf(Unpooled.buffer());
        unknown.writeByte(99);
        assertNull(FabricStatusPayloads.read(unknown));

        PacketByteBuf trailing = new PacketByteBuf(Unpooled.buffer());
        trailing.writeByte(1);
        trailing.writeByte(2);
        assertNull(FabricStatusPayloads.read(trailing));
    }
}
