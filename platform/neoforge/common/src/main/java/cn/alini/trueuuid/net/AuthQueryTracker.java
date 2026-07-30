package cn.alini.trueuuid.net;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded by one short-lived login listener per id; consumed on decode. */
public final class AuthQueryTracker {
    private static final Set<Integer> IN_FLIGHT = ConcurrentHashMap.newKeySet();
    public static boolean mark(int transactionId) { return IN_FLIGHT.add(transactionId); }
    public static boolean consume(int transactionId) { return IN_FLIGHT.remove(transactionId); }
    public static void clear(int transactionId) { IN_FLIGHT.remove(transactionId); }
    private AuthQueryTracker() {}
}
