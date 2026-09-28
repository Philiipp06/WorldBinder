package net.worldbinder.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class WbSidebar {
    private WbSidebar() {
    }

    public static void drawShell(GuiGraphicsExtractor context, int x, int y, int width, int height) {
        WbChrome.drawPanel(context, x, y, width, height);
        int sidebar = WbLayout.sidebarWidth(width);
        context.fill(x + sidebar, y + 16, x + sidebar + 1, y + height - 16, WbTheme.DIVIDER);
    }

    public static void drawHeader(GuiGraphicsExtractor context, Font font, int x, int y, int panelWidth) {
        int sidebarRight = x + WbLayout.sidebarWidth(panelWidth) - 12;
        WbBrand.draw(context, font, x + 14, y + 17, sidebarRight - x - 14);
        context.fill(x + 14, y + 52, sidebarRight, y + 53, WbTheme.DIVIDER);
    }

    public static void drawEntry(GuiGraphicsExtractor context, int x, int y, int width, int height, boolean active) {
        if (active) context.fill(x + 1, y + 1, x + width - 1, y + height - 1, WbTheme.ACCENT_MUTED);
    }
}
