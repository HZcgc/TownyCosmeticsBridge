package com.townyreborn.crateoffsets;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import su.nightexpress.engine.id.Identifier;
import su.nightexpress.excellentcrates.api.ExcellentCratesAPI;

public final class TownyRebornCrateOffsets extends JavaPlugin {
    private Map<String, Offset> offsets = Map.of();
    private OffsetProfiles profiles;
    private ExcellentCratesAPI api;

    @Override public void onEnable() {
        try {
            saveDefaultConfig();
            readOffsets();
            api = getServer().getServicesManager().load(ExcellentCratesAPI.class);
            if (api == null) throw new IllegalStateException("ExcellentCrates 7 API is not available.");
            profiles = new OffsetProfiles(id -> offsets.getOrDefault(id, Offset.ZERO), getLogger());
            getServer().getPluginManager().registerEvents(profiles, this);
            Objects.requireNonNull(getCommand("trcrateoffset")).setExecutor(this);
            Objects.requireNonNull(getCommand("trcrateoffset")).setTabCompleter(this);
            getLogger().info("Native crate animation offsets enabled for " + offsets.size() + " crates.");
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "Unable to enable crate animation offsets", exception);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override public void onDisable() { if (profiles != null) profiles.restore(); }

    private void readOffsets() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.load(new File(getDataFolder(), "config.yml"));
        Map<String, Offset> updated = Offset.read(config);
        reloadConfig();
        offsets = updated;
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("townyreborn.crateoffsets.admin")) return true;
        try {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                readOffsets();
                sender.sendMessage("Animation offsets reloaded. Changes apply to the next opening.");
                return true;
            }
            boolean reset = args.length == 2 && args[0].equalsIgnoreCase("reset");
            boolean set = args.length == 5 && args[0].equalsIgnoreCase("set");
            if (!reset && !set) return false;
            Identifier id = new Identifier(args[1]);
            if (api.getCratesAPI().registry().resolveCrate(id) == null) {
                sender.sendMessage("Unknown crate: " + args[1]);
                return true;
            }
            Offset offset = reset ? Offset.ZERO : new Offset(Double.parseDouble(args[2]), Double.parseDouble(args[3]), Double.parseDouble(args[4]));
            String path = "offsets." + id.value() + ".";
            getConfig().set(path + "x", offset.x());
            getConfig().set(path + "y", offset.y());
            getConfig().set(path + "z", offset.z());
            getConfig().save(new File(getDataFolder(), "config.yml"));
            offsets = Offset.read(getConfig());
            sender.sendMessage("Animation offset for " + id.value() + ": X=" + offset.x() + ", Y=" + offset.y() + ", Z=" + offset.z() + ". Applies to the next opening.");
        } catch (Exception exception) {
            sender.sendMessage("Could not update offsets: " + exception.getMessage());
        }
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("townyreborn.crateoffsets.admin")) return List.of();
        if (args.length == 1) return List.of("set", "reset", "reload").stream().filter(s -> s.startsWith(args[0].toLowerCase(java.util.Locale.ROOT))).toList();
        if (args.length == 2 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("reset")))
            return api.getCratesAPI().registry().crates().stream().map(crate -> crate.idString()).sorted().filter(s -> s.startsWith(args[1])).toList();
        return List.of();
    }
}
