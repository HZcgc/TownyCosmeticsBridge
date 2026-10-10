package su.nightexpress.excellentcrates.keys.storage.db;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import su.nightexpress.engine.cache.CacheStrategy;
import su.nightexpress.engine.id.Identifier;
import su.nightexpress.engine.sql.CachedDataService;
import su.nightexpress.excellentcrates.keys.storage.model.StoredKey;

/** Keep current cached balances when a fast rejoin races the periodic save. */
public class KeyStorageCachedDataService extends CachedDataService<UUID, Identifier, StoredKey> {
    public KeyStorageCachedDataService(KeyStorageSQLRepository repository, Duration cacheTTL) {
        super(repository, cacheTTL);
    }

    @Override
    public StoredKey createDefaultData(UUID owner, Identifier key) {
        return new StoredKey(owner, key);
    }

    @Override
    public CompletableFuture<List<StoredKey>> loadAndCacheAsync(UUID owner, CacheStrategy strategy) {
        return this.repository.loadAllByParent(owner).thenApply(loaded -> {
            // The cache can be partial after an offline grant. Load missing keys,
            // but never replace a current record with the last periodic DB save.
            for (StoredKey stored : loaded) {
                StoredKey current = getCached(owner, stored.getKeyId()).orElse(stored);
                if (strategy == CacheStrategy.PERMANENT) this.cache.putPermanent(current);
                else if (strategy == CacheStrategy.TEMPORARY) this.cache.putTemporary(current);
                else this.cache.remove(current);
            }
            updateCacheStrategy(owner, strategy);
            return strategy == CacheStrategy.NONE ? loaded : getCached(owner);
        });
    }
}
