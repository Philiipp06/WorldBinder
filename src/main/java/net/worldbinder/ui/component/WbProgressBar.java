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
        double clamped = Math.max(0.0D, Math.min(1.0D, progress));
        int fill = (int) Math.round((safeWidth - 2) * clamped);
        GuiText.drawTextWithShadow(context, font, Component.literal(detail), x, y - 12, WbTheme.TEXT_MUTED);
        String percent = Math.round(clamped * 100.0D) + "%";
        GuiText.drawTextWithShadow(context, font, Component.literal(percent), x + safeWidth - font.width(percent), y - 12, WbTheme.TEXT_SOFT);
        WbChrome.drawProgressTrack(context, x, y, safeWidth, 9, fill, WbTheme.ACCENT);
        if (clamped < 1.0D) {
            WbChrome.drawProgressShimmer(context, x, y, Math.max(2, fill + 2), 9, WbTheme.ACCENT);
        }
    }
}
