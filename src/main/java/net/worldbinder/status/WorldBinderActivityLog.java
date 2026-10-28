package net.worldbinder.status;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class WorldBinderActivityLog {
    private static final int MAX_ENTRIES = 12;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private record Entry(String time,String message){}
    private static final ArrayDeque<Entry> ENTRIES = new ArrayDeque<>();

    private WorldBinderActivityLog() {
    }

    public static synchronized void add(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        ENTRIES.addFirst(new Entry(LocalTime.now().format(TIME),message));
        while (ENTRIES.size() > MAX_ENTRIES) {
            ENTRIES.removeLast();
        }
    }

    public static synchronized List<String> snapshot() {
        return ENTRIES.stream().map(entry->entry.time()+" • "+net.worldbinder.util.Lang.resolve(entry.message())).toList();
    }
}
