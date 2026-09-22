package net.worldbinder.util;

import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.worldbinder.WorldBinder;
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
        Component text = Component.translatable(
                "worldbinder.chat.saved_archive.clean",
                type,
                name,
                blocks,
                blockEntities,
                entities
        );
        notifySuccess(text);
    }

    private static void notifyInfo(Component message) {
        notify(message, ChatType.INFO, () -> WorldBinderNotifications.info(message));
    }

    private static void notifySuccess(Component message) {
        notify(message, ChatType.SUCCESS, () -> WorldBinderNotifications.success(message));
    }

    private static void notifyWarn(Component message) {
        notify(message, ChatType.WARNING, () -> WorldBinderNotifications.warn(message));
    }

    private static void notifyError(Component message) {
        notify(message, ChatType.ERROR, () -> WorldBinderNotifications.error(message));
    }

    private static void notify(Component message, ChatType type, Runnable notificationAction) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.player != null) {
                if (WorldBinder.config().effectiveMessageMode().toast()) {
                    notificationAction.run();
                }
                if (WorldBinder.config().effectiveMessageMode().chat()) {
                    client.gui.chatListener().handleSystemMessage(chatLine(message, type), false);
                }
            }
        });
    }

    private static Component chatLine(Component message, ChatType type) {
        String body = stripLegacyFormatting(message == null ? "" : message.getString());
        return Component.empty()
                .append(Component.literal("WorldBinder").withStyle(type.color, ChatFormatting.BOLD))
                .append(Component.literal("  |  ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(body).withStyle(ChatFormatting.GRAY));
    }

    private static String stripLegacyFormatting(String value) {
        return value == null ? "" : value.replaceAll("(?i)§[0-9A-FK-OR]", "").strip();
    }

    private enum ChatType {
        INFO(ChatFormatting.AQUA),
        SUCCESS(ChatFormatting.GREEN),
        WARNING(ChatFormatting.GOLD),
        ERROR(ChatFormatting.RED);

        private final ChatFormatting color;

        ChatType(ChatFormatting color) {
            this.color = color;
        }
    }
}
