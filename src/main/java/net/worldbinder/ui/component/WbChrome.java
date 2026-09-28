package net.worldbinder.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class WbChrome {
    private WbChrome() {
    }

    public static void drawBackdrop(GuiGraphicsExtractor context, int width, int height) {
        context.fill(0, 0, width, height, WbTheme.BACKDROP);
        context.fill(0, height - Math.max(1, height / 3), width, height, WbTheme.BACKDROP_SOFT);
    }

    public static void drawPanel(GuiGraphicsExtractor context, int x, int y, int width, int height) {
        context.fill(x + 4, y + 5, x + width + 4, y + height + 5, WbTheme.PANEL_SHADOW);
        context.fill(x, y, x + width, y + height, WbTheme.panelBorder());
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, WbTheme.PANEL_INNER);
        context.fill(x + 1, y + 1, x + width - 1, y + 3, WbTheme.PANEL);
        context.fill(x + 1, y + 1, x + Math.min(width - 1, 84), y + 3, WbTheme.ACCENT);
        context.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, WbTheme.PANEL);
    }

    public static boolean drawCard(GuiGraphicsExtractor context, Font font, int x, int y, int width, int height, Component title, int accent, boolean hover) {
        int fill = hover ? WbTheme.CARD_HOVER : WbTheme.CARD;
        context.fill(x + 1, y + 2, x + width + 1, y + height + 2, 0x55000000);
        context.fill(x, y, x + width, y + height, WbTheme.panelBorder());
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
        context.fill(x, y, x + 2, y + height, accent);
        context.fill(x + 10, y + 29, x + width - 10, y + 30, WbTheme.divider());
        String text = title == null ? "" : title.getString();
        WbText.drawClipped(context, font, text, x + 14, y + 10, Math.max(1, width - 28), hover ? WbTheme.TEXT : accent);
        return hover;
    }

    public static void drawInset(GuiGraphicsExtractor context, int x, int y, int width, int height, int accent, boolean active) {
        context.fill(x, y, x + width, y + height, active ? WbTheme.CARD_HOVER : WbTheme.CARD_ALT);
        context.fill(x, y, x + 2, y + height, accent);
        context.fill(x + 2, y + height - 1, x + width, y + height, WbTheme.divider());
    }

    public static void drawField(GuiGraphicsExtractor context, int x, int y, int width, int height, int accent, boolean focused, boolean hovered) {
        int fill = focused ? WbTheme.FIELD_FOCUS : hovered ? WbTheme.FIELD_HOVER : WbTheme.FIELD;
        int border = focused ? WbTheme.focusRing() : WbTheme.panelBorder();
        context.fill(x, y, x + width, y + height, border);
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
        if (focused) {
            context.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, WbTheme.focusRing());
        }
    }

    public static void drawSectionSurface(GuiGraphicsExtractor context, int x, int y, int width, int height, int accent) {
        context.fill(x, y, x + width, y + height, 0x441E302E);
        context.fill(x, y, x + 2, y + height, accent);
        context.fill(x + 10, y + height - 1, x + width - 10, y + height, WbTheme.divider());
    }

    public static void drawDivider(GuiGraphicsExtractor context, int x, int y, int width) {
        context.fill(x, y, x + width, y + 1, WbTheme.divider());
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
        context.fill(x + 1, y + 1, fillRight, y + height - 1, accent);
    }

    public static void drawDropdownPanel(GuiGraphicsExtractor context, int x, int y, int width, int rowHeight, int rows) {
        drawDropdownPanel(context, x, y, width, rowHeight, rows, WbTheme.ACCENT);
    }

    public static void drawDropdownPanel(GuiGraphicsExtractor context, int x, int y, int width, int rowHeight, int rows, int accent) {
        int height = rowHeight * rows + 6;
        context.fill(x + 4, y + 5, x + width + 4, y + height + 5, 0x99000000);
        context.fill(x, y, x + width, y + height, WbTheme.panelBorder());
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, WbTheme.PANEL_INNER);
        context.fill(x, y, x + width, y + 2, accent);
    }

    public static void drawDropdownRow(GuiGraphicsExtractor context, int x, int y, int width, int height, boolean active, boolean hovered, int accent) {
        context.fill(x, y, x + width, y + height, active ? WbTheme.ROW_SELECTED : hovered ? WbTheme.ROW_HOVER : WbTheme.ROW_ALT);
        context.fill(x, y, x + (active ? 3 : 2), y + height, active ? accent : hovered ? WbTheme.ACCENT_SOFT : WbTheme.DIVIDER);
        if (active) {
            context.fill(x + width - 8, y + height / 2 - 1, x + width - 5, y + height / 2 + 2, accent);
        }
    }

    public static boolean contains(int x, int y, int width, int height, int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}
