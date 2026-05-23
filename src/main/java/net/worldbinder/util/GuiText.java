package net.worldbinder.util;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class GuiText {
    private GuiText() {
    }

    public static void drawTextWithShadow(GuiGraphicsExtractor context, Font font, Component text, int x, int y, int color) {
        context.text(font, text, x, y, color, true);
    }

    public static void drawTextWithShadow(GuiGraphicsExtractor context, Font font, String text, int x, int y, int color) {
        context.text(font, text, x, y, color, true);
    }

    public static void drawCenteredTextWithShadow(GuiGraphicsExtractor context, Font font, Component text, int centerX, int y, int color) {
        int x = centerX - font.width(text) / 2;
        context.text(font, text, x, y, color, true);
    }

    public static void drawCenteredTextWithShadow(GuiGraphicsExtractor context, Font font, String text, int centerX, int y, int color) {
        int x = centerX - font.width(text) / 2;
        context.text(font, text, x, y, color, true);
    }
}
