package net.worldbinder.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class WbSectionHeader {
    private WbSectionHeader() {
    }

    public static void draw(GuiGraphicsExtractor context, Font font, int x, int y, int width, String title, String subtitle) {
        WbText.drawClipped(context, font, title, x, y, width, WbTheme.TEXT);
        WbText.drawClipped(context, font, subtitle, x, y + 16, width, WbTheme.TEXT_MUTED);
        context.fill(x, y + 40, x + width, y + 41, WbTheme.ACCENT_SOFT);
    }
}
