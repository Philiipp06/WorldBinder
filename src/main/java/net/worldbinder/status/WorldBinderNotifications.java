package net.worldbinder.status;

import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class WorldBinderNotifications {
    private static final int MAX_VISIBLE = 4;
    private static final Deque<Entry> ENTRIES = new ArrayDeque<>();
    private static long nextId;

    private WorldBinderNotifications() {
    }

    public static void info(Component message) {
        show(Type.INFO, Component.translatable("worldbinder.notification.info"), message, 5_500L);
    }

    public static void success(Component message) {
        show(Type.SUCCESS, Component.translatable("worldbinder.notification.success"), message, 6_000L);
    }

    public static void warn(Component message) {
        show(Type.WARN, Component.translatable("worldbinder.notification.warning"), message, 7_000L);
    }

    public static void error(Component message) {
        show(Type.ERROR, Component.translatable("worldbinder.notification.error"), message, 8_500L);
    }

    public static synchronized List<Entry> visible() {
        long now = System.currentTimeMillis();
        ENTRIES.removeIf(entry -> entry.visibleUntil <= now);
        return new ArrayList<>(ENTRIES);
    }

    private static synchronized void show(Type type, Component title, Component message, long durationMillis) {
        long now = System.currentTimeMillis();
        ENTRIES.addFirst(new Entry(++nextId, type, title, message, now, now + durationMillis));
        while (ENTRIES.size() > MAX_VISIBLE) {
            ENTRIES.removeLast();
        }
    }

    public enum Type {
        INFO,
        SUCCESS,
        WARN,
        ERROR
    }

    public record Entry(long id, Type type, Component title, Component message, long createdAt, long visibleUntil) {
        public double lifeProgress(long now) {
            long total = Math.max(1L, visibleUntil - createdAt);
            return Math.max(0.0D, Math.min(1.0D, (now - createdAt) / (double) total));
        }

        public long ageMillis(long now) {
            return Math.max(0L, now - createdAt);
        }
    }
}
