package com.townyreborn.compat;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/** Uses the public ExcellentCrates 7 service API in its own plugin class loader. */
public final class ExcellentCrates7 {
    private static final String API = "su.nightexpress.excellentcrates.api.";
    private final ClassLoader loader;
    private final Object service;

    @SuppressWarnings({"rawtypes", "unchecked"})
    public ExcellentCrates7(Plugin plugin) throws ReflectiveOperationException {
        loader = plugin.getClass().getClassLoader();
        Class apiType = type(API + "ExcellentCratesAPI");
        service = plugin.getServer().getServicesManager().load(apiType);
        if (service == null) {
            throw new IllegalStateException("ExcellentCrates 7 API service is not available.");
        }
    }

    private Class<?> type(String name) throws ClassNotFoundException {
        return Class.forName(name, true, loader);
    }

    private Object invoke(Object target, String owner, String name, Class<?>[] parameters,
                          Object... arguments) throws ReflectiveOperationException {
        return type(owner).getMethod(name, parameters).invoke(target, arguments);
    }

    private Object get(Object target, String owner, String name) throws ReflectiveOperationException {
        return invoke(target, owner, name, new Class<?>[0]);
    }

    private Object module(String method) throws ReflectiveOperationException {
        Object value = get(service, API + "ExcellentCratesAPI", method);
        if (value instanceof Optional<?> optional) {
            return optional.orElseThrow(() -> new IllegalStateException("ExcellentCrates module is disabled: " + method));
        }
        return value;
    }

    private Object identifier(String value) throws ReflectiveOperationException {
        return type("su.nightexpress.engine.id.Identifier").getConstructor(String.class).newInstance(value);
    }

    private Class<?> identifierType() throws ClassNotFoundException {
        return type("su.nightexpress.engine.id.Identifier");
    }

    public Object crate(String id) throws ReflectiveOperationException {
        Object registry = get(module("getCratesAPI"), API + "crate.CratesAPI", "registry");
        return invoke(registry, API + "crate.registry.CrateRegistry", "resolveCrate",
                      new Class<?>[]{identifierType()}, identifier(id));
    }

    public String crateId(Object crate) throws ReflectiveOperationException {
        return String.valueOf(get(crate, API + "crate.Crate", "getId"));
    }

    public List<String> crateIds() throws ReflectiveOperationException {
        Object registry = get(module("getCratesAPI"), API + "crate.CratesAPI", "registry");
        Collection<?> crates = (Collection<?>)get(registry, API + "crate.registry.CrateRegistry", "crates");
        java.util.ArrayList<String> ids = new java.util.ArrayList<>();
        for (Object crate : crates) ids.add(crateId(crate));
        return ids.stream().sorted().toList();
    }

    public String blockCrateId(Location location) throws ReflectiveOperationException {
        Optional<?> blocks = (Optional<?>)get(module("getCratesAPI"), API + "crate.CratesAPI", "blocks");
        if (blocks.isEmpty()) return null;
        Object registry = get(blocks.get(), API + "crate.block.BlockAPI", "getPositionRegistry");
        Object id = invoke(registry, API + "crate.block.position.CratePositionRegistry", "getCrateIdAt",
                           new Class<?>[]{Location.class}, location);
        return id == null ? null : id.toString();
    }

    public boolean holdingCrate(ItemStack item) throws ReflectiveOperationException {
        if (item == null || item.getType().isAir()) return false;
        Object items = get(module("getCratesAPI"), API + "crate.CratesAPI", "items");
        return (Boolean)invoke(items, API + "crate.item.CrateItemAPI", "isCrateItem",
                               new Class<?>[]{ItemStack.class}, item);
    }

    public void interact(Player player, String id, boolean preview, Location location)
            throws ReflectiveOperationException {
        Object crate = crate(id);
        if (crate == null) throw new IllegalArgumentException("Unknown ExcellentCrates ID: " + id);
        Object context = type(API + "crate.interact.context.LocatedCrateInteractContext")
                .getConstructor(Player.class, type(API + "crate.Crate"), Location.class)
                .newInstance(player, crate, location);
        Object action = type(API + "crate.interact.InteractionKeys")
                .getField(preview ? "PREVIEW_CRATE" : "OPEN_CRATE").get(null);
        Object interaction = get(module("getCratesAPI"), API + "crate.CratesAPI", "interaction");
        invoke(interaction, API + "crate.interact.InteractionAPI", "performAction",
               new Class<?>[]{type(API + "crate.interact.context.CrateInteractContext"), identifierType()},
               context, action);
    }

    public Class<? extends Event> openingEvent() throws ClassNotFoundException {
        return type(API + "crate.event.CratePipelinePreStartEvent").asSubclass(Event.class);
    }

    public Object virtualKey(String id) throws ReflectiveOperationException {
        Object registry = get(module("getKeysAPI"), API + "key.KeysAPI", "getRegistry");
        Object key = invoke(registry, API + "key.registry.KeyRegistry", "resolveKey",
                            new Class<?>[]{identifierType()}, identifier(id));
        if (key == null) throw new IllegalStateException("ExcellentCrates key not found: " + id);
        Object base = get(key, API + "key.CrateKey", "getBase");
        if (!Boolean.TRUE.equals(get(base, API + "key.data.model.KeyBase", "isVirtual"))) {
            throw new IllegalStateException("A virtual key is required: " + id);
        }
        return key;
    }

    public int keyBalance(Player player, Object key) throws ReflectiveOperationException {
        Object balance = get(module("getKeysAPI"), API + "key.KeysAPI", "getBalanceAPI");
        return ((Number)invoke(balance, API + "key.balance.KeyBalanceAPI", "countVirtualKeys",
                new Class<?>[]{Player.class, type(API + "key.CrateKey")}, player, key)).intValue();
    }

    public void giveOneKey(Player player, Object key) throws ReflectiveOperationException {
        if (!player.isOnline()) throw new IllegalStateException("The player is offline.");
        Object balance = get(module("getKeysAPI"), API + "key.KeysAPI", "getBalanceAPI");
        Object result = invoke(balance, API + "key.balance.KeyBalanceAPI", "addKey",
                new Class<?>[]{Player.class, type(API + "key.CrateKey"), int.class}, player, key, 1);
        if (!Boolean.TRUE.equals(get(result, "su.nightexpress.engine.action.ActionResult", "success"))) {
            throw new IllegalStateException("ExcellentCrates rejected the key grant.");
        }
    }

    public void setRewardName(String crateId, String rewardId, String name)
            throws ReflectiveOperationException {
        Object rewards = module("getRewardAPI");
        Object reward = invoke(rewards, API + "reward.RewardsAPI", "getReward",
                new Class<?>[]{identifierType(), identifierType()}, identifier(crateId), identifier(rewardId));
        if (reward == null) throw new IllegalStateException("Missing ExcellentCrates reward: " + crateId + "/" + rewardId);
        Object preview = get(reward, API + "reward.Reward", "getPreview");
        invoke(preview, API + "reward.data.model.RewardPreview", "setName", new Class<?>[]{String.class}, name);
    }
}
