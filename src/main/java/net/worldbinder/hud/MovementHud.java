package net.worldbinder.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.worldbinder.config.WorldBinderConfig;
import net.worldbinder.movement.MovementController;
import net.worldbinder.movement.MovementTool;
import net.worldbinder.ui.component.WbChrome;
import net.worldbinder.ui.component.WbIcon;
import net.worldbinder.ui.component.WbMotion;
import net.worldbinder.ui.component.WbText;
import net.worldbinder.ui.component.WbTheme;
import net.worldbinder.util.Lang;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MovementHud {
    private static final int BADGE_HEIGHT = 20;
    private static final int ICON_TILE = 20;
    private static final int GAP = 3;
    private static final int ROWS = 8;
    private static final long FLASH_MILLIS = 1800L;

    private static Component flashMessage;
    private static MovementTool flashTool;
    private static boolean flashEnabled;
    private static long flashStartMillis;

    private MovementHud() {
    }

    /** Shows a short centered banner after a movement toggle key was pressed. */
    public static void flash(Component message, MovementTool tool, boolean enabled) {
        flashMessage = message;
        flashTool = tool;
        flashEnabled = enabled;
        flashStartMillis = System.currentTimeMillis();
    }

    public static boolean hasActive(WorldBinderConfig config) {
        if (config.movement == null) return false;
        if (!config.movementMasterEnabled) return config.movement.enabledCount() > 0;
        return config.movement.enabledCount() > 0;
    }

    public static WorldBinderHud.WidgetBounds bounds(Minecraft client, WorldBinderConfig config, boolean preview) {
        List<MovementTool> tools = visibleTools(config, preview);
        Size size = logicalSize(client, config, tools, preview);
        float scale = scale(client, config, size);
        int width = Math.round(size.width() * scale);
        int height = Math.round(size.height() * scale);
        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();
        int x = WorldBinderWidgetLayout.positionX(screenW, width, config.movementBadgeXPercent);
        int y = WorldBinderWidgetLayout.positionY(screenH, height, config.movementBadgeYPercent);
        return new WorldBinderHud.WidgetBounds(x, y, width, height);
    }

    public static void draw(GuiGraphicsExtractor graphics, Minecraft client, WorldBinderConfig config, boolean preview) {
        if (!preview) {
            drawFlash(graphics, client);
        }
        if (!preview && !config.showMovementBadges) {
            return;
        }
        List<MovementTool> tools = visibleTools(config, preview);
        if (tools.isEmpty()) {
            return;
        }
        WorldBinderHud.WidgetBounds bounds = bounds(client, config, preview);
        Size size = logicalSize(client, config, tools, preview);
        float scale = scale(client, config, size);
        boolean paused = !preview && !MovementController.masterActive();
        int accent = 0xFF000000 | (config.movementBadgeAccentColor & 0x00FFFFFF);
        if (paused) {
            accent = WbTheme.TEXT_DISABLED;
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(bounds.x(), bounds.y());
        graphics.pose().scale(scale, scale);
        try {
            int offsetY = 0;
            if (paused) {
                drawPausedHeader(graphics, client.font, size.width());
                offsetY = BADGE_HEIGHT + GAP;
            }
            WorldBinderConfig.MovementHudStyle style = config.effectiveMovementHudStyle();
            for (int i = 0; i < tools.size(); i++) {
                MovementTool tool = tools.get(i);
                if (style == WorldBinderConfig.MovementHudStyle.ICONS) {
                    int perRow = Math.max(1, size.columns());
                    int x = (i % perRow) * (ICON_TILE + GAP);
                    int y = offsetY + (i / perRow) * (ICON_TILE + GAP);
                    drawIconTile(graphics, config, tool, x, y, accent);
                } else {
                    int x = (i / ROWS) * (size.badgeWidth() + GAP);
                    int y = offsetY + (i % ROWS) * (BADGE_HEIGHT + GAP);
                    drawBadge(graphics, client.font, config, tool, x, y, size.badgeWidth(), accent, style);
                }
            }
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private static void drawPausedHeader(GuiGraphicsExtractor graphics, Font font, int width) {
        WbChrome.rounded(graphics, 0, 0, width, BADGE_HEIGHT, 0xD8301A20);
        WbChrome.roundedOutline(graphics, 0, 0, width, BADGE_HEIGHT, WbTheme.withAlpha(WbTheme.ERROR, 0xAA));
        WbIcon.POWER.draw(graphics, 6, 6, 1, WbTheme.ERROR);
        WbText.drawClipped(graphics, font, Lang.string("worldbinder.movement.hud.paused"), 18, 6, width - 24, WbTheme.TEXT_SOFT);
    }

    private static void drawBadge(GuiGraphicsExtractor graphics, Font font, WorldBinderConfig config, MovementTool tool,
                                  int x, int y, int width, int accent, WorldBinderConfig.MovementHudStyle style) {
        int background = config.movementBadgeBackgroundColor;
        WbChrome.rounded(graphics, x, y + 1, width, BADGE_HEIGHT, 0x50000000);
        WbChrome.rounded(graphics, x, y, width, BADGE_HEIGHT, WbMotion.mix(background, accent, 0.18F));
        WbChrome.rounded(graphics, x + 1, y + 1, width - 2, BADGE_HEIGHT - 2, background);
        graphics.fill(x + 1, y + 3, x + 3, y + BADGE_HEIGHT - 3, accent);
        WbChrome.rounded(graphics, x + 5, y + 3, 14, 14, WbTheme.withAlpha(accent, 0x2E));
        WbIcon.movement(tool).draw(graphics, x + 8, y + 6, 1, accent);
        String name = style == WorldBinderConfig.MovementHudStyle.COMPACT
                ? Lang.string(tool.key() + ".badge") : Lang.string(tool.key());
        String value = valueLabel(config, tool);
        int valueWidth = value == null ? 0 : font.width(value) + 6;
        WbText.drawClipped(graphics, font, name, x + 23, y + 6, width - 28 - valueWidth, WbTheme.TEXT);
        if (value != null) {
            graphics.text(font, value, x + width - font.width(value) - 5, y + 6, WbMotion.mix(accent, WbTheme.TEXT, 0.4F), false);
        }
    }

    private static void drawIconTile(GuiGraphicsExtractor graphics, WorldBinderConfig config, MovementTool tool, int x, int y, int accent) {
        WbChrome.rounded(graphics, x, y + 1, ICON_TILE, ICON_TILE, 0x50000000);
        WbChrome.rounded(graphics, x, y, ICON_TILE, ICON_TILE, WbMotion.mix(config.movementBadgeBackgroundColor, accent, 0.25F));
        WbChrome.rounded(graphics, x + 1, y + 1, ICON_TILE - 2, ICON_TILE - 2, config.movementBadgeBackgroundColor);
        WbIcon.movement(tool).draw(graphics, x + 3, y + 3, 2, accent);
    }

    private static void drawFlash(GuiGraphicsExtractor graphics, Minecraft client) {
        if (flashMessage == null) {
            return;
        }
        long age = System.currentTimeMillis() - flashStartMillis;
        if (age > FLASH_MILLIS) {
            flashMessage = null;
            return;
        }
        float in = WbMotion.easeOutBack(Math.min(1.0F, age / 180.0F));
        float out = 1.0F - WbMotion.clamp01((age - (FLASH_MILLIS - 350L)) / 350.0F);
        float alpha = WbMotion.clamp01(Math.min(in, out));
        Font font = client.font;
        String text = flashMessage.getString();
        int width = font.width(text) + 34;
        int height = 20;
        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();
        int x = (screenW - width) / 2;
        int y = screenH - 78 + Math.round((1.0F - in) * 8.0F);
        int accent = flashEnabled ? WbTheme.OK : WbTheme.TEXT_DIM;
        WbChrome.softShadow(graphics, x, y, width, height, Math.round(0x90 * alpha));
        WbChrome.rounded(graphics, x, y, width, height, WbTheme.alpha(WbMotion.mix(0xFF2A3542, accent, 0.4F), alpha));
        WbChrome.rounded(graphics, x + 1, y + 1, width - 2, height - 2, WbTheme.alpha(0xF0141B24, alpha));
        WbIcon icon = flashTool == null ? WbIcon.POWER : WbIcon.movement(flashTool);
        icon.draw(graphics, x + 8, y + 6, 1, WbTheme.alpha(accent, alpha));
        graphics.text(font, text, x + 22, y + 6, WbTheme.alpha(WbTheme.TEXT, alpha), true);
    }

    private static String valueLabel(WorldBinderConfig config, MovementTool tool) {
        if (!config.movementHudShowValues || !tool.adjustable() || config.movement == null) {
            return null;
        }
        double value = config.movement.value(tool);
        return switch (tool.unit) {
            case BLOCKS -> String.format(Locale.ROOT, "%.1fb", value);
            case GRAVITY -> Math.round(value * 100) + "%";
            default -> String.format(Locale.ROOT, "%.1fx", value);
        };
    }

    private static List<MovementTool> activeTools(WorldBinderConfig config) {
        List<MovementTool> result = new ArrayList<>();
        if (config.movement == null) {
            return result;
        }
        for (MovementTool tool : MovementTool.values()) {
            if (config.movement.enabled(tool)) {
                result.add(tool);
            }
        }
        return result;
    }

    private static List<MovementTool> visibleTools(WorldBinderConfig config, boolean preview) {
        List<MovementTool> tools = activeTools(config);
        return preview && tools.isEmpty() ? List.of(MovementTool.SPEED, MovementTool.FLY, MovementTool.STEP) : tools;
    }

    private record Size(int width, int height, int badgeWidth, int columns) {
    }

    private static Size logicalSize(Minecraft client, WorldBinderConfig config, List<MovementTool> tools, boolean preview) {
        int count = Math.max(1, tools.size());
        boolean paused = !preview && !MovementController.masterActive() && !tools.isEmpty();
        int header = paused ? BADGE_HEIGHT + GAP : 0;
        WorldBinderConfig.MovementHudStyle style = config.effectiveMovementHudStyle();
        if (style == WorldBinderConfig.MovementHudStyle.ICONS) {
            int columns = Math.min(count, 6);
            int rows = (count + columns - 1) / columns;
            int width = columns * ICON_TILE + (columns - 1) * GAP;
            if (paused) width = Math.max(width, client.font.width(Lang.string("worldbinder.movement.hud.paused")) + 26);
            return new Size(width, header + rows * ICON_TILE + (rows - 1) * GAP, ICON_TILE, columns);
        }
        int badgeWidth = badgeWidth(client, config, tools);
        int cols = Math.max(1, (count + ROWS - 1) / ROWS);
        int rows = Math.min(ROWS, count);
        int width = cols * badgeWidth + (cols - 1) * GAP;
        if (paused) width = Math.max(width, client.font.width(Lang.string("worldbinder.movement.hud.paused")) + 26);
        return new Size(width, header + rows * BADGE_HEIGHT + (rows - 1) * GAP, badgeWidth, cols);
    }

    private static int badgeWidth(Minecraft client, WorldBinderConfig config, List<MovementTool> tools) {
        boolean compact = config.effectiveMovementHudStyle() == WorldBinderConfig.MovementHudStyle.COMPACT;
        int width = compact ? 48 : 72;
        for (MovementTool tool : tools) {
            String name = compact ? Lang.string(tool.key() + ".badge") : Lang.string(tool.key());
            String value = valueLabel(config, tool);
            width = Math.max(width, client.font.width(name) + 32 + (value == null ? 0 : client.font.width(value) + 6));
        }
        return Math.min(compact ? 96 : 150, width);
    }

    private static float scale(Minecraft client, WorldBinderConfig config, Size size) {
        float requested = Math.max(50, Math.min(180, config.movementBadgeScalePercent)) / 100.0F;
        float fit = Math.min((client.getWindow().getGuiScaledWidth() - 12) / (float) Math.max(1, size.width()),
                (client.getWindow().getGuiScaledHeight() - 12) / (float) Math.max(1, size.height()));
        return Math.max(0.2F, Math.min(requested, fit));
    }
}
