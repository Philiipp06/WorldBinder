package net.worldbinder.hud;

import net.worldbinder.config.WorldBinderConfig;

public final class WorldBinderWidgetLayout {
    public static final int SCREEN_MARGIN = 6;

    public enum Widget {
        STATUS,
        RADAR,
        NOTIFICATIONS
    }

    private WorldBinderWidgetLayout() {
    }

    public static int positionX(int screenWidth, int widgetWidth, int percent) {
        int available = Math.max(0, screenWidth - widgetWidth - SCREEN_MARGIN * 2);
        return SCREEN_MARGIN + Math.round(available * clampPercent(percent) / 100.0F);
    }

    public static int positionY(int screenHeight, int widgetHeight, int percent) {
        int available = Math.max(0, screenHeight - widgetHeight - SCREEN_MARGIN * 2);
        return SCREEN_MARGIN + Math.round(available * clampPercent(percent) / 100.0F);
    }

    public static int percentX(int screenWidth, int widgetWidth, int x) {
        int available = Math.max(1, screenWidth - widgetWidth - SCREEN_MARGIN * 2);
        return clampPercent(Math.round((x - SCREEN_MARGIN) * 100.0F / available));
    }

    public static int percentY(int screenHeight, int widgetHeight, int y) {
        int available = Math.max(1, screenHeight - widgetHeight - SCREEN_MARGIN * 2);
        return clampPercent(Math.round((y - SCREEN_MARGIN) * 100.0F / available));
    }

    public static int xPercent(WorldBinderConfig config, Widget widget) {
        return switch (widget) {
            case STATUS -> config.bossbarWidgetXPercent;
            case RADAR -> config.chunkRadarWidgetXPercent;
            case NOTIFICATIONS -> config.notificationWidgetXPercent;
        };
    }

    public static int yPercent(WorldBinderConfig config, Widget widget) {
        return switch (widget) {
            case STATUS -> config.bossbarWidgetYPercent;
            case RADAR -> config.chunkRadarWidgetYPercent;
            case NOTIFICATIONS -> config.notificationWidgetYPercent;
        };
    }

    public static void setPosition(
            WorldBinderConfig config,
            Widget widget,
            int screenWidth,
            int screenHeight,
            int widgetWidth,
            int widgetHeight,
            int x,
            int y
    ) {
        int percentX = percentX(screenWidth, widgetWidth, x);
        int percentY = percentY(screenHeight, widgetHeight, y);
        switch (widget) {
            case STATUS -> {
                config.bossbarWidgetXPercent = percentX;
                config.bossbarWidgetYPercent = percentY;
            }
            case RADAR -> {
                config.chunkRadarWidgetXPercent = percentX;
                config.chunkRadarWidgetYPercent = percentY;
            }
            case NOTIFICATIONS -> {
                config.notificationWidgetXPercent = percentX;
                config.notificationWidgetYPercent = percentY;
            }
        }
    }

    public static boolean enabled(WorldBinderConfig config, Widget widget) {
        return switch (widget) {
            case STATUS -> config.showBossbarOverlay;
            case RADAR -> config.showChunkRadar;
            case NOTIFICATIONS -> config.showNotifications;
        };
    }

    public static void setEnabled(WorldBinderConfig config, Widget widget, boolean enabled) {
        switch (widget) {
            case STATUS -> config.showBossbarOverlay = enabled;
            case RADAR -> config.showChunkRadar = enabled;
            case NOTIFICATIONS -> config.showNotifications = enabled;
        }
    }

    public static int scale(WorldBinderConfig config, Widget widget) {
        return switch (widget) {
            case STATUS -> config.effectiveBossbarScalePercent();
            case RADAR -> config.effectiveChunkRadarScalePercent();
            case NOTIFICATIONS -> config.effectiveNotificationScalePercent();
        };
    }

    public static void setScale(WorldBinderConfig config, Widget widget, int scale) {
        int clamped = switch (widget) {
            case STATUS, RADAR -> Math.max(50, Math.min(180, scale));
            case NOTIFICATIONS -> Math.max(50, Math.min(160, scale));
        };
        switch (widget) {
            case STATUS -> config.bossbarScalePercent = clamped;
            case RADAR -> config.chunkRadarScalePercent = clamped;
            case NOTIFICATIONS -> config.notificationScalePercent = clamped;
        }
    }

    public static int accentColor(WorldBinderConfig config, Widget widget) {
        return switch (widget) {
            case STATUS -> config.bossbarAccentColor;
            case RADAR -> config.chunkRadarAccentColor;
            case NOTIFICATIONS -> config.notificationAccentColor;
        };
    }

    public static void setAccentColor(WorldBinderConfig config, Widget widget, int color) {
        int opaque = 0xFF000000 | (color & 0x00FFFFFF);
        switch (widget) {
            case STATUS -> config.bossbarAccentColor = opaque;
            case RADAR -> config.chunkRadarAccentColor = opaque;
            case NOTIFICATIONS -> config.notificationAccentColor = opaque;
        }
    }

    public static int backgroundColor(WorldBinderConfig config, Widget widget) {
        return switch (widget) {
            case STATUS -> config.bossbarBackgroundColor;
            case RADAR -> config.chunkRadarBackgroundColor;
            case NOTIFICATIONS -> config.notificationBackgroundColor;
        };
    }

    public static void setBackgroundColor(WorldBinderConfig config, Widget widget, int color) {
        switch (widget) {
            case STATUS -> config.bossbarBackgroundColor = color;
            case RADAR -> config.chunkRadarBackgroundColor = color;
            case NOTIFICATIONS -> config.notificationBackgroundColor = color;
        }
    }

    public static void reset(WorldBinderConfig config, Widget widget) {
        switch (widget) {
            case STATUS -> {
                config.showBossbarOverlay = true;
                config.bossbarScalePercent = 100;
                config.bossbarWidgetXPercent = 50;
                config.bossbarWidgetYPercent = 2;
                config.bossbarAccentColor = 0xFF30D5C8;
                config.bossbarBackgroundColor = 0xE6121A24;
            }
            case RADAR -> {
                config.showChunkRadar = true;
                config.chunkRadarScalePercent = 100;
                config.chunkRadarWidgetXPercent = 100;
                config.chunkRadarWidgetYPercent = 12;
                config.chunkRadarAccentColor = 0xFF66A9FF;
                config.chunkRadarBackgroundColor = 0xE6111822;
            }
            case NOTIFICATIONS -> {
                config.showNotifications = true;
                config.notificationScalePercent = 80;
                config.notificationWidgetXPercent = 100;
                config.notificationWidgetYPercent = 100;
                config.notificationAccentColor = 0xFF66A9FF;
                config.notificationBackgroundColor = 0xF0121A24;
            }
        }
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
