package net.worldbinder.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public final class WbEditBox extends EditBox {
    private final int chromeX;
    private final int chromeY;
    private final int chromeWidth;
    private final int chromeHeight;

    public WbEditBox(Font font, int x, int y, int width, int height) {
        super(font, x + 6, y + 6, Math.max(24, width - 12), Math.max(10, height - 10), Component.empty());
        chromeX = x;
        chromeY = y;
        chromeWidth = width;
        chromeHeight = height;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return visible
                && mouseX >= chromeX
                && mouseY >= chromeY
                && mouseX < chromeX + chromeWidth
                && mouseY < chromeY + chromeHeight;
    }

    public int chromeX() {
        return chromeX;
    }

    public int chromeY() {
        return chromeY;
    }

    public int chromeWidth() {
        return chromeWidth;
    }

    public int chromeHeight() {
        return chromeHeight;
    }
}
