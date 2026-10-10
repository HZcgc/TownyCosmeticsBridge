package com.townyreborn.hunt;

import com.townyreborn.hunt.AtomicFiles;
import com.townyreborn.hunt.ClaimService;
import com.townyreborn.hunt.CrateBridge;
import com.townyreborn.hunt.HeadIndex;
import com.townyreborn.hunt.HuntLedger;
import com.townyreborn.hunt.HuntProgress;
import com.townyreborn.hunt.PlaceholderSupport;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class TownyRebornHunt
extends JavaPlugin
implements Listener {
    private final Set<UUID> setup = new HashSet<UUID>();
    private final Map<UUID, Long> noticeAt = new HashMap<UUID, Long>();
    private HeadIndex heads;
    private HuntLedger ledger;
    private volatile HuntProgress progress;
    private Runnable stopPlaceholders = () -> {};
    private ClaimService claims;
    private CrateBridge crates;
    private String eventId;
    private String keyId;
    private int maximum;
    private long lastErrorLog;

    public void onEnable() {
        this.saveDefaultConfig();
        try {
            LegacyCrateMigration.run(Bukkit.getPluginManager().getPlugin("ExcellentCrates"), this.getLogger());
            this.crates = new CrateBridge(Bukkit.getPluginManager().getPlugin("ExcellentCrates"));
            this.loadHunt();
            Bukkit.getPluginManager().registerEvents((Listener)this, (Plugin)this);
            this.registerPlaceholders();
            this.getLogger().info("Loaded " + this.heads.activeCount() + "/" + this.maximum + " ghosts for " + this.eventId + ". Reward: 1 virtual " + this.keyId + " key per ghost.");
            Bukkit.getScheduler().runTaskLater((Plugin)this, () -> {
                try {
                    this.crates.check(this.keyId);
                    this.getLogger().info("ExcellentCrates key integration is ready. Use /hunt setup or /hunt add.");
                }
                catch (ReflectiveOperationException | RuntimeException exception) {
                    this.getLogger().warning("Rewards are not ready yet: " + this.reason(exception) + ". Use /hunt check.");
                }
            }, 40L);
        }
        catch (IOException | ReflectiveOperationException | RuntimeException exception) {
            this.getLogger().log(Level.SEVERE, "Could not start the hunt. Existing player data was not overwritten.", exception);
            Bukkit.getPluginManager().disablePlugin((Plugin)this);
        }
    }

    private void loadHunt() throws IOException {
        String string = AtomicFiles.id(this.getConfig().getString("event-id", "halloween2026"));
        String string2 = AtomicFiles.id(this.getConfig().getString("key-id", "halloween2026"));
        int n = this.getConfig().getInt("max-finds", 44);
        if (n < 1 || n > 1000) {
            throw new IllegalArgumentException("max-finds must be between 1 and 1000.");
        }
        HeadIndex headIndex = new HeadIndex(this.getDataFolder().toPath(), string);
        HuntLedger huntLedger = new HuntLedger(this.getDataFolder().toPath(), string);
        this.eventId = string;
        this.keyId = string2;
        this.maximum = n;
        this.heads = headIndex;
        this.ledger = huntLedger;
        this.progress = new HuntProgress(this.ledger, this.maximum);
        this.claims = new ClaimService(this.ledger);
        this.setup.clear();
    }

    private void registerPlaceholders() {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            this.getLogger().info("PlaceholderAPI is not enabled; /hunt works, but hologram progress needs PlaceholderAPI.");
            return;
        }
        this.stopPlaceholders = PlaceholderSupport.register(() -> this.progress, this.getDescription().getVersion(), iOException -> {
            long l = System.currentTimeMillis();
            if (l - this.lastErrorLog > 10000L) {
                this.lastErrorLog = l;
                this.getLogger().log(Level.WARNING, "Could not read hologram hunt progress. Existing data has been retained.", (Throwable)iOException);
            }
        }, this.getLogger());
    }

    public void onDisable() {
        this.stopPlaceholders.run();
    }

    static boolean acceptedClick(Action action, EquipmentSlot equipmentSlot) {
        return action == Action.RIGHT_CLICK_BLOCK && equipmentSlot == EquipmentSlot.HAND;
    }

    private static boolean skull(Block block) {
        return block != null && (block.getType() == Material.PLAYER_HEAD || block.getType() == Material.PLAYER_WALL_HEAD);
    }

    private HeadIndex.Position position(Block block) {
        return new HeadIndex.Position(block.getWorld().getUID(), block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    private HeadIndex.Position support(Block block) {
        BlockData blockData;
        BlockFace blockFace = BlockFace.DOWN;
        if (block.getType() == Material.PLAYER_WALL_HEAD && (blockData = block.getBlockData()) instanceof Directional) {
            Directional directional = (Directional)blockData;
            blockFace = directional.getFacing().getOppositeFace();
        }
        return this.position(block.getRelative(blockFace));
    }

    private boolean protectedBlock(Block block) {
        return block != null && this.heads.protectedAt(this.position(block));
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onInteract(PlayerInteractEvent playerInteractEvent) {
        if (!TownyRebornHunt.acceptedClick(playerInteractEvent.getAction(), playerInteractEvent.getHand())) {
            return;
        }
        Block block = playerInteractEvent.getClickedBlock();
        if (!TownyRebornHunt.skull(block)) {
            return;
        }
        HeadIndex.Head head = this.heads.at(this.position(block));
        if (head == null) {
            return;
        }
        playerInteractEvent.setUseInteractedBlock(Event.Result.DENY);
        playerInteractEvent.setUseItemInHand(Event.Result.DENY);
        Player player = playerInteractEvent.getPlayer();
        if (!this.getConfig().getBoolean("enabled", true)) {
            this.notice(player, "paused", new Object[0]);
            return;
        }
        if (!player.hasPermission("townyreborn.hunt.play")) {
            this.notice(player, "no-permission", new Object[0]);
            return;
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (this.setup.contains(player.getUniqueId())) {
            this.tell((CommandSender)player, "setup-inspect", "id", head.id());
            return;
        }
        ClaimService.Result result = this.claims.claim(player.getUniqueId(), player.getName(), head.id(), this.maximum, this.keyId, () -> this.crates.prepare(player, this.keyId));
        switch (result.kind()) {
            case FOUND: {
                this.progress.invalidate(player.getUniqueId());
                this.tell((CommandSender)player, "found", "found", result.found(), "total", this.maximum, "id", head.id());
                player.sendActionBar(this.text("&dGhosts found: &a" + result.found() + "&7/&a" + this.maximum));
                if (this.getConfig().getBoolean("sound.enabled", true)) {
                    player.playSound(player.getLocation(), this.getConfig().getString("sound.found", "entity.experience_orb.pickup"), 0.8f, 1.2f);
                }
                if (result.found() != this.maximum) break;
                this.tell((CommandSender)player, "complete", "total", this.maximum);
                if (!this.getConfig().getBoolean("completion-title", true)) break;
                player.showTitle(Title.title((Component)this.text("&dHALLOWEEN &aHUNT"), (Component)this.text("&aAll " + this.maximum + " ghosts found!")));
                break;
            }
            case ALREADY_FOUND: {
                this.notice(player, "already-found", new Object[0]);
                break;
            }
            case LIMIT: {
                this.notice(player, "limit", "total", this.maximum);
                break;
            }
            case PENDING: {
                this.notice(player, "pending", new Object[0]);
                break;
            }
            case UNAVAILABLE: 
            case STORAGE_ERROR: {
                this.notice(player, "unavailable", new Object[0]);
            }
        }
        if (result.error() != null && System.currentTimeMillis() - this.lastErrorLog > 5000L) {
            this.lastErrorLog = System.currentTimeMillis();
            this.getLogger().log(Level.WARNING, "Hunt reward " + String.valueOf((Object)result.kind()) + " for " + player.getName() + " (" + String.valueOf(player.getUniqueId()) + "), ghost " + head.id() + ": " + this.reason(result.error()), result.error());
        }
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onPlace(BlockPlaceEvent blockPlaceEvent) {
        Player player = blockPlaceEvent.getPlayer();
        if (!(this.setup.contains(player.getUniqueId()) && player.hasPermission("townyreborn.hunt.admin") && TownyRebornHunt.skull(blockPlaceEvent.getBlockPlaced()))) {
            return;
        }
        Block block = blockPlaceEvent.getBlockPlaced();
        Bukkit.getScheduler().runTask((Plugin)this, () -> {
            if (!(player.isOnline() && this.setup.contains(player.getUniqueId()) && TownyRebornHunt.skull(block))) {
                return;
            }
            try {
                this.register((CommandSender)player, block, null);
            }
            catch (IOException | RuntimeException exception) {
                this.adminError((CommandSender)player, exception);
            }
        });
    }

    private void register(CommandSender commandSender, Block block, String string) throws IOException {
        if (!TownyRebornHunt.skull(block)) {
            throw new IllegalArgumentException("Look at a placed HDB player head within 5 blocks.");
        }
        HeadIndex.Head head = this.heads.add(string, this.position(block), this.support(block), this.maximum);
        this.tell(commandSender, "registered", "id", head.id(), "active", this.heads.activeCount(), "total", this.maximum);
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onBreak(BlockBreakEvent blockBreakEvent) {
        if (!this.protectedBlock(blockBreakEvent.getBlock())) {
            return;
        }
        blockBreakEvent.setCancelled(true);
        if (blockBreakEvent.getPlayer().hasPermission("townyreborn.hunt.admin")) {
            this.notice(blockBreakEvent.getPlayer(), "protected-admin", new Object[0]);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onEntityExplode(EntityExplodeEvent entityExplodeEvent) {
        entityExplodeEvent.blockList().removeIf(this::protectedBlock);
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onBlockExplode(BlockExplodeEvent blockExplodeEvent) {
        blockExplodeEvent.blockList().removeIf(this::protectedBlock);
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onPhysics(BlockPhysicsEvent blockPhysicsEvent) {
        if (this.heads.at(this.position(blockPhysicsEvent.getBlock())) != null) {
            blockPhysicsEvent.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onBurn(BlockBurnEvent blockBurnEvent) {
        if (this.protectedBlock(blockBurnEvent.getBlock())) {
            blockBurnEvent.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onFade(BlockFadeEvent blockFadeEvent) {
        if (this.protectedBlock(blockFadeEvent.getBlock())) {
            blockFadeEvent.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onFlow(BlockFromToEvent blockFromToEvent) {
        if (this.protectedBlock(blockFromToEvent.getToBlock())) {
            blockFromToEvent.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onChange(EntityChangeBlockEvent entityChangeBlockEvent) {
        if (this.protectedBlock(entityChangeBlockEvent.getBlock())) {
            entityChangeBlockEvent.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onTree(StructureGrowEvent structureGrowEvent) {
        if (structureGrowEvent.getBlocks().stream().anyMatch(blockState -> this.protectedBlock(blockState.getBlock()))) {
            structureGrowEvent.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onFertilize(BlockFertilizeEvent blockFertilizeEvent) {
        if (blockFertilizeEvent.getBlocks().stream().anyMatch(blockState -> this.protectedBlock(blockState.getBlock()))) {
            blockFertilizeEvent.setCancelled(true);
        }
    }

    private boolean pistonTouches(Block block, List<Block> list, BlockFace blockFace) {
        if (this.protectedBlock(block)) {
            return true;
        }
        for (Block block2 : list) {
            if (!this.protectedBlock(block2) && !this.protectedBlock(block2.getRelative(blockFace)) && !this.protectedBlock(block2.getRelative(blockFace.getOppositeFace()))) continue;
            return true;
        }
        return false;
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onExtend(BlockPistonExtendEvent blockPistonExtendEvent) {
        if (this.pistonTouches(blockPistonExtendEvent.getBlock(), blockPistonExtendEvent.getBlocks(), blockPistonExtendEvent.getDirection())) {
            blockPistonExtendEvent.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onRetract(BlockPistonRetractEvent blockPistonRetractEvent) {
        if (this.pistonTouches(blockPistonRetractEvent.getBlock(), blockPistonRetractEvent.getBlocks(), blockPistonRetractEvent.getDirection())) {
            blockPistonRetractEvent.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent playerQuitEvent) {
        this.setup.remove(playerQuitEvent.getPlayer().getUniqueId());
        this.noticeAt.remove(playerQuitEvent.getPlayer().getUniqueId());
        this.progress.invalidate(playerQuitEvent.getPlayer().getUniqueId());
    }

    public boolean onCommand(CommandSender commandSender, Command command, String string, String[] stringArray) {
        try {
            String string2;
            String string3 = string2 = stringArray.length == 0 ? "progress" : stringArray[0].toLowerCase(Locale.ROOT);
            if (string2.equals("progress") || string2.equals("help")) {
                if (!commandSender.hasPermission("townyreborn.hunt.play")) {
                    this.tell(commandSender, "no-permission", new Object[0]);
                    return true;
                }
                if (stringArray.length >= 2) {
                    if (!this.admin(commandSender)) {
                        return true;
                    }
                    this.showProgress(commandSender, this.resolve(stringArray[1]));
                } else {
                    if (!(commandSender instanceof Player)) {
                        commandSender.sendMessage("Use /hunt check, /hunt list or /hunt progress <player>.");
                        return true;
                    }
                    Player player = (Player)commandSender;
                    this.showProgress(commandSender, player.getUniqueId());
                }
                if (commandSender.hasPermission("townyreborn.hunt.admin") && string2.equals("help")) {
                    commandSender.sendMessage(this.text("&7/hunt setup | add [id] | move <id> | remove [id] | list | check | reload"));
                }
                return true;
            }
            if (!this.admin(commandSender)) {
                return true;
            }
            switch (string2) {
                case "setup": {
                    Player player = this.player(commandSender);
                    if (this.setup.remove(player.getUniqueId())) {
                        this.tell(commandSender, "setup-off", new Object[0]);
                        break;
                    }
                    this.setup.add(player.getUniqueId());
                    this.tell(commandSender, "setup-on", new Object[0]);
                    break;
                }
                case "add": {
                    this.register(commandSender, this.target(commandSender), stringArray.length >= 2 ? stringArray[1] : null);
                    break;
                }
                case "move": {
                    if (stringArray.length != 2) {
                        throw new IllegalArgumentException("Usage: /hunt move <id>");
                    }
                    Block block = this.target(commandSender);
                    HeadIndex.Head head = this.heads.move(stringArray[1], this.position(block), this.support(block), this.maximum);
                    this.tell(commandSender, "moved", "id", head.id());
                    break;
                }
                case "remove": {
                    String string4;
                    if (stringArray.length >= 2) {
                        string4 = stringArray[1];
                    } else {
                        HeadIndex.Head head = this.heads.at(this.position(this.target(commandSender)));
                        if (head == null) {
                            throw new IllegalArgumentException("This head is not registered.");
                        }
                        string4 = head.id();
                    }
                    this.heads.remove(string4);
                    this.tell(commandSender, "removed", "id", string4);
                    break;
                }
                case "list": {
                    this.tell(commandSender, "head-count", "active", this.heads.activeCount(), "total", this.maximum);
                    for (HeadIndex.Head head : this.heads.active()) {
                        HeadIndex.Position position = head.position();
                        commandSender.sendMessage(this.text("&a" + head.id() + " &7- " + position.worldName() + " &f" + position.x() + ", " + position.y() + ", " + position.z()));
                    }
                    break;
                }
                case "check": 
                case "status": {
                    this.crates.check(this.keyId);
                    this.tell(commandSender, "ready", "key", this.keyId, "active", this.heads.activeCount(), "total", this.maximum);
                    break;
                }
                case "reload": {
                    this.reloadConfig();
                    this.loadHunt();
                    this.tell(commandSender, "reloaded", new Object[0]);
                    break;
                }
                case "reset": {
                    if (stringArray.length != 2) {
                        throw new IllegalArgumentException("Usage: /hunt reset <player or UUID>");
                    }
                    UUID uUID = this.resolve(stringArray[1]);
                    int n = this.ledger.reset(uUID);
                    this.progress.invalidate(uUID);
                    this.getLogger().warning(commandSender.getName() + " reset " + n + " finds for " + String.valueOf(uUID) + " in " + this.eventId + ". No keys were removed.");
                    this.tell(commandSender, "reset", "player", stringArray[1], "count", n);
                    break;
                }
                case "pending": {
                    if (stringArray.length != 2) {
                        throw new IllegalArgumentException("Usage: /hunt pending <player or UUID>");
                    }
                    commandSender.sendMessage(this.text("&dPending ghosts: &f" + String.join((CharSequence)", ", this.ledger.pending(this.resolve(stringArray[1])))));
                    break;
                }
                case "confirm": {
                    if (stringArray.length != 3) {
                        throw new IllegalArgumentException("Usage: /hunt confirm <player or UUID> <ghost-id>");
                    }
                    UUID uUID = this.resolve(stringArray[1]);
                    AtomicFiles.id(stringArray[2]);
                    this.ledger.finish(uUID, stringArray[2]);
                    this.progress.invalidate(uUID);
                    this.getLogger().warning(commandSender.getName() + " manually confirmed pending hunt find " + stringArray[2] + " for " + String.valueOf(uUID) + ". No extra key was given.");
                    this.tell(commandSender, "confirmed", "id", stringArray[2], "player", stringArray[1]);
                    break;
                }
                default: {
                    commandSender.sendMessage(this.text("&7/hunt setup | add [id] | move <id> | remove [id] | list | check | reload"));
                }
            }
        }
        catch (IOException | ReflectiveOperationException | RuntimeException exception) {
            this.adminError(commandSender, exception);
        }
        return true;
    }

    private void showProgress(CommandSender commandSender, UUID uUID) throws IOException {
        HuntLedger.Summary summary = this.ledger.summary(uUID);
        this.tell(commandSender, "progress", "found", summary.found(), "total", this.maximum);
        if (summary.pending() > 0) {
            this.tell(commandSender, "pending", new Object[0]);
        }
    }

    private boolean admin(CommandSender commandSender) {
        if (commandSender.hasPermission("townyreborn.hunt.admin")) {
            return true;
        }
        this.tell(commandSender, "no-permission", new Object[0]);
        return false;
    }

    private Player player(CommandSender commandSender) {
        if (commandSender instanceof Player) {
            Player player = (Player)commandSender;
            return player;
        }
        throw new IllegalArgumentException("This command must be used in game.");
    }

    private Block target(CommandSender commandSender) {
        Block block = this.player(commandSender).getTargetBlockExact(5);
        if (!TownyRebornHunt.skull(block)) {
            throw new IllegalArgumentException("Look at a placed HDB player head within 5 blocks.");
        }
        return block;
    }

    private UUID resolve(String string) throws IOException {
        try {
            return UUID.fromString(string);
        }
        catch (IllegalArgumentException illegalArgumentException) {
            Player player = Bukkit.getPlayerExact((String)string);
            if (player != null) {
                return player.getUniqueId();
            }
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayerIfCached((String)string);
            if (offlinePlayer != null) {
                return offlinePlayer.getUniqueId();
            }
            UUID uUID = this.ledger.byName(string);
            if (uUID != null) {
                return uUID;
            }
            throw new IllegalArgumentException("Unknown player. Use the exact name or UUID.");
        }
    }

    public List<String> onTabComplete(CommandSender commandSender, Command command, String string, String[] stringArray) {
        ArrayList<String> arrayList = new ArrayList<String>();
        boolean bl = commandSender.hasPermission("townyreborn.hunt.admin");
        if (stringArray.length == 1) {
            arrayList.addAll(List.of("progress", "help"));
            if (bl) {
                arrayList.addAll(List.of("setup", "add", "move", "remove", "list", "check", "reload", "reset", "pending", "confirm"));
            }
        } else if (bl && stringArray.length == 2) {
            if (List.of("move", "remove").contains(stringArray[0].toLowerCase(Locale.ROOT))) {
                this.heads.active().forEach(head -> arrayList.add(head.id()));
            }
            if (List.of("progress", "reset", "pending", "confirm").contains(stringArray[0].toLowerCase(Locale.ROOT))) {
                Bukkit.getOnlinePlayers().forEach(player -> arrayList.add(player.getName()));
            }
        } else if (bl && stringArray.length == 3 && stringArray[0].equalsIgnoreCase("confirm")) {
            try {
                arrayList.addAll(this.ledger.pending(this.resolve(stringArray[1])));
            }
            catch (IOException | RuntimeException exception) {
                // empty catch block
            }
        }
        String string3 = stringArray.length == 0 ? "" : stringArray[stringArray.length - 1].toLowerCase(Locale.ROOT);
        return arrayList.stream().filter(string2 -> string2.toLowerCase(Locale.ROOT).startsWith(string3)).sorted().toList();
    }

    private void notice(Player player, String string, Object ... objectArray) {
        long l = System.currentTimeMillis();
        if (l - this.noticeAt.getOrDefault(player.getUniqueId(), 0L) < 1000L) {
            return;
        }
        this.noticeAt.put(player.getUniqueId(), l);
        player.sendActionBar(this.text(this.render(string, objectArray)));
    }

    private void tell(CommandSender commandSender, String string, Object ... objectArray) {
        commandSender.sendMessage(this.text(this.getConfig().getString("prefix", "&d&lTOWNY &a&lREBORN &8\u00bb &r") + this.render(string, objectArray)));
    }

    private String render(String string, Object ... objectArray) {
        String string2 = this.getConfig().getString("messages." + string, string);
        int n = 0;
        while (n + 1 < objectArray.length) {
            string2 = string2.replace("{" + String.valueOf(objectArray[n]) + "}", String.valueOf(objectArray[n + 1]));
            n += 2;
        }
        return string2;
    }

    private Component text(String string) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(string);
    }

    private void adminError(CommandSender commandSender, Exception exception) {
        commandSender.sendMessage(this.text(this.getConfig().getString("prefix", "&dTOWNY &aREBORN &8\u00bb &r") + "&c" + this.reason(exception)));
        if (exception instanceof IOException) {
            this.getLogger().log(Level.WARNING, "Hunt data operation failed. Existing data has been retained.", exception);
        }
    }

    private String reason(Throwable throwable) {
        Throwable throwable2;
        for (throwable2 = throwable; throwable2.getCause() != null && throwable2.getCause() != throwable2; throwable2 = throwable2.getCause()) {
        }
        return throwable2.getMessage() == null ? throwable2.getClass().getSimpleName() : throwable2.getMessage();
    }
}

