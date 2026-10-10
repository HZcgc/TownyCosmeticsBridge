package com.townyreborn.hunt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/** One-time repair of confirmed Halloween payments lost by EC7.1.0's INSERT-only save. */
final class LostHuntKeyRecovery {
    private static final String EVENT = "halloween2026";
    private static final String MIGRATION = "ec7-key-balance-recovery-halloween2026-v1";
    private LostHuntKeyRecovery() {}

    static void run(Plugin crates, HuntLedger ledger, String event, String keyId, Logger logger)
            throws IOException, ReflectiveOperationException {
        if (!EVENT.equals(event) || !EVENT.equals(keyId)) return;
        String version = crates.getDescription().getVersion();
        if (!"7.1.0-townykeys.1".equals(version) && !"7.1.0-townykeys.2".equals(version)) {
            logger.warning("Halloween key recovery skipped: ExcellentCrates " + version
                    + " does not report a supported key-storage patch. Install ExcellentCrates-7.1.0-townykeys.2.jar and fully restart.");
            return;
        }
        if (!crates.getServer().getOnlinePlayers().isEmpty()) {
            logger.warning("Halloween key recovery requires a full restart; online balances were not changed.");
            return;
        }
        Path folder = crates.getDataFolder().toPath();
        var config = YamlConfiguration.loadConfiguration(folder.resolve("config/engine.database.yml").toFile());
        if (!"SQLITE".equalsIgnoreCase(config.getString("Type", "SQLITE"))) return;
        var crate = YamlConfiguration.loadConfiguration(folder.resolve("objects/crate/" + EVENT + ".yml").toFile());
        var keys = crate.getConfigurationSection("key_requirements.keys");
        if (!crate.getBoolean("key_requirements.enabled") || keys == null || keys.getKeys(false).size() != 1
                || crate.getInt("key_requirements.keys." + keyId + ".amount") != 1
                || crate.getInt("rewards.required_rewards", 1) != 1) {
            logger.warning("Halloween key recovery skipped: the crate is not one key for one reward.");
            return;
        }
        Path database = folder.resolve(config.getString("SQLite.FileName", "data.db"));
        if (!Files.isRegularFile(database)) return;
        Driver driver = (Driver) Class.forName("org.sqlite.JDBC", true, crates.getClass().getClassLoader())
                .getConstructor().newInstance();
        try (Connection connection = driver.connect("jdbc:sqlite:" + database.toAbsolutePath(), new Properties())) {
            try (var statement = connection.createStatement()) { statement.execute("PRAGMA busy_timeout=10000"); }
            if (completed(connection)) return;
            Map<UUID, Integer> payments = ledger.confirmedKeyPayments(keyId);
            if (payments.isEmpty()) return;
            Path backup = folder.resolve("data-before-key-recovery-" + System.currentTimeMillis() + ".db");
            try (var statement = connection.createStatement()) {
                statement.execute("VACUUM INTO '" + backup.toAbsolutePath().toString().replace("'", "''") + "'");
            }
            int restored = migrate(connection, payments);
            logger.info("Halloween key recovery restored " + restored + " confirmed unspent keys once. "
                    + "Existing higher balances were kept. Audit: townyreborn_key_recovery. Backup: " + backup.getFileName());
        } catch (SQLException | RuntimeException failure) {
            throw new IOException("Halloween key recovery failed; transaction rolled back", failure);
        }
    }

    private static boolean completed(Connection connection) throws SQLException {
        try (var query = connection.prepareStatement("SELECT 1 FROM sqlite_master WHERE type='table' AND name='townyreborn_migrations'");
             var rows = query.executeQuery()) {
            if (!rows.next()) return false;
        }
        try (var query = connection.prepareStatement("SELECT 1 FROM townyreborn_migrations WHERE id=?")) {
            query.setString(1, MIGRATION);
            try (var rows = query.executeQuery()) { return rows.next(); }
        }
    }

    static int migrate(Connection connection, Map<UUID, Integer> confirmedPayments) throws SQLException {
        if (completed(connection) || confirmedPayments.isEmpty()) return 0;
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        int restored = 0;
        try {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS townyreborn_migrations(id TEXT PRIMARY KEY, completed_at INTEGER NOT NULL)");
                statement.execute("CREATE TABLE IF NOT EXISTS townyreborn_key_recovery("
                        + "owner_id TEXT NOT NULL,key_id TEXT NOT NULL,found INTEGER NOT NULL,spent INTEGER NOT NULL,"
                        + "balance_before INTEGER NOT NULL,balance_after INTEGER NOT NULL,recovered_at INTEGER NOT NULL,"
                        + "PRIMARY KEY(owner_id,key_id))");
            }
            try (var balance = connection.prepareStatement("SELECT virtual_balance,unclaimed_amount FROM key_storage WHERE owner_id=? AND key_id=?");
                 var spent = connection.prepareStatement("SELECT COALESCE(SUM(uses),0) FROM reward_limits WHERE player_id=? AND crate_id=?");
                 var save = connection.prepareStatement("INSERT INTO key_storage(owner_id,key_id,virtual_balance,unclaimed_amount) VALUES(?,?,?,?) "
                         + "ON CONFLICT(owner_id,key_id) DO UPDATE SET virtual_balance=excluded.virtual_balance");
                 var audit = connection.prepareStatement("INSERT INTO townyreborn_key_recovery VALUES(?,?,?,?,?,?,?)")) {
                for (var payment : confirmedPayments.entrySet()) {
                    String owner = payment.getKey().toString();
                    int found = payment.getValue();
                    if (found < 0 || found > 1000) throw new SQLException("Invalid confirmed find count");
                    balance.setString(1, owner); balance.setString(2, EVENT);
                    int before = 0, unclaimed = 0;
                    try (var rows = balance.executeQuery()) {
                        if (rows.next()) { before = rows.getInt(1); unclaimed = rows.getInt(2); }
                    }
                    spent.setString(1, owner); spent.setString(2, EVENT);
                    long used;
                    try (var rows = spent.executeQuery()) { rows.next(); used = rows.getLong(1); }
                    if (before < 0 || unclaimed < 0 || used < 0 || used > Integer.MAX_VALUE)
                        throw new SQLException("Invalid key balance or reward usage");
                    // Keep legitimate extra keys and count unclaimed keys as already delivered.
                    int after = (int) Math.max(before, (long) found - used - unclaimed);
                    if (after == before) continue;
                    save.setString(1, owner); save.setString(2, EVENT);
                    save.setInt(3, after); save.setInt(4, unclaimed); save.executeUpdate();
                    audit.setString(1, owner); audit.setString(2, EVENT); audit.setInt(3, found);
                    audit.setInt(4, (int) used); audit.setInt(5, before); audit.setInt(6, after);
                    audit.setLong(7, System.currentTimeMillis()); audit.executeUpdate();
                    restored += after - before;
                }
            }
            try (var marker = connection.prepareStatement("INSERT INTO townyreborn_migrations VALUES(?,?)")) {
                marker.setString(1, MIGRATION); marker.setLong(2, System.currentTimeMillis()); marker.executeUpdate();
            }
            connection.commit();
            return restored;
        } catch (SQLException | RuntimeException failure) {
            connection.rollback();
            throw failure;
        } finally { connection.setAutoCommit(autoCommit); }
    }
}
