package com.townyreborn.petshops;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.node.types.PermissionNode;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import com.townyreborn.petshops.NpcBindings.Binding;
import com.townyreborn.petshops.PurchaseLedger.*;

public final class TownyRebornPetShops extends JavaPlugin implements Listener {
    private static final String ADMIN="townyreborn.petshops.admin", USE="townyreborn.petshops.use";
    private PetCatalog catalog;
    private PurchaseLedger ledger;
    private PurchaseService purchases;
    private NpcBindings bindings;
    private LuckPerms permissions;
    private Integrations.Citizens citizens;
    private Integrations.Models models;
    private NamespacedKey marker;
    private final Map<UUID,RuntimeNpc> active=new HashMap<>();
    private final Map<UUID,UUID> entities=new ConcurrentHashMap<>();
    private final Map<UUID,Long> lastClick=new HashMap<>(), failures=new HashMap<>();
    private record RuntimeNpc(Entity base, TextDisplay hologram, Interaction hitbox, Integrations.Models.Handle model) {}

    @Override public void onEnable() {
        try {
            saveDefaultConfig(); if (!getDataFolder().toPath().resolve("pets.yml").toFile().exists()) saveResource("pets.yml",false);
            marker=new NamespacedKey(this,"shop_npc");
            permissions=Objects.requireNonNull(Bukkit.getServicesManager().load(LuckPerms.class),"LuckPerms API unavailable");
            citizens=new Integrations.Citizens(Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("Citizens")));
            Plugin me=Bukkit.getPluginManager().getPlugin("ModelEngine");
            if (me!=null && me.isEnabled()) models=new Integrations.Models(me);
            catalog=loadCatalog();
            bindings=new NpcBindings(getDataFolder().toPath().resolve("npcs.yml"));
            ledger=new PurchaseLedger(getDataFolder().toPath().resolve("transactions.yml"));
            purchases=new PurchaseService(ledger,new Gateway(),this::error);
            Bukkit.getPluginManager().registerEvents(this,this);
            registerCitizensEvents();
            if (models!=null) Bukkit.getPluginManager().registerEvent(models.interaction,this,EventPriority.HIGHEST,(listener,event)-> {
                try {
                    UUID baseId=models.baseId(event);
                    UUID npc=entities.get(baseId);
                    if (npc!=null && models.rightClick(event)) {
                        if (event instanceof Cancellable c) c.setCancelled(true);
                        Player player=models.player(event);
                        runMain(()->click(player,npc));
                    }
                } catch (ReflectiveOperationException e) { error(e); }
            },this,false);
            Bukkit.getScheduler().runTask(this,()-> {
                for (World world:Bukkit.getWorlds()) for (Chunk chunk:world.getLoadedChunks()) removeOrphans(chunk.getEntities());
                reconcile(); recoverCharged(null);
                long unresolved=ledger.entries().stream().filter(e->e.phase()==Phase.PREPARED).count();
                if (unresolved>0) getLogger().warning(unresolved+" ambiguous purchases require bank verification: /trpetshop pending");
            });
            Bukkit.getScheduler().runTaskTimer(this,this::reconcile,40,60);
            getLogger().info("Ready: "+catalog.pets().size()+" shop pets, "+catalog.availableIds().size()+" MCPets definitions, "+bindings.all().size()+" NPCs. UltraCosmetics is not used.");
        } catch (Exception|LinkageError e) { error(e); Bukkit.getPluginManager().disablePlugin(this); }
    }
    @Override public void onDisable() {
        for (UUID id:List.copyOf(active.keySet())) cleanup(id);
        for (Player player:Bukkit.getOnlinePlayers()) if (player.getOpenInventory().getTopInventory().getHolder() instanceof ShopMenu) player.closeInventory();
        lastClick.clear(); failures.clear();
    }
    private PetCatalog loadCatalog() throws IOException {
        var defaults=Objects.requireNonNull(getConfig().getConfigurationSection("defaults"),"Missing defaults section");
        return new PetCatalog(getDataFolder().toPath().resolve("pets.yml"),getDataFolder().toPath().getParent().resolve("MCPets/Pets"),defaults);
    }
    private void registerCitizensEvents() throws ReflectiveOperationException {
        Bukkit.getPluginManager().registerEvent(citizens.event("NPCRightClickEvent"),this,EventPriority.HIGHEST,(listener,event)-> {
            try {
                UUID id=citizens.uuid(citizens.eventNpc(event));
                if (bindings.get(id)!=null) {
                    if (event instanceof Cancellable c) c.setCancelled(true);
                    click(citizens.clicker(event),id);
                }
            } catch (ReflectiveOperationException e) { error(e); }
        },this,false);
        Bukkit.getPluginManager().registerEvent(citizens.event("NPCSpawnEvent"),this,EventPriority.MONITOR,
                (listener,event)->Bukkit.getScheduler().runTask(this,this::reconcile),this,true);
        Bukkit.getPluginManager().registerEvent(citizens.event("NPCDespawnEvent"),this,EventPriority.MONITOR,(listener,event)-> {
            try { cleanup(citizens.uuid(citizens.eventNpc(event))); } catch (ReflectiveOperationException e) { error(e); }
        },this,true);
        Bukkit.getPluginManager().registerEvent(citizens.event("NPCRemoveEvent"),this,EventPriority.MONITOR,(listener,event)-> {
            try {
                UUID id=citizens.uuid(citizens.eventNpc(event)); cleanup(id);
                if (bindings.get(id)!=null) bindings.remove(id);
            } catch (Exception e) { error(e); }
        },this,false);
    }
    private void reconcile() {
        for (Binding binding:bindings.all()) {
            try {
                Object npc=citizens.get(binding.id(),binding.uuid());
                PetCatalog.Pet pet=catalog.get(binding.pet());
                if (npc==null || pet==null || !pet.enabled() || !citizens.spawned(npc)) { cleanup(binding.uuid()); continue; }
                Entity base=citizens.entity(npc);
                if (base==null || !base.isValid()) { cleanup(binding.uuid()); continue; }
                RuntimeNpc runtime=active.get(binding.uuid());
                if (runtime!=null && (!runtime.base().getUniqueId().equals(base.getUniqueId()) || !runtime.hologram().isValid() || !runtime.hitbox().isValid())) {
                    cleanup(binding.uuid()); runtime=null;
                }
                if (runtime==null) { attach(binding,base,pet); runtime=active.get(binding.uuid()); }
                Location location=base.getLocation();
                runtime.hologram().teleport(location.clone().add(0,pet.hologramHeight(),0));
                runtime.hitbox().teleport(location);
                runtime.hologram().text(hologramText(pet));
            } catch (Exception e) {
                long now=System.currentTimeMillis();
                if (now-failures.getOrDefault(binding.uuid(),0L)>60000) { failures.put(binding.uuid(),now); error(e); }
                cleanup(binding.uuid());
            }
        }
    }
    private void attach(Binding binding, Entity base, PetCatalog.Pet pet) throws ReflectiveOperationException {
        Integrations.Models.Handle handle=null; TextDisplay holo=null; Interaction hitbox=null;
        try {
            if (binding.managed()) {
                if (models==null || !models.has(pet.model())) throw new IllegalStateException("ModelEngine model unavailable: "+pet.model());
                base.setGravity(false); base.setInvulnerable(true); base.setSilent(true);
                if (base instanceof LivingEntity living) { living.setAI(false); living.setCollidable(false); }
                handle=models.attach(base,pet.model(),pet.idle());
            }
            Location location=base.getLocation(); World world=Objects.requireNonNull(location.getWorld());
            holo=world.spawn(location.clone().add(0,pet.hologramHeight(),0),TextDisplay.class,d-> {
                d.setPersistent(false); d.setGravity(false); d.setInvulnerable(true);
                d.setBillboard(Display.Billboard.CENTER); d.setAlignment(TextDisplay.TextAlignment.CENTER);
                d.setDefaultBackground(false); d.setBackgroundColor(Color.fromARGB(0,0,0,0));
                d.setShadowed(false); d.setSeeThrough(false); d.setLineWidth(500);
                d.setViewRange((float)Math.clamp(getConfig().getDouble("hologram.view-range",2.5),0.5,10));
                d.text(hologramText(pet)); d.getPersistentDataContainer().set(marker,PersistentDataType.STRING,binding.uuid().toString());
            });
            hitbox=world.spawn(location,Interaction.class,d-> {
                d.setPersistent(false); d.setInvulnerable(true); d.setInteractionWidth(pet.width()); d.setInteractionHeight(pet.height()); d.setResponsive(true);
                d.getPersistentDataContainer().set(marker,PersistentDataType.STRING,binding.uuid().toString());
            });
            active.put(binding.uuid(),new RuntimeNpc(base,holo,hitbox,handle));
            for (Entity entity:List.of(base,holo,hitbox)) entities.put(entity.getUniqueId(),binding.uuid());
        } catch (ReflectiveOperationException|RuntimeException e) {
            if (holo!=null) holo.remove(); if (hitbox!=null) hitbox.remove();
            if (handle!=null) try { models.detach(handle); } catch (ReflectiveOperationException cleanup) { e.addSuppressed(cleanup); }
            throw e;
        }
    }
    private Component hologramText(PetCatalog.Pet pet) {
        return ShopMenu.color(String.join("\n",getConfig().getStringList("hologram.lines"))
            .replace("{pet}",pet.name()).replace("{ingame_price}",ShopMenu.money(pet.price())).replace("{store_price}",ShopMenu.euros(pet.storePrice())));
    }
    private void cleanup(UUID id) {
        RuntimeNpc runtime=active.remove(id); if (runtime==null) return;
        for (Entity e:List.of(runtime.base(),runtime.hologram(),runtime.hitbox())) entities.remove(e.getUniqueId());
        runtime.hologram().remove(); runtime.hitbox().remove();
        if (runtime.model()!=null) try { models.detach(runtime.model()); } catch (ReflectiveOperationException e) { error(e); }
    }
    private void removeOrphans(Entity[] loaded) {
        for (Entity entity:loaded) if (entity.getPersistentDataContainer().has(marker,PersistentDataType.STRING) && !entities.containsKey(entity.getUniqueId())) entity.remove();
    }
    private void click(Player player, UUID npc) {
        Binding binding=bindings.get(npc); if (binding==null) return;
        long now=System.currentTimeMillis(); if (now-lastClick.getOrDefault(player.getUniqueId(),0L)<350) return;
        lastClick.put(player.getUniqueId(),now); open(player,binding.pet(),false);
    }
    private void open(Player player, String id, boolean confirmation) {
        if (!player.hasPermission(USE)) { message(player,"&cDafür hast du keine Berechtigung."); return; }
        PetCatalog.Pet pet=catalog.get(id);
        if (!available(pet)) { message(player,"&cDieses Pet ist momentan nicht verfügbar."); return; }
        player.openInventory(new ShopMenu(player,pet,confirmation,purchases.owns(player.getUniqueId(),pet),Math.clamp(getConfig().getLong("confirmation-timeout-seconds",30),5,120)).getInventory());
    }
    private boolean available(PetCatalog.Pet pet) {
        Plugin mc=Bukkit.getPluginManager().getPlugin("MCPets");
        return pet!=null && pet.enabled() && catalog.existsInMCPets(pet) && mc!=null && mc.isEnabled();
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void entityClick(PlayerInteractEntityEvent event) {
        UUID npc=entities.get(event.getRightClicked().getUniqueId());
        if (npc!=null) { event.setCancelled(true); if (event.getHand()==EquipmentSlot.HAND) click(event.getPlayer(),npc); }
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void entityClickAt(PlayerInteractAtEntityEvent event) { entityClick(event); }
    @EventHandler(priority=EventPriority.HIGHEST) public void protect(EntityDamageEvent event) {
        if (entities.containsKey(event.getEntity().getUniqueId())) event.setCancelled(true);
    }
    @EventHandler public void entitiesLoaded(EntitiesLoadEvent event) {
        removeOrphans(event.getEntities().toArray(Entity[]::new)); Bukkit.getScheduler().runTask(this,this::reconcile);
    }
    @EventHandler public void join(PlayerJoinEvent event) { Bukkit.getScheduler().runTaskLater(this,()->recoverCharged(event.getPlayer().getUniqueId()),20); }
    @EventHandler public void quit(PlayerQuitEvent event) { lastClick.remove(event.getPlayer().getUniqueId()); }
    @EventHandler(priority=EventPriority.HIGHEST) public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ShopMenu) event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void menuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ShopMenu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !menu.player.equals(player.getUniqueId())
                || event.getRawSlot()<0 || event.getRawSlot()>=27) return;
        int slot=event.getRawSlot();
        Bukkit.getScheduler().runTask(this,()-> {
            if (player.isOnline() && player.getOpenInventory().getTopInventory().getHolder()==menu) handleMenuClick(player,menu,slot);
        });
    }
    private void handleMenuClick(Player player, ShopMenu menu, int slot) {
        if (slot==22) { player.closeInventory(); return; }
        PetCatalog.Pet pet=catalog.get(menu.pet);
        if (!available(pet) || !player.hasPermission(USE)) { player.closeInventory(); message(player,"&cDieses Pet ist momentan nicht verfügbar."); return; }
        boolean owned=purchases.owns(player.getUniqueId(),pet);
        if (owned) {
            if (slot==15) { player.closeInventory(); player.performCommand("mcpets"); }
            else if (slot==11) message(player,"&aDieses Pet gehört dir bereits.");
            return;
        }
        if (!menu.confirmation) {
            if (slot==11) open(player,pet.id(),true);
            if (slot==15) { player.closeInventory(); storeLink(player,pet); }
        } else if (slot==15) open(player,pet.id(),false);
        else if (slot==11) {
            player.closeInventory();
            if (System.currentTimeMillis()>menu.expires || Double.compare(menu.price,pet.price())!=0) {
                message(player,"&eDie Bestätigung ist abgelaufen oder der Preis hat sich geändert. Bitte öffne den Shop erneut."); return;
            }
            message(player,"&7Kauf wird geprüft …");
            purchases.buy(player.getUniqueId(),pet).whenComplete((result,error)-> {
                if (error!=null) { error(error); if (player.isOnline()) message(player,"&cDer Kauf konnte nicht abgeschlossen werden."); }
                else if (player.isOnline()) purchaseMessage(player,result,pet);
            });
        }
    }
    private void storeLink(Player player, PetCatalog.Pet pet) {
        if (pet.storeUrl().isBlank()) { message(player,"&eFür dieses Pet ist noch kein Shop-Link eingerichtet."); return; }
        String url=PetCatalog.urlFor(pet,player.getName(),player.getUniqueId());
        message(player,"&d"+pet.name()+" &7– &a"+ShopMenu.euros(pet.storePrice())+" € im Store.");
        player.sendMessage(ShopMenu.color("&a&nHier klicken und Store öffnen")
            .clickEvent(ClickEvent.openUrl(url)).hoverEvent(HoverEvent.showText(Component.text(url))));
    }
    private void purchaseMessage(CommandSender sender, PurchaseService.Result result, PetCatalog.Pet pet) {
        message(sender,switch(result) {
            case BOUGHT -> "&a"+pet.name()+" dauerhaft für "+ShopMenu.money(pet.price())+" $ freigeschaltet! &7Öffne deine Pets mit /mcpets.";
            case GRANTED -> "&a"+pet.name()+" dauerhaft freigeschaltet.";
            case ALREADY_OWNED -> "&aDieses Pet gehört dir bereits. Es wurde kein Geld abgezogen.";
            case BUSY -> "&eEine Freischaltung läuft bereits. Bitte warte kurz.";
            case NO_MONEY -> "&cDu brauchst "+ShopMenu.money(pet.price())+" $ für dieses Pet.";
            case UNAVAILABLE -> "&cDer Ingame-Kauf ist momentan nicht verfügbar.";
            case DENIED -> "&cDie Berechtigung für dieses Pet ist ausdrücklich gesperrt. Bitte wende dich ans Team.";
            case PENDING -> "&eDiese Freischaltung muss noch abgeschlossen werden. Bitte kaufe nicht erneut und wende dich ans Team.";
            case ERROR -> "&cDie Transaktion konnte nicht gespeichert werden. Es wurde kein Geld abgezogen.";
        });
    }
    private void recoverCharged(UUID player) {
        for (Entry entry:ledger.entries()) if (entry.phase()==Phase.CHARGED && (player==null || entry.key().player().equals(player)))
            purchases.recover(entry).whenComplete((result,e)-> { if (e!=null) error(e); else getLogger().info("Pending pet delivery: "+entry.key()+" -> "+result); });
    }
    private void message(CommandSender sender, String text) { sender.sendMessage(ShopMenu.color(getConfig().getString("prefix","&dTowny&aReborn &8» &r")+text)); }
    private void error(Throwable e) { getLogger().log(Level.SEVERE,"Pet shop operation failed; pending purchases must not be charged twice.",e); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length==0 || args[0].equalsIgnoreCase("help")) {
            message(sender,"&7/trpetshop open <pet-id>");
            if (sender.hasPermission(ADMIN)) for (String line:List.of("add <pet-id>","place <pet-id>","bind <pet-id> [npc-id]",
                    "price <pet-id> <ingame-price> [store-price]","store <pet-id> <https-link|off>","height <pet-id> <blocks>",
                    "model <pet-id> <model-id>","remove <npc-id>","list | status | reload | pending",
                    "grant <uuid|known-name> <pet-id> [transaction] (Konsole)","resolve <uuid> <pet-id> paid|unpaid (Bankverlauf vorher prüfen)"))
                message(sender,"&7/trpetshop "+line);
            return true;
        }
        try {
            String sub=args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("open")) {
                if (sender instanceof Player p && args.length==2) open(p,args[1],false); else message(sender,"&7/trpetshop open <pet-id>");
                return true;
            }
            if (!sender.hasPermission(ADMIN)) { message(sender,"&cDafür hast du keine Berechtigung."); return true; }
            switch(sub) {
                case "add" -> { require(args,2); var pet=catalog.add(args[1]); message(sender,"&aShop-Pet angelegt: "+pet.id()); }
                case "place" -> {
                    require(args,2); if (!(sender instanceof Player player)) throw new IllegalArgumentException("Place requires an in-game player");
                    var pet=catalog.add(args[1]);
                    if (models==null || !models.has(pet.model())) throw new IllegalArgumentException("ModelEngine model is not loaded: "+pet.model());
                    Object npc=citizens.create(pet.name()); UUID uuid=citizens.uuid(npc);
                    try {
                        bindings.put(new Binding(uuid,citizens.id(npc),pet.id(),true));
                        if (!citizens.spawn(npc,player.getLocation())) throw new IllegalStateException("Citizens refused NPC spawn");
                        reconcile();
                        if (!active.containsKey(uuid)) throw new IllegalStateException("Could not attach pet model/hologram");
                        citizens.save(); message(sender,"&aPetmodell mit Hologramm aufgestellt. Anzeige-ID: "+citizens.id(npc));
                    } catch (Exception e) { cleanup(uuid); bindings.remove(uuid); citizens.destroy(npc); throw e; }
                }
                case "bind" -> {
                    if (args.length<2 || args.length>3) throw new IllegalArgumentException("/trpetshop bind <pet-id> [npc-id]");
                    var pet=catalog.add(args[1]); Object npc=args.length==3 ? citizens.get(Integer.parseInt(args[2])) : citizens.selected(sender);
                    if (npc==null) throw new IllegalArgumentException("Select an existing Citizens NPC or specify its ID");
                    UUID uuid=citizens.uuid(npc); if (bindings.get(uuid)!=null) throw new IllegalArgumentException("NPC already has a shop binding");
                    bindings.put(new Binding(uuid,citizens.id(npc),pet.id(),false)); reconcile();
                    message(sender,"&aNPC "+citizens.id(npc)+" mit "+pet.id()+" verknüpft. Vorhandenes NPC-Modell bleibt erhalten.");
                }
                case "price" -> {
                    if (args.length<3 || args.length>4) throw new IllegalArgumentException("/trpetshop price <pet-id> <ingame-price> [store-price]");
                    double in=PetCatalog.price(number(args[2])); Double store=args.length==4?PetCatalog.price(number(args[3])):null;
                    catalog.set(args[1],"ingame-price",in); if (store!=null) catalog.set(args[1],"store-price",store);
                    refresh(); message(sender,"&aPreis für "+args[1]+" gespeichert. Den echten Store-Paketpreis im Store-Dashboard setzen.");
                }
                case "store" -> { require(args,3); String url=args[2].equalsIgnoreCase("off")?"":args[2]; PetCatalog.validUrl(url); catalog.set(args[1],"store-url",url); refresh(); message(sender,"&aShop-Link gespeichert."); }
                case "height" -> { require(args,3); catalog.set(args[1],"hologram-height",number(args[2])); refresh(); message(sender,"&aHologrammhöhe gespeichert."); }
                case "model" -> {
                    require(args,3); if (models==null || !models.has(args[2])) throw new IllegalArgumentException("ModelEngine model not loaded");
                    catalog.set(args[1],"model",args[2]); refresh(); message(sender,"&aModell gespeichert.");
                }
                case "remove" -> {
                    require(args,2); int id=Integer.parseInt(args[1]); Binding binding=bindings.all().stream().filter(b->b.id()==id).findFirst().orElseThrow(()->new IllegalArgumentException("Not a pet-shop NPC"));
                    Object npc=citizens.get(id,binding.uuid()); bindings.remove(binding.uuid()); cleanup(binding.uuid());
                    if (npc!=null && binding.managed()) { citizens.destroy(npc); citizens.save(); }
                    message(sender,"&aPetshop-Verknüpfung entfernt."+(binding.managed()?"":" &7Der vorhandene NPC bleibt stehen."));
                }
                case "list" -> {
                    message(sender,"&7Verfügbare MCPets: "+String.join(", ",catalog.availableIds()));
                    for (var pet:catalog.pets().values()) message(sender,"&d"+pet.id()+" &7– &a"+ShopMenu.money(pet.price())+" $ &7/ &a"+ShopMenu.euros(pet.storePrice())+" €");
                    for (Binding b:bindings.all()) message(sender,"&7NPC "+b.id()+" → "+b.pet());
                }
                case "status" -> message(sender,"&7Shop-Pets: "+catalog.pets().size()+", MCPets: "+catalog.availableIds().size()+", NPCs: "+bindings.all().size()+", aktiv: "+active.size()+", ModelEngine: "+(models!=null));
                case "reload" -> { reloadConfig(); PetCatalog replacement=loadCatalog(); catalog=replacement; refresh(); message(sender,"&aPetshop-Konfiguration neu geladen."); }
                case "grant" -> {
                    if (sender instanceof Player || !(sender instanceof ConsoleCommandSender || sender instanceof RemoteConsoleCommandSender))
                        throw new IllegalArgumentException("Store grants are console-only");
                    if (args.length<3 || args.length>4) throw new IllegalArgumentException("/trpetshop grant <uuid|known-name> <pet-id> [transaction]");
                    PetCatalog.Pet pet=Objects.requireNonNull(catalog.get(args[2]),"Unknown shop pet");
                    if (!catalog.existsInMCPets(pet)) throw new IllegalArgumentException("MCPets definition or permission missing");
                    String transaction=args.length==4?args[3]:"manual";
                    if (!transaction.matches("[A-Za-z0-9_.:-]{1,128}")) throw new IllegalArgumentException("Invalid transaction ID");
                    resolvePlayer(args[1]).whenComplete((uuid,error)->runMain(()-> {
                        if (error!=null || uuid==null) { message(sender,"&cSpieler nicht gefunden. Verwende die Minecraft-UUID."); return; }
                        purchases.grant(uuid,pet,transaction).whenComplete((result,e)-> {
                            if (e!=null) { error(e); message(sender,"&cFreischaltung fehlgeschlagen; /trpetshop pending prüfen."); }
                            else {
                                purchaseMessage(sender,result,pet);
                                Player online=Bukkit.getPlayer(uuid); if (online!=null && result==PurchaseService.Result.GRANTED) purchaseMessage(online,result,pet);
                            }
                        });
                    }));
                }
                case "pending" -> {
                    int n=0; for (Entry entry:ledger.entries()) if (entry.phase()!=Phase.DONE) {
                        message(sender,"&7"+entry.key().player()+" "+entry.key().pet()+" "+entry.phase()+" "+entry.price()+" $ "+entry.source()); n++;
                    }
                    if (n==0) message(sender,"&aKeine offenen Freischaltungen.");
                }
                case "resolve" -> {
                    require(args,4); var key=new Key(parseUuid(args[1]),args[2]); Entry entry=Objects.requireNonNull(ledger.get(key),"No pending transaction");
                    if (entry.phase()==Phase.DONE) throw new IllegalArgumentException("Purchase already completed");
                    if (args[3].equalsIgnoreCase("paid")) {
                        Entry charged=entry.phase(Phase.CHARGED); ledger.put(charged);
                        purchases.recover(charged).whenComplete((r,e)-> { if(e!=null)error(e); else message(sender,"&7Freischaltung: "+r); });
                    } else if (args[3].equalsIgnoreCase("unpaid") && entry.phase()==Phase.PREPARED) { ledger.remove(key); message(sender,"&aAls nicht abgebucht geprüft; erneuter Kauf ist möglich."); }
                    else throw new IllegalArgumentException("Use paid, or unpaid only for an ambiguous PREPARED payment");
                }
                default -> message(sender,"&7/trpetshop help");
            }
        } catch (Exception e) { message(sender,"&c"+Objects.toString(e.getMessage(),"Petshop-Aktion fehlgeschlagen")); if (!(e instanceof IllegalArgumentException || e instanceof NullPointerException)) error(e); }
        return true;
    }
    private void refresh() {
        for (UUID id:List.copyOf(active.keySet())) cleanup(id);
        for (Player p:Bukkit.getOnlinePlayers()) if(p.getOpenInventory().getTopInventory().getHolder() instanceof ShopMenu)p.closeInventory();
        reconcile();
    }
    private static void require(String[] args,int n) { if(args.length!=n)throw new IllegalArgumentException("Invalid arguments; /trpetshop help"); }
    private static double number(String text) { return Double.parseDouble(text.replace(',','.')); }
    private static UUID parseUuid(String text) {
        if (text.matches("[0-9a-fA-F]{32}")) text=text.substring(0,8)+"-"+text.substring(8,12)+"-"+text.substring(12,16)+"-"+text.substring(16,20)+"-"+text.substring(20);
        if (!text.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) throw new IllegalArgumentException("Invalid UUID");
        return UUID.fromString(text);
    }
    private CompletionStage<UUID> resolvePlayer(String text) {
        try { return CompletableFuture.completedFuture(parseUuid(text)); }
        catch (IllegalArgumentException ignored) {
            if (!text.matches("[A-Za-z0-9_]{1,16}")) return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid player"));
            Player p=Bukkit.getPlayerExact(text); if (p!=null) return CompletableFuture.completedFuture(p.getUniqueId());
            return permissions.getUserManager().lookupUniqueId(text);
        }
    }
    private void runMain(Runnable task) { if (isEnabled()) { if(Bukkit.isPrimaryThread())task.run(); else Bukkit.getScheduler().runTask(this,task); } }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> choices=List.of();
        if(args.length==1) choices=sender.hasPermission(ADMIN)?List.of("open","add","place","bind","price","store","height","model","remove","list","status","reload","pending","grant","resolve","help"):List.of("open","help");
        else if(args.length==2 && Set.of("add","place","bind").contains(args[0])) choices=new ArrayList<>(catalog.availableIds());
        else if(args.length==2 && Set.of("open","price","store","height","model").contains(args[0])) choices=new ArrayList<>(catalog.pets().keySet());
        else if(args.length==3 && args[0].equals("grant")) choices=new ArrayList<>(catalog.pets().keySet());
        String prefix=args[args.length-1].toLowerCase(Locale.ROOT);
        return choices.stream().filter(s->s.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    }
    private final class Gateway implements PurchaseService.Gateway {
        private Economy economy() { return Bukkit.getServicesManager().load(Economy.class); }
        @Override public boolean owns(UUID uuid, String permission) {
            Player online=Bukkit.getPlayer(uuid);
            if (online!=null && online.hasPermission(permission)) return true;
            var user=permissions.getUserManager().getUser(uuid);
            return user!=null && user.getCachedData().getPermissionData().checkPermission(permission).asBoolean();
        }
        @Override public boolean ready(UUID uuid) {
            Economy e=economy(); Plugin mc=Bukkit.getPluginManager().getPlugin("MCPets");
            return e!=null && e.isEnabled() && Bukkit.getPlayer(uuid)!=null && permissions.getUserManager().getUser(uuid)!=null && mc!=null && mc.isEnabled();
        }
        @Override public boolean allowed(UUID uuid,String permission) {
            var user=permissions.getUserManager().getUser(uuid);
            return user!=null && user.data().toCollection().stream().noneMatch(n->n.getKey().equals(permission)&&!n.getValue()&&!n.hasExpiry()&&n.getContexts().isEmpty());
        }
        @Override public double balance(UUID uuid) { return Objects.requireNonNull(economy()).getBalance(Bukkit.getOfflinePlayer(uuid)); }
        @Override public boolean withdraw(UUID uuid,double price) {
            var result=Objects.requireNonNull(economy()).withdrawPlayer(Bukkit.getOfflinePlayer(uuid),price);
            if (!result.transactionSuccess()) return false;
            if (!Double.isFinite(result.amount) || Math.abs(result.amount-price)>0.005) throw new IllegalStateException("Economy returned an unexpected withdrawn amount; verify bank history");
            return true;
        }
        @Override public CompletionStage<Void> grant(UUID uuid,String permission) {
            var result=new CompletableFuture<Void>();
            permissions.getUserManager().loadUser(uuid).thenCompose(user-> {
                user.data().add(PermissionNode.builder(permission).value(true).build());
                return permissions.getUserManager().saveUser(user);
            }).whenComplete((ignored,error)->runMain(()-> { if(error==null)result.complete(null); else result.completeExceptionally(error); }));
            return result;
        }
    }
}
