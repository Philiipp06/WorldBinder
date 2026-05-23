package net.worldbinder.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.nio.file.Path;

public final class Chat {
    private Chat() {
    }

    public static void info(String message) {
        send(Component.literal("§d◆ WorldBinder ◆ §7" + message));
    }

    public static void infoKey(String key, Object... args) {
        send(prefixed("§7", Component.translatable(key, args)));
    }

    public static void warn(String message) {
        send(Component.literal("§d◆ WorldBinder ◆ §e" + message));
    }

    public static void warnKey(String key, Object... args) {
        send(prefixed("§e", Component.translatable(key, args)));
    }

    public static void error(String message) {
        send(Component.literal("§d◆ WorldBinder ◆ §c" + message));
    }

    public static void errorKey(String key, Object... args) {
        send(prefixed("§c", Component.translatable(key, args)));
    }

    public static void savedArchive(String type, String name, int blocks, int blockEntities, int entities, Path path) {
        boolean folder = java.nio.file.Files.isDirectory(path);
        MutableComponent text = Component.literal("§d◆ WorldBinder ◆ §7")
                .append(Component.translatable("worldbinder.chat.saved_archive.prefix", type))
                .append(Component.literal("§d" + name)
                        .setStyle(Style.EMPTY
                                .withColor(ChatFormatting.LIGHT_PURPLE)
                                .withClickEvent(new ClickEvent.OpenFile(path.toAbsolutePath().toString()))
                                .withHoverEvent(new HoverEvent.ShowText(Component.translatable(folder ? "worldbinder.tooltip.open_export_folder" : "worldbinder.tooltip.open_archive_file")))))
                .append(Component.translatable("worldbinder.chat.saved_archive.stats", blocks, blockEntities, entities));
        if (folder) {
            text.append(Component.translatable("worldbinder.chat.saved_archive.click_hint"));
        }
        send(text);
    }

    private static MutableComponent prefixed(String color, Component message) {
        return Component.literal("§d◆ WorldBinder ◆ " + color).append(message);
    }

    private static void send(Component message) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.player != null) {
                client.gui.getChat().addClientSystemMessage(message);
            }
        });
    }
}
