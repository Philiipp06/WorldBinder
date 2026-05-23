package net.worldbinder.status;

public final class OperationStatus {
    private static String title = "WorldBinder";
    private static String detail = "";
    private static double progress = 0.0D;
    private static long visibleUntil = 0L;
    private static boolean active = false;

    private OperationStatus() {
    }

    public static void begin(String newTitle, String newDetail) {
        title = newTitle;
        detail = newDetail;
        progress = 0.0D;
        active = true;
        visibleUntil = System.currentTimeMillis() + 8_000L;
    }

    public static void update(String newDetail, double newProgress) {
        detail = newDetail;
        progress = Math.max(0.0D, Math.min(1.0D, newProgress));
        active = true;
        visibleUntil = System.currentTimeMillis() + 8_000L;
    }

    public static void finish(String newDetail) {
        detail = newDetail;
        progress = 1.0D;
        active = false;
        visibleUntil = System.currentTimeMillis() + 6_000L;
    }

    public static boolean visible() {
        return active || System.currentTimeMillis() < visibleUntil;
    }

    public static boolean active() {
        return active;
    }

    public static String title() {
        return title;
    }

    public static String detail() {
        return detail == null || detail.isBlank() ? net.worldbinder.util.Lang.string("worldbinder.storage.detail.ready") : detail;
    }

    public static double progress() {
        return progress;
    }
}
