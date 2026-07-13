package cn.alini.trueuuid.protocol;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Bounded, cancellable request execution shared by all platform adapters. */
public final class BoundedRequestCoordinator implements AutoCloseable {
    private static final int MAX_IN_FLIGHT = 64;
    private static final int MAX_PER_NAME = 2;
    private static final int MAX_PER_IP = 2;
    private record Key(String name, String ip, String discriminator) {}

    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(4, 4, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(MAX_IN_FLIGHT), r -> {
        Thread thread = new Thread(r, "TrueUUID-AuthWorker");
        thread.setDaemon(true);
        return thread;
    }, new ThreadPoolExecutor.AbortPolicy());
    private final Map<Key, CancellableFuture<?>> inFlight = new HashMap<>();
    private final Map<String, Integer> byName = new HashMap<>();
    private final Map<String, Integer> byIp = new HashMap<>();
    private boolean closed;

    @SuppressWarnings("unchecked")
    public synchronized <T> CompletableFuture<T> submit(String name, String ip, String discriminator, Callable<T> request) {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("authentication service is closed"));
        String normalizedName = Objects.requireNonNullElse(name, "").toLowerCase(Locale.ROOT);
        String normalizedIp = Objects.requireNonNullElse(ip, "");
        Key key = new Key(normalizedName, normalizedIp, Objects.requireNonNullElse(discriminator, ""));
        CancellableFuture<?> existing = inFlight.get(key);
        if (existing != null) return (CompletableFuture<T>) existing;
        if (inFlight.size() >= MAX_IN_FLIGHT || byName.getOrDefault(normalizedName, 0) >= MAX_PER_NAME
                || byIp.getOrDefault(normalizedIp, 0) >= MAX_PER_IP) {
            return CompletableFuture.failedFuture(new IllegalStateException("authentication request limit reached"));
        }
        CancellableFuture<T> result = new CancellableFuture<>();
        inFlight.put(key, result);
        byName.merge(normalizedName, 1, Integer::sum);
        byIp.merge(normalizedIp, 1, Integer::sum);
        try {
            result.task = executor.submit(() -> {
                if (result.isCancelled()) return;
                try { result.complete(request.call()); }
                catch (Throwable ex) { result.completeExceptionally(ex); }
            });
        } catch (Throwable ex) {
            result.completeExceptionally(ex);
        }
        result.whenComplete((ignored, error) -> remove(key, normalizedName, normalizedIp, result));
        return result;
    }

    private synchronized void remove(Key key, String name, String ip, CancellableFuture<?> expected) {
        if (!inFlight.remove(key, expected)) return;
        decrement(byName, name);
        decrement(byIp, ip);
    }

    private static void decrement(Map<String, Integer> counts, String key) {
        counts.computeIfPresent(key, (ignored, count) -> count <= 1 ? null : count - 1);
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        var pending = java.util.List.copyOf(inFlight.values());
        inFlight.clear(); byName.clear(); byIp.clear();
        pending.forEach(future -> future.cancel(true));
        executor.shutdownNow();
    }

    private static final class CancellableFuture<T> extends CompletableFuture<T> {
        private volatile Future<?> task;
        @Override public boolean cancel(boolean mayInterruptIfRunning) {
            boolean cancelled = super.cancel(mayInterruptIfRunning);
            Future<?> current = task;
            if (current != null) current.cancel(mayInterruptIfRunning);
            return cancelled;
        }
    }
}
