package net.worldbinder.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public final class WbBrand {
    private static final Identifier LOGO_TEXTURE = Identifier.fromNamespaceAndPath("worldbinder", "textures/gui/logo.png");

    private WbBrand() {
    }

    public static void draw(GuiGraphicsExtractor context, Font font, int x, int y, int availableWidth) {
        int badge = Math.max(20, Math.min(32, availableWidth / 4));
        context.blit(LOGO_TEXTURE, x, y, x + badge, y + badge, 0.0F, 1.0F, 0.0F, 1.0F);
        int labelGap = 4;
        if (availableWidth >= badge + labelGap + font.width("WorldBinder")) {
            WbText.drawClipped(context, font, "WorldBinder", x + badge + labelGap,
                    y + (badge - font.lineHeight) / 2, availableWidth - badge - labelGap, WbTheme.TEXT);
        }
    }
}
