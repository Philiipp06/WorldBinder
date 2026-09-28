package net.worldbinder.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.worldbinder.WorldBinder;
import net.worldbinder.config.WorldBinderConfig;
import net.worldbinder.status.WorldBinderNotifications;
import net.worldbinder.ui.component.WbTheme;
import net.worldbinder.util.GuiText;

import java.util.ArrayList;
import java.util.List;

public final class WorldBinderNotificationOverlay {
    private static final int BASE_WIDTH = 300;
    private static final int MIN_HEIGHT = 34;
    private static final int MARGIN = 8;
    private static final int GAP = 5;
    private static final int MAX_MESSAGE_LINES = 4;
    private static final int MAX_HEIGHT = MIN_HEIGHT + (MAX_MESSAGE_LINES - 1) * 10;
    private static final long ENTER_MILLIS = 180L;
    private static final long EXIT_MILLIS = 220L;

    private WorldBinderNotificationOverlay() {
    }

    public static LayoutResult draw(
            GuiGraphicsExtractor context,
            Minecraft client,
            WorldBinderHud.WidgetBounds statusBounds,
            WorldBinderHud.WidgetBounds radarBounds,
            WorldBinderHud.WidgetBounds movementBounds
    ) {
        WorldBinderConfig config = WorldBinder.config();
        if (!config.effectiveMessageMode().toast()) {
            WorldBinderNotifications.clear();
            return LayoutResult.EMPTY;
        }

        Font font = client.font;
        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();
        float scale = notificationScale(config, screenW, screenH);
        int physicalWidth = Math.max(1, Math.round(BASE_WIDTH * scale));
        int physicalGap = Math.max(2, Math.round(GAP * scale));
        int physicalMaxHeight = Math.max(1, Math.round(MAX_HEIGHT * scale));
        int anchorY = MARGIN + Math.round((screenH - MARGIN * 2) * config.effectiveWidgetPositionPercent(config.notificationWidgetYPercent) / 100.0F);
        boolean bottomAligned = config.notificationWidgetYPercent > 50;
        int availableSlots = Math.min(4, Math.max(0,
                (screenH - MARGIN * 2 + physicalGap) / (physicalMaxHeight + physicalGap)));
        List<WorldBinderNotifications.Entry> entries = WorldBinderNotifications.visible(availableSlots);
        if (entries.isEmpty()) {
            return LayoutResult.EMPTY;
        }

        List<EntryLayout> layouts = new ArrayList<>(entries.size());
        int stackHeight = Math.max(0, (entries.size() - 1) * physicalGap);
        for (WorldBinderNotifications.Entry entry : entries) {
            EntryLayout layout = layout(font, entry, BASE_WIDTH, BASE_WIDTH);
            layouts.add(layout);
            stackHeight += Math.max(1, Math.round(layout.height() * scale));
        }
        int x = WorldBinderWidgetLayout.positionX(screenW, physicalWidth,
                config.effectiveWidgetPositionPercent(config.notificationWidgetXPercent));
        int desiredTop = bottomAligned ? anchorY - stackHeight : anchorY;
        WorldBinderHud.WidgetBounds hotbarBounds = client.player == null ? null
                : new WorldBinderHud.WidgetBounds(screenW / 2 - 100, screenH - 45, 200, 45);
        int stackTop = chooseStackTop(desiredTop, stackHeight, screenH, x, physicalWidth,
                statusBounds, radarBounds, movementBounds, hotbarBounds);
        int cursor = bottomAligned ? stackTop + stackHeight : stackTop;
        long now = System.currentTimeMillis();

        for (int i = 0; i < entries.size(); i++) {
            WorldBinderNotifications.Entry entry = entries.get(i);
            EntryLayout layout = layouts.get(i);
            int physicalHeight = Math.max(1, Math.round(layout.height() * scale));
            int y = bottomAligned ? cursor - physicalHeight : cursor;
            double visibility = visibility(entry, now);
            int slide = (int) Math.round((1.0D - visibility) * 22.0D * scale);
            int animatedX = clamp(x + (config.notificationWidgetXPercent >= 50 ? slide : -slide),
                    MARGIN, Math.max(MARGIN, screenW - MARGIN - physicalWidth));
            drawScaledEntry(context, font, config, entry, layout, animatedX, y, scale, now, visibility);
            cursor = bottomAligned ? y - physicalGap : y + physicalHeight + physicalGap;
        }
        return new LayoutResult(stackTop, stackTop + stackHeight);
    }

    public static int chooseStackTop(int desired, int stackHeight, int screenHeight, int x, int width,
                              WorldBinderHud.WidgetBounds... obstacles) {
        int min = MARGIN;
        int max = Math.max(min, screenHeight - MARGIN - stackHeight);
        int bestTop = clamp(desired, min, max);
        long bestScore = placementScore(bestTop, desired, stackHeight, x, width, obstacles);
        for (WorldBinderHud.WidgetBounds obstacle : obstacles) {
            if (obstacle == null) continue;
            int[] candidates = {obstacle.y() - stackHeight - GAP, obstacle.y() + obstacle.height() + GAP};
            for (int candidate : candidates) {
                int top = clamp(candidate, min, max);
                long score = placementScore(top, desired, stackHeight, x, width, obstacles);
                if (score < bestScore) {
                    bestScore = score;
                    bestTop = top;
                }
            }
        }
        return bestTop;
    }

