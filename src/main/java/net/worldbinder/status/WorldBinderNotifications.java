package net.worldbinder.status;

import net.minecraft.network.chat.Component;
import net.worldbinder.WorldBinder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class WorldBinderNotifications {
    private static final int MAX_ACTIVE = 4;
    private static final int MAX_PENDING = 32;
    private static final Deque<Entry> ACTIVE = new ArrayDeque<>();
    private static final Deque<PendingEntry> PENDING = new ArrayDeque<>();
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

    public static synchronized List<Entry> visible(int availableSlots) {
        long now = System.currentTimeMillis();
        ACTIVE.removeIf(entry -> entry.visibleUntil <= now);
        int capacity = Math.max(0, Math.min(MAX_ACTIVE, availableSlots));
        demoteOverflow(capacity, now);
        while (ACTIVE.size() < capacity && !PENDING.isEmpty()) {
            PendingEntry pending = PENDING.removeFirst();
            ACTIVE.addFirst(new Entry(
                    pending.id(),
                    pending.type(),
                    pending.title(),
                    pending.message(),
                    now,
                    now + pending.durationMillis()
            ));
        }
        return new ArrayList<>(ACTIVE);
    }

    private static synchronized void show(Type type, Component title, Component message, long durationMillis) {
        long effectiveDuration = WorldBinder.config().effectiveNotificationDurationMillis(durationMillis);
        PENDING.addLast(new PendingEntry(++nextId, type, title, message, effectiveDuration));
        while (PENDING.size() > MAX_PENDING) {
            PENDING.removeFirst();
        }
    }

    public static synchronized void clear() {
        ACTIVE.clear();
        PENDING.clear();
    }

    private static void demoteOverflow(int capacity, long now) {
        List<PendingEntry> demoted = new ArrayList<>();
        while (ACTIVE.size() > capacity) {
            Entry entry = ACTIVE.removeLast();
            demoted.add(new PendingEntry(
                    entry.id(),
                    entry.type(),
                    entry.title(),
                    entry.message(),
                    Math.max(1L, entry.visibleUntil() - now)
            ));
        }
        for (int i = demoted.size() - 1; i >= 0; i--) {
            PENDING.addFirst(demoted.get(i));
        }
        while (PENDING.size() > MAX_PENDING) {
            PENDING.removeFirst();
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

        public long remainingMillis(long now) {
            return Math.max(0L, visibleUntil - now);
        }
    }

    private record PendingEntry(long id, Type type, Component title, Component message, long durationMillis) {
    }
}
