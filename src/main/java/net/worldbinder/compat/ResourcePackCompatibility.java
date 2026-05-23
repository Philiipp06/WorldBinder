package net.worldbinder.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.protocol.Packet;
import net.worldbinder.WorldBinder;
import net.worldbinder.config.WorldBinderConfig;
import net.worldbinder.util.Chat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.UUID;

public final class ResourcePackCompatibility {
    private static boolean warnedThisSession;

    private ResourcePackCompatibility() {
    }

    public static Packet<?> replacementFor(Packet<?> packet) {
        if (packet == null || !WorldBinder.config().resourcePackFallbackEnabled()) {
            return null;
        }
        if (!packet.getClass().getName().equals("net.minecraft.network.protocol.common.ServerboundResourcePackPacket")) {
            return null;
        }
        Object action = readAction(packet);
        if (!isFailureAction(action)) {
            return null;
        }
        if (!fallbackAllowedForCurrentConnection()) {
            return null;
        }
        Object id = readPackId(packet);
        if (!(id instanceof UUID uuid)) {
            return null;
        }
        Packet<?> replacement = successPacket(uuid);
        if (replacement != null) {
            maybeWarn(action);
        }
        return replacement;
    }

    private static boolean fallbackAllowedForCurrentConnection() {
        WorldBinderConfig.ResourcePackFallbackMode mode = WorldBinder.config().resourcePackFallbackMode;
        if (mode == WorldBinderConfig.ResourcePackFallbackMode.DISABLED) {
            return false;
        }
        if (mode == WorldBinderConfig.ResourcePackFallbackMode.ENABLED) {
            return true;
        }
        return viaFabricPlusPresent();
    }

    private static boolean viaFabricPlusPresent() {
        if (!FabricLoader.getInstance().isModLoaded("viafabricplus")) {
            return false;
        }
        // ViaFabricPlus does not expose a stable tiny public API across all versions. Treat its
        // presence as a conservative lower-protocol hint for this compatibility fallback and only
        // react to actual resource-pack failure packets, never to normal success/discard packets.
        return true;
    }

    private static boolean isFailureAction(Object action) {
        if (action == null) {
            return false;
        }
        String name = action instanceof Enum<?> enumValue ? enumValue.name() : String.valueOf(action);
        String upper = name.toUpperCase(Locale.ROOT);
        return upper.contains("FAILED") || upper.contains("INVALID");
    }

    private static Object readAction(Object packet) {
        Object value = invokeNoArg(packet, "action");
        if (value != null) return value;
        value = invokeNoArg(packet, "getAction");
        if (value != null) return value;
        return readFieldByTypeName(packet, "Action");
    }

    private static Object readPackId(Object packet) {
        Object value = invokeNoArg(packet, "id");
        if (value instanceof UUID) return value;
        value = invokeNoArg(packet, "getId");
        if (value instanceof UUID) return value;
        value = readFieldByType(packet, UUID.class);
        return value instanceof UUID ? value : null;
    }

    @SuppressWarnings("unchecked")
    private static Packet<?> successPacket(UUID id) {
        try {
            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.common.ServerboundResourcePackPacket");
            Class<?> actionClass = Class.forName("net.minecraft.network.protocol.common.ServerboundResourcePackPacket$Action");
            Object success = Enum.valueOf((Class<Enum>) actionClass.asSubclass(Enum.class), "SUCCESSFULLY_LOADED");
            Constructor<?> constructor = packetClass.getDeclaredConstructor(UUID.class, actionClass);
            constructor.setAccessible(true);
            return (Packet<?>) constructor.newInstance(id, success);
        } catch (Throwable throwable) {
            WorldBinder.LOGGER.warn("Failed to create fake resource-pack success packet", throwable);
            return null;
        }
    }

    private static Object invokeNoArg(Object target, String name) {
        try {
            Method method = target.getClass().getDeclaredMethod(name);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object readFieldByType(Object target, Class<?> type) {
        for (Field field : target.getClass().getDeclaredFields()) {
            if (type.isAssignableFrom(field.getType())) {
                try {
                    field.setAccessible(true);
                    return field.get(target);
                } catch (Throwable ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static Object readFieldByTypeName(Object target, String typeNamePart) {
        for (Field field : target.getClass().getDeclaredFields()) {
            if (field.getType().getSimpleName().contains(typeNamePart)) {
                try {
                    field.setAccessible(true);
                    return field.get(target);
                } catch (Throwable ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static void maybeWarn(Object action) {
        if (warnedThisSession || !WorldBinder.config().showResourcePackFallbackWarning) {
            return;
        }
        warnedThisSession = true;
        Chat.warnKey("worldbinder.chat.pack_fallback_warning", action);
    }
}
