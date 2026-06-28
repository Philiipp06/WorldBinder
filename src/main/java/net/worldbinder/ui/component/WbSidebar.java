package net.worldbinder.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.worldbinder.util.GuiText;
import net.worldbinder.util.Lang;

public final class WbSidebar {
    private WbSidebar() {
    }

    public static void drawShell(GuiGraphicsExtractor context, int x, int y, int width, int height) {
        WbChrome.drawPanel(context, x, y, width, height);
        int sidebar = WbLayout.sidebarWidth(width);
        context.fill(x + sidebar, y + 18, x + sidebar + 1, y + height - 18, WbTheme.PANEL_BORDER);
        context.fill(x + sidebar + 1, y + 18, x + sidebar + 2, y + height - 18, 0x33000000);
    }

    public static void drawHeader(GuiGraphicsExtractor context, Font font, int x, int y, int panelWidth) {
        boolean compact = panelWidth < 520;
        context.fill(x + 13, y + 18, x + WbLayout.sidebarWidth(panelWidth) - 11, y + 54, 0x44000000);
        context.fill(x + 12, y + 16, x + WbLayout.sidebarWidth(panelWidth) - 12, y + 52, WbTheme.CARD_ALT);
        context.fill(x + 12, y + 16, x + 15, y + 52, WbTheme.ACCENT);
        context.fill(x + 15, y + 16, x + WbLayout.sidebarWidth(panelWidth) - 12, y + 17, WbTheme.ACCENT_SOFT);
        GuiText.drawTextWithShadow(context, font, Component.literal(compact ? "WB" : "WorldBinder"), x + 22, y + 22, WbTheme.TEXT);
        if (!compact) {
            GuiText.drawTextWithShadow(context, font, Lang.text("worldbinder.sidebar.control_center"), x + 22, y + 38, WbTheme.TEXT_DIM);
        }
    }

    public static void drawEntry(GuiGraphicsExtractor context, int x, int y, int width, int height, boolean active) {
        context.fill(x + 1, y + 2, x + width + 1, y + height + 2, 0x33000000);
        context.fill(x, y, x + width, y + height, active ? WbTheme.CARD_HOVER : WbTheme.ROW);
        context.fill(x, y, x + 3, y + height, active ? WbTheme.ACCENT : WbTheme.PANEL_BORDER);
        if (active) {
            context.fill(x + 3, y, x + width, y + 1, WbTheme.ACCENT_SOFT);
            context.fill(x + 3, y + height - 1, x + width, y + height, 0x33000000);
            context.fill(x + width - 2, y + 1, x + width, y + height - 1, WbTheme.ACCENT_MUTED);
        }
    }
}
