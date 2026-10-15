package com.townyreborn.staffconnector;

import java.util.Map;

/** Capabilities of the PHP code that actually answered, including hosts stripping custom headers. */
record PortalCompatibility(int apiVersion, Boolean journey) {
    static final PortalCompatibility UNKNOWN = new PortalCompatibility(0, null);

    static PortalCompatibility read(String header, Map<?, ?> body) {
        int version = version(body.get("api_version"));
        if (version == 0) version = version(body.get("version"));
        if (version == 0) version = version(header);
        Object support = body.get("journey");
        if (body.get("capabilities") instanceof Map<?, ?> capabilities
                && capabilities.get("journey") instanceof Boolean) support = capabilities.get("journey");
        return new PortalCompatibility(version, support instanceof Boolean value ? value : null);
    }

    boolean known() { return apiVersion > 0 || journey != null; }
    boolean journeyUnavailable() { return Boolean.FALSE.equals(journey) || apiVersion > 0 && apiVersion < 8; }

    private static int version(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        if (!text.matches("[1-9][0-9]{0,3}")) return 0;
        return Integer.parseInt(text);
    }
}
