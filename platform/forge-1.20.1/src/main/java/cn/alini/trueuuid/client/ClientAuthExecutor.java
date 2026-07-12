package cn.alini.trueuuid.client;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class ClientAuthExecutor {
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(8), r -> {
        Thread t = new Thread(r, "TrueUUID-ClientAuth");
        t.setDaemon(true);
        return t;
    }, new ThreadPoolExecutor.AbortPolicy());

    public static <T> CompletableFuture<T> submit(Supplier<T> operation) {
        try { return CompletableFuture.supplyAsync(operation, EXECUTOR); }
        catch (Throwable failure) { return CompletableFuture.failedFuture(failure); }
    }

    private ClientAuthExecutor() {}
}
