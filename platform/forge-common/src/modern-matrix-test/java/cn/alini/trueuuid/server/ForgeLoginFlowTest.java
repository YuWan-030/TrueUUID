package cn.alini.trueuuid.server;

import cn.alini.trueuuid.protocol.OfflineFallbackPolicy;

import cn.alini.trueuuid.net.ForgeAuthAnswerPayload;
import cn.alini.trueuuid.protocol.AuthMessages;
import cn.alini.trueuuid.protocol.AuthWireCodec;
import cn.alini.trueuuid.protocol.MigrationTransaction;
import cn.alini.trueuuid.protocol.SessionVerifier;
import cn.alini.trueuuid.protocol.VerifiedProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Contract test reused by each modern-matrix Forge target. */
class ForgeLoginFlowTest {
    @Test
    void usesSharedQueryFixtureAndOnlyAcceptsTheActiveTransaction() {
        ForgeLoginFlow flow = new ForgeLoginFlow();
        assertEquals("nonce", AuthWireCodec.decodeQuery(flow.start(22, "nonce", 100)).nonce());
        byte[] answer = AuthWireCodec.encodeAnswer(new AuthMessages.Answer(true, "", false, false));
        assertTrue(flow.accept(23, answer, "Player", "203.0.113.9", verifier()).join().isEmpty());
        assertTrue(flow.accept(22, answer, "Player", "203.0.113.9", verifier()).join().isPresent());
    }

    @Test
    void timeoutAndCloseAreExplicitLifecycleEffects() {
        ForgeLoginFlow flow = new ForgeLoginFlow();
        flow.start(22, "nonce", 100);
        assertTrue(flow.timedOut(130, 30));
        flow.close();
        assertTrue(!flow.active());
    }

    @Test
    void migrationConfirmationCanOnlyFollowVerification() {
        ForgeLoginFlow flow = new ForgeLoginFlow();
        byte[] answer = AuthWireCodec.encodeAnswer(new AuthMessages.Answer(true, "", false, false));
        flow.start(22, "nonce", 100);
        assertTrue(flow.accept(22, answer, "Player", "203.0.113.9", verifier()).join().isPresent());

        UUID offline = UUID.nameUUIDFromBytes("OfflinePlayer:Player".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        AuthMessages.Query query = AuthWireCodec.decodeQuery(flow.migrationQuery(23,
                new MigrationTransaction.Offer(offline, "playerdata"), 110));
        assertTrue(query.migrationAvailable());
        assertEquals(offline.toString(), query.offlineUuid());
        assertTrue(!flow.acceptMigration(24, AuthWireCodec.encodeAnswer(new AuthMessages.Answer(true, "", true, false))));
        assertTrue(flow.acceptMigration(23, AuthWireCodec.encodeAnswer(new AuthMessages.Answer(true, "", true, false))));
    }

    @Test
    void queryAnswerUsesTheVanillaNullableEnvelopeBeforeTheTrueuuidWirePayload() {
        AuthMessages.Answer answer = new AuthMessages.Answer(true, "", false, false);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeBoolean(true);
        new ForgeAuthAnswerPayload(answer).write(buffer);

        assertTrue(buffer.readBoolean());
        assertEquals(answer, new ForgeAuthAnswerPayload(buffer).message());
    }

    @Test
    void vanillaPacketCodecWritesTheNullableEnvelopeBeforeTheTrueuuidWirePayload() {
        AuthMessages.Answer answer = new AuthMessages.Answer(true, "", false, false);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        ServerboundCustomQueryAnswerPacket.STREAM_CODEC.encode(buffer,
                new ServerboundCustomQueryAnswerPacket(22, new ForgeAuthAnswerPayload(answer)));

        assertEquals(22, buffer.readVarInt());
        assertTrue(buffer.readBoolean());
        assertEquals(answer, new ForgeAuthAnswerPayload(buffer).message());
        assertEquals(0, buffer.readableBytes());
    }

    @Test
    void offlineFallbackPolicyAllowsOnlyUnknownNamesByDefault() {
        assertTrue(OfflineFallbackPolicy.permits(false, true, true, true));
        assertTrue(!OfflineFallbackPolicy.permits(true, true, true, true));
        assertTrue(!OfflineFallbackPolicy.permits(false, false, false, false));
        assertTrue(OfflineFallbackPolicy.permits(true, true, false, false));
    }

    private static SessionVerifier verifier() {
        return request -> CompletableFuture.completedFuture(Optional.of(new VerifiedProfile(UUID.randomUUID(), "Player", List.of())));
    }
}
