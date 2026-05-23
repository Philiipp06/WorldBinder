package net.worldbinder.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.worldbinder.util.GuiText;

public final class WbWarningBox {
    private WbWarningBox() {
    }

    public static void drawLine(GuiGraphicsExtractor context, Font font, int x, int y, String text, int color) {
        GuiText.drawTextWithShadow(context, font, Component.literal("• " + text), x, y, color);
    }
}
