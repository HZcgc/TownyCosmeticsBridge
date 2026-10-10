package com.townyreborn.petshops;

import java.time.Duration;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.*;
import io.papermc.paper.registry.data.dialog.action.*;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;

/** Owner-only naming for summoned MCPets; shop pedestal models never enter this flow. */
final class PetNaming implements Listener {
    static final String PERMISSION="townyreborn.petshops.rename";
    record Session(Object pet,long created) {}
    private final JavaPlugin plugin;
    private final Integrations.MCPets pets;
    private final BiConsumer<Player,String> messages;
    private final Consumer<Throwable> errors;
    private final Map<UUID,Session> sessions=new HashMap<>();
    PetNaming(JavaPlugin plugin,Integrations.MCPets pets,BiConsumer<Player,String> messages,Consumer<Throwable> errors) {
        this.plugin=plugin; this.pets=pets; this.messages=messages; this.errors=errors;
    }
    void register() {
        Bukkit.getPluginManager().registerEvents(this,plugin);
        Bukkit.getPluginManager().registerEvent(pets.interaction,this,EventPriority.HIGHEST,(listener,event)-> {
            try {
                Object pet=pets.eventPet(event); Player player=Bukkit.getPlayer(pets.owner(pet));
                if (player!=null && open(player,pet) && event instanceof Cancellable c) c.setCancelled(true);
            } catch (ReflectiveOperationException e) { errors.accept(e); }
        },plugin,true);
    }
    boolean interact(Player player,Entity entity) {
        if (!player.isSneaking() || !enabled(player)) return false;
        try { Object pet=pets.find(entity); return pet!=null && open(player,pet); }
        catch (ReflectiveOperationException e) { errors.accept(e); return false; }
    }
    private boolean enabled(Player player) {
        return plugin.isEnabled() && player.isOnline() && player.hasPermission(PERMISSION)
            && plugin.getConfig().getBoolean("naming.enabled",true);
    }
    private boolean owned(Player player,Object pet) throws ReflectiveOperationException {
        return enabled(player) && pets.present(pet) && player.getUniqueId().equals(pets.owner(pet));
    }
    boolean open(Player player,Object pet) throws ReflectiveOperationException {
        if (!player.isSneaking() || !owned(player,pet)) return false;
        UUID playerId=player.getUniqueId(); Session current=sessions.get(playerId); long now=System.currentTimeMillis();
        if (current!=null && current.pet()==pet && now-current.created()<350) return true;
        Session session=new Session(pet,now); sessions.put(playerId,session);
        // Next tick also replaces menus opened by native handlers that ignore vanilla cancellation.
        Bukkit.getScheduler().runTask(plugin,()-> {
            if (!valid(player,session)) return;
            try { show(player,session); }
            catch (ReflectiveOperationException|RuntimeException e) { errors.accept(e); messages.accept(player,"&cThe pet name menu could not be opened."); }
        });
        return true;
    }
    private boolean valid(Player player,Session session) {
        if (sessions.get(player.getUniqueId())!=session || System.currentTimeMillis()-session.created()>120000) return false;
        try { return owned(player,session.pet()); }
        catch (ReflectiveOperationException e) { errors.accept(e); return false; }
    }
    void show(Player player,Session session) throws ReflectiveOperationException {
        int limit=Math.min(pets.maxNameLength(),Math.clamp(plugin.getConfig().getInt("naming.max-length",16),1,128));
        String current=Objects.toString(pets.currentName(session.pet()),"").replace('§','&');
        var options=ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(2)).build();
        var save=DialogAction.customClick((response,audience)-> {
            if (!plugin.isEnabled() || !(audience instanceof Player sender) || !sender.getUniqueId().equals(player.getUniqueId())) return;
            String name=response.getText("pet_name");
            Bukkit.getScheduler().runTask(plugin,()->save(sender,session,name));
        },options);
        var menu=DialogAction.customClick((response,audience)-> {
            if (!plugin.isEnabled() || !(audience instanceof Player sender) || !sender.getUniqueId().equals(player.getUniqueId())) return;
            Bukkit.getScheduler().runTask(plugin,()-> {
                openNativeMenu(sender,session);
            });
        },options);
        player.showDialog(Dialog.create(builder->builder.empty().base(DialogBase.builder(ShopMenu.color("&d&lTOWNY&a&lREBORN &8| &dPet name"))
            .canCloseWithEscape(true).body(List.of(
                DialogBody.plainMessage(ShopMenu.color("&7Name your pet with up to &a"+limit+" &7visible characters.")),
                DialogBody.plainMessage(Component.text("Paste RGBirdflop colors or gradients. Use &a, &#RRGGBB or <gradient:#ff55ff:#aaff55>Buddy</gradient>."))))
            .inputs(List.of(DialogInput.text("pet_name",Component.text("Pet name / RGBirdflop output"))
                .width(300).initial(current).maxLength(PetNameFormat.MAX_INPUT).build())).build())
            .type(DialogType.multiAction(List.of(ActionButton.builder(ShopMenu.color("&aSave name")).action(save).build(),
                ActionButton.builder(ShopMenu.color("&dMCPets menu")).action(menu).build()))
                .exitAction(ActionButton.builder(Component.text("Cancel")).build()).columns(2).build())));
    }
    void openNativeMenu(Player player,Session session) {
        if (!valid(player,session)) return;
        sessions.remove(player.getUniqueId());
        try { pets.openMenu(player,session.pet()); }
        catch (ReflectiveOperationException e) { errors.accept(e); messages.accept(player,"&cThe MCPets menu could not be opened."); }
    }
    void save(Player player,Session session,String input) {
        if (!valid(player,session)) { messages.accept(player,"&ePlease shift-right-click your summoned pet again."); return; }
        try {
            int limit=Math.min(pets.maxNameLength(),Math.clamp(plugin.getConfig().getInt("naming.max-length",16),1,128));
            var name=PetNameFormat.parse(input,limit);
            if (pets.blockedWord(name.legacy())!=null) throw new IllegalArgumentException("That name contains a blocked word. Please choose another name.");
            pets.rename(session.pet(),name.legacy()); sessions.remove(player.getUniqueId());
            player.sendMessage(ShopMenu.color(plugin.getConfig().getString("prefix","&d&lTOWNY&a&lREBORN &8» &r")+"&aPet name saved: ").append(name.display()));
        } catch (IllegalArgumentException e) {
            messages.accept(player,"&e"+e.getMessage());
            try { show(player,session); } catch (ReflectiveOperationException e2) { errors.accept(e2); }
        } catch (ReflectiveOperationException|RuntimeException e) { errors.accept(e); messages.accept(player,"&cThe pet name could not be saved."); }
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void click(PlayerInteractEntityEvent event) {
        if (event.getHand()==EquipmentSlot.HAND && interact(event.getPlayer(),event.getRightClicked())) event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void clickAt(PlayerInteractAtEntityEvent event) { click(event); }
    @EventHandler public void quit(PlayerQuitEvent event) { sessions.remove(event.getPlayer().getUniqueId()); }
    void clear() { sessions.clear(); }
}