    private static long placementScore(int top, int desired, int height, int x, int width,
                                       WorldBinderHud.WidgetBounds... obstacles) {
        long overlap = 0L;
        for (WorldBinderHud.WidgetBounds obstacle : obstacles) {
            overlap += overlapArea(top, height, x, width, obstacle);
        }
        return overlap * 10_000L + Math.abs(top - desired);
    }

    private static long overlapArea(int top, int height, int x, int width, WorldBinderHud.WidgetBounds bounds) {
        if (bounds == null) return 0L;
        int overlapX = Math.max(0, Math.min(x + width, bounds.x() + bounds.width()) - Math.max(x, bounds.x()));
        int overlapY = Math.max(0, Math.min(top + height, bounds.y() + bounds.height()) - Math.max(top, bounds.y()));
        return (long) overlapX * overlapY;
    }

    public static int preferredWidth(WorldBinderConfig config, int screenWidth, int screenHeight) {
        return Math.max(1, Math.round(BASE_WIDTH * notificationScale(config, screenWidth, screenHeight)));
    }

    public static int previewHeight(WorldBinderConfig config, int screenWidth, int screenHeight) {
        return Math.max(1, Math.round(MAX_HEIGHT * notificationScale(config, screenWidth, screenHeight)));
    }

    public static void drawPreview(
            GuiGraphicsExtractor context,
            Font font,
            WorldBinderConfig config,
            int x,
            int y,
            int width
    ) {
        long now = System.currentTimeMillis();
        WorldBinderNotifications.Entry preview = new WorldBinderNotifications.Entry(
                -1L,
                WorldBinderNotifications.Type.INFO,
                Component.translatable("worldbinder.widget_editor.preview.notification_title"),
                Component.translatable("worldbinder.widget_editor.preview.notification_message"),
                now,
                now + 10_000L
        );
        EntryLayout content = layout(font, preview, BASE_WIDTH, BASE_WIDTH);
        EntryLayout layout = new EntryLayout(content.width(), MAX_HEIGHT, content.title(), content.lines());
        float scale = width / (float) BASE_WIDTH;
        drawScaledEntry(context, font, config, preview, layout, x, y, scale, now, 1.0D);
    }

    private static void drawScaledEntry(
            GuiGraphicsExtractor context,
            Font font,
            WorldBinderConfig config,
            WorldBinderNotifications.Entry entry,
            EntryLayout layout,
            int x,
            int y,
            float scale,
            long now,
            double visibility
    ) {
        context.pose().pushMatrix();
        context.pose().translate(x, y);
        context.pose().scale(scale, scale);
        try {
            drawEntry(context, font, config, entry, layout, 0, 0, now, visibility);
        } finally {
            context.pose().popMatrix();
        }
    }

    private static EntryLayout layout(Font font, WorldBinderNotifications.Entry entry, int minWidth, int maxWidth) {
        int maxTextWidth = Math.max(28, maxWidth - 38);
        String title = ellipsize(font, entry.title().getString(), maxTextWidth);
        List<String> lines = wrap(font, entry.message().getString(), maxTextWidth, MAX_MESSAGE_LINES);
        int contentWidth = font.width(title);
        for (String line : lines) {
            contentWidth = Math.max(contentWidth, font.width(line));
        }
        int width = clamp(contentWidth + 38, minWidth, maxWidth);
        int textWidth = Math.max(28, width - 38);
        title = ellipsize(font, title, textWidth);
        lines = wrap(font, entry.message().getString(), textWidth, MAX_MESSAGE_LINES);
        int height = MIN_HEIGHT + Math.max(0, lines.size() - 1) * 10;
        return new EntryLayout(width, height, title, lines);
    }

    private static void drawEntry(
            GuiGraphicsExtractor context,
            Font font,
            WorldBinderConfig config,
            WorldBinderNotifications.Entry entry,
            EntryLayout layout,
            int x,
            int y,
            long now,
            double visibility
    ) {
        if (visibility <= 0.02D) {
            return;
        }

        int width = layout.width();
        int height = layout.height();
        int accent = config.notificationAccentColor;
        int statusColor = statusColor(entry.type());
        context.fill(x + 2, y + 3, x + width + 2, y + height + 3, fade(0x78000000, visibility));
        context.fill(x, y, x + width, y + height, fade(config.notificationBackgroundColor, visibility));
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, fade(0x243C5747, visibility));
        context.fill(x, y, x + 3, y + height, fade(statusColor, visibility));
        context.fill(x + 3, y, x + width, y + 1, fade(accent, visibility));

