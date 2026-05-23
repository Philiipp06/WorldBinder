package net.worldbinder.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class WbStatusChip {
    private WbStatusChip() {
    }

    public static void draw(GuiGraphicsExtractor context, Font font, int x, int y, int width, String label, String value, int accent) {
        context.fill(x, y, x + width, y + 38, WbTheme.CARD_ALT);
        context.fill(x, y, x + width, y + 2, accent);
        WbText.drawClipped(context, font, label, x + 8, y + 7, width - 16, WbTheme.TEXT_DIM);
        WbText.drawClipped(context, font, "§f" + value, x + 8, y + 21, width - 16, WbTheme.TEXT);
    }
}
