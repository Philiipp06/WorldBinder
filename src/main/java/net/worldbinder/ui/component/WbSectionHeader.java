package net.worldbinder.ui.component;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class WbSectionHeader {
    private WbSectionHeader() {
    }

    public static void draw(GuiGraphicsExtractor context, Font font, int x, int y, int width, String title, String subtitle) {
        draw(context, font, x, y, width, title, subtitle, WbTheme.ACCENT);
    }

    public static void draw(GuiGraphicsExtractor context, Font font, int x, int y, int width, String title, String subtitle, int accent) {
        draw(context, font, x, y, width, title, subtitle, accent, null);
    }

    public static void draw(GuiGraphicsExtractor context, Font font, int x, int y, int width, String title, String subtitle, int accent, WbIcon icon) {
        int textX = x;
        if (icon != null && net.worldbinder.WorldBinder.config().uiShowIcons) {
            WbChrome.rounded(context, x, y - 2, 22, 22, WbTheme.withAlpha(accent, 0x2C));
            WbChrome.roundedOutline(context, x, y - 2, 22, 22, WbTheme.withAlpha(accent, 0x70));
            icon.draw(context, x + 4, y + 2, 2, accent);
            textX = x + 30;
        }
        int textWidth = Math.max(10, width - (textX - x));
        context.text(font, Component.literal(WbText.ellipsize(font, title, textWidth)).withStyle(ChatFormatting.BOLD), textX, y, WbTheme.TEXT, true);
        WbText.drawClipped(context, font, subtitle, textX, y + 13, textWidth, WbTheme.mutedText());
        WbChrome.hGradient(context, x, y + 31, Math.min(width, 90), 2, accent, WbTheme.withAlpha(accent, 0x00));
        context.fill(x, y + 33, x + width, y + 34, WbTheme.divider());
    }
}
