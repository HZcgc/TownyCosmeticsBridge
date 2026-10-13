package net.townyreborn.journey;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Durable, at-most-once side effects. Ambiguous delivery always needs review. */
public final class GiftClaims {
    private final Journey journey;
    private final Store store;
    private final Backend backend;
    private final Set<UUID> processing = new HashSet<>();

    public GiftClaims(Journey journey, Store store, Backend backend) { this.journey=journey; this.store=store; this.backend=backend; }
    public boolean processing(UUID id) { return processing.contains(id); }

    public String claim(State state, Journey.Gift gift, String town, Consumer<String> callback) throws Exception {
        if (state.blocked || state.busy()) return "A previous transaction needs to finish first.";
        if (gift == null || journey.level(state.points) < gift.level() || state.claimed.contains(gift.id()))
            return "This reward is claimed or still locked.";
        if (gift.townRequired() && town.isBlank()) return "Join a town first; you can claim this reward later.";
        if (!state.name.matches("[A-Za-z0-9_.-]{1,32}")) return "Your player name needs a staff check before claiming this reward.";
        List<String> commands = gift.commands().stream().map(c -> c.replace("%player%", state.name).replace("%uuid%", state.uuid.toString())).toList();
        Receipt receipt = new Receipt(gift.id(), town, commands, gift.townBlocks(), gift.money());
        restore(state, receipt);
        try { store.save(state); } catch (Exception failure) { clear(state); state.blocked=true; throw failure; }
        List<String> nodes = new ArrayList<>(gift.permissions()); nodes.add(Journey.giftMarker(gift.id()));
        processing.add(state.uuid);
        try {
            backend.grant(state.uuid, nodes).whenComplete((ignored, failure) -> backend.main(() -> {
                String result = "This reward needs a staff check. It will not be claimed twice.";
                try {
                    if (!state.pendingGift.equals(gift.id())) return;
                    if (failure == null) {
                        try {
                            // The original amount is persisted before making the only deposit attempt.
                            if (receipt.money > 0 && !backend.deposit(state.uuid, receipt.money))
                                throw new IllegalStateException("Economy did not confirm the reward");
                            for (String command : receipt.commands)
                                if (!backend.dispatch(command)) throw new IllegalStateException("Reward command did not confirm completion");
                            if (receipt.blocks > 0) backend.townBonus(receipt.town, receipt.blocks);
                            state.claimed.add(receipt.id); clear(state); store.save(state);
                            result = "Claimed " + gift.name() + "!";
                        } catch (Exception deliveryFailure) {
                            // A deposit or command could already have succeeded. Never replay it.
                            state.claimed.remove(receipt.id); restore(state, receipt); state.dirty=true;
                        }
                    }
                } finally { processing.remove(state.uuid); }
                // UI failure cannot reopen a successfully delivered receipt.
                callback.accept(result);
            }));
            return "Claiming your free reward...";
        } catch (RuntimeException failure) {
            processing.remove(state.uuid);
            return "This reward needs a staff check. It will not be claimed twice.";
        }
    }

    public void delivered(State state) throws Exception {
        if (processing(state.uuid)) throw new IllegalArgumentException("Reward save is still running; try again after it finishes");
        if (state.pendingGift.isEmpty()) throw new IllegalArgumentException("No pending reward");
        Receipt receipt = new Receipt(state.pendingGift, state.pendingGiftTown, state.pendingGiftCommands, state.pendingGiftTownBlocks, state.pendingGiftMoney);
        state.claimed.add(receipt.id); clear(state);
        try { store.save(state); }
        catch (Exception failure) { state.claimed.remove(receipt.id); restore(state, receipt); state.blocked=true; throw failure; }
    }

    private static void restore(State state, Receipt receipt) {
        state.pendingGift=receipt.id; state.pendingGiftTown=receipt.town; state.pendingGiftCommands=receipt.commands;
        state.pendingGiftTownBlocks=receipt.blocks; state.pendingGiftMoney=receipt.money;
    }
    private static void clear(State state) {
        state.pendingGift=""; state.pendingGiftTown=""; state.pendingGiftCommands=List.of(); state.pendingGiftTownBlocks=0; state.pendingGiftMoney=0;
    }
    private record Receipt(String id, String town, List<String> commands, int blocks, double money) {}
    public interface Backend {
        CompletableFuture<Void> grant(UUID id, List<String> nodes);
        boolean dispatch(String command);
        default boolean deposit(UUID id, double amount) { throw new UnsupportedOperationException("Money rewards are not supported by this backend"); }
        void townBonus(String name, int blocks) throws Exception;
        void main(Runnable task);
    }
}
