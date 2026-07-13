package cn.alini.trueuuid.protocol;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Platform-supplied asynchronous transaction for one verified-login migration. */
public interface MigrationTransaction {
    record Offer(UUID offlineUuid, String summary) {}

    CompletableFuture<Optional<Offer>> find(String verifiedName);
    CompletableFuture<Void> migrate(String verifiedName, UUID verifiedUuid);
}
