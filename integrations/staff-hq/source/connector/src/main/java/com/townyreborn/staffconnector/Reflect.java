package com.townyreborn.staffconnector;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.UUID;

final class Reflect {
    private Reflect() {
    }

    static Object call(Object target, String... names) {
        if (target == null) return null;
        for (String name : names) {
            try {
                Method method = target.getClass().getMethod(name);
                if (method.getParameterCount() == 0) {
                    // LiteBans and a few other plugins expose public API methods through
                    // package-private implementation classes. A public Method obtained
                    // from that implementation can still fail invocation access checks.
                    // Making it accessible here keeps the integration independent of the
                    // plugin's obfuscated/private concrete class names.
                    if (!method.canAccess(target)) method.trySetAccessible();
                    return method.invoke(target);
                }
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            }
        }
        return null;
    }

    static Object callOneArgument(Object target, Object argument, String... names) {
        if (target == null) return null;
        for (String name : names) {
            for (Method method : publicMethods(target.getClass())) {
                if (!method.getName().equals(name) || method.getParameterCount() != 1) continue;
                Class<?> expected = wrap(method.getParameterTypes()[0]);
                if (argument == null || expected.isInstance(argument)) {
                    try {
                        Object receiver = Modifier.isStatic(method.getModifiers()) ? null : target;
                        if (!method.canAccess(receiver)) method.trySetAccessible();
                        return method.invoke(receiver, argument);
                    } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
                    }
                }
            }
        }
        return null;
    }

    static Object callStatic(Class<?> type, String methodName, Object... arguments) {
        for (Method method : publicMethods(type)) {
            if (!method.getName().equals(methodName) || !Modifier.isStatic(method.getModifiers())
                    || method.getParameterCount() != arguments.length) continue;
            boolean compatible = true;
            for (int i = 0; i < arguments.length; i++) {
                if (arguments[i] != null && !wrap(method.getParameterTypes()[i]).isInstance(arguments[i])) {
                    compatible = false;
                    break;
                }
            }
            if (!compatible) continue;
            try {
                return method.invoke(null, arguments);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            }
        }
        return null;
    }

    static Method[] publicMethods(Class<?> type) {
        if (type == null) return new Method[0];
        try {
            // Resolving the method list also resolves unrelated parameter/return
            // types. Optional plugin dependencies may be absent at this point.
            return type.getMethods();
        } catch (LinkageError | SecurityException ignored) {
            return new Method[0];
        }
    }

    static String string(Object target, String fallback, String... names) {
        Object value = call(target, names);
        if (value == null) return fallback;
        String result = String.valueOf(value).trim();
        return result.isEmpty() ? fallback : result;
    }

    static double number(Object target, double fallback, String... names) {
        Object value = call(target, names);
        if (value instanceof Number number) return number.doubleValue();
        if (value != null) {
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    static long longNumber(Object target, long fallback, String... names) {
        double value = number(target, Double.NaN, names);
        return Double.isFinite(value) ? (long) value : fallback;
    }

    static boolean bool(Object target, boolean fallback, String... names) {
        Object value = call(target, names);
        if (value instanceof Boolean bool) return bool;
        if (value != null) return Boolean.parseBoolean(String.valueOf(value));
        return fallback;
    }

    static UUID uuid(Object target, String... names) {
        Object value = call(target, names);
        if (value instanceof UUID uuid) return uuid;
        if (value != null) {
            try {
                return UUID.fromString(String.valueOf(value));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == char.class) return Character.class;
        return type;
    }
}
