package net.worldbinder.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class WbSectionHeader {
    private WbSectionHeader() {
    }

    public static void draw(GuiGraphicsExtractor context, Font font, int x, int y, int width, String title, String subtitle) {
        draw(context, font, x, y, width, title, subtitle, WbTheme.ACCENT);
    }

    public static void draw(GuiGraphicsExtractor context, Font font, int x, int y, int width, String title, String subtitle, int accent) {
        context.fill(x, y + 36, x + width, y + 37, WbTheme.PANEL_BORDER);
        context.fill(x, y + 36, x + Math.min(x + width, x + 132), y + 38, accent);
        WbText.drawClipped(context, font, title, x, y, width, WbTheme.TEXT);
        WbText.drawClipped(context, font, subtitle, x, y + 16, width, WbTheme.TEXT_MUTED);
    }
}
