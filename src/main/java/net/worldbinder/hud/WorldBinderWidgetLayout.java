package net.worldbinder.hud;

import net.worldbinder.config.WorldBinderConfig;

public final class WorldBinderWidgetLayout {
    public static final int SCREEN_MARGIN = 6;

    public enum Widget {
        STATUS,
        RADAR,
        NOTIFICATIONS,
        MOVEMENT
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
            case MOVEMENT -> config.movementBadgeXPercent;
        };
    }

    public static int yPercent(WorldBinderConfig config, Widget widget) {
        return switch (widget) {
            case STATUS -> config.bossbarWidgetYPercent;
            case RADAR -> config.chunkRadarWidgetYPercent;
            case NOTIFICATIONS -> config.notificationWidgetYPercent;
            case MOVEMENT -> config.movementBadgeYPercent;
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
            case MOVEMENT -> {
                config.movementBadgeXPercent = percentX;
                config.movementBadgeYPercent = percentY;
            }
        }
    }

    public static boolean enabled(WorldBinderConfig config, Widget widget) {
        return switch (widget) {
            case STATUS -> config.showBossbarOverlay;
            case RADAR -> config.showChunkRadar;
            case NOTIFICATIONS -> config.effectiveMessageMode().toast();
            case MOVEMENT -> config.showMovementBadges;
        };
    }

    public static void setEnabled(WorldBinderConfig config, Widget widget, boolean enabled) {
        switch (widget) {
            case STATUS -> config.showBossbarOverlay = enabled;
            case RADAR -> config.showChunkRadar = enabled;
            case NOTIFICATIONS -> config.messageMode = enabled
                    ? (config.effectiveMessageMode().chat() ? WorldBinderConfig.MessageMode.CHAT_AND_TOAST : WorldBinderConfig.MessageMode.TOAST)
                    : (config.effectiveMessageMode().chat() ? WorldBinderConfig.MessageMode.CHAT : WorldBinderConfig.MessageMode.NONE);
            case MOVEMENT -> config.showMovementBadges = enabled;
        }
    }

    public static int scale(WorldBinderConfig config, Widget widget) {
        return switch (widget) {
            case STATUS -> config.effectiveBossbarScalePercent();
            case RADAR -> config.effectiveChunkRadarScalePercent();
            case NOTIFICATIONS -> config.effectiveNotificationScalePercent();
            case MOVEMENT -> Math.max(50, Math.min(180, config.movementBadgeScalePercent));
        };
    }

    public static void setScale(WorldBinderConfig config, Widget widget, int scale) {
        int clamped = switch (widget) {
            case STATUS, RADAR, MOVEMENT -> Math.max(50, Math.min(180, scale));
            case NOTIFICATIONS -> Math.max(50, Math.min(160, scale));
        };
        switch (widget) {
            case STATUS -> config.bossbarScalePercent = clamped;
            case RADAR -> config.chunkRadarScalePercent = clamped;
            case NOTIFICATIONS -> config.notificationScalePercent = clamped;
            case MOVEMENT -> config.movementBadgeScalePercent = clamped;
        }
    }

    public static int accentColor(WorldBinderConfig config, Widget widget) {
        return switch (widget) {
            case STATUS -> config.bossbarAccentColor;
            case RADAR -> config.chunkRadarAccentColor;
            case NOTIFICATIONS -> config.notificationAccentColor;
            case MOVEMENT -> config.movementBadgeAccentColor;
        };
    }

    public static void setAccentColor(WorldBinderConfig config, Widget widget, int color) {
        int opaque = 0xFF000000 | (color & 0x00FFFFFF);
        switch (widget) {
            case STATUS -> config.bossbarAccentColor = opaque;
            case RADAR -> config.chunkRadarAccentColor = opaque;
            case NOTIFICATIONS -> config.notificationAccentColor = opaque;
            case MOVEMENT -> config.movementBadgeAccentColor = opaque;
        }
    }

    public static int backgroundColor(WorldBinderConfig config, Widget widget) {
        return switch (widget) {
            case STATUS -> config.bossbarBackgroundColor;
            case RADAR -> config.chunkRadarBackgroundColor;
            case NOTIFICATIONS -> config.notificationBackgroundColor;
            case MOVEMENT -> config.movementBadgeBackgroundColor;
        };
    }

    public static void setBackgroundColor(WorldBinderConfig config, Widget widget, int color) {
        switch (widget) {
            case STATUS -> config.bossbarBackgroundColor = color;
            case RADAR -> config.chunkRadarBackgroundColor = color;
            case NOTIFICATIONS -> config.notificationBackgroundColor = color;
            case MOVEMENT -> config.movementBadgeBackgroundColor = color;
        }
    }

    public static void reset(WorldBinderConfig config, Widget widget) {
        switch (widget) {
            case STATUS -> {
                config.showBossbarOverlay = true;
                config.bossbarScalePercent = 100;
                config.bossbarWidgetXPercent = 50;
                config.bossbarWidgetYPercent = 2;
                config.bossbarAccentColor = 0xFF49D7BB;
                config.bossbarBackgroundColor = 0xE6142020;
            }
            case RADAR -> {
                config.showChunkRadar = true;
                config.chunkRadarScalePercent = 100;
                config.chunkRadarWidgetXPercent = 100;
                config.chunkRadarWidgetYPercent = 12;
                config.chunkRadarAccentColor = 0xFF9AC2FF;
                config.chunkRadarBackgroundColor = 0xE6142020;
            }
            case NOTIFICATIONS -> {
                setEnabled(config, widget, true);
                config.notificationScalePercent = 100;
                config.notificationWidgetXPercent = 100;
                config.notificationWidgetYPercent = 100;
                config.notificationAccentColor = 0xFF9AC2FF;
                config.notificationBackgroundColor = 0xF0142020;
            }
            case MOVEMENT -> {
                config.showMovementBadges = true;
                config.movementBadgeScalePercent = 100;
                config.movementBadgeXPercent = 0;
                config.movementBadgeYPercent = 0;
                config.movementBadgeAccentColor = 0xFF84BFFF;
                config.movementBadgeBackgroundColor = 0xD8121C29;
            }
        }
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
