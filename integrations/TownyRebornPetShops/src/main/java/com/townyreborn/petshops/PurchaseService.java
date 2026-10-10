package com.townyreborn.petshops;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import com.townyreborn.petshops.PurchaseLedger.*;

/** Called on the server thread; gateway grant completions also return there. */
final class PurchaseService {
    enum Result { BOUGHT, GRANTED, ALREADY_OWNED, BUSY, NO_MONEY, UNAVAILABLE, DENIED, PENDING, ERROR }
    interface Gateway {
        boolean owns(UUID player, String permission);
        boolean ready(UUID player);
        boolean allowed(UUID player, String permission);
        double balance(UUID player);
        boolean withdraw(UUID player, double price);
        CompletionStage<Void> grant(UUID player, String permission);
    }
    private final PurchaseLedger ledger;
    private final Gateway gateway;
    private final Consumer<Throwable> errors;
    private final Set<Key> busy=new HashSet<>();
    PurchaseService(PurchaseLedger ledger, Gateway gateway, Consumer<Throwable> errors) {
        this.ledger=ledger; this.gateway=gateway; this.errors=errors;
    }
    boolean owns(UUID player, PetCatalog.Pet pet) {
        Entry entry=ledger.get(new Key(player,pet.id()));
        return gateway.owns(player,pet.permission()) || entry!=null && entry.phase()==Phase.DONE;
    }
    CompletionStage<Result> buy(UUID player, PetCatalog.Pet pet) {
        Key key=new Key(player,pet.id());
        if (busy.contains(key)) return result(Result.BUSY);
        if (owns(player,pet)) return result(Result.ALREADY_OWNED);
        if (ledger.get(key)!=null) return result(Result.PENDING);
        if (!pet.enabled() || !gateway.ready(player)) return result(Result.UNAVAILABLE);
        if (!gateway.allowed(player,pet.permission())) return result(Result.DENIED);
        double amount=PetCatalog.price(pet.price()), balance=gateway.balance(player);
        if (!Double.isFinite(balance) || balance<amount) return result(Result.NO_MONEY);
        busy.add(key);
        Entry entry=new Entry(key,pet.permission(),amount,Phase.PREPARED,"ingame","");
        try { ledger.put(entry); }
        catch (IOException e) { errors.accept(e); busy.remove(key); return result(Result.ERROR); }
        try {
            if (!gateway.withdraw(player,amount)) {
                ledger.remove(key); busy.remove(key); return result(Result.NO_MONEY);
            }
        } catch (Exception e) {
            // An exception may have happened after withdrawal. Never charge again
            // or automatically refund an ambiguous payment.
            errors.accept(e); busy.remove(key); return result(Result.PENDING);
        }
        Entry charged=entry.phase(Phase.CHARGED);
        try { ledger.put(charged); }
        catch (IOException e) { errors.accept(e); /* Still try to deliver the paid pet. */ }
        return completeGrant(charged,Result.BOUGHT);
    }
    CompletionStage<Result> grant(UUID player, PetCatalog.Pet pet, String transaction) {
        Key key=new Key(player,pet.id());
        if (busy.contains(key)) return result(Result.BUSY);
        Entry existing=ledger.get(key);
        if (existing!=null && existing.phase()!=Phase.DONE) return result(Result.PENDING);
        busy.add(key);
        // Retry saves the same permanent permission without an economy withdrawal.
        Entry entry=existing!=null ? existing : new Entry(key,pet.permission(),0,Phase.CHARGED,"store",transaction);
        try { if (existing==null) ledger.put(entry); }
        catch (IOException e) { errors.accept(e); busy.remove(key); return result(Result.ERROR); }
        return completeGrant(entry,Result.GRANTED);
    }
    CompletionStage<Result> recover(Entry entry) {
        if (busy.contains(entry.key())) return result(Result.BUSY);
        if (entry.phase()==Phase.PREPARED) return result(Result.PENDING);
        busy.add(entry.key()); return completeGrant(entry,Result.GRANTED);
    }
    private CompletionStage<Result> completeGrant(Entry entry, Result success) {
        var output=new CompletableFuture<Result>();
        try {
            gateway.grant(entry.key().player(),entry.permission()).whenComplete((ignored,error)-> {
                try {
                    if (error!=null) { errors.accept(error); output.complete(Result.PENDING); }
                    else { ledger.put(entry.phase(Phase.DONE)); output.complete(success); }
                } catch (IOException e) { errors.accept(e); output.complete(Result.PENDING); }
                finally { busy.remove(entry.key()); }
            });
        } catch (Exception e) { errors.accept(e); busy.remove(entry.key()); output.complete(Result.PENDING); }
        return output;
    }
    private static CompletionStage<Result> result(Result r) { return CompletableFuture.completedFuture(r); }
}
