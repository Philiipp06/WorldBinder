package net.worldbinder.ui.component;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class WbSelectableList {
    private WbSelectableList() {
    }

    public static void drawRow(GuiGraphicsExtractor context, int x, int y, int width, int height, boolean selected, boolean even) {
        context.fill(x, y, x + width, y + height, selected ? WbTheme.ROW_SELECTED : (even ? WbTheme.ROW_ALT : WbTheme.ROW));
    }
}
