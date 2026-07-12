package cn.alini.trueuuid.protocol;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EndpointPolicyTest {
    private static EndpointPolicy policy(List<String> hosts, String ip) {
        return new EndpointPolicy(hosts, ignored -> new InetAddress[]{InetAddress.getByName(ip)});
    }

    @Test void acceptsAllowlistedHttpsHasJoinedOnPublicAddress() throws Exception {
        var approved = policy(List.of("auth.example.com"), "93.184.216.34")
                .approveClientEndpoint("https://auth.example.com/api/sessionserver/session/minecraft/hasJoined");
        assertEquals("auth.example.com", approved.host());
    }

    @Test void emptyAllowlistRejectsClientEndpoint() {
        assertThrows(IllegalArgumentException.class, () -> policy(List.of(), "93.184.216.34")
                .approveClientEndpoint("https://auth.example.com/sessionserver/session/minecraft/hasJoined"));
    }

    @Test void rejectsHttpPortsRedirectLikePathsAndPrivateDns() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> policy(List.of("auth.example.com"), "93.184.216.34")
                        .approveClientEndpoint("http://auth.example.com/sessionserver/session/minecraft/hasJoined")),
                () -> assertThrows(IllegalArgumentException.class, () -> policy(List.of("auth.example.com"), "93.184.216.34")
                        .approveClientEndpoint("https://auth.example.com:8443/sessionserver/session/minecraft/hasJoined")),
                () -> assertThrows(IllegalArgumentException.class, () -> policy(List.of("auth.example.com"), "93.184.216.34")
                        .approveClientEndpoint("https://auth.example.com/other")),
                () -> assertThrows(IllegalArgumentException.class, () -> policy(List.of("auth.example.com"), "127.0.0.1")
                        .approveClientEndpoint("https://auth.example.com/sessionserver/session/minecraft/hasJoined")),
                () -> assertThrows(IllegalArgumentException.class, () -> policy(List.of("auth.example.com"), "169.254.169.254")
                        .approveClientEndpoint("https://auth.example.com/sessionserver/session/minecraft/hasJoined"))
        );
    }

    @Test void rejectsExcessiveDnsAnswers() throws Exception {
        InetAddress publicAddress = InetAddress.getByName("93.184.216.34");
        InetAddress[] answers = new InetAddress[EndpointPolicy.MAX_RESOLVED_ADDRESSES + 1];
        java.util.Arrays.fill(answers, publicAddress);
        EndpointPolicy policy = new EndpointPolicy(List.of("auth.example.com"), ignored -> answers);
        assertThrows(IllegalArgumentException.class, () -> policy.approveClientEndpoint(
                "https://auth.example.com/sessionserver/session/minecraft/hasJoined"));
    }
}
