package com.townyreborn.crateoffsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import su.nightexpress.engine.id.Identifier;
import su.nightexpress.excellentcrates.animation.DefaultAnimationRegistry;
import su.nightexpress.excellentcrates.animation.pipeline.AnimationProfilePipelineStage;
import su.nightexpress.excellentcrates.animation.session.AnimationSessionManager;
import su.nightexpress.excellentcrates.animation.session.AnimationSessionService;
import su.nightexpress.excellentcrates.animation.style.csgo.CSGOAnimationInstance;
import su.nightexpress.excellentcrates.animation.style.csgo.CSGOAnimationProfile;
import su.nightexpress.excellentcrates.animation.style.csgo.CSGOAnimationSettings;
import su.nightexpress.excellentcrates.animation.style.simpleroll.SimpleRollAnimation;
import su.nightexpress.excellentcrates.animation.style.simpleroll.SimpleRollProfile;
import su.nightexpress.excellentcrates.animation.style.simpleroll.SimpleRollSettings;
import su.nightexpress.excellentcrates.api.animation.AnimationContext;
import su.nightexpress.excellentcrates.api.animation.AnimationProfile;
import su.nightexpress.excellentcrates.api.common.preview.NameAndLore;
import su.nightexpress.excellentcrates.api.crate.Crate;
import su.nightexpress.excellentcrates.api.crate.dispatcher.CrateMessageDispatcher;
import su.nightexpress.excellentcrates.api.crate.event.CratePipelinePreStartEvent;
import su.nightexpress.excellentcrates.api.crate.pipeline.PipelineChain;
import su.nightexpress.excellentcrates.api.crate.pipeline.component.PipelineComponentKeys;
import su.nightexpress.excellentcrates.api.crate.pipeline.reward.PipelineReward;
import su.nightexpress.excellentcrates.api.reward.Reward;
import su.nightexpress.excellentcrates.api.reward.preview.RewardPreviewAPI;
import su.nightexpress.excellentcrates.api.animation.component.AnimationComponent;
import su.nightexpress.excellentcrates.api.crate.component.CrateComponentKeys;
import su.nightexpress.excellentcrates.crates.pipeline.DefaultPipelineContext;
import su.nightexpress.nightcore.bridge.entity.WrappedTextDisplay;
import su.nightexpress.nightcore.bridge.key.AdaptedKey;

class AnimationOffsetTest {
    final World world = mock(World.class);
    final Player player = mock(Player.class);
    final Block block = mock(Block.class);
    final Location source = new Location(world, 10.5, 64, 20.5);
    final RewardPreviewAPI preview = mock(RewardPreviewAPI.class);
    final Reward winner = mock(Reward.class);
    final PipelineReward reward = mock(PipelineReward.class);
    final List<Location> spawns = new ArrayList<>();
    final List<Entity> entities = new ArrayList<>();
    final AdaptedKey key = mock(AdaptedKey.class);

