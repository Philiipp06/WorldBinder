package net.worldbinder.ui.component;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class WbSelectableList {
    private WbSelectableList() {
    }

    public static void drawRow(GuiGraphicsExtractor context, int x, int y, int width, int height, boolean selected, boolean even) {
        drawRow(context, x, y, width, height, selected, even, false);
    }

    public static void drawRow(GuiGraphicsExtractor context, int x, int y, int width, int height, boolean selected, boolean even, boolean hovered) {
        int fill = selected ? WbTheme.withAlpha(WbTheme.ACCENT, 0x3A) : hovered ? WbTheme.ROW_HOVER : (even ? WbTheme.ROW_ALT : WbTheme.ROW);
        WbChrome.rounded(context, x, y, width, height, fill);
        if (selected) {
            WbChrome.roundedOutline(context, x, y, width, height, WbTheme.withAlpha(WbTheme.ACCENT, 0x90));
            context.fill(x + 1, y + 2, x + 3, y + height - 2, WbTheme.ACCENT);
        }
    }
}
