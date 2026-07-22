package cn.alini.trueuuid.presentation;

import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

/** Persistent server-confirmed state plus a monotonic three-second HUD notice. */
public final class ClientStatusState {
    public static final long VISIBLE_NANOS = 3_000_000_000L;
    public static final long FADE_NANOS = 500_000_000L;

    private final LongSupplier nanoTime;
    private ConfirmedAccountStatus status;
    private long receivedAtNanos;

    /** Client-local integrated-world changes that may produce one private chat notice. */
    public enum LocalWorldTransition {
        NONE(null),
        ENTERED_SINGLEPLAYER("trueuuid.chat.singleplayer"),
        OPENED_TO_LAN("trueuuid.chat.lan_premium");

        private final String chatTranslationKey;

        LocalWorldTransition(String chatTranslationKey) {
            this.chatTranslationKey = chatTranslationKey;
        }

        public String chatTranslationKey() {
            return chatTranslationKey;
        }
    }

    public ClientStatusState() {
        this(System::nanoTime);
    }

    public ClientStatusState(LongSupplier nanoTime) {
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    /** A new server receipt replaces the persistent value and restarts the HUD timer. */
    public synchronized void receive(ConfirmedAccountStatus status) {
        this.status = Objects.requireNonNull(status, "status");
        this.receivedAtNanos = nanoTime.getAsLong();
    }

    /** Enters a client-local state once without restarting its timer every render frame. */
    public synchronized boolean receiveIfChanged(ConfirmedAccountStatus status) {
        Objects.requireNonNull(status, "status");
        if (this.status == status) return false;
        receive(status);
        return true;
    }

    /**
     * Reconciles the local integrated-server mode. A private world overrides
     * server login markers with Singleplayer; publishing it to LAN changes the
     * persistent and transient badge to the explicit Premium (LAN) mode.
     */
    public synchronized LocalWorldTransition reconcileIntegratedWorld(boolean integratedWorld,
                                                                        boolean publishedToLan) {
        if (!integratedWorld) return LocalWorldTransition.NONE;
        ConfirmedAccountStatus target = IntegratedWorldPolicy.isPrivateSingleplayer(true, publishedToLan)
                ? ConfirmedAccountStatus.SINGLEPLAYER
                : ConfirmedAccountStatus.LAN_PREMIUM;
        if (!receiveIfChanged(target)) return LocalWorldTransition.NONE;
        return publishedToLan
                ? LocalWorldTransition.OPENED_TO_LAN
                : LocalWorldTransition.ENTERED_SINGLEPLAYER;
    }

    /** Clears both persistent and transient state on disconnect/world leave. */
    public synchronized void clear() {
        status = null;
        receivedAtNanos = 0L;
    }

    public synchronized Optional<ConfirmedAccountStatus> persistentStatus() {
        return Optional.ofNullable(status);
    }

    public synchronized Optional<TransientNotice> transientNotice() {
        if (status == null) return Optional.empty();
        long elapsed = Math.max(0L, nanoTime.getAsLong() - receivedAtNanos);
        if (elapsed >= VISIBLE_NANOS) return Optional.empty();
        long fadeStart = VISIBLE_NANOS - FADE_NANOS;
        float alpha = elapsed <= fadeStart
                ? 1.0F
                : (float) (VISIBLE_NANOS - elapsed) / (float) FADE_NANOS;
        return Optional.of(new TransientNotice(status, Math.max(0.0F, Math.min(1.0F, alpha))));
    }

    public record TransientNotice(ConfirmedAccountStatus status, float alpha) {}
}
