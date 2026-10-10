package su.nightexpress.excellentcrates.keys.storage.db;

import java.util.UUID;
import su.nightexpress.engine.id.Identifier;
import su.nightexpress.engine.id.IdentifierParser;
import su.nightexpress.excellentcrates.keys.storage.model.StoredKey;
import su.nightexpress.nightcore.db.column.Column;
import su.nightexpress.nightcore.db.statement.RowMapper;
import su.nightexpress.nightcore.db.statement.template.InsertStatement;
import su.nightexpress.nightcore.db.statement.template.SelectStatement;

/** EC 7.1.0 schema, with updates on the existing owner/key primary key. */
public final class KeyStorageDBSchema {
    public static final Column<UUID> OWNER_ID = Column.uuidType("owner_id").primaryKey().build();
    public static final Column<String> KEY_ID = Column.stringType("key_id", 128).primaryKey().build();
    public static final Column<Integer> VIRTUAL_BALANCE = Column.intType("virtual_balance").defaultValue(0).build();
    public static final Column<Integer> UNCLAIMED_AMOUNT = Column.intType("unclaimed_amount").defaultValue(0).build();

    public static final RowMapper<StoredKey> STORED_KEY_MAPPER = rows -> {
        UUID owner = OWNER_ID.readOrThrow(rows);
        String rawId = KEY_ID.readOrThrow(rows);
        Identifier id = IdentifierParser.parse(rawId)
                .orElseThrow(() -> new IllegalStateException("Corrupted key ID: '" + rawId + "'"));
        return new StoredKey(owner, id, VIRTUAL_BALANCE.readOrThrow(rows), UNCLAIMED_AMOUNT.readOrThrow(rows));
    };

    public static final InsertStatement<StoredKey> STORED_KEY_INSERT = InsertStatement.builder(StoredKey.class)
            .setUUID(OWNER_ID, StoredKey::getOwnerId)
            .setString(KEY_ID, data -> data.getKeyId().value())
            .setInt(VIRTUAL_BALANCE, StoredKey::getVirtualBalance)
            .setInt(UNCLAIMED_AMOUNT, StoredKey::getUnclaimedAmount)
            .updateOnConflict()
            .build();

    public static final SelectStatement<StoredKey> STORED_KEY_SELECT = SelectStatement.builder(STORED_KEY_MAPPER).build();

    private KeyStorageDBSchema() {}
}