        int iconLeft = x + 7;
        int iconTop = y + 8;
        context.fill(iconLeft, iconTop, iconLeft + 17, iconTop + 17, fade(WbTheme.FIELD, visibility));
        context.fill(iconLeft, iconTop, iconLeft + 2, iconTop + 17, fade(statusColor, visibility));
        GuiText.drawCenteredTextWithShadow(
                context,
                font,
                Component.literal(icon(entry.type())),
                iconLeft + 9,
                iconTop + 4,
                fade(statusColor, visibility)
        );

        int textX = x + 30;
        GuiText.drawTextWithShadow(context, font, layout.title(), textX, y + 5, fade(WbTheme.TEXT, visibility));
        for (int i = 0; i < layout.lines().size(); i++) {
            int color = i == 0 ? WbTheme.TEXT_SOFT : WbTheme.TEXT_MUTED;
            GuiText.drawTextWithShadow(context, font, layout.lines().get(i), textX, y + 17 + i * 10, fade(color, visibility));
        }

        int trackX = x + 2;
        int trackY = y + height - 2;
        int trackWidth = width - 4;
        int fillWidth = (int) Math.round(trackWidth * (1.0D - entry.lifeProgress(now)));
        context.fill(trackX, trackY, trackX + trackWidth, trackY + 2, fade(0x66070B10, visibility));
        if (fillWidth > 0) {
            context.fill(trackX, trackY, trackX + Math.min(trackWidth, fillWidth), trackY + 2, fade(accent, visibility));
        }
    }

    private static double visibility(WorldBinderNotifications.Entry entry, long now) {
        double entering = clamp01(entry.ageMillis(now) / (double) ENTER_MILLIS);
        double leaving = clamp01(entry.remainingMillis(now) / (double) EXIT_MILLIS);
        return smoothStep(Math.min(entering, leaving));
    }

    private static double smoothStep(double value) {
        return value * value * (3.0D - 2.0D * value);
    }

    private static int statusColor(WorldBinderNotifications.Type type) {
        return switch (type) {
            case INFO -> WbTheme.INFO;
            case SUCCESS -> WbTheme.OK;
            case WARN -> WbTheme.WARN;
            case ERROR -> WbTheme.ERROR;
        };
    }

    private static String icon(WorldBinderNotifications.Type type) {
        return switch (type) {
            case INFO -> "i";
            case SUCCESS -> "OK";
            case WARN -> "!";
            case ERROR -> "X";
        };
    }

    private static List<String> wrap(Font font, String value, int width, int maxLines) {
        List<String> lines = new ArrayList<>();
        String remaining = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').strip();
        while (!remaining.isBlank() && lines.size() < maxLines) {
            int cut = fitIndex(font, remaining, width);
            String line = remaining.substring(0, cut).strip();
            remaining = remaining.substring(cut).strip();
            if (lines.size() == maxLines - 1 && !remaining.isBlank()) {
                line = ellipsize(font, line + " " + remaining, width);
                remaining = "";
            }
            lines.add(line);
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        return lines;
    }

    private static int fitIndex(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text.length();
        }
        int lastSpace = -1;
        int previous = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            int next = i + Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint)) {
                lastSpace = next;
            }
            if (font.width(text.substring(0, next)) > width) {
                return lastSpace > 0 ? lastSpace : previous > 0 ? previous : next;
            }
            previous = next;
            i = next;
        }
        return text.length();
    }

    private static String ellipsize(Font font, String text, int width) {
        String value = text == null ? "" : text.replace('\n', ' ').replace('\r', ' ').strip();
        if (font.width(value) <= width) {
            return value;
        }
        String suffix = "...";
        int available = Math.max(1, width - font.width(suffix));
        int end = fitIndex(font, value, available);
        String prefix = value.substring(0, Math.min(value.length(), end)).stripTrailing();
        return prefix.isEmpty() ? suffix : prefix + suffix;
    }

    private static int fade(int color, double visibility) {
        int originalAlpha = color >>> 24;
        int alpha = clamp((int) Math.round(originalAlpha * clamp01(visibility)), 0, 255);
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float notificationScale(WorldBinderConfig config, int screenWidth, int screenHeight) {
        float responsive = Math.max(
                0.70F,
                Math.min(1.0F, Math.min(screenWidth / 960.0F, screenHeight / 540.0F))
        );
        float requested = responsive * config.effectiveNotificationScalePercent() / 100.0F;
        float fit = Math.min(
                (screenWidth - MARGIN * 2) / (float) BASE_WIDTH,
                (screenHeight - MARGIN * 2) / (float) MAX_HEIGHT
        );
        return Math.max(0.2F, Math.min(requested, fit));
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private record EntryLayout(int width, int height, String title, List<String> lines) {
    }

    public record LayoutResult(int top, int bottom) {
        private static final LayoutResult EMPTY = new LayoutResult(0, 0);

        public boolean isEmpty() {
            return bottom <= top;
        }
    }
}
