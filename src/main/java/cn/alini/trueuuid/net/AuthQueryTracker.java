package cn.alini.trueuuid.net;

import java.util.HashSet;
import java.util.Set;

public final class AuthQueryTracker {
    private static final Set<Integer> IN_FLIGHT = new HashSet<>();

    public static void mark(int txId) {
        synchronized (IN_FLIGHT) {
            IN_FLIGHT.add(txId);
        }
    }

    public static boolean contains(int txId) {
        synchronized (IN_FLIGHT) {
            return IN_FLIGHT.contains(txId);
        }
    }

    public static boolean consume(int txId) {
        synchronized (IN_FLIGHT) {
            return IN_FLIGHT.remove(txId);
        }
    }

    private AuthQueryTracker() {}
}