package net.townyreborn.journey;

import java.util.*;
import org.bukkit.configuration.ConfigurationSection;

public final class Journey {
    public static final int[] PATH = {9,18,27,36,45,46,47,38,29,20,11,12,13,22,31,40,49,50,51,42,33,24,15,16,17,26,35,44,53};
    public final int steps;
    public final long base, increase;
    public final Map<String, Perk> perks = new LinkedHashMap<>();
    public final Map<String, Gift> gifts = new LinkedHashMap<>();
    public final NavigableMap<Integer, String> titles = new TreeMap<>();
    public final NavigableMap<Integer, String> abbreviations = new TreeMap<>();
    private final Map<String, String> giftAliases = new HashMap<>();

    public Journey(ConfigurationSection config) {
        steps = config.getInt("journey.steps", 116);
        base = config.getLong("journey.points-first", 30);
        increase = config.getLong("journey.points-increase", 15);
        if (steps < 2 || steps > 10000 || base < 1 || base > 1000000 || increase < 0 || increase > 100000 || threshold(steps) > 1000000000000L)
            throw new IllegalArgumentException("Invalid progression curve");
        ConfigurationSection names = config.getConfigurationSection("journey.titles");
        if (names != null) for (String key : names.getKeys(false)) {
            int level = Integer.parseInt(key);
            String title = names.getString(key);
            if (level < 1 || level > steps || title == null || title.isBlank()) throw new IllegalArgumentException("Invalid rank title " + key);
            titles.put(level, title);
        }
        titles.putIfAbsent(1, "Newcomer");
        for (var entry : titles.entrySet()) {
            String fallback = entry.getValue().replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
            fallback = fallback.substring(0, Math.min(3, fallback.length()));
            String abbreviation = config.getString("journey.short-titles." + entry.getKey(), fallback);
            if (!abbreviation.matches("[A-Za-z0-9]{1,8}")) throw new IllegalArgumentException("Invalid short title " + entry.getKey());
            abbreviations.put(entry.getKey(), abbreviation);
        }
        ConfigurationSection paid = config.getConfigurationSection("perks");
        if (paid != null) for (String id : paid.getKeys(false)) {
            validateId(id);
            ConfigurationSection p = paid.getConfigurationSection(id);
            if (p == null) throw new IllegalArgumentException("Invalid perk " + id);
            int level = p.getInt("level"); double price = p.getDouble("price");
            if (level < 1 || level > steps || !Double.isFinite(price) || price <= 0 || p.getStringList("permissions").isEmpty())
                throw new IllegalArgumentException("Invalid perk " + id);
            perks.put(id, new Perk(id, p.getString("name", id), level, price, List.copyOf(p.getStringList("permissions")),
                List.copyOf(p.getStringList("legacy-markers")), p.getString("requires", ""), List.copyOf(p.getStringList("help-commands")),
                Tier.valueOf(p.getString("rank-tier", "EXTRA").toUpperCase(Locale.ROOT)), p.getInt("menu-order", level)));
        }
        for (Perk perk : perks.values()) {
            if (!perk.requires().isEmpty() && (!perks.containsKey(perk.requires()) || perks.get(perk.requires()).level() > perk.level()))
                throw new IllegalArgumentException("Invalid predecessor for " + perk.id());
            Set<String> visited = new HashSet<>();
            for (Perk p = perk; p != null && !p.requires().isEmpty(); p = perks.get(p.requires()))
                if (!visited.add(p.id())) throw new IllegalArgumentException("Cyclic perk chain");
        }
        ConfigurationSection rewards = config.getConfigurationSection("gifts");
        if (rewards != null) for (String id : rewards.getKeys(false)) {
            validateId(id);
            ConfigurationSection g = rewards.getConfigurationSection(id);
            if (g == null) throw new IllegalArgumentException("Invalid gift " + id);
            int level = g.getInt("level"), blocks = g.getInt("town-blocks", 0);
            double money = g.getDouble("money", 0);
            if (level < 1 || level > steps || blocks < 0 || blocks > 100 || !Double.isFinite(money) || money < 0 || money > 1000000)
                throw new IllegalArgumentException("Invalid gift " + id);
            gifts.put(id, new Gift(id, g.getString("name", id), level, List.copyOf(g.getStringList("permissions")),
                List.copyOf(g.getStringList("commands")), g.getBoolean("town-required", false) || blocks > 0, blocks,
                money, g.getString("icon", "CHEST"), List.copyOf(g.getStringList("legacy-ids"))));
        }
        for (Gift gift : gifts.values()) for (String alias : gift.legacy()) {
            validateId(alias);
            if (gifts.containsKey(alias) && !alias.equals(gift.id())) throw new IllegalArgumentException("Reward alias shadows another gift: " + alias);
            String previous = giftAliases.putIfAbsent(alias, gift.id());
            if (previous != null && !previous.equals(gift.id())) throw new IllegalArgumentException("Duplicate reward alias: " + alias);
        }
        for (int level = 1; level <= steps; level++) {
            int target = level;
            long count = perks.values().stream().filter(p -> p.level() == target).count() + gifts.values().stream().filter(g -> g.level() == target).count();
            if (count > 28) throw new IllegalArgumentException("Too many rewards at level " + level);
        }
    }

