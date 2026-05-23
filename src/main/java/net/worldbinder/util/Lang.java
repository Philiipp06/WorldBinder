package net.worldbinder.util;

import net.minecraft.network.chat.Component;

public final class Lang {
    private Lang() {
    }

    public static Component text(String key, Object... args) {
        return Component.translatable(key, args);
    }

    public static String string(String key, Object... args) {
        return text(key, args).getString();
    }
}
