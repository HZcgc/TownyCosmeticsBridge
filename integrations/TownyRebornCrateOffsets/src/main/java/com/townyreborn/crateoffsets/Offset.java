package com.townyreborn.crateoffsets;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

record Offset(double x, double y, double z) {
    static final Offset ZERO = new Offset(0, 0, 0);

    Offset {
        for (double value : new double[]{x, y, z})
            if (!Double.isFinite(value) || Math.abs(value) > 32)
                throw new IllegalArgumentException("Offsets must be finite numbers between -32 and 32 blocks.");
    }

    boolean zero() { return x == 0 && y == 0 && z == 0; }

    static Map<String, Offset> read(ConfigurationSection config) {
        Map<String, Offset> result = new HashMap<>();
        ConfigurationSection offsets = config.getConfigurationSection("offsets");
        if (offsets == null) throw new IllegalArgumentException("Missing offsets section.");
        for (String id : offsets.getKeys(false)) {
            ConfigurationSection values = offsets.getConfigurationSection(id);
            if (values == null) throw new IllegalArgumentException("Invalid offset for crate " + id);
            result.put(id, new Offset(number(values, "x"), number(values, "y"), number(values, "z")));
        }
        return Map.copyOf(result);
    }

    private static double number(ConfigurationSection section, String key) {
        Object value = section.get(key, 0.0);
        if (!(value instanceof Number number)) throw new IllegalArgumentException(key + " must be a number.");
        return number.doubleValue();
    }
}
