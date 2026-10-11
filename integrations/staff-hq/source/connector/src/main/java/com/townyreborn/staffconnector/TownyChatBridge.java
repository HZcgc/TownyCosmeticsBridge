package com.townyreborn.staffconnector;

import org.bukkit.entity.Player;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Map;

/** Reads TownyChat's API without resolving its unrelated optional integrations. */
final class TownyChatBridge {
    private static final Access UNAVAILABLE = new Access(null, null, null, null, null);
    private static final ClassValue<Access> ACCESS = new ClassValue<>() {
        @Override
        protected Access computeValue(Class<?> pluginClass) {
            try {
                ClassLoader loader = pluginClass.getClassLoader();
                Class<?> channel = Class.forName(
                        "com.palmergames.bukkit.TownyChat.channels.Channel", false, loader);
                Class<?> holder = Class.forName(
                        "com.palmergames.bukkit.TownyChat.channels.ChannelsHolder", false, loader);
                return new Access(
                        virtual(pluginClass, "getPlayerChannel", channel, Player.class),
                        virtual(pluginClass, "getChannelsHandler", holder),
                        virtual(holder, "getAllChannels", Map.class),
                        virtual(channel, "getName", String.class),
                        virtual(channel, "getCommands", List.class));
            } catch (ClassNotFoundException | LinkageError | RuntimeException ignored) {
                return UNAVAILABLE;
            }
        }
    };

    private TownyChatBridge() {
    }

    static Object playerChannel(Object plugin, Player player) {
        if (plugin == null || player == null) return null;
        return invoke(ACCESS.get(plugin.getClass()).playerChannel(), plugin, player);
    }

    static Object channels(Object plugin) {
        if (plugin == null) return null;
        Access access = ACCESS.get(plugin.getClass());
        Object holder = invoke(access.channelsHolder(), plugin);
        return holder == null ? null : invoke(access.allChannels(), holder);
    }

    static String channelName(Object plugin, Object channel) {
        if (plugin == null || channel == null) return null;
        Object result = invoke(ACCESS.get(plugin.getClass()).channelName(), channel);
        return result instanceof String name ? name : null;
    }

    static Object commands(Object plugin, Object channel) {
        if (plugin == null || channel == null) return null;
        return invoke(ACCESS.get(plugin.getClass()).commands(), channel);
    }

    private static MethodHandle virtual(Class<?> owner, String name, Class<?> result, Class<?>... parameters) {
        try {
            // findVirtual resolves only this exact signature. Class.getMethods()
            // and Class.getMethod() would also resolve Chat.getDynmap() and fail
            // when Dynmap is not installed.
            return MethodHandles.publicLookup().findVirtual(owner, name, MethodType.methodType(result, parameters));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return null;
        }
    }

    private static Object invoke(MethodHandle method, Object... arguments) {
        if (method == null) return null;
        try {
            return method.invokeWithArguments(arguments);
        } catch (Throwable failure) {
            // MethodHandle invocation declares Throwable. Missing optional APIs
            // and integration exceptions are safe to ignore; VM errors are not.
            if (failure instanceof Error error && !(error instanceof LinkageError)) throw error;
            return null;
        }
    }

    private record Access(MethodHandle playerChannel, MethodHandle channelsHolder,
                          MethodHandle allChannels, MethodHandle channelName, MethodHandle commands) {
    }
}
