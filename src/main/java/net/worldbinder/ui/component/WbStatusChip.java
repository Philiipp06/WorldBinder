package net.worldbinder.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class WbStatusChip {
    private WbStatusChip() {
    }

    public static void draw(GuiGraphicsExtractor context, Font font, int x, int y, int width, String label, String value, int accent) {
        context.fill(x, y, x + width, y + 40, 0x552B403B);
        context.fill(x, y, x + 2, y + 40, accent);
        WbText.drawClipped(context, font, label, x + 8, y + 6, width - 16, WbTheme.TEXT_DIM);
        WbText.drawClipped(context, font, value, x + 8, y + 21, width - 16, WbTheme.TEXT);
    }
}
