package net.worldbinder.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.worldbinder.status.WorldBinderNotifications;

import java.nio.file.Path;

public final class Chat {
    private Chat() {
    }

    public static void info(String message) {
        notifyInfo(Component.literal(message));
    }

    public static void infoKey(String key, Object... args) {
        notifyInfo(Component.translatable(key, args));
    }

    public static void warn(String message) {
        notifyWarn(Component.literal(message));
    }

    public static void warnKey(String key, Object... args) {
        notifyWarn(Component.translatable(key, args));
    }

    public static void error(String message) {
        notifyError(Component.literal(message));
    }

    public static void errorKey(String key, Object... args) {
        notifyError(Component.translatable(key, args));
    }

    public static void savedArchive(String type, String name, int blocks, int blockEntities, int entities, Path path) {
        MutableComponent text = Component.literal("§d◆ WorldBinder ◆ §7")
                .append(Component.translatable("worldbinder.chat.saved_archive.prefix", type))
                .append(Component.literal("§d" + name))
                .append(Component.translatable("worldbinder.chat.saved_archive.stats", blocks, blockEntities, entities));
        notifySuccess(text);
    }

    private static void notifyInfo(Component message) {
        notify(() -> WorldBinderNotifications.info(message));
    }

    private static void notifySuccess(Component message) {
        notify(() -> WorldBinderNotifications.success(message));
    }

    private static void notifyWarn(Component message) {
        notify(() -> WorldBinderNotifications.warn(message));
    }

    private static void notifyError(Component message) {
        notify(() -> WorldBinderNotifications.error(message));
    }

    private static void notify(Runnable action) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.player != null) {
                action.run();
            }
        });
    }
}
