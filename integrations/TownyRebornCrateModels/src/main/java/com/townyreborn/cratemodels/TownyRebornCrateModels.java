package com.townyreborn.cratemodels;

import com.townyreborn.cratemodels.CrateInteraction;
import com.townyreborn.cratemodels.PlacementStore;
import com.townyreborn.cratemodels.PluginApis;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public final class TownyRebornCrateModels
extends JavaPlugin
implements Listener {
    private static final String ADMIN = "townyreborn.cratemodels.admin";
    private static final String PREFIX = "\u00a7dTowny\u00a7aReborn \u00a78| \u00a7f";
    private final Map<String, PlacementStore.Placement> placements = new LinkedHashMap<String, PlacementStore.Placement>();
    private final Map<String, RuntimeCrate> active = new HashMap<String, RuntimeCrate>();
    private final Map<UUID, String> entityPlacements = new HashMap<UUID, String>();
    private final Map<BlockKey, String> blockPlacements = new HashMap<BlockKey, String>();
    private final Map<String, Preset> models = new LinkedHashMap<String, Preset>();
    private final Map<UUID, Selection> selections = new HashMap<UUID, Selection>();
    private final Map<UUID, Long> lastClick = new HashMap<UUID, Long>();
    private final CrateInteraction queuedClicks = new CrateInteraction();
    private final Map<String, Long> lastFailure = new HashMap<String, Long>();
    private final Map<String, Long> animationGenerations = new HashMap<String, Long>();
    private long animationSequence;
    private PluginApis api;
    private NamespacedKey ownerKey;
    private Path placementFile;
    private boolean shuttingDown;
    private boolean anchoring;
    private int renderDistance;

    public void onEnable() {
        this.ownerKey = new NamespacedKey((Plugin)this, "placement");
        this.placementFile = this.getDataFolder().toPath().resolve("placements.yml");
        try {
            this.api = new PluginApis(Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("ModelEngine")), Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("ExcellentCrates")));
            this.saveDefaultConfig();
            this.renderDistance = Math.clamp((long)this.getConfig().getInt("render-distance", 96), (int)32, (int)160);
            try (InputStreamReader inputStreamReader = new InputStreamReader(Objects.requireNonNull(this.getResource("models.yml")), StandardCharsets.UTF_8);){
                ConfigurationSection object = Objects.requireNonNull(YamlConfiguration.loadConfiguration((Reader)inputStreamReader).getConfigurationSection("models"));
                for (String string : object.getKeys(false)) {
                    ConfigurationSection configurationSection = Objects.requireNonNull(object.getConfigurationSection(string));
                    this.models.put(string, new Preset((float)configurationSection.getDouble("width"), (float)configurationSection.getDouble("height"), Math.max(1L, configurationSection.getLong("open-ticks")), configurationSection.getBoolean("idle")));
                }
            }
            for (PlacementStore.Placement placement : PlacementStore.read(this.placementFile)) {
                this.placements.put(placement.id(), placement);
                this.indexBlock(placement);
            }
            Bukkit.getPluginManager().registerEvents((Listener)this, (Plugin)this);
            Bukkit.getPluginManager().registerEvent(this.api.openEvent, (Listener)this, EventPriority.MONITOR, (listener, event) -> this.opening(event), (Plugin)this, true);
            Bukkit.getPluginManager().registerEvent(this.api.modelInteractEvent, (Listener)this, EventPriority.HIGHEST, (listener, event) -> this.modelInteraction(event), (Plugin)this, false);
            Bukkit.getScheduler().runTask((Plugin)this, () -> {
                for (World world : Bukkit.getWorlds()) {
                    for (Chunk chunk : world.getLoadedChunks()) {
                        this.removeOrphans(chunk.getEntities());
                    }
                }
                this.reconcile();
            });
            Bukkit.getScheduler().runTaskTimer((Plugin)this, this::reconcile, 40L, 60L);
            this.getLogger().info("Ready: " + this.placements.size() + " saved placements. /trcrate place <crate> <model>");
        }
        catch (Exception | LinkageError throwable) {
            this.getLogger().log(Level.SEVERE, "Cannot initialize crate models; no crate data was changed.", throwable);
            Bukkit.getPluginManager().disablePlugin((Plugin)this);
        }
    }

    public void onDisable() {
        this.shuttingDown = true;
        for (String string : new ArrayList<String>(this.active.keySet())) {
            this.despawn(string);
        }
        this.selections.clear();
        this.lastClick.clear();
        this.queuedClicks.clear();
    }

    private Location location(PlacementStore.Placement placement) {
        World world = Bukkit.getWorld((UUID)placement.worldId());
        return world == null ? null : new Location(world, placement.x(), placement.y(), placement.z(), placement.yaw(), 0.0f);
    }

    private boolean loaded(PlacementStore.Placement placement) {
        World world = Bukkit.getWorld((UUID)placement.worldId());
        return world != null && world.isChunkLoaded(placement.chunkX(), placement.chunkZ());
    }

    private BlockKey blockKey(PlacementStore.Placement placement) {
        return new BlockKey(placement.worldId(), (int)Math.floor(placement.x()), (int)Math.floor(placement.y()), (int)Math.floor(placement.z()));
    }

    private BlockKey blockKey(Block block) {
        return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    private void indexBlock(PlacementStore.Placement placement) {
        if (placement.anchorOriginal() != null) {
            this.blockPlacements.put(this.blockKey(placement), placement.id());
        }
    }

    private boolean protectedBlock(Block block) {
        return block.getType() == Material.BARRIER && this.blockPlacements.containsKey(this.blockKey(block));
    }

    private Block block(PlacementStore.Placement placement) {
        return Objects.requireNonNull(this.location(placement)).getBlock();
    }

    private PlacementStore.Placement ensureAnchor(PlacementStore.Placement placement) throws IOException {
        Block block = this.block(placement);
        if (placement.anchorOriginal() == null) {
            if (!block.getType().isAir() || Math.abs(placement.y() - Math.floor(placement.y())) > 0.001) {
                return placement;
            }
            PlacementStore.Placement placement3 = placement.anchored(block.getBlockData().getAsString());
            List<PlacementStore.Placement> list = this.placements.values().stream().map(placement2 -> placement2.id().equals(placement3.id()) ? placement3 : placement2).toList();
            PlacementStore.write(this.placementFile, list);
            this.placements.put(placement.id(), placement3);
            this.indexBlock(placement3);
            placement = placement3;
        }
        if (block.getType() != Material.BARRIER) {
            if (!block.getType().isAir()) {
                throw new IOException("Anchor occupied by " + String.valueOf(block.getType()) + " at " + placement.worldName() + " " + block.getX() + " " + block.getY() + " " + block.getZ());
            }
            block.setType(Material.BARRIER, false);
        }
        return placement;
    }

    private void restoreAnchor(PlacementStore.Placement placement) {
        if (placement.anchorOriginal() == null) {
            return;
        }
        Block block = this.block(placement);
        if (block.getType() == Material.BARRIER) {
            block.setBlockData(Bukkit.createBlockData((String)placement.anchorOriginal()), false);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void reconcile() {
        if (this.shuttingDown || this.api == null) {
            return;
        }
        long l = System.currentTimeMillis();
        this.selections.values().removeIf(selection -> selection.expiresAt() < l);
        for (PlacementStore.Placement placement : new ArrayList<PlacementStore.Placement>(this.placements.values())) {
            RuntimeCrate runtimeCrate;
            PlacementStore.Placement placement2 = placement;
            if (this.loaded(placement2)) {
                try {
                    placement2 = this.ensureAnchor(placement2);
                }
                catch (IOException iOException) {
                    this.despawn(placement2.id());
                    this.report(placement2.id(), iOException);
                    continue;
                }
            }
            if (!((runtimeCrate = this.active.get(placement2.id())) == null || this.loaded(placement2) && runtimeCrate.base.isValid() && runtimeCrate.hitbox.isValid())) {
                this.despawn(placement2.id());
                runtimeCrate = null;
            }
            if (runtimeCrate == null && this.loaded(placement2) && l - this.lastFailure.getOrDefault(placement2.id(), 0L) >= 60000L) {
                try {
                    this.spawn(placement2);
                }
                catch (Exception | LinkageError throwable) {
                    this.report(placement2.id(), throwable);
                }
                continue;
            }
            if (runtimeCrate == null) continue;
            Location location = this.location(placement2);
            try {
                this.anchoring = true;
                for (Entity entity : List.of(runtimeCrate.base, runtimeCrate.hitbox)) {
                    entity.setVelocity(new Vector());
                    if (!(entity.getLocation().distanceSquared(location) > 1.0E-8)) continue;
                    entity.teleport(location);
                }
            }
            finally {
                this.anchoring = false;
            }
        }
    }

    private void spawn(PlacementStore.Placement placement) throws ReflectiveOperationException {
        if (this.active.containsKey(placement.id())) {
            return;
        }
        Preset preset = this.models.get(placement.modelId());
        if (preset == null) {
            throw new IllegalArgumentException("Unsupported crate model " + placement.modelId());
        }
        if (!this.api.hasModel(placement.modelId())) {
            throw new IllegalStateException("ModelEngine has not loaded " + placement.modelId());
        }
        Location location = Objects.requireNonNull(this.location(placement));
        ArmorStand armorStand2 = (ArmorStand)location.getWorld().spawn(location, ArmorStand.class, armorStand -> {
            this.tag((Entity)armorStand, placement.id());
            armorStand.setVisible(false);
            armorStand.setBasePlate(false);
            armorStand.setArms(false);
            armorStand.setMarker(true);
            armorStand.setGravity(false);
            armorStand.setCanMove(false);
            armorStand.setCanTick(true);
            armorStand.setInvulnerable(true);
            armorStand.setCollidable(false);
            armorStand.setSilent(true);
            armorStand.setPersistent(false);
            armorStand.setDisabledSlots(new EquipmentSlot[]{EquipmentSlot.HAND, EquipmentSlot.OFF_HAND, EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD});
        });
        Interaction interaction2 = null;
        PluginApis.ModelHandle modelHandle = null;
        try {
            modelHandle = this.api.attach((Entity)armorStand2, placement.modelId(), placement.yaw(), this.renderDistance);
            interaction2 = (Interaction)location.getWorld().spawn(location, Interaction.class, interaction -> {
                this.tag((Entity)interaction, placement.id());
                interaction.setGravity(false);
                interaction.setInvulnerable(true);
                interaction.setSilent(true);
                interaction.setPersistent(false);
                interaction.setResponsive(true);
                interaction.setInteractionWidth(preset.width());
                interaction.setInteractionHeight(preset.height());
            });
            if (!armorStand2.isValid() || !interaction2.isValid()) {
                throw new IllegalStateException("Another plugin blocked the crate entity spawn.");
            }
            RuntimeCrate runtimeCrate = new RuntimeCrate(armorStand2, interaction2, modelHandle);
            this.active.put(placement.id(), runtimeCrate);
            this.entityPlacements.put(armorStand2.getUniqueId(), placement.id());
            this.entityPlacements.put(interaction2.getUniqueId(), placement.id());
            this.lastFailure.remove(placement.id());
            if (preset.idle()) {
                Bukkit.getScheduler().runTaskLater((Plugin)this, () -> {
                    if (this.active.get(placement.id()) == runtimeCrate) {
                        this.play(runtimeCrate, "idle", placement.id());
                    }
                }, 2L);
            }
        }
        catch (LinkageError | ReflectiveOperationException | RuntimeException throwable) {
            if (interaction2 != null) {
                interaction2.remove();
            }
            if (modelHandle != null) {
                try {
                    this.api.detach(modelHandle);
                }
                catch (ReflectiveOperationException reflectiveOperationException) {
                    throwable.addSuppressed(reflectiveOperationException);
                }
            }
            armorStand2.remove();
            throw throwable;
        }
    }

    private void tag(Entity entity, String string) {
        entity.getPersistentDataContainer().set(this.ownerKey, PersistentDataType.STRING, string);
    }

    private boolean owned(Entity entity) {
        return entity.getPersistentDataContainer().has(this.ownerKey, PersistentDataType.STRING);
    }

    private void despawn(String string) {
        RuntimeCrate runtimeCrate = this.active.remove(string);
        this.animationGenerations.remove(string);
        if (runtimeCrate == null) {
            return;
        }
        this.entityPlacements.remove(runtimeCrate.base.getUniqueId());
        this.entityPlacements.remove(runtimeCrate.hitbox.getUniqueId());
        try {
            if (this.api != null) {
                this.api.detach(runtimeCrate.model);
            }
        }
        catch (LinkageError | ReflectiveOperationException throwable) {
            this.getLogger().warning("Model cleanup failed for " + string + ": " + throwable.getMessage());
        }
        runtimeCrate.hitbox.remove();
        runtimeCrate.base.remove();
    }

    private void removeOrphans(Entity[] entityArray) {
        for (Entity entity : entityArray) {
            if (!this.owned(entity) || this.entityPlacements.containsKey(entity.getUniqueId())) continue;
            entity.remove();
        }
    }

    private void interact(Player player, String string, boolean bl) {
        if (this.shuttingDown || !player.isOnline()) {
            return;
        }
        PlacementStore.Placement placement = this.placements.get(string);
        if (placement == null || !this.active.containsKey(string)) {
            return;
        }
        Location location = this.location(placement);
        if (location == null || !player.getWorld().equals((Object)location.getWorld()) || player.getEyeLocation().distanceSquared(location.clone().add(0.0, 0.8, 0.0)) > 49.0) {
            return;
        }
        long l = System.currentTimeMillis();
        if (l - this.lastClick.getOrDefault(player.getUniqueId(), 0L) < 350L) {
            return;
        }
        this.lastClick.put(player.getUniqueId(), l);
        try {
            if (!bl && placement.anchorOriginal() != null) {
                Block block = this.block(placement);
                if (!this.protectedBlock(block)) {
                    return;
                }
                if (!CrateInteraction.allowed(player, block, player.getInventory().getItemInMainHand(), (Plugin)this, Bukkit.getPluginManager().getPlugin("ExcellentCrates"))) {
                    this.selections.remove(player.getUniqueId());
                    player.sendMessage("\u00a7dTowny\u00a7aReborn \u00a78| \u00a7f\u00a7cCrate interaction is blocked here. Please tell a staff member.");
                    return;
                }
            }
            if (!bl) {
                this.selections.put(player.getUniqueId(), new Selection(string, placement.crateId(), l + 300000L));
            }
            this.api.interact(player, placement.crateId(), bl, location);
        }
        catch (Exception | LinkageError throwable) {
            this.selections.remove(player.getUniqueId());
            player.sendMessage("\u00a7dTowny\u00a7aReborn \u00a78| \u00a7f\u00a7cThis crate is unavailable. Please tell a staff member.");
            this.report(string, throwable);
        }
    }

    private void queueInteraction(Player player, String string, boolean bl) {
        if (this.shuttingDown || string == null || !this.active.containsKey(string)) {
            return;
        }
        UUID uUID = player.getUniqueId();
        if (!this.queuedClicks.queue(uUID)) {
            return;
        }
        Bukkit.getScheduler().runTask((Plugin)this, () -> {
            if (this.queuedClicks.take(uUID)) {
                this.interact(player, string, bl);
            }
        });
    }

    private void modelInteraction(Event event) {
        try {
            UUID uUID = this.api.modelBaseUuid(event);
            String string = this.api.modelAction(event);
            if (!"ATTACK".equals(string) && this.api.modelSlot(event) != EquipmentSlot.HAND) {
                return;
            }
            Player player = this.api.modelPlayer(event);
            Runnable runnable = () -> {
                String string2 = this.entityPlacements.get(uUID);
                if (string2 != null) {
                    this.queueInteraction(player, string2, "ATTACK".equals(string));
                }
            };
            if (event.isAsynchronous() || !Bukkit.isPrimaryThread()) {
                Bukkit.getScheduler().runTask((Plugin)this, runnable);
            } else {
                runnable.run();
            }
        }
        catch (ReflectiveOperationException reflectiveOperationException) {
            this.report("model-interaction", reflectiveOperationException);
        }
    }

    private void opening(Event event) {
        if (event instanceof Cancellable cancelled && cancelled.isCancelled()) {
            return;
        }
        try {
            Location location;
            Player openingPlayer = this.api.openingPlayer(event);
            Selection selection = this.selections.get(openingPlayer.getUniqueId());
            if (selection == null || selection.expiresAt() < System.currentTimeMillis() || !selection.crate().equals(this.api.openingCrateId(event))) {
                return;
            }
            PlacementStore.Placement placement = this.placements.get(selection.placement());
            RuntimeCrate runtimeCrate = this.active.get(selection.placement());
            Location location2 = location = placement == null ? null : this.location(placement);
            if (runtimeCrate == null || location == null || !openingPlayer.getWorld().equals((Object)location.getWorld()) || openingPlayer.getLocation().distanceSquared(location) > 64.0) {
                return;
            }
            this.animate(placement, runtimeCrate);
        }
        catch (ReflectiveOperationException reflectiveOperationException) {
            this.report("crate-opening", reflectiveOperationException);
        }
    }

    private void animate(PlacementStore.Placement placement, RuntimeCrate runtimeCrate) throws ReflectiveOperationException {
        Preset preset = this.models.get(placement.modelId());
        if (preset.idle()) {
            this.api.stop(runtimeCrate.model, "idle");
        }
        this.play(runtimeCrate, "open", placement.id());
        long l = ++this.animationSequence;
        this.animationGenerations.put(placement.id(), l);
        Bukkit.getScheduler().runTaskLater((Plugin)this, () -> {
            if (this.active.get(placement.id()) != runtimeCrate || !Long.valueOf(l).equals(this.animationGenerations.get(placement.id()))) {
                return;
            }
            try {
                this.api.stop(runtimeCrate.model, "open");
                if (preset.idle()) {
                    this.play(runtimeCrate, "idle", placement.id());
                }
                this.animationGenerations.remove(placement.id());
            }
            catch (ReflectiveOperationException reflectiveOperationException) {
                this.report(placement.id(), reflectiveOperationException);
            }
        }, preset.openTicks());
    }

    private void play(RuntimeCrate runtimeCrate, String string, String string2) {
        try {
            if (!this.api.play(runtimeCrate.model, string)) {
                this.getLogger().warning("Missing animation " + string + " for " + string2);
            }
        }
        catch (LinkageError | ReflectiveOperationException throwable) {
            this.report(string2, throwable);
        }
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void selectBlock(PlayerInteractEvent playerInteractEvent) {
        Block block = playerInteractEvent.getClickedBlock();
        if (block == null || !this.protectedBlock(block) || playerInteractEvent.getHand() != EquipmentSlot.HAND || playerInteractEvent.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        String string = this.blockPlacements.get(this.blockKey(block));
        PlacementStore.Placement placement = this.placements.get(string);
        this.selections.put(playerInteractEvent.getPlayer().getUniqueId(), new Selection(string, placement.crateId(), System.currentTimeMillis() + 300000L));
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void clickBlock(PlayerInteractEvent playerInteractEvent) {
        Block block = playerInteractEvent.getClickedBlock();
        if (block == null || !this.protectedBlock(block)) {
            return;
        }
        playerInteractEvent.setCancelled(true);
        if (playerInteractEvent.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (playerInteractEvent.getAction() != Action.RIGHT_CLICK_BLOCK && playerInteractEvent.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        try {
            if (this.api.blockCrateId(block) != null || this.api.holdingCrate(playerInteractEvent.getItem())) {
                this.lastClick.put(playerInteractEvent.getPlayer().getUniqueId(), System.currentTimeMillis());
                return;
            }
            this.interact(playerInteractEvent.getPlayer(), this.blockPlacements.get(this.blockKey(block)), playerInteractEvent.getAction() == Action.LEFT_CLICK_BLOCK);
        }
        catch (ReflectiveOperationException reflectiveOperationException) {
            this.report("block-interaction", reflectiveOperationException);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void breakBlock(BlockBreakEvent blockBreakEvent) {
        if (this.protectedBlock(blockBreakEvent.getBlock())) {
            blockBreakEvent.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void replaceBlock(BlockPlaceEvent blockPlaceEvent) {
        if (this.blockPlacements.containsKey(this.blockKey(blockPlaceEvent.getBlock())) && blockPlaceEvent.getBlockReplacedState().getType() == Material.BARRIER) {
            blockPlaceEvent.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void explode(EntityExplodeEvent entityExplodeEvent) {
        entityExplodeEvent.blockList().removeIf(this::protectedBlock);
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void explode(BlockExplodeEvent blockExplodeEvent) {
        blockExplodeEvent.blockList().removeIf(this::protectedBlock);
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void piston(BlockPistonExtendEvent blockPistonExtendEvent) {
        if (blockPistonExtendEvent.getBlocks().stream().anyMatch(block -> this.protectedBlock((Block)block) || this.protectedBlock(block.getRelative(blockPistonExtendEvent.getDirection())))) {
            blockPistonExtendEvent.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void piston(BlockPistonRetractEvent blockPistonRetractEvent) {
        if (blockPistonRetractEvent.getBlocks().stream().anyMatch(block -> this.protectedBlock((Block)block) || this.protectedBlock(block.getRelative(blockPistonRetractEvent.getDirection())))) {
            blockPistonRetractEvent.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void rightClick(PlayerInteractEntityEvent playerInteractEntityEvent) {
        if (!this.owned(playerInteractEntityEvent.getRightClicked())) {
            return;
        }
        boolean bl = playerInteractEntityEvent.isCancelled();
        playerInteractEntityEvent.setCancelled(true);
        String string = this.entityPlacements.get(playerInteractEntityEvent.getRightClicked().getUniqueId());
        PlacementStore.Placement placement = this.placements.get(string);
        if (!(playerInteractEntityEvent.getHand() != EquipmentSlot.HAND || placement == null || bl && placement.anchorOriginal() == null)) {
            this.queueInteraction(playerInteractEntityEvent.getPlayer(), string, false);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void rightClickAt(PlayerInteractAtEntityEvent playerInteractAtEntityEvent) {
        if (!this.owned(playerInteractAtEntityEvent.getRightClicked())) {
            return;
        }
        boolean bl = playerInteractAtEntityEvent.isCancelled();
        playerInteractAtEntityEvent.setCancelled(true);
        String string = this.entityPlacements.get(playerInteractAtEntityEvent.getRightClicked().getUniqueId());
        PlacementStore.Placement placement = this.placements.get(string);
        if (!(playerInteractAtEntityEvent.getHand() != EquipmentSlot.HAND || placement == null || bl && placement.anchorOriginal() == null)) {
            this.queueInteraction(playerInteractAtEntityEvent.getPlayer(), string, false);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void leftClick(PrePlayerAttackEntityEvent prePlayerAttackEntityEvent) {
        if (!this.owned(prePlayerAttackEntityEvent.getAttacked())) {
            return;
        }
        boolean bl = TownyRebornCrateModels.canPreviewAttack(prePlayerAttackEntityEvent);
        prePlayerAttackEntityEvent.setCancelled(true);
        if (bl) {
            this.interact(prePlayerAttackEntityEvent.getPlayer(), this.entityPlacements.get(prePlayerAttackEntityEvent.getAttacked().getUniqueId()), true);
        }
    }

    static boolean canPreviewAttack(PrePlayerAttackEntityEvent prePlayerAttackEntityEvent) {
        return !prePlayerAttackEntityEvent.willAttack() || !prePlayerAttackEntityEvent.isCancelled();
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void damage(EntityDamageEvent entityDamageEvent) {
        if (this.owned(entityDamageEvent.getEntity())) {
            entityDamageEvent.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void manipulate(PlayerArmorStandManipulateEvent playerArmorStandManipulateEvent) {
        if (this.owned((Entity)playerArmorStandManipulateEvent.getRightClicked())) {
            playerArmorStandManipulateEvent.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void teleport(EntityTeleportEvent entityTeleportEvent) {
        if (!this.anchoring && this.owned(entityTeleportEvent.getEntity())) {
            entityTeleportEvent.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void portal(EntityPortalEvent entityPortalEvent) {
        if (this.owned(entityPortalEvent.getEntity())) {
            entityPortalEvent.setCancelled(true);
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent playerQuitEvent) {
        UUID uUID = playerQuitEvent.getPlayer().getUniqueId();
        this.selections.remove(uUID);
        this.lastClick.remove(uUID);
        this.queuedClicks.take(uUID);
    }

    @EventHandler
    public void entitiesLoad(EntitiesLoadEvent entitiesLoadEvent) {
        if (this.shuttingDown) {
            return;
        }
        this.removeOrphans((Entity[])entitiesLoadEvent.getEntities().toArray(Entity[]::new));
        Bukkit.getScheduler().runTask((Plugin)this, this::reconcile);
    }

    @EventHandler
    public void entitiesUnload(EntitiesUnloadEvent entitiesUnloadEvent) {
        for (PlacementStore.Placement placement : this.placements.values()) {
            if (!placement.worldId().equals(entitiesUnloadEvent.getWorld().getUID()) || placement.chunkX() != entitiesUnloadEvent.getChunk().getX() || placement.chunkZ() != entitiesUnloadEvent.getChunk().getZ()) continue;
            this.despawn(placement.id());
        }
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void chunkUnload(ChunkUnloadEvent chunkUnloadEvent) {
        for (PlacementStore.Placement placement : this.placements.values()) {
            if (!placement.worldId().equals(chunkUnloadEvent.getWorld().getUID()) || placement.chunkX() != chunkUnloadEvent.getChunk().getX() || placement.chunkZ() != chunkUnloadEvent.getChunk().getZ()) continue;
            this.despawn(placement.id());
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void worldUnload(WorldUnloadEvent worldUnloadEvent) {
        for (PlacementStore.Placement placement : this.placements.values()) {
            if (!placement.worldId().equals(worldUnloadEvent.getWorld().getUID())) continue;
            this.despawn(placement.id());
        }
    }

    private void report(String string, Throwable throwable) {
        long l = System.currentTimeMillis();
        if (l - this.lastFailure.getOrDefault(string, 0L) >= 60000L) {
            this.getLogger().log(Level.WARNING, "Crate model " + string + ": " + throwable.getMessage(), throwable);
            this.lastFailure.put(string, l);
        }
    }

    public boolean onCommand(CommandSender commandSender, Command command, String string, String[] stringArray) {
        if (!commandSender.hasPermission(ADMIN)) {
            commandSender.sendMessage("\u00a7dTowny\u00a7aReborn \u00a78| \u00a7f\u00a7cYou do not have permission.");
            return true;
        }
        try {
            if (stringArray.length == 0) {
                this.help(commandSender);
                return true;
            }
            switch (stringArray[0].toLowerCase(Locale.ROOT)) {
                case "place": {
                    if (!(commandSender instanceof Player)) {
                        commandSender.sendMessage("\u00a7dTowny\u00a7aReborn \u00a78| \u00a7fUse this command in-game at the crate position.");
                        return true;
                    }
                    Player player = (Player)commandSender;
                    if (stringArray.length != 3) {
                        this.help(commandSender);
                        return true;
                    }
                    String string2 = stringArray[1];
                    String string3 = stringArray[2];
                    if (this.api.crate(string2) == null) {
                        commandSender.sendMessage("\u00a7dTowny\u00a7aReborn \u00a78| \u00a7f\u00a7cUnknown crate. Available: \u00a7f" + String.join((CharSequence)", ", this.api.crateIds()));
                        return true;
                    }
                    if (!this.models.containsKey(string3) || !this.api.hasModel(string3)) {
                        commandSender.sendMessage("\u00a7dTowny\u00a7aReborn \u00a78| \u00a7f\u00a7cModel is not loaded. Use /trcrate models.");
                        return true;
                    }
                    Location location = player.getLocation();
                    double d = (double)location.getBlockX() + 0.5;
                    double d2 = location.getBlockY();
                    double d3 = (double)location.getBlockZ() + 0.5;
                    Block block = location.getBlock();
                    if (!block.getType().isAir() || Math.abs(location.getY() - d2) > 0.001) {
                        commandSender.sendMessage("\u00a7dTowny\u00a7aReborn \u00a78| \u00a7f\u00a7cStand on a full block with empty space above it. This position is occupied or sits on a partial block.");
                        return true;
                    }
                    for (PlacementStore.Placement placement : this.placements.values()) {
                        if (!placement.worldId().equals(location.getWorld().getUID()) || !(Math.abs(placement.x() - d) < 0.1) || !(Math.abs(placement.y() - location.getY()) < 0.5) || !(Math.abs(placement.z() - d3) < 0.1)) continue;
                        commandSender.sendMessage("\u00a7dTowny\u00a7aReborn \u00a78| \u00a7f\u00a7cA model already exists here: \u00a7f" + placement.id());
                        return true;
                    }
                    String string4 = string2.toLowerCase(Locale.ROOT);
                    int n = 1;
                    while (this.placements.containsKey((String)string4 + "-" + n)) {
                        ++n;
                    }
                    float f = ((float)Math.round(location.getYaw() / 90.0f) * 90.0f + 180.0f) % 360.0f;
                    PlacementStore.Placement placement = new PlacementStore.Placement((String)string4 + "-" + n, string2, string3, location.getWorld().getUID(), location.getWorld().getName(), d, d2, d3, f, block.getBlockData().getAsString());
                    ArrayList<PlacementStore.Placement> arrayList = new ArrayList<PlacementStore.Placement>(this.placements.values());
                    ArrayList<PlacementStore.Placement> arrayList2 = new ArrayList<PlacementStore.Placement>(arrayList);
                    arrayList2.add(placement);
                    PlacementStore.write(this.placementFile, arrayList2);
                    this.placements.put(placement.id(), placement);
                    this.indexBlock(placement);
                    try {
                        this.ensureAnchor(placement);
                        this.spawn(placement);
                    }
                    catch (Exception | LinkageError throwable) {
                        try {
                            this.restoreAnchor(placement);
                            this.despawn(placement.id());
                            PlacementStore.write(this.placementFile, arrayList);
                            this.placements.remove(placement.id());
                            this.blockPlacements.remove(this.blockKey(placement));
                        }
                        catch (Exception exception) {
                            throwable.addSuppressed(exception);
                        }
                        throw throwable;
                    }
                    player.teleport(new Location(location.getWorld(), d, d2 + 1.0, d3, location.getYaw(), location.getPitch()));
                    commandSender.sendMessage("\u00a7dTowny\u00a7aReborn \u00a78| \u00a7f\u00a7aPlaced solid crate \u00a7f" + placement.id() + "\u00a7a. Right-click to open, left-click to preview.");
                    break;
                }
                case "remove": {
                    if (stringArray.length != 2) {
                        this.help(commandSender);
                        return true;
                    }
                    PlacementStore.Placement placement = this.placements.get(stringArray[1]);
                    if (placement == null) {
                        commandSender.sendMessage("\u00a7dTowny\u00a7aReborn \u00a78| \u00a7f\u00a7cUnknown placement. Use /trcrate list.");
                        return true;
                    }
                    if (this.location(placement) == null) {
                        commandSender.sendMessage("\u00a7dTowny\u00a7aReborn \u00a78| \u00a7f\u00a7cLoad " + placement.worldName() + " first so its block can be restored.");
                        return true;
                    }
                    List<PlacementStore.Placement> list = this.placements.values().stream().filter(placement2 -> !placement2.id().equals(placement.id())).toList();
                    this.restoreAnchor(placement);
                    PlacementStore.write(this.placementFile, list);
                    this.placements.remove(placement.id());
                    this.despawn(placement.id());
                    this.blockPlacements.remove(this.blockKey(placement));
                    this.selections.values().removeIf(selection -> selection.placement().equals(placement.id()));
                    commandSender.sendMessage("\u00a7dTowny\u00a7aReborn \u00a78| \u00a7f\u00a7aRemoved \u00a7f" + placement.id());
                    break;
                }
                case "list": {
                    commandSender.sendMessage(PREFIX + this.placements.size() + " saved crate models:");
                    for (PlacementStore.Placement placement : this.placements.values()) {
                        commandSender.sendMessage("\u00a7a" + placement.id() + " \u00a77| " + placement.crateId() + " | " + placement.modelId() + " | " + placement.worldName() + " " + placement.x() + " " + placement.y() + " " + placement.z() + (placement.anchorOriginal() != null ? " [block]" : " [legacy decoration]") + (this.active.containsKey(placement.id()) ? " \u00a7a[loaded]" : " \u00a77[waiting]"));
                    }
                    break;
                }
                case "models": {
                    for (String string5 : this.models.keySet()) {
                        commandSender.sendMessage("\u00a7dTowny\u00a7aReborn \u00a78| \u00a7f\u00a7a" + string5 + (this.api.hasModel(string5) ? " \u00a77[loaded]" : " \u00a7c[not loaded]"));
                    }
                    break;
                }
                case "status": {
                    commandSender.sendMessage("\u00a7dTowny\u00a7aReborn \u00a78| \u00a7fCrateModels " + this.getDescription().getVersion() + " | ModelEngine " + Bukkit.getPluginManager().getPlugin("ModelEngine").getDescription().getVersion() + " | ExcellentCrates " + Bukkit.getPluginManager().getPlugin("ExcellentCrates").getDescription().getVersion());
                    commandSender.sendMessage("\u00a7dTowny\u00a7aReborn \u00a78| \u00a7fSaved: " + this.placements.size() + " | Active: " + this.active.size() + " | Block anchors: " + this.blockPlacements.size() + " | Render radius: " + this.renderDistance + " blocks | Native key checks enabled.");
                    break;
                }
                case "animate": {
                    if (stringArray.length != 2) {
                        this.help(commandSender);
                        return true;
                    }
                    PlacementStore.Placement placement = this.placements.get(stringArray[1]);
                    RuntimeCrate runtimeCrate = this.active.get(stringArray[1]);
                    if (placement == null || runtimeCrate == null) {
                        commandSender.sendMessage("\u00a7dTowny\u00a7aReborn \u00a78| \u00a7f\u00a7cThat placement is not currently loaded.");
                        return true;
                    }
                    this.animate(placement, runtimeCrate);
                    commandSender.sendMessage("\u00a7dTowny\u00a7aReborn \u00a78| \u00a7f\u00a7aPlaying the model animation. No keys or rewards are used.");
                    break;
                }
                case "reload": {
                    List<PlacementStore.Placement> list = PlacementStore.read(this.placementFile);
                    for (String object : new ArrayList<String>(this.active.keySet())) {
                        this.despawn(object);
                    }
                    this.placements.clear();
                    this.blockPlacements.clear();
                    this.selections.clear();
                    this.lastFailure.clear();
                    this.queuedClicks.clear();
                    this.lastClick.clear();
                    this.reloadConfig();
                    this.renderDistance = Math.clamp((long)this.getConfig().getInt("render-distance", 96), (int)32, (int)160);
                    for (PlacementStore.Placement placement : list) {
                        this.placements.put(placement.id(), placement);
                        this.indexBlock(placement);
                    }
                    this.reconcile();
                    commandSender.sendMessage("\u00a7dTowny\u00a7aReborn \u00a78| \u00a7f\u00a7aReloaded " + this.placements.size() + " saved placements.");
                    break;
                }
                default: {
                    this.help(commandSender);
                }
            }
        }
        catch (Exception | LinkageError throwable) {
            commandSender.sendMessage("\u00a7dTowny\u00a7aReborn \u00a78| \u00a7f\u00a7cCould not complete that action. Check the server log.");
            this.getLogger().log(Level.WARNING, "Crate model command failed", throwable);
        }
        return true;
    }

    private void help(CommandSender commandSender) {
        commandSender.sendMessage("\u00a7dTowny\u00a7aReborn \u00a78| \u00a7f/trcrate place <crate> <model>");
        commandSender.sendMessage("\u00a7a/trcrate \u00a7fremove <placement> | animate <placement> | list | models | status | reload");
    }

    public List<String> onTabComplete(CommandSender commandSender, Command command, String string, String[] stringArray) {
        if (!commandSender.hasPermission(ADMIN)) {
            return List.of();
        }
        try {
            List<String> list = switch (stringArray.length) {
                case 1 -> List.of("place", "remove", "animate", "list", "models", "status", "reload");
                case 2 -> {
                    if (stringArray[0].equalsIgnoreCase("place")) {
                        yield this.api.crateIds();
                    }
                    if (List.of("remove", "animate").contains(stringArray[0].toLowerCase(Locale.ROOT))) {
                        yield new ArrayList<String>(this.placements.keySet());
                    }
                    yield List.of();
                }
                case 3 -> {
                    if (stringArray[0].equalsIgnoreCase("place")) {
                        yield new ArrayList<String>(this.models.keySet());
                    }
                    yield List.of();
                }
                default -> List.of();
            };
            String string3 = stringArray.length == 0 ? "" : stringArray[stringArray.length - 1].toLowerCase(Locale.ROOT);
            return list.stream().filter(string2 -> string2.toLowerCase(Locale.ROOT).startsWith(string3)).sorted().toList();
        }
        catch (ReflectiveOperationException reflectiveOperationException) {
            return List.of();
        }
    }

    private record Preset(float width, float height, long openTicks, boolean idle) {
    }

    private record BlockKey(UUID world, int x, int y, int z) {
    }

    private record RuntimeCrate(ArmorStand base, Interaction hitbox, PluginApis.ModelHandle model) {
    }

    private record Selection(String placement, String crate, long expiresAt) {
    }
}

