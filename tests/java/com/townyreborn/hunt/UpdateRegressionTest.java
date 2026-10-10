package com.townyreborn.hunt;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UpdateRegressionTest {
    @TempDir Path folder;
    private static final UUID OWNER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private Connection database() throws Exception {
        Class.forName("org.sqlite.JDBC");
        Connection c = DriverManager.getConnection("jdbc:sqlite:" + folder.resolve("data.db"));
        try (var s = c.createStatement()) {
            s.execute("CREATE TABLE excellentcrates_users(uuid TEXT, keys TEXT, keysOnHold TEXT, crateData TEXT)");
            s.execute("CREATE TABLE excellentcrates_reward_limits(holder TEXT,crateId TEXT,rewardId TEXT,amount TEXT,resetDate TEXT)");
            s.execute("CREATE TABLE key_storage(owner_id TEXT,key_id TEXT,virtual_balance INTEGER,unclaimed_amount INTEGER,PRIMARY KEY(owner_id,key_id))");
            s.execute("CREATE TABLE reward_limits(player_id TEXT,crate_id TEXT,reward_id TEXT,uses INTEGER,PRIMARY KEY(player_id,crate_id,reward_id))");
            s.execute("CREATE TABLE crate_cooldowns(player_id TEXT,crate_id TEXT,permanent INTEGER,expiration_timestamp INTEGER,PRIMARY KEY(player_id,crate_id))");
            s.execute("INSERT INTO excellentcrates_users VALUES('" + OWNER + "','{\"halloween2026\":40,\"vote\":8}','{\"vote\":2}','{\"daily\":{\"openCooldown\":12345}}')");
            s.execute("INSERT INTO excellentcrates_reward_limits VALUES('" + OWNER + "','halloween2026','pumpkin_helmet','1','0')");
        }
        return c;
    }

    private int value(Connection c, String table, String column, String where) throws Exception {
        try (var s=c.createStatement(); var r=s.executeQuery("SELECT " + column + " FROM " + table + " WHERE " + where)) {
            assertTrue(r.next()); return r.getInt(1);
        }
    }

    @Test void migrationPreservesBalancesUniqueRewardsAndDoesNotRegrantAfterRestart() throws Exception {
        try (Connection c=database()) {
            assertTrue(LegacyCrateMigration.migrate(c)>0);
            assertEquals(40,value(c,"key_storage","virtual_balance","key_id='halloween2026'"));
            assertEquals(2,value(c,"key_storage","unclaimed_amount","key_id='vote'"));
            assertEquals(1,value(c,"reward_limits","uses","reward_id='pumpkin_helmet'"));
            assertEquals(12345,value(c,"crate_cooldowns","expiration_timestamp","crate_id='daily'"));
            try(var s=c.createStatement()) { s.execute("DELETE FROM key_storage WHERE key_id='halloween2026'"); }
            assertEquals(0,LegacyCrateMigration.migrate(c));
            assertEquals(1,value(c,"key_storage","COUNT(*)","1=1"));
        }
    }

    @Test void alreadyUsedEc7BalanceTakesPrecedenceAndRewardUsesNeverDecrease() throws Exception {
        try (Connection c=database()) {
            try(var s=c.createStatement()) {
                s.execute("INSERT INTO key_storage VALUES('"+OWNER+"','halloween2026',3,0)");
                s.execute("INSERT INTO reward_limits VALUES('"+OWNER+"','halloween2026','pumpkin_helmet',5)");
            }
            LegacyCrateMigration.migrate(c);
            assertEquals(3,value(c,"key_storage","virtual_balance","key_id='halloween2026'"));
            assertEquals(5,value(c,"reward_limits","uses","reward_id='pumpkin_helmet'"));
        }
    }

    @Test void malformedSourceRollsBackEntireMigration() throws Exception {
        try (Connection c=database()) {
            try(var s=c.createStatement()) { s.execute("INSERT INTO excellentcrates_users VALUES('broken','{}','{}','{}')"); }
            assertThrows(IllegalArgumentException.class,()->LegacyCrateMigration.migrate(c));
            assertEquals(0,value(c,"key_storage","COUNT(*)","1=1"));
            try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM sqlite_master WHERE name='townyreborn_migrations'")) {
                assertTrue(r.next()); assertEquals(0,r.getInt(1));
            }
        }
    }

    @Test void huntGivesOneKeyPerGhostPersistsAndStopsAtForty() throws Exception {
        var ledger=new HuntLedger(folder,"halloween2026");
        var claims=new ClaimService(ledger);
        var keys=new AtomicInteger();
        ClaimService.Gateway gateway=()->new ClaimService.Payment() {
            public int balanceBefore(){return keys.get();}
            public void giveOne(){keys.incrementAndGet();}
            public boolean definitelyNotGiven(){return false;}
        };
        for(int i=0;i<40;i++) assertEquals(ClaimService.Kind.FOUND,claims.claim(OWNER,"Tester","ghost"+i,40,"halloween2026",gateway).kind());
        assertEquals(40,keys.get());
        claims=new ClaimService(new HuntLedger(folder,"halloween2026"));
        assertEquals(ClaimService.Kind.ALREADY_FOUND,claims.claim(OWNER,"Tester","ghost0",40,"halloween2026",gateway).kind());
        assertEquals(ClaimService.Kind.LIMIT,claims.claim(OWNER,"Tester","ghost40",40,"halloween2026",gateway).kind());
        assertEquals(40,keys.get());
    }

    @Test void ambiguousGrantRemainsPendingAndCannotBePaidTwice() throws Exception {
        var ledger=new HuntLedger(folder,"halloween2026");
        var claims=new ClaimService(ledger);
        var keys=new AtomicInteger();
        ClaimService.Gateway gateway=()->new ClaimService.Payment() {
            public int balanceBefore(){return keys.get();}
            public void giveOne(){keys.incrementAndGet();throw new IllegalStateException("uncertain response");}
            public boolean definitelyNotGiven(){return false;}
        };
        assertEquals(ClaimService.Kind.PENDING,claims.claim(OWNER,"Tester","ghost0",40,"halloween2026",gateway).kind());
        assertEquals(ClaimService.Kind.PENDING,new ClaimService(new HuntLedger(folder,"halloween2026")).claim(OWNER,"Tester","ghost0",40,"halloween2026",gateway).kind());
        assertEquals(1,keys.get());
    }
}
