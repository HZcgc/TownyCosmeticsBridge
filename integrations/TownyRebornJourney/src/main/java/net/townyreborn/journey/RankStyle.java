package net.townyreborn.journey;

import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;

/** A gradual pink-to-lime palette with a subtle per-title text gradient. */
final class RankStyle {
    private final int start, end;
    RankStyle(ConfigurationSection config) {
        start = parse(config.getString("journey.rank-colours.start", "#ff6bd6"));
        end = parse(config.getString("journey.rank-colours.end", "#b8ff60"));
    }
    private static int parse(String hex) {
        if (hex == null || !hex.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Invalid rank colour");
        return Integer.parseInt(hex.substring(1), 16);
    }
    static int mix(int a, int b, double t) {
        int result = 0;
        for (int shift : new int[]{16, 8, 0}) {
            int channel = (int) Math.round(((a >> shift) & 255) * (1 - t) + ((b >> shift) & 255) * t);
            result |= channel << shift;
        }
        return result;
    }
    int colour(int level, int steps) { return mix(start, end, Math.max(0, Math.min(1, (level - 1.0) / Math.max(1, steps - 1)))); }
    private int highlight(int level, int steps) { return mix(colour(level, steps), 0xffffff, .22); }
    private static String hex(int value) { return String.format(Locale.ROOT, "%06x", value); }
    private static String legacyHex(int value) {
        StringBuilder out = new StringBuilder("§x");
        for (char c : hex(value).toCharArray()) out.append('§').append(c);
        return out.toString();
    }
    String legacy(String text, int level, int steps) {
        StringBuilder out = new StringBuilder();
        int[] chars = text.codePoints().toArray();
        for (int i = 0; i < chars.length; i++) {
            out.append(legacyHex(mix(colour(level, steps), highlight(level, steps), i / (double) Math.max(1, chars.length - 1))));
            out.appendCodePoint(chars[i]);
        }
        return out.toString();
    }
    String mini(String text, int level, int steps) {
        // Rank titles come from the server configuration, not player input.
        String escaped = text.replace("\\", "\\\\").replace("<", "\\<");
        return "<gradient:#" + hex(colour(level, steps)) + ":#" + hex(highlight(level, steps)) + ">" + escaped + "</gradient>";
    }
}
