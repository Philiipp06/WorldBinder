package net.worldbinder.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.worldbinder.config.WorldBinderConfig;
import net.worldbinder.movement.MovementTool;
import net.worldbinder.ui.component.WbTheme;
import net.worldbinder.ui.component.WbText;
import net.worldbinder.util.Lang;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class MovementHud {
    private static final int BADGE_HEIGHT = 22;
    private static final int GAP = 3;
    private static final int ROWS = 7;
    private static final Map<MovementTool, String[]> ICONS = new EnumMap<>(MovementTool.class);

    static {
        ICONS.put(MovementTool.SPEED, icon("1000100", "1100110", "1110111", "1111111", "1110111", "1100110", "1000100"));
        ICONS.put(MovementTool.FLY, icon("1000001", "1100011", "1110111", "0111110", "0011100", "0001000", "0001000"));
        ICONS.put(MovementTool.SPIDER, icon("1001100", "1011000", "1011110", "1010010", "1010010", "1010010", "1011110"));
        ICONS.put(MovementTool.JESUS, icon("0010100", "0010100", "1111111", "0101010", "1111111", "0000000", "1111111"));
        ICONS.put(MovementTool.NO_FALL, icon("0111110", "1100011", "1010101", "1001001", "1001001", "0100010", "0011100"));
        ICONS.put(MovementTool.STEP, icon("0000001", "0000001", "0000111", "0000100", "0011100", "0010000", "1110000"));
        ICONS.put(MovementTool.HIGH_JUMP, icon("0001000", "0011100", "0111110", "0001000", "0001000", "0001000", "0001000"));
        ICONS.put(MovementTool.GLIDE, icon("0011100", "0111110", "1111111", "0010100", "0010100", "0001000", "0001000"));
        ICONS.put(MovementTool.FAST_LADDER, icon("1010101", "1111111", "1010101", "1111111", "1010101", "1111111", "1010101"));
        ICONS.put(MovementTool.AUTO_SPRINT, icon("0001110", "0010100", "0111100", "0010110", "0110010", "1100010", "0000000"));
        ICONS.put(MovementTool.SAFE_WALK, icon("1111111", "1000001", "1000001", "1000001", "1110001", "0010001", "0011111"));
        ICONS.put(MovementTool.AUTO_JUMP, icon("0001100", "0010010", "0100001", "1110011", "0010100", "0011000", "0010000"));
        ICONS.put(MovementTool.WATER_SPEED, icon("0001000", "0001100", "1111110", "0101011", "1111110", "0000000", "1111111"));
        ICONS.put(MovementTool.AIR_CONTROL, icon("0001000", "0101010", "0011100", "1111111", "0011100", "0101010", "0001000"));
    }

    private MovementHud() {
    }

    public static boolean hasActive(WorldBinderConfig config) {
        if (config.movement == null) return false;
        for (MovementTool tool : MovementTool.values()) {
            if (config.movement.enabled(tool)) return true;
        }
        return false;
    }

    public static WorldBinderHud.WidgetBounds bounds(Minecraft client, WorldBinderConfig config, boolean preview) {
        List<MovementTool> tools = visibleTools(config, preview);
        int count = tools.size();
        int badgeWidth = badgeWidth(client, tools);
        int cols = Math.max(1, (count + ROWS - 1) / ROWS);
        int rows = Math.min(ROWS, Math.max(1, count));
        float scale = scale(client, config, count, badgeWidth);
        int width = Math.round((cols * badgeWidth + (cols - 1) * GAP) * scale);
        int height = Math.round((rows * BADGE_HEIGHT + (rows - 1) * GAP) * scale);
        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();
        int x = WorldBinderWidgetLayout.positionX(screenW, width, config.movementBadgeXPercent);
        int y = WorldBinderWidgetLayout.positionY(screenH, height, config.movementBadgeYPercent);
        return new WorldBinderHud.WidgetBounds(x, y, width, height);
    }

    public static void draw(GuiGraphicsExtractor graphics, Minecraft client, WorldBinderConfig config, boolean preview) {
        if (!preview && !config.showMovementBadges) {
            return;
        }
        List<MovementTool> tools = visibleTools(config, preview);
        if (tools.isEmpty()) {
            return;
        }
        WorldBinderHud.WidgetBounds bounds = bounds(client, config, preview);
        int badgeWidth = badgeWidth(client, tools);
        float scale = scale(client, config, tools.size(), badgeWidth);
        graphics.pose().pushMatrix();
        graphics.pose().translate(bounds.x(), bounds.y());
        graphics.pose().scale(scale, scale);
        try {
            for (int i = 0; i < tools.size(); i++) {
                int x = (i / ROWS) * (badgeWidth + GAP);
                int y = (i % ROWS) * (BADGE_HEIGHT + GAP);
                int accent = 0xFF000000 | (config.movementBadgeAccentColor & 0x00FFFFFF);
                graphics.fill(x + 1, y + 2, x + badgeWidth + 1, y + BADGE_HEIGHT + 2, 0x66000000);
                graphics.fill(x, y, x + badgeWidth, y + BADGE_HEIGHT, WbTheme.DIVIDER);
                graphics.fill(x + 1, y + 1, x + badgeWidth - 1, y + BADGE_HEIGHT - 1,
                        config.movementBadgeBackgroundColor);
                graphics.fill(x, y + 3, x + 2, y + BADGE_HEIGHT - 3, accent);
                graphics.fill(x + 4, y + 3, x + 20, y + 19, 0x7A080E16);
                drawIcon(graphics, ICONS.get(tools.get(i)), x + 5, y + 4, accent);
                String name = Lang.string(tools.get(i).key());
                WbText.drawClipped(graphics, client.font, name, x + 24, y + 7,
                        badgeWidth - 32, WbTheme.TEXT);
                graphics.fill(x + badgeWidth - 5, y + 9, x + badgeWidth - 3, y + 11, accent);
                if (i % ROWS != ROWS - 1 && i + 1 < tools.size()) {
                    graphics.fill(x, y + BADGE_HEIGHT, x + 1, y + BADGE_HEIGHT + GAP, accent);
                }
            }
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private static String[] icon(String... rows) {
        return rows;
    }

    private static void drawIcon(GuiGraphicsExtractor graphics, String[] rows, int x, int y, int color) {
        for (int row = 0; row < rows.length; row++) {
            String pixels = rows[row];
            for (int column = 0; column < pixels.length();) {
                if (pixels.charAt(column) != '1') {
                    column++;
                    continue;
                }
                int start = column;
                while (column < pixels.length() && pixels.charAt(column) == '1') column++;
                graphics.fill(x + start * 2, y + row * 2, x + column * 2, y + row * 2 + 2, color);
            }
        }
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

    private static int badgeWidth(Minecraft client, List<MovementTool> tools) {
        int width = 68;
        for (MovementTool tool : tools) {
            width = Math.max(width, client.font.width(Lang.string(tool.key())) + 34);
        }
        return Math.min(132, width);
    }

    private static float scale(Minecraft client, WorldBinderConfig config, int count, int badgeWidth) {
        int cols = Math.max(1, (count + ROWS - 1) / ROWS);
        int rows = Math.min(ROWS, Math.max(1, count));
        int logicalWidth = cols * badgeWidth + (cols - 1) * GAP;
        int logicalHeight = rows * BADGE_HEIGHT + (rows - 1) * GAP;
        float requested = Math.max(50, Math.min(180, config.movementBadgeScalePercent)) / 100.0F;
        float fit = Math.min((client.getWindow().getGuiScaledWidth() - 12) / (float) logicalWidth,
                (client.getWindow().getGuiScaledHeight() - 12) / (float) logicalHeight);
        return Math.max(0.2F, Math.min(requested, fit));
    }
}
