package cn.alini.trueuuid.presentation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoginNotificationRouterTest {
    private record Player(String name, boolean operator) {}

    @Test void joiningPlayerGetsExactlyOneResultAndNonOperatorObserverGetsNothing() {
        Player joining = new Player("joining", false);
        Player observer = new Player("observer", false);
        Player operator = new Player("operator", true);
        var deliveries = LoginNotificationRouter.route(
                joining, List.of(joining, observer, operator), Player::operator, true, true);

        assertEquals(1, deliveries.stream().filter(delivery ->
                delivery.recipient() == joining && delivery.kind() == LoginNotificationRouter.Kind.JOIN_RESULT).count());
        assertEquals(0, deliveries.stream().filter(delivery -> delivery.recipient() == observer).count());
        assertEquals(1, deliveries.stream().filter(delivery ->
                delivery.recipient() == operator && delivery.kind() == LoginNotificationRouter.Kind.OPERATOR_AUDIT).count());
    }

    @Test void conservativeOperatorDefaultSendsNoOperatorMessage() {
        Player joining = new Player("joining", false);
        Player operator = new Player("operator", true);
        var deliveries = LoginNotificationRouter.route(
                joining, List.of(joining, operator), Player::operator, true, false);
        assertEquals(List.of(new LoginNotificationRouter.Delivery<>(
                joining, LoginNotificationRouter.Kind.JOIN_RESULT)), deliveries);
    }
}
