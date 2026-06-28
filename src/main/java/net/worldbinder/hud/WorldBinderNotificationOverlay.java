package net.worldbinder.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.worldbinder.status.WorldBinderNotifications;
import net.worldbinder.ui.component.WbChrome;
import net.worldbinder.ui.component.WbTheme;
import net.worldbinder.util.GuiText;

import java.util.ArrayList;
import java.util.List;

public final class WorldBinderNotificationOverlay {
    private WorldBinderNotificationOverlay() {
    }

    public static void draw(GuiGraphicsExtractor context, Minecraft client) {
        List<WorldBinderNotifications.Entry> entries = WorldBinderNotifications.visible();
        if (entries.isEmpty()) {
            return;
        }
        Font font = client.font;
        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();
        int width = Math.max(184, Math.min(278, screenW - 18));
        int x = screenW - width - 9;
        int bottom = Math.max(58, screenH - 14);
        long now = System.currentTimeMillis();
        for (WorldBinderNotifications.Entry entry : entries) {
            int height = entryHeight(font, entry, width);
            int y = bottom - height;
            if (y < 8) {
                break;
            }
            int slide = slideOffset(entry.ageMillis(now));
            drawEntry(context, font, entry, x + slide, y, width, now);
            bottom = y - 8;
        }
    }

    private static int entryHeight(Font font, WorldBinderNotifications.Entry entry, int width) {
        int textW = Math.max(80, width - 56);
        List<String> lines = wrap(font, entry.message().getString(), textW, 3);
        return Math.max(52, 36 + lines.size() * 12);
    }

    private static int drawEntry(GuiGraphicsExtractor context, Font font, WorldBinderNotifications.Entry entry, int x, int y, int width, long now) {
        int accent = accent(entry.type());
        int textX = x + 44;
        int textW = Math.max(80, width - 56);
        List<String> lines = wrap(font, entry.message().getString(), textW, 3);
        int height = Math.max(52, 36 + lines.size() * 12);

        context.fill(x + 3, y + 4, x + width + 3, y + height + 4, 0x88000000);
        context.fill(x, y, x + width, y + height, 0xF0121A24);
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0x442B3A4A);
        context.fill(x, y, x + width, y + 2, accent);
        context.fill(x, y, x + 3, y + height, accent);
        context.fill(x, y + height - 1, x + width, y + height, 0x77000000);

        context.fill(x + 12, y + 12, x + 32, y + 32, 0xCC070B10);
        context.fill(x + 12, y + 12, x + 32, y + 14, accent);
        context.fill(x + 12, y + 30, x + 32, y + 32, 0x77000000);
        GuiText.drawCenteredTextWithShadow(context, font, Component.literal(icon(entry.type())), x + 22, y + 18, accent);

        GuiText.drawTextWithShadow(context, font, entry.title(), textX, y + 9, WbTheme.TEXT);
        for (int i = 0; i < lines.size(); i++) {
            GuiText.drawTextWithShadow(context, font, Component.literal(lines.get(i)), textX, y + 24 + i * 12, i == 0 ? WbTheme.TEXT_SOFT : WbTheme.TEXT_MUTED);
        }

        int barW = width - 16;
        int fill = (int) (barW * (1.0D - entry.lifeProgress(now)));
        WbChrome.drawProgressTrack(context, x + 8, y + height - 7, barW, 3, fill, accent);
        return height;
    }

    private static int slideOffset(long ageMillis) {
        if (ageMillis >= 220L) {
            return 0;
        }
        double t = ageMillis / 220.0D;
        return (int) Math.round((1.0D - t) * 34.0D);
    }

    private static int accent(WorldBinderNotifications.Type type) {
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
        String remaining = value == null ? "" : value.replace('\n', ' ').strip();
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
        for (int i = 1; i <= text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i - 1))) {
                lastSpace = i;
            }
            if (font.width(text.substring(0, i)) > width) {
                return Math.max(1, lastSpace > 0 ? lastSpace : i - 1);
            }
        }
        return text.length();
    }

    private static String ellipsize(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        String suffix = "...";
        int end = Math.max(0, text.length() - 1);
        while (end > 0 && font.width(text.substring(0, end) + suffix) > width) {
            end--;
        }
        return end <= 0 ? suffix : text.substring(0, end).stripTrailing() + suffix;
    }
}
