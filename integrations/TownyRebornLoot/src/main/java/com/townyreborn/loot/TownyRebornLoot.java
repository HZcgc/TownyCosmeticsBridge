package com.townyreborn.loot;

import com.townyreborn.compat.ExcellentCrates7;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class TownyRebornLoot
extends JavaPlugin {
    private final Map<String, ConfigurationSection> profiles = new LinkedHashMap<String, ConfigurationSection>();
    private Method iaGet;
    private Method iaStack;
    private ExcellentCrates7 cratesApi;
    private String resetStatus = "waiting";
    private final Map<String, Long> guardUntil = new LinkedHashMap<String, Long>();

    public void onEnable() {
        this.saveDefaultConfig();
        if (!new File(this.getDataFolder(), "profiles.yml").exists()) {
            this.saveResource("profiles.yml", false);
        }
        try {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("ItemsAdder");
            Class<?> clazz = Class.forName("dev.lone.itemsadder.api.CustomStack", true, plugin.getClass().getClassLoader());
            this.iaGet = clazz.getMethod("getInstance", String.class);
            this.iaStack = clazz.getMethod("getItemStack", new Class[0]);
            Plugin plugin2 = Bukkit.getPluginManager().getPlugin("ExcellentCrates");
            this.cratesApi = new ExcellentCrates7(plugin2);
            this.loadProfiles();
            this.registerCrateGuard(plugin2);
            this.scheduleReset();
            this.getLogger().info("Loaded " + this.profiles.size() + " loot profiles. Use /trloot status.");
        }
        catch (ReflectiveOperationException | RuntimeException exception) {
            this.getLogger().log(Level.SEVERE, "Could not bind ItemsAdder / ExcellentCrates APIs", exception);
            Bukkit.getPluginManager().disablePlugin((Plugin)this);
        }
    }

    private void loadProfiles() {
        this.profiles.clear();
        this.guardUntil.clear();
        ConfigurationSection configurationSection = YamlConfiguration.loadConfiguration((File)new File(this.getDataFolder(), "profiles.yml")).getConfigurationSection("profiles");
        if (configurationSection == null) {
            throw new IllegalStateException("No loot profiles configured");
        }
        for (String string : configurationSection.getKeys(false)) {
            ConfigurationSection configurationSection2 = configurationSection.getConfigurationSection(string);
            if (configurationSection2 == null) continue;
            this.profiles.put(string, configurationSection2);
        }
    }

    private void registerCrateGuard(Plugin plugin) throws ReflectiveOperationException {
        ClassLoader classLoader = plugin.getClass().getClassLoader();
        Class<? extends Event> clazz = this.cratesApi.openingEvent();
        Method method = clazz.getMethod("getCrate", new Class[0]);
        Method method2 = clazz.getMethod("getPlayer", new Class[0]);
        
        Bukkit.getPluginManager().registerEvent(clazz, new Listener(){}, EventPriority.HIGHEST, (listener, event) -> {
            Object object;
            if (event instanceof Cancellable cancelled && cancelled.isCancelled()) {
                return;
            }
            try {
                object = this.cratesApi.crateId(method.invoke(event));
                List<String> list = this.getConfig().getStringList("guarded-crates." + (String)object);
                if (list.isEmpty() || this.guardUntil.getOrDefault(object, 0L) > System.currentTimeMillis()) {
                    return;
                }
                for (String string : list) {
                    this.create(string);
                }
                this.guardUntil.put((String)object, System.currentTimeMillis() + 10000L);
            }
            catch (ReflectiveOperationException | RuntimeException exception) {
                if (event instanceof Cancellable) {
                    Cancellable cancellable = (Cancellable)event;
                    cancellable.setCancelled(true);
                }
                try {
                    this.tell((CommandSender)((Player)method2.invoke(event, new Object[0])), "Crate loot is still loading. Please tell staff if this continues.");
                }
                catch (ReflectiveOperationException reflectiveOperationException) {
                    // empty catch block
                }
                this.getLogger().warning("Blocked a crate opening before key payment: " + exception.getMessage());
            }
        }, (Plugin)this, true);
    }

    private void scheduleReset() {
        this.resetStatus = "legacy test reset retired on ExcellentCrates 7; existing progress preserved";
    }

    private void beginReset(CommandSender sender) {
        this.tell(sender, "The old one-time Halloween test reset is retired on ExcellentCrates 7. Existing progress is preserved.");
    }


    private ItemStack create(String profileId) throws ReflectiveOperationException {
        ConfigurationSection profile = this.profiles.get(profileId);
        if (profile == null) throw new IllegalArgumentException("Unknown profile: " + profileId);
        Material material = Material.valueOf(profile.getString("material"));
        ItemStack stack;
        if (profile.contains("itemsadder")) {
            Object custom = this.iaGet.invoke(null, profile.getString("itemsadder"));
            if (custom == null) throw new IllegalStateException("ItemsAdder item not registered: " + profile.getString("itemsadder"));
            stack = ((ItemStack)this.iaStack.invoke(custom)).clone();
            if (stack.getType() != material) throw new IllegalStateException("Wrong material for " + profileId + ": " + stack.getType() + ", expected " + material);
        } else {
            stack = new ItemStack(material);
        }
        stack.setAmount(1);
        ItemMeta meta = stack.getItemMeta();
        for (Enchantment enchantment : new ArrayList<>(meta.getEnchants().keySet())) meta.removeEnchant(enchantment);
        ConfigurationSection enchants = profile.getConfigurationSection("enchants");
        if (enchants != null) {
            for (String enchantId : enchants.getKeys(false)) {
                NamespacedKey key = NamespacedKey.fromString(enchantId);
                Enchantment enchant = key == null ? null : Registry.ENCHANTMENT.get(key);
                if (enchant == null) throw new IllegalStateException("Enchantment not registered: " + enchantId + ". Replace the EE folder and fully restart.");
                meta.addEnchant(enchant, enchants.getInt(enchantId), true);
            }
        }
        meta.displayName(this.styled(profile.getString("name", profileId)));
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.removeIf(line -> PlainTextComponentSerializer.plainText().serialize(line).startsWith("Towny Reborn"));
        Component branding = Component.text("TOWNY", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text(" REBORN", NamedTextColor.GREEN))
                .append(Component.text(" | " + profile.getString("collection", "Loot"), NamedTextColor.DARK_GRAY))
                .decoration(TextDecoration.ITALIC, false);
        lore.add(0, branding);
        String description = profile.getString("description", "");
        if (!description.isBlank()) lore.add(Component.text(description, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(new NamespacedKey(this, "profile"), PersistentDataType.STRING, profileId);
        stack.setItemMeta(meta);
        return stack;
    }

    private Component styled(String string) {
        int n = string.indexOf(32);
        if (n >= 0) {
            ++n;
        } else {
            n = switch (string) {
                case "Netherbane", "Lumberkeeper" -> 6;
                case "Deepvein", "Silkkeeper", "Tidecaller" -> 4;
                case "Earthshaper" -> 5;
                case "Harvestkeeper" -> 7;
                case "Farwatch", "Outrider" -> 3;
                default -> Math.max(1, string.length() / 2);
            };
        }
        return ((TextComponent)((TextComponent)Component.text((String)string.substring(0, n), (TextColor)NamedTextColor.LIGHT_PURPLE).append((Component)Component.text((String)string.substring(n), (TextColor)NamedTextColor.GREEN))).decorate(TextDecoration.BOLD)).decoration(TextDecoration.ITALIC, false);
    }

    private void deliver(Player player, Collection<ItemStack> collection) {
        for (ItemStack itemStack2 : collection) {
            player.getInventory().addItem(new ItemStack[]{itemStack2}).values().forEach(itemStack -> player.getWorld().dropItemNaturally(player.getLocation(), itemStack));
        }
    }

    public boolean onCommand(CommandSender commandSender, Command command, String string, String[] stringArray) {
        if (!commandSender.hasPermission("townyreborn.loot.admin")) {
            return true;
        }
        try {
            if (stringArray.length == 3 && stringArray[0].equalsIgnoreCase("give")) {
                Player player = Bukkit.getPlayerExact((String)stringArray[1]);
                if (player == null) {
                    throw new IllegalArgumentException("Player must be online.");
                }
                this.deliver(player, List.of(this.create(stringArray[2])));
                return true;
            }
            if (stringArray.length == 2 && stringArray[0].equalsIgnoreCase("relics")) {
                Player player = Bukkit.getPlayerExact((String)stringArray[1]);
                if (player == null) {
                    throw new IllegalArgumentException("Player must be online.");
                }
                ArrayList<ItemStack> arrayList = new ArrayList<ItemStack>();
                for (String string2 : this.getConfig().getStringList("relic-set")) {
                    arrayList.add(this.create(string2));
                }
                this.deliver(player, arrayList);
                this.tell(commandSender, "Gave " + arrayList.size() + " Relic items to " + player.getName() + ".");
                return true;
            }
            if (stringArray.length == 1 && stringArray[0].equalsIgnoreCase("reset")) {
                this.beginReset(commandSender);
                return true;
            }
            if (stringArray.length == 1 && stringArray[0].equalsIgnoreCase("reload")) {
                this.reloadConfig();
                this.loadProfiles();
                this.scheduleReset();
                this.tell(commandSender, "Loot profiles reloaded.");
                return true;
            }
            if (stringArray.length == 1 && stringArray[0].equalsIgnoreCase("status")) {
                this.tell(commandSender, "Profiles: " + this.profiles.size() + ". Halloween reset: " + this.resetStatus + ".");
                ArrayList<String> arrayList = new ArrayList<String>();
                for (String string3 : List.of("hardened", "elemental_protection", "river_master", "seasoned_angler")) {
                    if (Registry.ENCHANTMENT.get(NamespacedKey.fromString((String)("excellentenchants:" + string3))) != null) continue;
                    arrayList.add(string3);
                }
                this.tell(commandSender, (String)(arrayList.isEmpty() ? "All four new enchantments are registered." : "Missing enchants: " + String.join((CharSequence)", ", arrayList)));
                return true;
            }
        }
        catch (ReflectiveOperationException | RuntimeException exception) {
            this.tell(commandSender, "Loot action failed: " + exception.getMessage());
            this.getLogger().log(Level.SEVERE, "Loot action failed", exception);
            return true;
        }
        return false;
    }

    public List<String> onTabComplete(CommandSender commandSender, Command command, String string2, String[] stringArray) {
        if (!commandSender.hasPermission("townyreborn.loot.admin")) {
            return List.of();
        }
        if (stringArray.length == 1) {
            return List.of("give", "relics", "reset", "status", "reload").stream().filter(string -> string.startsWith(stringArray[0].toLowerCase())).toList();
        }
        if (stringArray.length == 3 && stringArray[0].equalsIgnoreCase("give")) {
            return this.profiles.keySet().stream().filter(string -> string.startsWith(stringArray[2])).toList();
        }
        return null;
    }

    private void tell(CommandSender commandSender, String string) {
        if (commandSender == null) {
            return;
        }
        commandSender.sendMessage(((TextComponent)Component.text((String)"TOWNY", (TextColor)NamedTextColor.LIGHT_PURPLE).append((Component)Component.text((String)"REBORN", (TextColor)NamedTextColor.GREEN))).append((Component)Component.text((String)(" \u00bb " + string), (TextColor)NamedTextColor.GRAY)));
    }
}

