package cn.alini.trueuuid.server;

import net.minecraft.server.MinecraftServer;

import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class MigrationCoordinator implements AutoCloseable {
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(16), r -> {
        Thread t = new Thread(r, "TrueUUID-MigrationWorker");
        t.setDaemon(true);
        return t;
    }, new ThreadPoolExecutor.AbortPolicy());

    public CompletableFuture<PlayerDataMigration.OfflineData> find(MinecraftServer server, String name) {
        return submit(() -> PlayerDataMigration.findOfflineData(server, name));
    }

    public CompletableFuture<Void> migrate(MinecraftServer server, String name, UUID verifiedUuid) {
        return submit(() -> { PlayerDataMigration.migrateOfflineToVerified(server, name, verifiedUuid); return null; });
    }

    public CompletableFuture<PlayerDataMigration.CleanupResult> cleanup(MinecraftServer server, String name) {
        return submit(() -> PlayerDataMigration.cleanupOfflineData(server, name));
    }

    public <T> CompletableFuture<T> io(java.util.concurrent.Callable<T> operation) {
        return submit(operation);
    }

    private <T> CompletableFuture<T> submit(java.util.concurrent.Callable<T> operation) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try { future.complete(operation.call()); }
                catch (Throwable ex) { future.completeExceptionally(ex); }
            });
        } catch (Throwable ex) {
            future.completeExceptionally(ex);
        }
        return future;
    }

    @Override public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException ex) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
