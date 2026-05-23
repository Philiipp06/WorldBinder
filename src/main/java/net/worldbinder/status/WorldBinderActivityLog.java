package net.worldbinder.status;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class WorldBinderActivityLog {
    private static final int MAX_ENTRIES = 12;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final ArrayDeque<String> ENTRIES = new ArrayDeque<>();

    private WorldBinderActivityLog() {
    }

    public static synchronized void add(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        ENTRIES.addFirst(LocalTime.now().format(TIME) + " • " + message);
        while (ENTRIES.size() > MAX_ENTRIES) {
            ENTRIES.removeLast();
        }
    }

    public static synchronized List<String> snapshot() {
        return new ArrayList<>(ENTRIES);
    }
}
