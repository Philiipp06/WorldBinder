package net.worldbinder.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class WbStatusChip {
    private WbStatusChip() {
    }

    public static void draw(GuiGraphicsExtractor context, Font font, int x, int y, int width, String label, String value, int accent) {
        WbChrome.drawInset(context, x, y, width, 40, accent, true);
        WbText.drawClipped(context, font, label, x + 9, y + 7, width - 18, WbTheme.TEXT_DIM);
        WbText.drawClipped(context, font, "§f" + value, x + 9, y + 22, width - 18, WbTheme.TEXT);
    }
}