    AnimationOffsetTest() {
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getWorld()).thenReturn(world);
        when(world.getBlockAt(10, 64, 20)).thenReturn(block);
        when(world.getBlockAt(any(Location.class))).thenReturn(block);
        when(block.getLocation()).thenAnswer(inv -> new Location(world, 10, 64, 20));
        when(block.getBoundingBox()).thenReturn(new BoundingBox(10, 64, 20, 11, 65, 21));
        when(reward.getGranted()).thenReturn(winner);
        when(reward.getRolled()).thenReturn(winner);
        when(preview.createPreviewItem(winner)).thenReturn(mock(ItemStack.class));
        when(preview.getDisplayInfo(winner)).thenReturn(new NameAndLore("Reward", List.of()));
        doAnswer(inv -> {
            Location location = inv.getArgument(0);
            Class<? extends Entity> type = inv.getArgument(1);
            Entity entity = mock(type);
            if (entity instanceof Item item) when(item.teleport(any(Location.class))).thenReturn(true);
            Consumer<Entity> initialize = inv.getArgument(2);
            initialize.accept(entity);
            spawns.add(location.clone());
            entities.add(entity);
            return entity;
        }).when(world).spawn(any(Location.class), any(Class.class), any(Consumer.class));
    }

    AnimationContext context(String id) {
        Crate crate = mock(Crate.class);
        when(crate.idString()).thenReturn(id);
        when(crate.id()).thenReturn(new Identifier(id));
        return new AnimationContext(player, source, crate, reward, List.of(winner), preview);
    }

    CSGOAnimationProfile csgo() { return new CSGOAnimationProfile(key, CSGOAnimationSettings.defaultSettings()); }

    MockedStatic<WrappedTextDisplay> textBridge() {
        var bridge = mockStatic(WrappedTextDisplay.class);
        bridge.when(() -> WrappedTextDisplay.wrap(any(TextDisplay.class))).thenReturn(mock(WrappedTextDisplay.class));
        return bridge;
    }

    void expectPosition(Location actual, double x, double y, double z) {
        assertSame(world, actual.getWorld());
        assertEquals(x, actual.getX(), 0.0000001);
        assertEquals(y, actual.getY(), 0.0000001);
        assertEquals(z, actual.getZ(), 0.0000001);
    }

    @Test void fractionalOffsetsMoveEveryNativeItemAndLabelWithoutMovingTheSource() throws Exception {
        AtomicInteger completed = new AtomicInteger();
        CSGOAnimationInstance instance = new CSGOAnimationInstance(context("vote"), completed::incrementAndGet, CSGOAnimationSettings.defaultSettings());
        new NativeAnimationOffsets().apply(instance, new Offset(0.25, 0.75, -0.5));
        try (var bridge = textBridge()) {
            instance.start();
            assertTrue(instance.tick());
        }
        assertTrue(spawns.size() >= 2);
        assertEquals(0, spawns.size() % 2);
        spawns.forEach(location -> expectPosition(location, 10.75, 66.25, 20));
        expectPosition(source, 10.5, 64, 20.5);
        assertEquals(0, completed.get());
        instance.stop();
        assertEquals(1, completed.get());
        entities.forEach(entity -> verify(entity).remove());
        verify(reward, never()).setGranted(any());
    }

    @Test void sharedProfileUsesEachCratesCurrentOffsetAndKeepsNativeSessionGuardsAndCompletion() {
        Map<String, Offset> settings = new HashMap<>();
        settings.put("vote", new Offset(0, 0.5, 0));
        settings.put("daily", new Offset(-0.5, -0.25, 0.25));
        OffsetProfiles offsets = new OffsetProfiles(id -> settings.getOrDefault(id, Offset.ZERO), Logger.getAnonymousLogger());
        DefaultAnimationRegistry registry = new DefaultAnimationRegistry();
        registry.registerProfile(csgo());
        offsets.wrap(registry);
        AnimationProfile wrapped = registry.getProfile(key);
        var manager = new AnimationSessionManager();
        var service = new AnimationSessionService(manager);
        AtomicInteger completed = new AtomicInteger();
        try (var bridge = textBridge()) {
            assertTrue(service.startSession(wrapped, context("vote"), completed::incrementAndGet).success());
            var instance = manager.getSession(player.getUniqueId());
            assertInstanceOf(CSGOAnimationInstance.class, instance);
            assertTrue(manager.hasSession(player.getUniqueId()));
            instance.tick();
            spawns.forEach(location -> expectPosition(location, 10.5, 66, 20.5));
            for (int tick = 1; tick < 71; tick++) if (!instance.tick()) instance.stop();
            assertEquals(1, completed.get());
            assertFalse(manager.hasSession(player.getUniqueId()));
            assertTrue(service.checkActiveSession(player).success());
            spawns.clear();
            var daily = wrapped.createInstance(context("daily"), () -> {});
            daily.start(); daily.tick(); daily.stop();
            spawns.forEach(location -> expectPosition(location, 10, 65.25, 20.75));
            spawns.clear();
            settings.put("vote", new Offset(0, 1.125, 0));
            var changed = wrapped.createInstance(context("vote"), () -> {});
            changed.start(); changed.tick(); changed.stop();
            spawns.forEach(location -> expectPosition(location, 10.5, 66.625, 20.5));
        }
    }

    @Test void preStartHookSurvivesProfileReloadDoesNotStackOrCancelAndRestoresTheOriginal() {
        var registry = new DefaultAnimationRegistry();
        var original = csgo();
        registry.registerProfile(original);
        var offsets = new OffsetProfiles(id -> Offset.ZERO, Logger.getAnonymousLogger());
        var sessions = mock(AnimationSessionService.class);
        when(sessions.checkActiveSession(player)).thenReturn(su.nightexpress.engine.action.ActionResult.ok());
        var stage = new AnimationProfilePipelineStage(registry, sessions, mock(CrateMessageDispatcher.class));
        var context = context("vote");
        var pipeline = new DefaultPipelineContext(context.crate());
        var event = new CratePipelinePreStartEvent(player, context.crate(), pipeline, List.of(stage));
        offsets.beforeOpening(event);
        var wrapped = registry.getProfile(key);
        offsets.beforeOpening(event);
        assertSame(wrapped, registry.getProfile(key));
        assertSame(original.key(), wrapped.key());
        assertEquals(original.getName(), wrapped.getName());
        assertFalse(event.isCancelled());
        assertFalse(pipeline.hasComponent(PipelineComponentKeys.ANIMATION));
        var animation = mock(AnimationComponent.class);
        when(animation.isEnabled()).thenReturn(true);
        when(animation.getProfileKey()).thenReturn(key);
        when(context.crate().getComponentOrNull(CrateComponentKeys.ANIMATION)).thenReturn(animation);
        var chain = mock(PipelineChain.class);
        stage.intercept(player, context.crate(), pipeline, chain);
        assertEquals(key, pipeline.getComponentOrNull(PipelineComponentKeys.ANIMATION).getProfileKey());
        verify(chain).proceed(player, pipeline);
        verify(chain, never()).abort();
        registry.clearProfiles();
        var reloaded = csgo();
        registry.registerProfile(reloaded);
        offsets.beforeOpening(event);
        assertNotSame(reloaded, registry.getProfile(key));
        offsets.restore();
        assertSame(reloaded, registry.getProfile(key));
    }

    @Test void simpleRollMovesTheExistingDisplayAndKeepsNativeCleanupAndCallback() {
        var registry = new DefaultAnimationRegistry();
        registry.registerProfile(new SimpleRollProfile(key, SimpleRollSettings.defaultSettings()));
        new OffsetProfiles(id -> new Offset(0.5, -0.85, -0.25), Logger.getAnonymousLogger()).wrap(registry);
        AtomicInteger completed = new AtomicInteger();
        var instance = registry.getProfile(key).createInstance(context("daily"), completed::incrementAndGet);
        assertInstanceOf(SimpleRollAnimation.class, instance);
        assertEquals(1, spawns.size());
        expectPosition(spawns.getFirst(), 10.5, 65.35, 20.5);
        Item item = (Item) entities.getFirst();
        var destination = org.mockito.ArgumentCaptor.forClass(Location.class);
        verify(item).teleport(destination.capture());
        expectPosition(destination.getValue(), 11, 64.5, 20.25);
        assertEquals(0, completed.get());
        instance.start(); instance.stop();
        verify(item).remove();
        assertEquals(1, completed.get());
    }

    @Test void failedSimpleRollTeleportRestoresItsOriginalLocationBeforeTheNextOffset() throws Exception {
        var instance = new SimpleRollAnimation(SimpleRollSettings.defaultSettings(), context("daily"), () -> {});
        Item item = (Item) entities.getFirst();
        when(item.teleport(any(Location.class))).thenReturn(false, true);
        var offsets = new NativeAnimationOffsets();
        assertThrows(IllegalStateException.class, () -> offsets.apply(instance, new Offset(0, 1, 0)));
        offsets.apply(instance, new Offset(0, 0.25, 0));
        var destination = org.mockito.ArgumentCaptor.forClass(Location.class);
        verify(item, times(2)).teleport(destination.capture());
        expectPosition(destination.getAllValues().get(1), 10.5, 65.6, 20.5);
        instance.stop();
    }

    @Test void configurationSupportsDecimalsIndependentCratesAndRejectsInvalidCoordinates() throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString("offsets:\n  vote: {y: 0.5}\n  daily: {x: -1.25, y: -0.75, z: 0.25}\n");
        var offsets = Offset.read(config);
        assertEquals(new Offset(0, 0.5, 0), offsets.get("vote"));
        assertEquals(new Offset(-1.25, -0.75, 0.25), offsets.get("daily"));
        config.set("offsets.vote.y", "abc");
        assertThrows(IllegalArgumentException.class, () -> Offset.read(config));
        for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -32.1, 32.1})
            assertThrows(IllegalArgumentException.class, () -> new Offset(0, value, 0));
    }
}
