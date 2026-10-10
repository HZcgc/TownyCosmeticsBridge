package com.townyreborn.petshops;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.townyreborn.petshops.PurchaseLedger.*;
import com.townyreborn.petshops.PurchaseService.Result;

class PetPurchaseTest {
    @TempDir Path folder;
    private static final UUID PLAYER=UUID.fromString("11111111-2222-3333-4444-555555555555");
    private final List<Throwable> errors=new ArrayList<>();
    private PetCatalog.Pet pet() {
        return new PetCatalog.Pet("fox","pet.fox","Fox","fox","",true,50000,10.99,"https://shop.example.com/package/123?username={player}",2.5,1.8f,2,"idle");
    }
    private final class Bank implements PurchaseService.Gateway {
        double balance=100000; int withdrawals,grants; boolean owns,ready=true,allowed=true,failWithdrawal,throwAfterWithdrawal;
        CompletableFuture<Void> grantResult=CompletableFuture.completedFuture(null);
        public boolean owns(UUID p,String permission) { return owns; }
        public boolean ready(UUID p) { return ready; }
        public boolean allowed(UUID p,String permission) { return allowed; }
        public double balance(UUID p) { return balance; }
        public boolean withdraw(UUID p,double amount) {
            if(failWithdrawal)return false;
            withdrawals++; balance-=amount;
            if(throwAfterWithdrawal)throw new IllegalStateException("Response lost after payment");
            return true;
        }
        public CompletionStage<Void> grant(UUID p,String permission) {
            grants++; return grantResult.thenRun(()->owns=true);
        }
    }
    private PurchaseLedger ledger() throws Exception { return new PurchaseLedger(folder.resolve("transactions.yml")); }
    private PurchaseService service(PurchaseLedger ledger,Bank bank) { return new PurchaseService(ledger,bank,errors::add); }
    private static Result done(CompletionStage<Result> stage) { return stage.toCompletableFuture().join(); }

