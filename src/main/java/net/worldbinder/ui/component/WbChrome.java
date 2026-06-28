package net.worldbinder.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class WbChrome {
    private WbChrome() {
    }

    public static void drawBackdrop(GuiGraphicsExtractor context, int width, int height) {
        context.fill(0, 0, width, height, WbTheme.BACKDROP);
        context.fill(0, 0, width, Math.max(1, height / 3), 0x22000000);
        context.fill(0, height - Math.max(1, height / 4), width, height, WbTheme.BACKDROP_SOFT);
    }

    public static void drawPanel(GuiGraphicsExtractor context, int x, int y, int width, int height) {
        int third = Math.max(1, width / 3);
        context.fill(x + 4, y + 5, x + width + 4, y + height + 5, WbTheme.PANEL_SHADOW);
        context.fill(x, y, x + width, y + height, WbTheme.PANEL);
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, WbTheme.PANEL_INNER);
        context.fill(x + 1, y + 1, x + width - 1, y + 2, WbTheme.PANEL_BORDER);
        context.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, 0x44000000);
        context.fill(x, y, x + third, y + 2, WbTheme.ACCENT);
        context.fill(x + third, y, x + third * 2, y + 2, WbTheme.ACCENT_DARK);
        context.fill(x + third * 2, y, x + width, y + 2, WbTheme.ACCENT_RIGHT);
        context.fill(x, y + 2, x + 2, y + height, WbTheme.ACCENT_DARK);
        context.fill(x + width - 2, y + 2, x + width, y + height, WbTheme.ACCENT_RIGHT);
        context.fill(x + 16, y + 8, x + width - 16, y + 9, WbTheme.ACCENT_GLOW);
    }

    public static boolean drawCard(GuiGraphicsExtractor context, Font font, int x, int y, int width, int height, Component title, int accent, boolean hover) {
        int fill = hover ? WbTheme.CARD_HOVER : WbTheme.CARD;
        context.fill(x + 2, y + 3, x + width + 2, y + height + 3, 0x55000000);
        context.fill(x, y, x + width, y + height, fill);
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0x221D2B3C);
        context.fill(x, y, x + width, y + 1, hover ? accent : 0x663A4658);
        context.fill(x + 4, y + 2, x + Math.min(x + width - 4, x + 86), y + 3, hover ? accent : 0x443A4658);
        context.fill(x, y + height - 1, x + width, y + height, 0x55000000);
        context.fill(x, y, x + 3, y + height, accent);
        String text = title == null ? "" : title.getString();
        WbText.drawClipped(context, font, text, x + 14, y + 10, Math.max(1, width - 28), hover ? WbTheme.TEXT : accent);
        return hover;
    }

    public static void drawInset(GuiGraphicsExtractor context, int x, int y, int width, int height, int accent, boolean active) {
        context.fill(x + 1, y + 2, x + width + 1, y + height + 2, 0x44000000);
        context.fill(x, y, x + width, y + height, active ? WbTheme.CARD_HOVER : WbTheme.CARD_ALT);
        context.fill(x, y, x + width, y + 1, active ? accent : WbTheme.PANEL_BORDER);
        context.fill(x, y + height - 1, x + width, y + height, 0x66000000);
    }

    public static void drawProgressTrack(GuiGraphicsExtractor context, int x, int y, int width, int height, int fill, int accent) {
        int fillRight = Math.max(x + 1, Math.min(x + width - 1, x + fill));
        context.fill(x, y, x + width, y + height, WbTheme.FIELD);
        context.fill(x, y, x + width, y + 1, 0x663A4658);
        context.fill(x + 1, y + 1, fillRight, y + height - 1, accent);
        context.fill(x + 1, y + 1, x + width - 1, y + 2, 0x33FFFFFF);
    }

    public static void drawDropdownPanel(GuiGraphicsExtractor context, int x, int y, int width, int rowHeight, int rows) {
        drawDropdownPanel(context, x, y, width, rowHeight, rows, WbTheme.ACCENT);
    }

    public static void drawDropdownPanel(GuiGraphicsExtractor context, int x, int y, int width, int rowHeight, int rows, int accent) {
        int height = rowHeight * rows + 6;
        context.fill(x + 3, y + 4, x + width + 3, y + height + 4, 0x77000000);
        context.fill(x, y, x + width, y + height, 0xF0141B28);
        context.fill(x, y, x + width, y + 1, accent);
        context.fill(x + 2, y + 2, x + Math.max(x + 2, x + width - 2), y + 3, WbTheme.PANEL_BORDER);
        context.fill(x, y + height - 1, x + width, y + height, 0x66000000);
        context.fill(x, y, x + 1, y + height, WbTheme.PANEL_BORDER);
        context.fill(x + width - 1, y, x + width, y + height, WbTheme.PANEL_BORDER);
    }

    public static void drawDropdownRow(GuiGraphicsExtractor context, int x, int y, int width, int height, boolean active, boolean hovered, int accent) {
        context.fill(x, y, x + width, y + height, active ? WbTheme.ACCENT_MUTED : hovered ? WbTheme.ROW_HOVER : WbTheme.ROW_ALT);
        context.fill(x, y, x + 3, y + height, active ? accent : hovered ? WbTheme.ACCENT_SOFT : 0x003CF2C8);
    }

    public static boolean contains(int x, int y, int width, int height, int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}
