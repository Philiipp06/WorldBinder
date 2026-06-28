package net.worldbinder.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.worldbinder.util.GuiText;

public final class WbCard {
    private WbCard() {
    }

    public static boolean draw(GuiGraphicsExtractor context, Font font, int x, int y, int width, int height, Component title, int mouseX, int mouseY) {
        return draw(context, font, x, y, width, height, title, WbTheme.ACCENT, mouseX, mouseY);
    }

    public static boolean draw(GuiGraphicsExtractor context, Font font, int x, int y, int width, int height, Component title, int accent, int mouseX, int mouseY) {
        boolean hover = WbChrome.contains(x, y, width, height, mouseX, mouseY);
        return WbChrome.drawCard(context, font, x, y, width, height, title, accent, hover);
    }
}
