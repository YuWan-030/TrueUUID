package cn.alini.trueuuid.net;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ForgeQueryTracker {
    private static final Set<Integer> PENDING = ConcurrentHashMap.newKeySet();
    public static boolean register(int transactionId) { return PENDING.add(transactionId); }
    public static boolean claim(int transactionId) { return PENDING.remove(transactionId); }
    public static void discard(int transactionId) { PENDING.remove(transactionId); }
    private ForgeQueryTracker() {}
}
