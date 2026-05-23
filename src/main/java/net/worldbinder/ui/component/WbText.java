package net.worldbinder.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.worldbinder.util.GuiText;

public final class WbText {
    private WbText() {
    }

    public static void draw(GuiGraphicsExtractor context, Font font, String text, int x, int y, int color) {
        draw(context, font, Component.literal(text), x, y, color);
    }

    public static void draw(GuiGraphicsExtractor context, Font font, Component text, int x, int y, int color) {
        GuiText.drawTextWithShadow(context, font, text, x, y, color);
    }

    public static void drawClipped(GuiGraphicsExtractor context, Font font, String text, int x, int y, int width, int color) {
        drawClipped(context, font, Component.literal(text), x, y, width, color);
    }

    public static void drawClipped(GuiGraphicsExtractor context, Font font, Component text, int x, int y, int width, int color) {
        GuiText.drawTextWithShadow(context, font, Component.literal(ellipsize(font, text.getString(), width)), x, y, color);
    }

    public static int drawWrapped(GuiGraphicsExtractor context, Font font, String text, int x, int y, int width, int color, int maxLines) {
        return drawWrapped(context, font, Component.literal(text), x, y, width, color, maxLines);
    }

    public static int drawWrapped(GuiGraphicsExtractor context, Font font, Component text, int x, int y, int width, int color, int maxLines) {
        int line = 0;
        String remaining = text == null ? "" : text.getString();
        while (!remaining.isBlank() && line < maxLines) {
            int cut = fitIndex(font, remaining, width);
            String current = remaining.substring(0, cut).strip();
            remaining = remaining.substring(cut).strip();
            if (line == maxLines - 1 && !remaining.isBlank()) {
                current = ellipsize(font, current + " " + remaining, width);
                remaining = "";
            }
            draw(context, font, current, x, y + line * 14, color);
            line++;
        }
        return line * 14;
    }

    public static String ellipsize(Font font, String text, int width) {
        if (text == null || width <= 10) {
            return "";
        }
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

    private static int fitIndex(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text.length();
        }
        int best = 1;
        int lastSpace = -1;
        for (int i = 1; i <= text.length(); i++) {
            char c = text.charAt(i - 1);
            if (Character.isWhitespace(c)) {
                lastSpace = i;
            }
            if (font.width(text.substring(0, i)) > width) {
                best = lastSpace > 0 ? lastSpace : Math.max(1, i - 1);
                break;
            }
        }
        return Math.max(1, Math.min(best, text.length()));
    }
}
