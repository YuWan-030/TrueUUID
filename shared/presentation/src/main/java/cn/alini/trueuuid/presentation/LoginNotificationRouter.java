package cn.alini.trueuuid.presentation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/** Pure routing policy used by adapters for player-only feedback and operator audit. */
public final class LoginNotificationRouter {
    public enum Kind { JOIN_RESULT, OPERATOR_AUDIT }

    public static <T> List<Delivery<T>> route(T joiningPlayer, Collection<T> onlinePlayers,
                                              Predicate<T> isOperator, boolean showJoinFeedback,
                                              boolean notifyOperators) {
        Objects.requireNonNull(joiningPlayer, "joiningPlayer");
        Objects.requireNonNull(onlinePlayers, "onlinePlayers");
        Objects.requireNonNull(isOperator, "isOperator");
        List<Delivery<T>> deliveries = new ArrayList<>();
        if (showJoinFeedback) deliveries.add(new Delivery<>(joiningPlayer, Kind.JOIN_RESULT));
        if (notifyOperators) {
            for (T player : onlinePlayers) {
                if (player != null && isOperator.test(player)) {
                    deliveries.add(new Delivery<>(player, Kind.OPERATOR_AUDIT));
                }
            }
        }
        return List.copyOf(deliveries);
    }

    public record Delivery<T>(T recipient, Kind kind) {}

    private LoginNotificationRouter() {}
}