    private static void validateId(String id) {
        if (!id.matches("[a-z0-9_]+")) throw new IllegalArgumentException("Invalid stable ID: " + id);
    }
    public long threshold(int level) {
        long n = Math.max(0L, Math.min(steps, level) - 1L);
        return base * n + increase * n * (n - 1L) / 2L;
    }
    public int level(long points) {
        int low = 1, high = steps;
        while (low < high) { int mid = (low + high + 1) / 2; if (threshold(mid) <= points) low = mid; else high = mid - 1; }
        return low;
    }
    public String title(int level) { var e = titles.floorEntry(level); return e == null ? "Newcomer" : e.getValue(); }
    public String abbreviation(int level) { var e = abbreviations.floorEntry(level); return e == null ? "NEW" : e.getValue(); }
    public int pages() { return (steps + PATH.length - 1) / PATH.length; }
    public int page(int level) { return (Math.max(1, level) - 1) / PATH.length; }
    public StepStatus status(long points, int target) {
        int current = level(points);
        return target < current || current == steps && target <= current ? StepStatus.COMPLETED :
            target == current ? StepStatus.CURRENT : StepStatus.LOCKED;
    }
    public List<Tier> perkTiers() {
        return perks.values().stream().map(Perk::tier).distinct().sorted().toList();
    }
    public List<Perk> sortedPerks(int level, Tier tier) {
        return perks.values().stream().filter(p -> level == 0 ? p.tier() == tier : p.level() == level)
            .sorted(Comparator.comparing(Perk::tier).thenComparingInt(Perk::menuOrder).thenComparing(Perk::id)).toList();
    }
    public List<PerkPage> perkPages(int level) {
        List<PerkPage> pages = new ArrayList<>();
        for (Tier tier : level == 0 ? perkTiers() : List.of(Tier.EXTRA)) {
            List<Perk> items = sortedPerks(level, tier);
            for (int start = 0; start < items.size(); start += 36)
                pages.add(new PerkPage(level == 0 ? tier : items.get(0).tier(), List.copyOf(items.subList(start, Math.min(items.size(), start + 36)))));
        }
        return pages.isEmpty() ? List.of(new PerkPage(Tier.EXTRA, List.of())) : List.copyOf(pages);
    }
    /** Mirror the five path rows on alternate pages, preserving the header. */
    public static int slot(int page, int index) {
        int slot = PATH[index];
        return page % 2 == 0 ? slot : (6 - slot / 9) * 9 + slot % 9;
    }
    public int percent(long points) {
        int level = level(points);
        return level >= steps ? 100 : (int) Math.max(0, Math.min(100, (points - threshold(level)) * 100 / (threshold(level + 1) - threshold(level))));
    }
    public int remainingPercent(long points) { return 100 - percent(points); }
    public long remaining(long points, int target) { return Math.max(0, threshold(target) - points); }
    public String canonicalGift(String id) { return giftAliases.getOrDefault(id, id); }
    public boolean migrateGiftReceipts(State state) {
        Set<String> claimed = new HashSet<>();
        for (String id : state.claimed) claimed.add(canonicalGift(id));
        String pending = canonicalGift(state.pendingGift);
        boolean changed = !claimed.equals(state.claimed) || !pending.equals(state.pendingGift);
        state.claimed.clear(); state.claimed.addAll(claimed); state.pendingGift = pending;
        if (changed) state.dirty = true;
        return changed;
    }
    /** Import old LP markers without executing any replacement reward. */
    public void importGiftMarkers(State state, Set<String> nodes) {
        for (Gift gift : gifts.values()) {
            boolean marked = nodes.contains(giftMarker(gift.id())) || gift.legacy().stream().anyMatch(id -> nodes.contains(giftMarker(id)));
            if (marked && !state.pendingGift.equals(gift.id())) state.claimed.add(gift.id());
        }
    }
    public long legacyPoints(int oldLevel, long minutes, int questPoints) {
        int level = oldLevel <= 1 ? 1 : Math.min(steps, (oldLevel - 1) * 12);
        return Math.max(threshold(level), Math.max(0, minutes) + Math.max(0, questPoints) * 25L);
    }
    public void inheritLowerPerks(Set<String> owned) {
        for (String id : new ArrayList<>(owned)) for (Perk p = perks.get(id); p != null && !p.requires().isEmpty(); p = perks.get(p.requires())) owned.add(p.requires());
    }
    public boolean purchasable(State state, Perk perk) {
        return perk != null && level(state.points) >= perk.level() && !state.owned.contains(perk.id()) && (perk.requires().isEmpty() || state.owned.contains(perk.requires()));
    }
    public static String perkMarker(String id) { return "townyreborn.journey.perk." + id; }
    public static String giftMarker(String id) { return "townyreborn.journey.reward." + id; }

    public record Gift(String id, String name, int level, List<String> permissions, List<String> commands, boolean townRequired, int townBlocks,
                       double money, String icon, List<String> legacy) {
        public Gift(String id, String name, int level, List<String> permissions, List<String> commands, boolean townRequired, int townBlocks) {
            this(id, name, level, permissions, commands, townRequired, townBlocks, 0, "CHEST", List.of());
        }
    }
    public enum StepStatus { COMPLETED, CURRENT, LOCKED }
    public record PerkPage(Tier tier, List<Perk> items) {}
    public enum Tier {
        SKELETON("Skeleton"), CREEPER("Creeper"), BLAZE("Blaze"), DRAGON("Dragon"), WITHER("Wither"), EXTRA("Extra");
        public final String label;
        Tier(String label) { this.label = label; }
    }
    public record Perk(String id, String name, int level, double price, List<String> permissions, List<String> legacy, String requires, List<String> commands,
                       Tier tier, int menuOrder) {
        public Perk(String id, String name, int level, double price, List<String> permissions, List<String> legacy, String requires, List<String> commands) {
            this(id, name, level, price, permissions, legacy, requires, commands, Tier.EXTRA, level);
        }
    }
}
