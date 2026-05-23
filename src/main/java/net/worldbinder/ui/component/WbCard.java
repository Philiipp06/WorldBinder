package net.worldbinder.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.worldbinder.util.GuiText;

public final class WbCard {
    private WbCard() {
    }

    public static boolean draw(GuiGraphicsExtractor context, Font font, int x, int y, int width, int height, Component title, int mouseX, int mouseY) {
        context.fill(x, y, x + width, y + height, WbTheme.CARD);
        context.fill(x, y, x + width, y + 1, WbTheme.ACCENT);
        context.fill(x, y + height - 1, x + width, y + height, WbTheme.ACCENT_SOFT);
        String text = title == null ? "" : title.getString();
        GuiText.drawTextWithShadow(context, font, Component.literal(WbText.ellipsize(font, text, Math.max(1, width - 24))), x + 12, y + 10, WbTheme.ACCENT);
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}
