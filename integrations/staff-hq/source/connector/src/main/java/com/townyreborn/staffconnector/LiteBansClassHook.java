package com.townyreborn.staffconnector;

import litebans.api.Entry;
import litebans.api.Events;

import java.util.function.Consumer;

/**
 * Loaded only when the installed LiteBans API exposes Events.Listener as a class.
 * LiteBans itself supplies these API classes at runtime; they are not bundled in this plugin.
 */
final class LiteBansClassHook extends Events.Listener {
    private final Consumer<Object> callback;

    LiteBansClassHook(Consumer<Object> callback) {
        this.callback = callback;
    }

    @Override
    public void entryAdded(Entry entry) {
        callback.accept(entry);
    }
}
