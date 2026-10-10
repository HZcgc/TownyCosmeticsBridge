package com.townyreborn.compat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.URLClassLoader;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import su.nightexpress.engine.cache.CacheStrategy;
import su.nightexpress.engine.component.DatabaseClient;
import su.nightexpress.engine.id.Identifier;
import su.nightexpress.excellentcrates.api.crate.Crate;
import su.nightexpress.excellentcrates.api.crate.component.CrateComponentKeys;
import su.nightexpress.excellentcrates.api.key.CrateKey;
import su.nightexpress.excellentcrates.api.key.crate.KeyRequirementComponent;
import su.nightexpress.excellentcrates.api.key.data.model.KeyBase;
import su.nightexpress.excellentcrates.api.key.registry.KeyResolver;
import su.nightexpress.excellentcrates.keys.balance.KeyBalanceService;
import su.nightexpress.excellentcrates.keys.cost.component.model.StandardKeyRequirementEntry;
import su.nightexpress.excellentcrates.keys.cost.evaluator.KeyCostLogicProvider;
import su.nightexpress.excellentcrates.keys.cost.evaluator.KeyCostOption;
import su.nightexpress.excellentcrates.keys.item.KeyItemService;
import su.nightexpress.excellentcrates.keys.storage.db.KeyStorageCachedDataService;
import su.nightexpress.excellentcrates.keys.storage.db.KeyStorageDBSchema;
import su.nightexpress.excellentcrates.keys.storage.db.KeyStorageSQLRepository;
import su.nightexpress.excellentcrates.keys.storage.model.StoredKey;
import su.nightexpress.nightcore.db.config.DatabaseType;
import su.nightexpress.nightcore.db.statement.condition.Wheres;
import su.nightexpress.nightcore.db.statement.template.InsertStatement;
import su.nightexpress.nightcore.db.statement.template.SelectStatement;
import su.nightexpress.nightcore.db.table.Table;

class KeyPersistenceTest {
    @TempDir Path folder;
    static final UUID OWNER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    static final Identifier HALLOWEEN = new Identifier("halloween2026");

