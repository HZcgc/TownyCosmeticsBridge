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
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import com.townyreborn.petshops.NpcBindings.Binding;
import com.townyreborn.petshops.PurchaseLedger.*;
import com.townyreborn.petshops.Integrations.Click;

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
    private boolean discoveryRunning;
    private long lastDiscoveryFailure;
    private org.bukkit.scheduler.BukkitTask discoveryTask;
    private org.bukkit.scheduler.BukkitTask pedestalTask;
    private final Map<UUID,RuntimeNpc> active=new HashMap<>();
    private final Map<UUID,UUID> entities=new ConcurrentHashMap<>();
    private final Map<UUID,Long> lastClick=new HashMap<>(), failures=new HashMap<>();
    private final Map<UUID,Long> animationUntil=new HashMap<>(), lastAttack=new HashMap<>();
    private record RuntimeNpc(Entity base, TextDisplay hologram, Interaction hitbox, Integrations.Models.Handle model, boolean managedModel) {}

    @Override public void onEnable() {
        try {
            saveDefaultConfig(); if (!getDataFolder().toPath().resolve("pets.yml").toFile().exists()) saveResource("pets.yml",false);
            if (ShopSettings.upgrade(getConfig())) saveConfig();
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
                    Click action=models.click(event);
                    if (npc!=null && action!=Click.IGNORE) {
                        if (event instanceof Cancellable c) c.setCancelled(true);
                        Player player=models.player(event);
                        runMain(()->click(player,npc,action));
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
            startPedestals();
            startDiscovery();
            getLogger().info("Ready: "+catalog.pets().size()+" shop pets, "+catalog.availableIds().size()+" MCPets definitions, "+bindings.all().size()+" NPCs. UltraCosmetics is not used.");
        } catch (Exception|LinkageError e) { error(e); Bukkit.getPluginManager().disablePlugin(this); }
    }
    @Override public void onDisable() {
        for (UUID id:List.copyOf(active.keySet())) cleanup(id);
        for (Player player:Bukkit.getOnlinePlayers()) if (player.getOpenInventory().getTopInventory().getHolder() instanceof ShopMenu) player.closeInventory();
        lastClick.clear(); failures.clear();
        animationUntil.clear(); lastAttack.clear();
        if (pedestalTask!=null) pedestalTask.cancel();
    }
    private PetCatalog loadCatalog() throws IOException {
        var defaults=Objects.requireNonNull(getConfig().getConfigurationSection("defaults"),"Missing defaults section");
        return new PetCatalog(getDataFolder().toPath().resolve("pets.yml"),getDataFolder().toPath().getParent().resolve("MCPets/Pets"),defaults);
    }
    private void startDiscovery() {
        if (discoveryTask!=null) discoveryTask.cancel();
        long period=Math.clamp(getConfig().getLong("discovery-interval-seconds",15),5,3600)*20;
        discoveryTask=Bukkit.getScheduler().runTaskTimer(this,this::discover,20,period);
    }
    private void startPedestals() {
        if (pedestalTask!=null) pedestalTask.cancel();
        long period=Math.clamp(getConfig().getLong("npc.update-interval-ticks",4),1,20);
        pedestalTask=Bukkit.getScheduler().runTaskTimer(this,this::tickPedestals,period,period);
    }
    private void tickPedestals() {
        double range=Math.clamp(getConfig().getDouble("npc.look-at-player-range",8),0,32);
        for (var entry:List.copyOf(active.entrySet())) {
            UUID id=entry.getKey(); RuntimeNpc runtime=entry.getValue();
            if (!runtime.base().isValid() || !runtime.hitbox().isValid()) continue;
            Interaction.PreviousInteraction attack=runtime.hitbox().getLastAttack();
            if (attack!=null && attack.getTimestamp()!=lastAttack.getOrDefault(id,Long.MIN_VALUE)) {
                lastAttack.put(id,attack.getTimestamp());
                Player player=Bukkit.getPlayer(attack.getPlayer().getUniqueId());
                if (player!=null) click(player,id,Click.SHOP);
            }
            if (range<=0) continue;
            Location location=runtime.base().getLocation();
            Player closest=location.getWorld().getNearbyPlayers(location,range).stream()
                .filter(p->!p.isDead() && p.getGameMode()!=GameMode.SPECTATOR && !p.isInvisible())
                .filter(p->p.getLocation().distanceSquared(location)<=range*range)
                .min(Comparator.comparingDouble(p->p.getLocation().distanceSquared(location))).orElse(null);
            if (closest==null) continue;
            Location target=closest.getLocation();
            double dx=target.getX()-location.getX(), dz=target.getZ()-location.getZ();
            if (dx*dx+dz*dz<0.01) continue;
            float yaw=(float)Math.toDegrees(Math.atan2(-dx,dz));
            try {
                if (!(runtime.base() instanceof Player)) runtime.base().setRotation(yaw,location.getPitch());
                if (runtime.model()!=null) models.face(runtime.model(),yaw);
            } catch (ReflectiveOperationException e) {
                long now=System.currentTimeMillis();
                if (now-failures.getOrDefault(id,0L)>60000) { failures.put(id,now); error(e); }
            }
        }
    }
    private void discover() {
        if (discoveryRunning) return;
        discoveryRunning=true; PetCatalog current=catalog; long revision=current.revision();
        Bukkit.getScheduler().runTaskAsynchronously(this,()-> {
            try {
                var found=current.scanDefinitions();
                runMain(()-> {
                    discoveryRunning=false;
                    if (catalog!=current || catalog.revision()!=revision) return; // An admin saved newer settings.
                    try {
                        Set<String> previous=new HashSet<>(current.availableIds());
                        if (current.updateDefinitions(found)) { refresh(); recoverCharged(null); }
                        var added=new TreeSet<>(current.availableIds()); added.removeAll(previous);
                        if (!added.isEmpty()) getLogger().info("New MCPets detected: "+String.join(", ",added)+". Ready for /trpetshop place <pet-id>.");
                    } catch (Exception e) { discoveryFailure(e); }
                });
            } catch (Exception e) { runMain(()-> { discoveryRunning=false; discoveryFailure(e); }); }
        });
    }
    private void discoveryFailure(Exception e) {
        long now=System.currentTimeMillis();
        if (now-lastDiscoveryFailure>60000) {
            lastDiscoveryFailure=now;
            getLogger().log(Level.WARNING,"MCPets scan could not be applied; the previous valid catalog is retained.",e);
        }
    }
    private void setupFile(CommandSender sender, PetCatalog.Pet pet) {
        message(sender,"&7Pet file: plugins/TownyRebornPetShops/shops/"+pet.id()+".yml");
        message(sender,"&7Tebex console command: &a"+PetCatalog.tebexCommand(pet.id()));
    }
    private void registerCitizensEvents() throws ReflectiveOperationException {
        registerCitizenClick("NPCRightClickEvent",Click.INTERACT);
        registerCitizenClick("NPCLeftClickEvent",Click.SHOP);
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
    private void registerCitizenClick(String eventName, Click action) throws ReflectiveOperationException {
        Bukkit.getPluginManager().registerEvent(citizens.event(eventName),this,EventPriority.HIGHEST,(listener,event)-> {
            try {
                UUID id=citizens.uuid(citizens.eventNpc(event));
                if (bindings.get(id)!=null) {
                    if (event instanceof Cancellable c) c.setCancelled(true);
                    click(citizens.clicker(event),id,action);
                }
            } catch (ReflectiveOperationException e) { error(e); }
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
                citizens.tracking(npc,ShopSettings.renderDistance(getConfig()));
                if (runtime.model()!=null) models.rendering(runtime.model(),ShopSettings.renderDistance(getConfig()));
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
                handle=models.attach(base,pet.model(),pet.idle(),ShopSettings.renderDistance(getConfig()));
            } else if (models!=null) handle=models.existing(base,pet.model());
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
            active.put(binding.uuid(),new RuntimeNpc(base,holo,hitbox,handle,binding.managed()));
            for (Entity entity:List.of(base,holo,hitbox)) entities.put(entity.getUniqueId(),binding.uuid());
        } catch (ReflectiveOperationException|RuntimeException e) {
            if (holo!=null) holo.remove(); if (hitbox!=null) hitbox.remove();
            if (handle!=null && binding.managed()) try { models.detach(handle); } catch (ReflectiveOperationException cleanup) { e.addSuppressed(cleanup); }
            throw e;
        }
    }
    private Component hologramText(PetCatalog.Pet pet) {
        return ShopMenu.color(String.join("\n",getConfig().getStringList("hologram.lines"))
            .replace("{pet}",pet.name()).replace("{ingame_price}",ShopMenu.money(pet.price())).replace("{store_price}",ShopMenu.euros(pet.storePrice())));
    }
    private void cleanup(UUID id) {
        animationUntil.remove(id); lastAttack.remove(id);
        RuntimeNpc runtime=active.remove(id); if (runtime==null) return;
        for (Entity e:List.of(runtime.base(),runtime.hologram(),runtime.hitbox())) entities.remove(e.getUniqueId());
        runtime.hologram().remove(); runtime.hitbox().remove();
        if (runtime.model()!=null && runtime.managedModel()) try { models.detach(runtime.model()); } catch (ReflectiveOperationException e) { error(e); }
    }
    private void removeOrphans(Entity[] loaded) {
        for (Entity entity:loaded) if (entity.getPersistentDataContainer().has(marker,PersistentDataType.STRING) && !entities.containsKey(entity.getUniqueId())) entity.remove();
    }
    private void click(Player player, UUID npc, Click action) {
        Binding binding=bindings.get(npc); if (binding==null) return;
        long now=System.currentTimeMillis(); if (now-lastClick.getOrDefault(player.getUniqueId(),0L)<350) return;
        lastClick.put(player.getUniqueId(),now);
        if (action==Click.SHOP) { open(player,binding.pet(),false); return; }
        if (action!=Click.INTERACT || now<animationUntil.getOrDefault(npc,0L)) return;
        if (!player.hasPermission(USE)) { message(player,"&cYou do not have permission to use this pet shop."); return; }
        RuntimeNpc runtime=active.get(npc); PetCatalog.Pet pet=catalog.get(binding.pet());
        if (runtime==null || runtime.model()==null || pet==null) return;
        try {
            long duration=models.interact(runtime.model(),pet.interaction());
            if (duration>0) animationUntil.put(npc,now+Math.max(duration,(long)(Math.clamp(getConfig().getDouble("interaction.cooldown-seconds",3),0,60)*1000)));
            else message(player,"&eThis pet has no interaction animation configured.");
        } catch (ReflectiveOperationException e) { error(e); }
    }
    private void open(Player player, String id, boolean confirmation) {
        if (!player.hasPermission(USE)) { message(player,"&cYou do not have permission to use this pet shop."); return; }
        PetCatalog.Pet pet=catalog.get(id);
        if (!available(pet)) { message(player,"&cThis pet is currently unavailable."); return; }
        player.openInventory(new ShopMenu(player,pet,confirmation,purchases.owns(player.getUniqueId(),pet),Math.clamp(getConfig().getLong("confirmation-timeout-seconds",30),5,120)).getInventory());
    }
    private boolean available(PetCatalog.Pet pet) {
        Plugin mc=Bukkit.getPluginManager().getPlugin("MCPets");
        return pet!=null && pet.enabled() && catalog.existsInMCPets(pet) && mc!=null && mc.isEnabled();
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void entityClick(PlayerInteractEntityEvent event) {
        UUID npc=entities.get(event.getRightClicked().getUniqueId());
        if (npc!=null) { event.setCancelled(true); if (event.getHand()==EquipmentSlot.HAND) click(event.getPlayer(),npc,Click.INTERACT); }
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void entityClickAt(PlayerInteractAtEntityEvent event) { entityClick(event); }
    @EventHandler(priority=EventPriority.HIGHEST) public void protect(EntityDamageEvent event) {
        UUID npc=entities.get(event.getEntity().getUniqueId());
        if (npc!=null) {
            event.setCancelled(true);
            if (event instanceof EntityDamageByEntityEvent hit && hit.getDamager() instanceof Player player) click(player,npc,Click.SHOP);
        }
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
        if (!available(pet) || !player.hasPermission(USE)) { player.closeInventory(); message(player,"&cThis pet is currently unavailable."); return; }
        boolean owned=purchases.owns(player.getUniqueId(),pet);
        if (owned) {
            if (slot==15) { player.closeInventory(); player.performCommand("mcpets"); }
            else if (slot==11) message(player,"&aYou already own this pet.");
            return;
        }
        if (!menu.confirmation) {
            if (slot==11) open(player,pet.id(),true);
            if (slot==15) { player.closeInventory(); storeLink(player,pet); }
        } else if (slot==15) open(player,pet.id(),false);
        else if (slot==11) {
            player.closeInventory();
            if (System.currentTimeMillis()>menu.expires || Double.compare(menu.price,pet.price())!=0) {
                message(player,"&eThis confirmation expired or the price changed. Please open the shop again."); return;
            }
            message(player,"&7Checking your purchase…");
            purchases.buy(player.getUniqueId(),pet).whenComplete((result,error)-> {
                if (error!=null) { error(error); if (player.isOnline()) message(player,"&cThe purchase could not be completed."); }
                else if (player.isOnline()) purchaseMessage(player,result,pet);
            });
        }
    }
    private void storeLink(Player player, PetCatalog.Pet pet) {
        if (pet.storeUrl().isBlank()) { message(player,"&eThe Tebex link for this pet has not been configured yet."); return; }
        String url=PetCatalog.urlFor(pet,player.getName(),player.getUniqueId());
        message(player,"&d"+pet.name()+" &7– &a"+ShopMenu.euros(pet.storePrice())+" € in the store.");
        player.sendMessage(ShopMenu.color("&a&nClick here to open Tebex")
            .clickEvent(ClickEvent.openUrl(url)).hoverEvent(HoverEvent.showText(Component.text(url))));
    }
    private void purchaseMessage(CommandSender sender, PurchaseService.Result result, PetCatalog.Pet pet) {
        message(sender,switch(result) {
            case BOUGHT -> "&a"+pet.name()+" permanently unlocked for "+ShopMenu.money(pet.price())+" $! &7Open your pets with /mcpets.";
            case GRANTED -> "&a"+pet.name()+" permanently unlocked.";
            case ALREADY_OWNED -> "&aYou already own this pet. No money was taken.";
            case BUSY -> "&eAn unlock is already in progress. Please wait.";
            case NO_MONEY -> "&cYou need "+ShopMenu.money(pet.price())+" $ for this pet.";
            case UNAVAILABLE -> "&cIn-game purchases are currently unavailable.";
            case DENIED -> "&cAccess to this pet is explicitly denied. Please contact staff.";
            case PENDING -> "&eThis unlock is still pending. Please contact staff before buying again.";
            case ERROR -> "&cThe transaction could not be saved. No money was taken.";
        });
    }
    private void recoverCharged(UUID player) {
        for (Entry entry:ledger.entries()) if (player==null || entry.key().player().equals(player)) {
            PetCatalog.Pet pet=catalog.get(entry.key().pet());
            CompletionStage<PurchaseService.Result> delivery=null;
            if (entry.phase()==Phase.CHARGED) delivery=purchases.recover(entry);
            else if (entry.phase()==Phase.DONE && pet!=null && catalog.existsInMCPets(pet) && !entry.permission().equals(pet.permission()))
                delivery=purchases.grant(entry.key().player(),pet,entry.transaction());
            if (delivery!=null) delivery.whenComplete((result,e)-> { if (e!=null) error(e); else getLogger().info("Pending pet delivery: "+entry.key()+" -> "+result); });
        }
    }
    private void message(CommandSender sender, String text) { sender.sendMessage(ShopMenu.color(getConfig().getString("prefix","&dTowny&aReborn &8» &r")+text)); }
    private void error(Throwable e) { getLogger().log(Level.SEVERE,"Pet shop operation failed; pending purchases must not be charged twice.",e); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length==0 || args[0].equalsIgnoreCase("help")) {
            message(sender,"&7/trpetshop open <pet-id>");
            if (sender.hasPermission(ADMIN)) for (String line:List.of("add <pet-id>","place <pet-id>","bind <pet-id> [npc-id]",
                    "price <pet-id> <ingame-price> [store-price]","store <pet-id> <https-link|off>","height <pet-id> <blocks>",
                    "model <pet-id> <model-id>","animation <pet-id> <auto|animation|off>","range <blocks>","remove <npc-id>","list | status | reload | pending",
                    "grant <uuid|known-name> <pet-id> [transaction] (console only)","resolve <uuid> <pet-id> paid|unpaid (check the bank history first)"))
                message(sender,"&7/trpetshop "+line);
            return true;
        }
        try {
            String sub=args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("open")) {
                if (sender instanceof Player p && args.length==2) open(p,args[1],false); else message(sender,"&7/trpetshop open <pet-id>");
                return true;
            }
            if (!sender.hasPermission(ADMIN)) { message(sender,"&cYou do not have permission to use this pet shop."); return true; }
            switch(sub) {
                case "add" -> { require(args,2); var pet=catalog.add(args[1]); message(sender,"&aPet offer created: "+pet.id()); setupFile(sender,pet); }
                case "place" -> {
                    require(args,2); if (!(sender instanceof Player player)) throw new IllegalArgumentException("Place requires an in-game player");
                    var pet=catalog.add(args[1]);
                    if (models==null || !models.has(pet.model())) throw new IllegalArgumentException("ModelEngine model is not loaded: "+pet.model());
                    Object npc=citizens.create(pet.name(),ShopSettings.renderDistance(getConfig())); UUID uuid=citizens.uuid(npc);
                    try {
                        bindings.put(new Binding(uuid,citizens.id(npc),pet.id(),true));
                        if (!citizens.spawn(npc,player.getLocation())) throw new IllegalStateException("Citizens refused NPC spawn");
                        reconcile();
                        if (!active.containsKey(uuid)) throw new IllegalStateException("Could not attach pet model/hologram");
                        citizens.save(); message(sender,"&aPet model and hologram placed. NPC ID: "+citizens.id(npc)); setupFile(sender,pet);
                    } catch (Exception e) { cleanup(uuid); bindings.remove(uuid); citizens.destroy(npc); throw e; }
                }
                case "bind" -> {
                    if (args.length<2 || args.length>3) throw new IllegalArgumentException("/trpetshop bind <pet-id> [npc-id]");
                    var pet=catalog.add(args[1]); Object npc=args.length==3 ? citizens.get(Integer.parseInt(args[2])) : citizens.selected(sender);
                    if (npc==null) throw new IllegalArgumentException("Select an existing Citizens NPC or specify its ID");
                    UUID uuid=citizens.uuid(npc); if (bindings.get(uuid)!=null) throw new IllegalArgumentException("NPC already has a shop binding");
                    bindings.put(new Binding(uuid,citizens.id(npc),pet.id(),false)); reconcile();
                    message(sender,"&aNPC "+citizens.id(npc)+" with "+pet.id()+" linked. Its existing model is kept.");
                    setupFile(sender,pet);
                }
                case "price" -> {
                    if (args.length<3 || args.length>4) throw new IllegalArgumentException("/trpetshop price <pet-id> <ingame-price> [store-price]");
                    double in=PetCatalog.price(number(args[2])); Double store=args.length==4?PetCatalog.price(number(args[3])):null;
                    catalog.set(args[1],"ingame-price",in); if (store!=null) catalog.set(args[1],"store-price",store);
                    refresh(); message(sender,"&aPrice for "+args[1]+" saved. Set the actual Tebex package price in the store dashboard.");
                }
                case "store" -> { require(args,3); String url=args[2].equalsIgnoreCase("off")?"":args[2]; PetCatalog.validUrl(url); catalog.set(args[1],"store-url",url); refresh(); message(sender,"&aTebex link saved."); }
                case "height" -> { require(args,3); catalog.set(args[1],"hologram-height",number(args[2])); refresh(); message(sender,"&aHologram height saved."); }
                case "model" -> {
                    require(args,3); if (models==null || !models.has(args[2])) throw new IllegalArgumentException("ModelEngine model not loaded");
                    catalog.set(args[1],"model",args[2]); refresh(); message(sender,"&aModel saved.");
                }
                case "animation" -> { require(args,3); catalog.set(args[1],"interaction-animation",args[2]); refresh(); message(sender,"&aInteraction animation saved."); }
                case "range" -> {
                    require(args,2); int distance=Integer.parseInt(args[1]);
                    if (distance<16 || distance>256) throw new IllegalArgumentException("Render distance must be between 16 and 256 blocks");
                    getConfig().set("npc.render-distance",distance); saveConfig(); refresh(); message(sender,"&aPet render distance saved: "+distance+" blocks.");
                }
                case "remove" -> {
                    require(args,2); int id=Integer.parseInt(args[1]); Binding binding=bindings.all().stream().filter(b->b.id()==id).findFirst().orElseThrow(()->new IllegalArgumentException("Not a pet-shop NPC"));
                    Object npc=citizens.get(id,binding.uuid()); bindings.remove(binding.uuid()); cleanup(binding.uuid());
                    if (npc!=null && binding.managed()) { citizens.destroy(npc); citizens.save(); }
                    message(sender,"&aPet shop binding removed."+(binding.managed()?"":" &7The existing NPC is kept."));
                }
                case "list" -> {
                    message(sender,"&7Available MCPets: "+String.join(", ",catalog.availableIds()));
                    for (var pet:catalog.pets().values()) message(sender,"&d"+pet.id()+" &7– &a"+ShopMenu.money(pet.price())+" $ &7/ &a"+ShopMenu.euros(pet.storePrice())+" €");
                    for (Binding b:bindings.all()) message(sender,"&7NPC "+b.id()+" → "+b.pet());
                }
                case "status" -> message(sender,"&7Shop-Pets: "+catalog.pets().size()+", MCPets: "+catalog.availableIds().size()+", NPCs: "+bindings.all().size()+", active: "+active.size()+", ModelEngine: "+(models!=null));
                case "reload" -> { reloadConfig(); if (ShopSettings.upgrade(getConfig())) saveConfig(); PetCatalog replacement=loadCatalog(); catalog=replacement; startDiscovery(); startPedestals(); refresh(); recoverCharged(null); message(sender,"&aPet shop configuration reloaded."); }
                case "grant" -> {
                    if (sender instanceof Player || !(sender instanceof ConsoleCommandSender || sender instanceof RemoteConsoleCommandSender))
                        throw new IllegalArgumentException("Store grants are console-only");
                    if (args.length<3 || args.length>4) throw new IllegalArgumentException("/trpetshop grant <uuid|known-name> <pet-id> [transaction]");
                    PetCatalog.Pet pet=Objects.requireNonNull(catalog.get(args[2]),"Unknown shop pet");
                    if (!catalog.existsInMCPets(pet)) throw new IllegalArgumentException("MCPets definition or permission missing");
                    String transaction=args.length==4?args[3]:"manual";
                    if (!transaction.matches("[A-Za-z0-9_.:-]{1,128}")) throw new IllegalArgumentException("Invalid transaction ID");
                    resolvePlayer(args[1]).whenComplete((uuid,error)->runMain(()-> {
                        if (error!=null || uuid==null) { message(sender,"&cPlayer not found. Use the Minecraft UUID."); return; }
                        PetCatalog.Pet current=catalog.get(pet.id());
                        if (current==null || !catalog.existsInMCPets(current)) { message(sender,"&cThe MCPets definition is currently unavailable."); return; }
                        purchases.grant(uuid,current,transaction).whenComplete((result,e)-> {
                            if (e!=null) { error(e); message(sender,"&cUnlock failed; check /trpetshop pending."); }
                            else {
                                purchaseMessage(sender,result,current);
                                Player online=Bukkit.getPlayer(uuid); if (online!=null && result==PurchaseService.Result.GRANTED) purchaseMessage(online,result,current);
                            }
                        });
                    }));
                }
                case "pending" -> {
                    int n=0; for (Entry entry:ledger.entries()) if (entry.phase()!=Phase.DONE) {
                        message(sender,"&7"+entry.key().player()+" "+entry.key().pet()+" "+entry.phase()+" "+entry.price()+" $ "+entry.source()); n++;
                    }
                    if (n==0) message(sender,"&aNo pending unlocks.");
                }
                case "resolve" -> {
                    require(args,4); var key=new Key(parseUuid(args[1]),args[2]); Entry entry=Objects.requireNonNull(ledger.get(key),"No pending transaction");
                    if (entry.phase()==Phase.DONE) throw new IllegalArgumentException("Purchase already completed");
                    if (args[3].equalsIgnoreCase("paid")) {
                        Entry charged=entry.phase(Phase.CHARGED); ledger.put(charged);
                        purchases.recover(charged).whenComplete((r,e)-> { if(e!=null)error(e); else message(sender,"&7Unlock: "+r); });
                    } else if (args[3].equalsIgnoreCase("unpaid") && entry.phase()==Phase.PREPARED) { ledger.remove(key); message(sender,"&aVerified as not charged; a new purchase is now allowed."); }
                    else throw new IllegalArgumentException("Use paid, or unpaid only for an ambiguous PREPARED payment");
                }
                default -> message(sender,"&7/trpetshop help");
            }
        } catch (Exception e) { message(sender,"&c"+Objects.toString(e.getMessage(),"Pet shop action failed")); if (!(e instanceof IllegalArgumentException || e instanceof NullPointerException)) error(e); }
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
        if(args.length==1) choices=sender.hasPermission(ADMIN)?List.of("open","add","place","bind","price","store","height","model","animation","range","remove","list","status","reload","pending","grant","resolve","help"):List.of("open","help");
        else if(args.length==2 && Set.of("add","place","bind").contains(args[0])) choices=new ArrayList<>(catalog.availableIds());
        else if(args.length==2 && Set.of("open","price","store","height","model","animation").contains(args[0])) choices=new ArrayList<>(catalog.pets().keySet());
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
