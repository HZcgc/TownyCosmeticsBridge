package com.townyreborn.hunt;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/** Transactional, one-time EC6 SQLite import. Existing EC7 key balances take precedence. */
public final class LegacyCrateMigration {
    private static final String MIGRATION = "ec6-to-ec7-v1";
    private LegacyCrateMigration() {}

    public static void run(Plugin crates, Logger logger) throws IOException, ReflectiveOperationException {
        if (crates == null || !crates.isEnabled()) throw new IOException("ExcellentCrates is disabled");
        Path folder = crates.getDataFolder().toPath();
        var config = YamlConfiguration.loadConfiguration(folder.resolve("config/engine.database.yml").toFile());
        if (!"SQLITE".equalsIgnoreCase(config.getString("Type", "SQLITE"))) return;
        Path database = folder.resolve(config.getString("SQLite.FileName", "data.db"));
        if (!Files.isRegularFile(database)) return;
        Driver driver = (Driver) Class.forName("org.sqlite.JDBC", true, crates.getClass().getClassLoader())
                .getConstructor().newInstance();
        try (Connection connection = driver.connect("jdbc:sqlite:" + database.toAbsolutePath(), new Properties())) {
            try (var statement = connection.createStatement()) { statement.execute("PRAGMA busy_timeout=10000"); }
            if (!hasTable(connection, "excellentcrates_users") || completed(connection)) return;
            if (!hasTable(connection, "key_storage") || !hasTable(connection, "reward_limits"))
                throw new SQLException("ExcellentCrates 7 database tables are missing");
            Path backup = folder.resolve("data-before-ec7-" + System.currentTimeMillis() + ".db");
            try (var statement = connection.createStatement()) {
                statement.execute("VACUUM INTO '" + backup.toAbsolutePath().toString().replace("'", "''") + "'");
            }
            int imported = migrate(connection);
            logger.info("ExcellentCrates legacy migration completed: " + imported + " records. Backup: " + backup.getFileName());
        } catch (SQLException | RuntimeException exception) {
            throw new IOException("ExcellentCrates legacy migration failed; transaction rolled back", exception);
        }
    }

    private static boolean hasTable(Connection connection, String name) throws SQLException {
        try (var query = connection.prepareStatement("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")) {
            query.setString(1, name);
            try (var result = query.executeQuery()) { return result.next(); }
        }
    }

    private static boolean completed(Connection connection) throws SQLException {
        if (!hasTable(connection, "townyreborn_migrations")) return false;
        try (var query = connection.prepareStatement("SELECT 1 FROM townyreborn_migrations WHERE id=?")) {
            query.setString(1, MIGRATION);
            try (var result = query.executeQuery()) { return result.next(); }
        }
    }

    public static int migrate(Connection connection) throws SQLException {
        if (completed(connection)) return 0;
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        int imported = 0;
        try {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS townyreborn_migrations(id TEXT PRIMARY KEY, completed_at INTEGER NOT NULL)");
            }
            try (var query = connection.createStatement();
                 ResultSet rows = query.executeQuery("SELECT uuid,keys,keysOnHold,crateData FROM excellentcrates_users");
                 PreparedStatement key = connection.prepareStatement("INSERT OR IGNORE INTO key_storage(owner_id,key_id,virtual_balance,unclaimed_amount) VALUES(?,?,?,?)");
                 PreparedStatement cooldown = connection.prepareStatement("INSERT OR IGNORE INTO crate_cooldowns(player_id,crate_id,permanent,expiration_timestamp) VALUES(?,?,?,?)")) {
                while (rows.next()) {
                    String owner = UUID.fromString(rows.getString("uuid")).toString();
                    JsonObject balances = json(rows.getString("keys"));
                    JsonObject unclaimed = json(rows.getString("keysOnHold"));
                    var ids = new java.util.HashSet<>(balances.keySet());
                    ids.addAll(unclaimed.keySet());
                    for (String id : ids) {
                        int balance = balances.has(id) ? balances.get(id).getAsInt() : 0;
                        int pending = unclaimed.has(id) ? unclaimed.get(id).getAsInt() : 0;
                        if (balance < 0 || pending < 0) throw new SQLException("Negative legacy key balance");
                        key.setString(1, owner); key.setString(2, id);
                        key.setInt(3, balance); key.setInt(4, pending);
                        imported += key.executeUpdate();
                    }
                    for (var entry : json(rows.getString("crateData")).entrySet()) {
                        var data = entry.getValue().getAsJsonObject();
                        long expiration = data.has("openCooldown") ? data.get("openCooldown").getAsLong() : 0;
                        if (expiration == 0) continue;
                        cooldown.setString(1, owner); cooldown.setString(2, entry.getKey());
                        cooldown.setInt(3, expiration < 0 ? 1 : 0); cooldown.setLong(4, Math.max(0, expiration));
                        imported += cooldown.executeUpdate();
                    }
                }
            }
            if (hasTable(connection, "excellentcrates_reward_limits")) {
                try (var query = connection.createStatement();
                     var rows = query.executeQuery("SELECT holder,crateId,rewardId,amount,resetDate FROM excellentcrates_reward_limits");
                     var limit = connection.prepareStatement("INSERT INTO reward_limits(player_id,crate_id,reward_id,uses) VALUES(?,?,?,?) ON CONFLICT(player_id,crate_id,reward_id) DO UPDATE SET uses=MAX(uses,excluded.uses)")) {
                    while (rows.next()) {
                        long reset = rows.getLong("resetDate");
                        if (reset > 0 && reset < System.currentTimeMillis()) continue;
                        String holder = rows.getString("holder");
                        if ("global".equalsIgnoreCase(holder)) holder = new UUID(0, 0).toString();
                        holder = UUID.fromString(holder).toString();
                        int uses = rows.getInt("amount");
                        if (uses < 0) throw new SQLException("Negative legacy reward uses");
                        limit.setString(1, holder); limit.setString(2, rows.getString("crateId"));
                        limit.setString(3, rows.getString("rewardId")); limit.setInt(4, uses);
                        imported += limit.executeUpdate();
                    }
                }
            }
            try (var marker = connection.prepareStatement("INSERT INTO townyreborn_migrations(id,completed_at) VALUES(?,?)")) {
                marker.setString(1, MIGRATION); marker.setLong(2, System.currentTimeMillis()); marker.executeUpdate();
            }
            connection.commit();
            return imported;
        } catch (SQLException | RuntimeException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private static JsonObject json(String value) {
        return value == null || value.isBlank() ? new JsonObject() : JsonParser.parseString(value).getAsJsonObject();
    }
}
