package com.townyreborn.petshops;

import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;

final class ShopMenu implements InventoryHolder {
    final UUID player;
    final String pet;
    final boolean confirmation;
    final double price;
    final long expires;
    private final Inventory inventory;
    ShopMenu(Player player, PetCatalog.Pet pet, boolean confirmation, boolean owned, long timeoutSeconds) {
        this.player=player.getUniqueId(); this.pet=pet.id(); this.confirmation=confirmation; this.price=pet.price();
        expires=System.currentTimeMillis()+timeoutSeconds*1000;
        inventory=Bukkit.createInventory(this,27,color(confirmation ? "&dConfirm purchase" : "&d&lTOWNY&a&lREBORN &8| &dPets"));
        ItemStack glass=item(Material.GRAY_STAINED_GLASS_PANE," ");
        for (int i=0;i<27;i++) inventory.setItem(i,glass);
        ItemStack icon=item(Material.PAPER,"&d"+pet.name(),"&7Unlock once, own permanently.");
        if (!pet.iconModel().isBlank()) {
            var meta=icon.getItemMeta(); NamespacedKey key=NamespacedKey.fromString(pet.iconModel());
            if (key!=null) { meta.setItemModel(key); icon.setItemMeta(meta); }
        }
        inventory.setItem(13,icon);
        if (owned) {
            inventory.setItem(11,item(Material.LIME_DYE,"&aAlready unlocked","&7You already own this pet."));
            inventory.setItem(15,item(Material.BOOK,"&aOpen my pets","&7Click to open the MCPets menu."));
        } else if (confirmation) {
            inventory.setItem(11,item(Material.LIME_CONCRETE,"&aBuy for "+money(pet.price())+" $",
                "&7Pet: &f"+pet.name(),"&7One-time purchase. The money will be taken now."));
            inventory.setItem(15,item(Material.RED_CONCRETE,"&cCancel","&7Return to the buying options."));
        } else {
            inventory.setItem(11,item(Material.EMERALD,"&aBuy with in-game money",
                "&7Price: &a"+money(pet.price())+" $","&7Click to confirm your purchase."));
            inventory.setItem(15,item(pet.storeUrl().isBlank()?Material.BARRIER:Material.SUNFLOWER,"&dBuy on Tebex",
                "&7Price: &a"+euros(pet.storePrice())+" €",pet.storeUrl().isBlank()?"&7Tebex link has not been configured yet.":"&7Click to receive the Tebex link in chat."));
        }
        inventory.setItem(22,item(Material.BARRIER,"&cClose"));
    }
    static ItemStack item(Material material, String name, String... lore) {
        var stack=new ItemStack(material); var meta=stack.getItemMeta();
        meta.displayName(color(name)); meta.lore(Arrays.stream(lore).map(ShopMenu::color).toList()); stack.setItemMeta(meta); return stack;
    }
    static Component color(String text) { return LegacyComponentSerializer.legacyAmpersand().deserialize(text.replace('§','&')); }
    static String money(double n) {
        var f=java.text.NumberFormat.getNumberInstance(Locale.US); f.setMaximumFractionDigits(2); return f.format(n);
    }
    static String euros(double n) {
        var f=java.text.NumberFormat.getNumberInstance(Locale.US); f.setMinimumFractionDigits(2); f.setMaximumFractionDigits(2); return f.format(n);
    }
    @Override public Inventory getInventory() { return inventory; }
}