    @Test void confirmedPurchaseChargesOnceAndOwnershipSurvivesRestart() throws Exception {
        var bank=new Bank(); var service=service(ledger(),bank);
        assertEquals(Result.BOUGHT,done(service.buy(PLAYER,pet())));
        assertEquals(50000,bank.balance); assertEquals(1,bank.withdrawals);
        bank.owns=false; // ledger also prevents duplicate purchase if permissions temporarily lag.
        assertEquals(Result.ALREADY_OWNED,done(service(ledger(),bank).buy(PLAYER,pet())));
        assertEquals(1,bank.withdrawals);
        assertEquals(Phase.DONE,ledger().get(new Key(PLAYER,"fox")).phase());
    }
    @Test void inheritedOrPreviouslyGrantedPermissionPreventsCharge() throws Exception {
        var bank=new Bank(); bank.owns=true;
        assertEquals(Result.ALREADY_OWNED,done(service(ledger(),bank).buy(PLAYER,pet())));
        assertEquals(0,bank.withdrawals); assertTrue(ledger().entries().isEmpty());
    }
    @Test void insufficientBalanceOrRejectedWithdrawalDoesNotUnlock() throws Exception {
        var bank=new Bank(); var service=service(ledger(),bank); bank.balance=49999;
        assertEquals(Result.NO_MONEY,done(service.buy(PLAYER,pet())));
        bank.balance=100000; bank.failWithdrawal=true;
        assertEquals(Result.NO_MONEY,done(service.buy(PLAYER,pet())));
        assertEquals(0,bank.grants); assertTrue(ledger().entries().isEmpty());
    }
    @Test void missingEconomyOrExplicitDenyDoesNotCharge() throws Exception {
        var bank=new Bank(); var service=service(ledger(),bank); bank.ready=false;
        assertEquals(Result.UNAVAILABLE,done(service.buy(PLAYER,pet())));
        bank.ready=true; bank.allowed=false;
        assertEquals(Result.DENIED,done(service.buy(PLAYER,pet())));
        assertEquals(0,bank.withdrawals);
    }
    @Test void rapidSecondClickWhilePermissionSaveIsPendingCannotDoubleCharge() throws Exception {
        var bank=new Bank(); bank.grantResult=new CompletableFuture<>(); var service=service(ledger(),bank);
        var first=service.buy(PLAYER,pet());
        assertFalse(first.toCompletableFuture().isDone());
        assertEquals(Result.BUSY,done(service.buy(PLAYER,pet())));
        assertEquals(Result.BUSY,done(service.grant(PLAYER,pet(),"tbx-1")));
        assertEquals(1,bank.withdrawals); bank.grantResult.complete(null);
        assertEquals(Result.BOUGHT,done(first));
    }
    @Test void failedPermissionSaveRecoversAfterRestartWithoutAnotherWithdrawal() throws Exception {
        var bank=new Bank(); bank.grantResult=CompletableFuture.failedFuture(new IllegalStateException("Storage offline"));
        assertEquals(Result.PENDING,done(service(ledger(),bank).buy(PLAYER,pet())));
        var reloaded=ledger(); var entry=reloaded.get(new Key(PLAYER,"fox"));
        assertEquals(Phase.CHARGED,entry.phase());
        bank.grantResult=CompletableFuture.completedFuture(null);
        assertEquals(Result.GRANTED,done(service(reloaded,bank).recover(entry)));
        assertEquals(1,bank.withdrawals); assertEquals(Phase.DONE,ledger().get(entry.key()).phase());
    }
    @Test void uncertainWithdrawalRemainsBlockedAcrossRestartForBankVerification() throws Exception {
        var bank=new Bank(); bank.throwAfterWithdrawal=true;
        assertEquals(Result.PENDING,done(service(ledger(),bank).buy(PLAYER,pet())));
        var reloaded=ledger(); var entry=reloaded.get(new Key(PLAYER,"fox"));
        assertEquals(Phase.PREPARED,entry.phase());
        assertEquals(Result.PENDING,done(service(reloaded,bank).recover(entry)));
        assertEquals(Result.PENDING,done(service(reloaded,bank).buy(PLAYER,pet())));
        assertEquals(1,bank.withdrawals); assertEquals(0,bank.grants);
    }
    @Test void failedLedgerWriteStopsBeforeTakingAnyMoney() throws Exception {
        Path blocked=folder.resolve("blocked"); Files.writeString(blocked,"file, not directory");
        var bank=new Bank(); var ledger=new PurchaseLedger(blocked.resolve("transactions.yml"));
        assertEquals(Result.ERROR,done(service(ledger,bank).buy(PLAYER,pet())));
        assertEquals(0,bank.withdrawals); assertEquals(0,bank.grants);
    }
    @Test void offlineStoreGrantIsPermanentRetryableAndNeverUsesIngameMoney() throws Exception {
        var bank=new Bank(); bank.ready=false;
        assertEquals(Result.GRANTED,done(service(ledger(),bank).grant(PLAYER,pet(),"tbx-1")));
        assertEquals(Result.GRANTED,done(service(ledger(),bank).grant(PLAYER,pet(),"tbx-1")));
        assertEquals(0,bank.withdrawals); assertEquals(100000,bank.balance);
        assertEquals("tbx-1",ledger().get(new Key(PLAYER,"fox")).transaction());
        assertEquals(Result.ALREADY_OWNED,done(service(ledger(),bank).buy(PLAYER,pet())));
    }
    @Test void damagedTransactionFileFailsClosed() throws Exception {
        Files.writeString(folder.resolve("transactions.yml"),"version: 1\nplayers:\n  not-a-uuid:\n    fox:\n      permission: pet.fox\n      phase: CHARGED\n");
        assertThrows(java.io.IOException.class,this::ledger);
    }
    @Test void newMCPetsCanBeAddedWithIndividualPricesModelsAndStoreLinks() throws Exception {
        Path definitions=folder.resolve("MCPets/Pets/new-pack"); Files.createDirectories(definitions);
        Files.writeString(definitions.resolve("future.yml"),"Id: future_pet\nPermission: pet.future\nMythicMob: future_mob\nIcon:\n  Name: Future Pet\n");
        var defaults=new YamlConfiguration(); defaults.set("ingame-price",50000); defaults.set("store-price",10.99); defaults.set("store-url","https://shop.example.com");
        var catalog=new PetCatalog(folder.resolve("pets.yml"),folder.resolve("MCPets/Pets"),defaults);
        assertTrue(catalog.pets().isEmpty()); assertEquals(Set.of("future_pet"),catalog.availableIds());
        var initial=catalog.add("future_pet"); assertEquals("pet.future",initial.permission()); assertEquals(50000,initial.price());
        catalog.set("future_pet","ingame-price",75000.0); catalog.set("future_pet","store-price",12.99);
        catalog.set("future_pet","model","custom_display_model"); catalog.set("future_pet","store-url","https://shop.example.com/package/987");
        var reloaded=new PetCatalog(folder.resolve("pets.yml"),folder.resolve("MCPets/Pets"),defaults).get("future_pet");
        assertEquals(75000,reloaded.price()); assertEquals(12.99,reloaded.storePrice()); assertEquals("custom_display_model",reloaded.model());
        assertEquals("https://shop.example.com/package/987",reloaded.storeUrl());
        assertThrows(java.io.IOException.class,()->catalog.set("future_pet","ingame-price",Double.NaN));
        assertEquals(75000,new PetCatalog(folder.resolve("pets.yml"),folder.resolve("MCPets/Pets"),defaults).get("future_pet").price());
    }
    @Test void nativeItemsAdderIconComponentsAreReadWithoutDeserializingBukkitItems() {
        var d=PetCatalog.parseDefinition("""
            Id: nm_example
            Permission: pet.example
            Icon:
              Raw:
                ==: org.bukkit.inventory.ItemStack
                DataVersion: 4903
                components:
                  minecraft:item_model: '"nogs_menagerie:heads/nm_head_example"'
                  minecraft:custom_name: '{color:"white",text:"Example Pet"}'
            """);
        assertEquals("Example Pet",d.name()); assertEquals("nogs_menagerie:heads/nm_head_example",d.iconModel());
    }
    @Test void wildcardGrantsUnsafeLinksAndInvalidPricesAreRejected() {
        assertThrows(IllegalArgumentException.class,()->PetCatalog.parseDefinition("Id: fox\nPermission: '*'"));
        assertThrows(IllegalArgumentException.class,()->PetCatalog.parseDefinition("Id: fox\nPermission: ultracosmetics.pets.fox"));
        for(double price: new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY,10.999}) assertThrows(IllegalArgumentException.class,()->PetCatalog.price(price));
        assertThrows(IllegalArgumentException.class,()->PetCatalog.validUrl("javascript:alert(1)"));
        assertThrows(IllegalArgumentException.class,()->PetCatalog.validUrl("https://user:secret@shop.example.com"));
        assertEquals("https://shop.example.com/package/123?username=Test_Player",PetCatalog.urlFor(pet(),"Test_Player",PLAYER));
    }
    @Test void npcBindingsPersistIdentityAndRemovingOneKeepsOtherNpcs() throws Exception {
        Path file=folder.resolve("npcs.yml"); var bindings=new NpcBindings(file);
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        bindings.put(new NpcBindings.Binding(a,5,"fox",true)); bindings.put(new NpcBindings.Binding(b,7,"future_pet",false));
        bindings=new NpcBindings(file); assertEquals("fox",bindings.get(a).pet()); assertFalse(bindings.get(b).managed());
        bindings.remove(a); var reloaded=new NpcBindings(file);
        assertNull(reloaded.get(a)); assertEquals(b,reloaded.get(b).uuid()); assertEquals(7,reloaded.get(b).id());
    }
}
