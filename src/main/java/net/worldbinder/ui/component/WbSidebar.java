package net.worldbinder.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class WbSidebar {
    private WbSidebar() {
    }

    public static void drawShell(GuiGraphicsExtractor context, int x, int y, int width, int height) {
        WbChrome.drawPanel(context, x, y, width, height);
        int sidebar = WbLayout.sidebarWidth(width);
        context.fillGradient(x + 1, y + 3, x + sidebar, y + height - 1, 0x22000000, 0x40000000);
        context.fill(x + sidebar, y + 12, x + sidebar + 1, y + height - 12, WbTheme.divider());
    }

    public static void drawHeader(GuiGraphicsExtractor context, Font font, int x, int y, int panelWidth) {
        int sidebarRight = x + WbLayout.sidebarWidth(panelWidth) - 12;
        WbBrand.draw(context, font, x + 14, y + 15, sidebarRight - x - 14);
        WbChrome.drawDivider(context, x + 14, y + 52, sidebarRight - x - 14);
    }

    public static void drawEntry(GuiGraphicsExtractor context, int x, int y, int width, int height, boolean active) {
        // Selection is painted by the animated tab buttons themselves.
    }
}
