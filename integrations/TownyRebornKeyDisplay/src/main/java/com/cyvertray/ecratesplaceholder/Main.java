package com.cyvertray.ecratesplaceholder;

import org.bukkit.plugin.java.JavaPlugin;
import su.nightexpress.excellentcrates.api.ExcellentCratesAPI;

public final class Main extends JavaPlugin {
    private ExcellentCratesPlaceholderExpansion expansion;

    @Override public void onEnable() {
        ExcellentCratesAPI api = getServer().getServicesManager().load(ExcellentCratesAPI.class);
        if (api == null || api.getKeysAPI().isEmpty()) {
            getLogger().severe("ExcellentCrates 7 key service is unavailable. Key display addon disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        var candidate = new ExcellentCratesPlaceholderExpansion(api.getKeysAPI().orElseThrow());
        if (!candidate.register()) {
            getLogger().severe("Placeholder identifier 'ec' is already registered. Remove the old key addon JAR and restart.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.expansion = candidate;
        getLogger().info("EC7 key placeholders registered: ec_keyz_, ec_physicalkeyz_, ec_totalkeys_.");
    }

    @Override public void onDisable() {
        if (expansion != null) {
            expansion.unregister();
            expansion = null;
        }
    }
}
