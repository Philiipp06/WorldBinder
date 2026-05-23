package net.worldbinder.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.worldbinder.util.GuiText;

public final class WbProgressBar {
    private WbProgressBar() {
    }

    public static void draw(GuiGraphicsExtractor context, Font font, int x, int y, int width, double progress, String detail) {
        int safeWidth = Math.max(1, width);
        int fill = (int) (safeWidth * Math.max(0.0D, Math.min(1.0D, progress)));
        GuiText.drawTextWithShadow(context, font, Component.literal(detail), x, y - 12, WbTheme.TEXT_MUTED);
        context.fill(x, y, x + safeWidth, y + 8, WbTheme.CARD_ALT);
        context.fill(x, y, x + fill, y + 8, WbTheme.ACCENT);
    }
}
