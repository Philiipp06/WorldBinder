package net.worldbinder.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class WbStatusChip {
    private WbStatusChip() {
    }

    public static void draw(GuiGraphicsExtractor context, Font font, int x, int y, int width, String label, String value, int accent) {
        draw(context, font, x, y, width, label, value, accent, null);
    }

    public static void draw(GuiGraphicsExtractor context, Font font, int x, int y, int width, String label, String value, int accent, WbIcon icon) {
        WbChrome.roundedGradient(context, x, y, width, 40, WbTheme.withAlpha(accent, 0x22), 0x40141B24);
        WbChrome.roundedOutline(context, x, y, width, 40, WbTheme.withAlpha(accent, 0x55));
        context.fill(x + 1, y + 4, x + 3, y + 36, accent);
        int textX = x + 9;
        if (icon != null && width > 70) {
            icon.draw(context, x + width - WbIcon.SIZE - 7, y + 7, 1, WbTheme.withAlpha(accent, 0xCC));
        }
        WbText.drawClipped(context, font, label.toUpperCase(java.util.Locale.ROOT), textX, y + 7, width - 16 - (icon == null ? 0 : 10), WbTheme.TEXT_DIM);
        WbText.drawClipped(context, font, value, textX, y + 22, width - 16, WbTheme.TEXT);
    }
}
