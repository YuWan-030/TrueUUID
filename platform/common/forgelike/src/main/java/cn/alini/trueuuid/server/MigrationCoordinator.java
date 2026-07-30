package cn.alini.trueuuid.server;

import cn.alini.trueuuid.protocol.MigrationTransaction;
import net.minecraft.server.MinecraftServer;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Minecraft server migration coordinator shared by modern Forge and NeoForge. */
public final class MigrationCoordinator implements AutoCloseable {
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(16), r -> {
        Thread thread = new Thread(r, "TrueUUID-MigrationWorker");
        thread.setDaemon(true);
        return thread;
    }, new ThreadPoolExecutor.AbortPolicy());

    public CompletableFuture<PlayerDataMigration.OfflineData> find(MinecraftServer server, String name) {
        return submit(() -> PlayerDataMigration.findOfflineData(server, name));
    }

    public CompletableFuture<Void> migrate(MinecraftServer server, String name, UUID verifiedUuid) {
        return submit(() -> {
            PlayerDataMigration.migrateOfflineToVerified(server, name, verifiedUuid);
            return null;
        });
    }

    public CompletableFuture<PlayerDataMigration.CleanupResult> cleanup(MinecraftServer server, String name) {
        return submit(() -> PlayerDataMigration.cleanupOfflineData(server, name));
    }

    public MigrationTransaction forServer(MinecraftServer server) {
        return new MigrationTransaction() {
            @Override public CompletableFuture<Optional<Offer>> find(String verifiedName) {
                return MigrationCoordinator.this.find(server, verifiedName)
                        .thenApply(data -> data == null ? Optional.empty()
                                : Optional.of(new Offer(data.offlineUuid(), data.summary())));
            }

            @Override public CompletableFuture<Void> migrate(String verifiedName, UUID verifiedUuid) {
                return MigrationCoordinator.this.migrate(server, verifiedName, verifiedUuid);
            }
        };
    }

    public <T> CompletableFuture<T> io(java.util.concurrent.Callable<T> operation) {
        return submit(operation);
    }

    private <T> CompletableFuture<T> submit(java.util.concurrent.Callable<T> operation) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try { future.complete(operation.call()); }
                catch (Throwable failure) { future.completeExceptionally(failure); }
            });
        } catch (Throwable failure) {
            future.completeExceptionally(failure);
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
