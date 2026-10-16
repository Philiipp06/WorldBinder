package net.worldbinder.ui.component;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class WbBrand {
    public static final String VERSION = "1.6.0";
    private static final Identifier LOGO_TEXTURE = Identifier.fromNamespaceAndPath("worldbinder", "textures/gui/logo.png");

    private WbBrand() {
    }

    public static void draw(GuiGraphicsExtractor context, Font font, int x, int y, int availableWidth) {
        int badge = Math.max(20, Math.min(30, availableWidth / 4));
        float pulse = WbMotion.pulse(3200L);
        WbChrome.rounded(context, x - 2, y - 2, badge + 4, badge + 4, WbTheme.withAlpha(WbTheme.ACCENT, 0x18 + Math.round(0x18 * pulse)));
        context.blit(LOGO_TEXTURE, x, y, x + badge, y + badge, 0.0F, 1.0F, 0.0F, 1.0F);
        int labelGap = 6;
        int labelX = x + badge + labelGap;
        int labelWidth = availableWidth - badge - labelGap;
        if (labelWidth >= font.width("WorldBinder")) {
            context.text(font, Component.literal("WorldBinder").withStyle(ChatFormatting.BOLD), labelX, y + badge / 2 - 9, WbTheme.TEXT, true);
            context.text(font, "v" + VERSION, labelX, y + badge / 2 + 2, WbTheme.withAlpha(WbTheme.ACCENT, 0xDD), false);
        }
    }
}
