package com.townyreborn.hunt;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LostHuntKeyRecoveryTest {
    @TempDir Path folder;
    static final UUID OWNER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    static final UUID OTHER = UUID.fromString("22222222-2222-3333-4444-555555555555");

    Connection database() throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + folder.resolve("data.db"));
        try (var s = connection.createStatement()) {
            s.execute("CREATE TABLE key_storage(owner_id TEXT,key_id TEXT,virtual_balance INTEGER,unclaimed_amount INTEGER,PRIMARY KEY(owner_id,key_id))");
            s.execute("CREATE TABLE reward_limits(player_id TEXT,crate_id TEXT,reward_id TEXT,uses INTEGER,PRIMARY KEY(player_id,crate_id,reward_id))");
            s.execute("INSERT INTO key_storage VALUES('"+OWNER+"','halloween2026',1,0)");
            s.execute("INSERT INTO key_storage VALUES('"+OWNER+"','vote',5,0)");
            for (int i = 0; i < 3; i++) s.execute("INSERT INTO reward_limits VALUES('"+OWNER+"','halloween2026','reward"+i+"',1)");
        }
        return connection;
    }

    int balance(Connection c, UUID owner) throws Exception {
        try (var query = c.prepareStatement("SELECT virtual_balance FROM key_storage WHERE owner_id=? AND key_id='halloween2026'")) {
            query.setString(1, owner.toString());
            try (var rows = query.executeQuery()) { assertTrue(rows.next()); return rows.getInt(1); }
        }
    }

    @Test void sixteenConfirmedFindsMinusThreeWinsRestoresThirteenExactlyOnce() throws Exception {
        try (var c = database()) {
            assertEquals(12, LostHuntKeyRecovery.migrate(c, Map.of(OWNER, 16)));
            assertEquals(13, balance(c, OWNER));
            try (var s = c.createStatement(); var rows = s.executeQuery("SELECT found,spent,balance_before,balance_after FROM townyreborn_key_recovery")) {
                assertTrue(rows.next()); assertEquals(16,rows.getInt(1)); assertEquals(3,rows.getInt(2));
                assertEquals(1,rows.getInt(3)); assertEquals(13,rows.getInt(4));
            }
            try (var s = c.createStatement()) { s.execute("UPDATE key_storage SET virtual_balance=0 WHERE key_id='halloween2026'"); }
            assertEquals(0, LostHuntKeyRecovery.migrate(c, Map.of(OWNER, 40)));
            assertEquals(0, balance(c, OWNER));
            try (var s = c.createStatement(); var rows = s.executeQuery("SELECT virtual_balance FROM key_storage WHERE key_id='vote'")) {
                assertTrue(rows.next()); assertEquals(5,rows.getInt(1));
            }
        }
    }

    @Test void extraBalancesAndUnclaimedKeysAreKeptAndPendingFindsAreNeverPaid() throws Exception {
        try (var c = database()) {
            try (var s = c.createStatement()) {
                s.execute("UPDATE key_storage SET virtual_balance=30 WHERE key_id='halloween2026'");
                s.execute("INSERT INTO key_storage VALUES('"+OTHER+"','halloween2026',1,2)");
            }
            assertEquals(7, LostHuntKeyRecovery.migrate(c, Map.of(OWNER, 16, OTHER, 10)));
            assertEquals(30,balance(c,OWNER)); assertEquals(8,balance(c,OTHER));
        }
        var ledger = new HuntLedger(folder.resolve("hunt"), "halloween2026");
        assertEquals(HuntLedger.Begin.RESERVED,ledger.reserve(OWNER,"Tester","paid",40,"halloween2026",0));
        ledger.finish(OWNER,"paid");
        ledger.reserve(OWNER,"Tester","pending",40,"halloween2026",1);
        ledger.reserve(OWNER,"Tester","otherkey",40,"vote",1); ledger.finish(OWNER,"otherkey");
        assertEquals(Map.of(OWNER,1),ledger.confirmedKeyPayments("halloween2026"));
    }

    @Test void malformedInputRollsBackBalancesAuditAndMarkerTogether() throws Exception {
        try (var c = database()) {
            Map<UUID,Integer> finds = new LinkedHashMap<>(); finds.put(OWNER,16); finds.put(OTHER,-1);
            assertThrows(SQLException.class,()->LostHuntKeyRecovery.migrate(c,finds));
            assertEquals(1,balance(c,OWNER));
            try (var s = c.createStatement(); var rows = s.executeQuery("SELECT COUNT(*) FROM sqlite_master WHERE name IN ('townyreborn_key_recovery','townyreborn_migrations')")) {
                assertTrue(rows.next()); assertEquals(0,rows.getInt(1));
            }
        }
    }
}
