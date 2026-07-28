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

    public static void drawField(GuiGraphicsExtractor context, int x, int y, int width, int height, int accent, boolean focused, boolean hovered) {
        int fill = focused ? WbTheme.FIELD_FOCUS : hovered ? WbTheme.FIELD_HOVER : WbTheme.FIELD;
        int border = focused ? accent : hovered ? WbTheme.PANEL_BORDER : 0x55354658;
        context.fill(x + 1, y + 2, x + width + 1, y + height + 2, 0x55000000);
        context.fill(x, y, x + width, y + height, border);
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
        context.fill(x + 2, y + 2, x + width - 2, y + 3, focused ? WbTheme.ACCENT_GLOW : 0x221D2B3C);
        if (focused) {
            context.fill(x, y, x + 2, y + height, accent);
        }
    }

    public static void drawSectionSurface(GuiGraphicsExtractor context, int x, int y, int width, int height, int accent) {
        context.fill(x + 2, y + 3, x + width + 2, y + height + 3, 0x44000000);
        context.fill(x, y, x + width, y + height, 0x75101A25);
        context.fill(x, y, x + width, y + 1, 0x553A4A5E);
        context.fill(x, y, x + 2, y + height, accent);
        context.fill(x + 10, y + height - 1, x + width - 10, y + height, 0x33000000);
    }

    public static void drawDivider(GuiGraphicsExtractor context, int x, int y, int width) {
        context.fill(x, y, x + width, y + 1, WbTheme.DIVIDER);
    }

    public static void drawScrollBar(GuiGraphicsExtractor context, int x, int y, int height, int scroll, int maxScroll, int viewportHeight) {
        if (maxScroll <= 0 || height <= 8) {
            return;
        }
        context.fill(x, y, x + 3, y + height, WbTheme.SCROLL_TRACK);
        int contentHeight = Math.max(viewportHeight + maxScroll, viewportHeight);
        int thumbHeight = Math.max(18, Math.min(height, Math.round(height * (viewportHeight / (float) contentHeight))));
        int travel = Math.max(0, height - thumbHeight);
        int thumbY = y + (maxScroll <= 0 ? 0 : Math.round(travel * (scroll / (float) maxScroll)));
        context.fill(x, thumbY, x + 3, thumbY + thumbHeight, WbTheme.SCROLL_THUMB);
        context.fill(x, thumbY, x + 1, thumbY + thumbHeight, WbTheme.ACCENT);
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
        context.fill(x + 4, y + 5, x + width + 4, y + height + 5, 0x99000000);
        context.fill(x, y, x + width, y + height, 0xFF101925);
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xF0182533);
        context.fill(x, y, x + width, y + 2, accent);
        context.fill(x + 2, y + 3, x + width - 2, y + 4, 0x334E6A82);
        context.fill(x, y + height - 1, x + width, y + height, 0x99000000);
        context.fill(x, y, x + 1, y + height, 0xAA3D5368);
        context.fill(x + width - 1, y, x + width, y + height, 0xAA3D5368);
    }

    public static void drawDropdownRow(GuiGraphicsExtractor context, int x, int y, int width, int height, boolean active, boolean hovered, int accent) {
        context.fill(x, y, x + width, y + height, active ? WbTheme.ROW_SELECTED : hovered ? WbTheme.ROW_HOVER : WbTheme.ROW_ALT);
        context.fill(x, y, x + (active ? 3 : 2), y + height, active ? accent : hovered ? WbTheme.ACCENT_SOFT : 0x223A4A5E);
        if (active) {
            context.fill(x + width - 8, y + height / 2 - 1, x + width - 5, y + height / 2 + 2, accent);
        }
    }

    public static boolean contains(int x, int y, int width, int height, int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}