    static final class OriginalSchemaLoader extends URLClassLoader {
        OriginalSchemaLoader() throws Exception {
            super(new java.net.URL[]{Path.of("build/deps/ExcellentCrates.jar").toUri().toURL()}, KeyPersistenceTest.class.getClassLoader());
        }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!KeyStorageDBSchema.class.getName().equals(name)) return super.loadClass(name, resolve);
            Class<?> type = findLoadedClass(name);
            if (type == null) type = findClass(name);
            if (resolve) resolveClass(type);
            return type;
        }
    }

    Connection connection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + folder.resolve("keys.db"));
    }

    void schema() throws Exception {
        try (var c = connection(); var s = c.createStatement()) {
            s.execute("CREATE TABLE key_storage(owner_id TEXT,key_id TEXT,virtual_balance INTEGER,unclaimed_amount INTEGER,PRIMARY KEY(owner_id,key_id))");
        }
    }

    void write(InsertStatement<StoredKey> insert, Collection<StoredKey> records) throws Exception {
        try (var c = connection(); var statement = c.prepareStatement(insert.toSql(DatabaseType.SQLITE, "key_storage", null))) {
            for (var data : records) { insert.prepare(statement, data, null); statement.addBatch(); }
            statement.executeBatch();
        }
    }

    List<StoredKey> read(SelectStatement<StoredKey> select, Wheres<Object> where) throws Exception {
        try (var c = connection(); var statement = c.prepareStatement(select.toSql("key_storage", where, null))) {
            for (int i = 0; i < where.size(); i++) statement.setObject(i + 1, where.getParameters().get(i).access(null));
            var result = new ArrayList<StoredKey>();
            try (var rows = statement.executeQuery()) { while (rows.next()) result.add(select.map(rows)); }
            return result;
        }
    }

    @SuppressWarnings("unchecked")
    KeyStorageSQLRepository repository() throws Exception {
        DatabaseClient client = mock(DatabaseClient.class);
        doAnswer(inv -> {
            InsertStatement<StoredKey> statement = inv.getArgument(1);
            Collection<StoredKey> records = inv.getArgument(2);
            write(statement, records);
            return null;
        }).when(client).<StoredKey>insert(any(Table.class),
                org.mockito.ArgumentMatchers.<InsertStatement<StoredKey>>any(),
                org.mockito.ArgumentMatchers.<Collection<StoredKey>>any());
        when(client.<StoredKey>selectWhere(any(Table.class),
                org.mockito.ArgumentMatchers.<SelectStatement<StoredKey>>any(), any(Wheres.class)))
                .thenAnswer(inv -> {
                    SelectStatement<StoredKey> statement = inv.getArgument(1);
                    Wheres<Object> where = inv.getArgument(2);
                    return read(statement, where);
                });
        return new KeyStorageSQLRepository(client, "key_storage");
    }

    KeyStorageCachedDataService cache(KeyStorageSQLRepository repository) {
        return new KeyStorageCachedDataService(repository, Duration.ofMinutes(15));
    }

    Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(OWNER);
        return player;
    }

    CrateKey key() {
        CrateKey key = mock(CrateKey.class);
        KeyBase base = mock(KeyBase.class);
        when(key.getId()).thenReturn(HALLOWEEN);
        when(key.getBase()).thenReturn(base);
        when(base.isVirtual()).thenReturn(true);
        return key;
    }

    @Test @SuppressWarnings("unchecked")
    void originalEc71CannotSaveAChangedExistingBalanceButThePatchCan() throws Exception {
        schema();
        String name = KeyStorageDBSchema.class.getName();
        try (var loader = new OriginalSchemaLoader()) {
            InsertStatement<StoredKey> original = (InsertStatement<StoredKey>)loader.loadClass(name).getField("STORED_KEY_INSERT").get(null);
            write(original, List.of(new StoredKey(OWNER, HALLOWEEN, 16, 2)));
            assertThrows(SQLException.class, () -> write(original, List.of(new StoredKey(OWNER, HALLOWEEN, 13, 0))));
        }
        var repository = repository();
        repository.upsert(List.of(new StoredKey(OWNER, HALLOWEEN, 13, 0))).join();
        var reloaded = cache(repository);
        reloaded.loadAndCacheAsync(OWNER, CacheStrategy.PERMANENT).join();
        assertEquals(13, reloaded.getCachedStrict(OWNER, HALLOWEEN).getVirtualBalance());
        assertEquals(0, reloaded.getCachedStrict(OWNER, HALLOWEEN).getUnclaimedAmount());
    }

    @Test void spentKeysSurviveFastRejoinAndColdRestartAndNativeCostUsesTheReducedBalance() throws Exception {
        schema();
        var repository = repository();
        repository.upsert(List.of(new StoredKey(OWNER, HALLOWEEN, 13, 0))).join();
        var cache = cache(repository);
        cache.loadAndCacheAsync(OWNER, CacheStrategy.PERMANENT).join();
        Player player = player(); CrateKey key = key();
        var balances = new KeyBalanceService(cache, mock(KeyItemService.class));
        KeyResolver resolver = mock(KeyResolver.class);
        when(resolver.resolveKey(HALLOWEEN)).thenReturn(key);
        Crate crate = mock(Crate.class);
        KeyRequirementComponent requirements = mock(KeyRequirementComponent.class);
        when(requirements.isEnabled()).thenReturn(true);
        when(requirements.getKeyEntry(HALLOWEEN)).thenReturn(new StandardKeyRequirementEntry(1));
        when(crate.getComponentOrNull(CrateComponentKeys.KEY_REQUIREMENT)).thenReturn(requirements);
        var costs = new KeyCostLogicProvider(resolver, balances);
        var option = new KeyCostOption(HALLOWEEN);
        assertTrue(costs.canAfford(player, crate, option, 3));
        // Start at the storage boundary after a three-key payment. The native
        // payment message requires Paper's live sound registry; storage does not.
        StoredKey spent = cache.getCachedStrict(OWNER, HALLOWEEN);
        spent.setVirtualBalance(10); cache.markDirty(spent);
        assertEquals(10, balances.countVirtualKeys(player, key));
        cache.updateCacheStrategy(OWNER, CacheStrategy.TEMPORARY); // quit
        cache.loadAndCacheAsync(OWNER, CacheStrategy.TEMPORARY).join(); // login before the five-second save
        cache.updateCacheStrategy(OWNER, CacheStrategy.PERMANENT); // join
        assertEquals(10, costs.getMaxAffordableOpens(player, crate, option));
        cache.saveDirtyData().join();
        var restarted = cache(repository);
        restarted.loadAndCacheAsync(OWNER, CacheStrategy.PERMANENT).join();
        assertEquals(10, restarted.getCachedStrict(OWNER, HALLOWEEN).getVirtualBalance());
        StoredKey exhausted = restarted.getCachedStrict(OWNER, HALLOWEEN);
        exhausted.setVirtualBalance(0); restarted.markDirty(exhausted);
        restarted.saveDirtyData().join();
        var empty = cache(repository);
        empty.loadAndCacheAsync(OWNER, CacheStrategy.PERMANENT).join();
        assertEquals(0, empty.getCachedStrict(OWNER, HALLOWEEN).getVirtualBalance());
        assertFalse(new KeyBalanceService(empty, mock(KeyItemService.class)).hasKey(player, key, 1));
    }

    @Test void fastRejoinPreservesNewKeysAndLoadsMissingKeysFromAPartialOfflineCache() throws Exception {
        schema();
        var vote = new Identifier("vote");
        var repository = repository();
        repository.upsert(List.of(new StoredKey(OWNER, HALLOWEEN, 1, 0), new StoredKey(OWNER, vote, 5, 0))).join();
        var cache = cache(repository);
        var payment = cache.getCachedOrCreate(OWNER, HALLOWEEN, CacheStrategy.TEMPORARY);
        payment.setVirtualBalance(16); cache.markDirty(payment);
        cache.loadAndCacheAsync(OWNER, CacheStrategy.TEMPORARY).join();
        assertSame(payment, cache.getCachedStrict(OWNER, HALLOWEEN));
        assertEquals(16, payment.getVirtualBalance());
        assertEquals(5, cache.getCachedStrict(OWNER, vote).getVirtualBalance());
        // A key created after login need not have reached the DB yet either.
        var daily = new Identifier("daily");
        cache.getCachedOrCreate(OWNER, daily, CacheStrategy.PERMANENT).setVirtualBalance(2);
        cache.loadAndCacheAsync(OWNER, CacheStrategy.TEMPORARY).join();
        assertEquals(2, cache.getCachedStrict(OWNER, daily).getVirtualBalance());
        cache.saveDirtyData().join();
        var restarted = cache(repository);
        restarted.loadAndCacheAsync(OWNER, CacheStrategy.PERMANENT).join();
        assertEquals(16, restarted.getCachedStrict(OWNER, HALLOWEEN).getVirtualBalance());
        assertEquals(2, restarted.getCachedStrict(OWNER, daily).getVirtualBalance());
    }
}
