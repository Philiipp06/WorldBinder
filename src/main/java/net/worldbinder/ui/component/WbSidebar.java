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
        context.fill(x, y, x + width, y + height, WbTheme.PANEL);
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, WbTheme.PANEL_INNER);
        context.fill(x, y, x + width, y + 3, WbTheme.ACCENT);
        context.fill(x, y + 3, x + 3, y + height, WbTheme.ACCENT_DARK);
        context.fill(x + width - 3, y + 3, x + width, y + height, WbTheme.ACCENT_RIGHT);
        context.fill(x, y + height - 3, x + width, y + height, WbTheme.ACCENT_DARK);
        int sidebar = WbLayout.sidebarWidth(width);
        context.fill(x + sidebar, y + 18, x + sidebar + 1, y + height - 18, WbTheme.ACCENT_SOFT);
    }

    public static void drawHeader(GuiGraphicsExtractor context, Font font, int x, int y, int panelWidth) {
        boolean compact = panelWidth < 520;
        GuiText.drawTextWithShadow(context, font, Component.literal(compact ? "◆ WB" : "◆ WorldBinder"), x + 14, y + 20, WbTheme.TEXT);
        if (!compact) {
            GuiText.drawTextWithShadow(context, font, Lang.text("worldbinder.sidebar.control_center"), x + 14, y + 38, WbTheme.TEXT_MUTED);
        }
    }

    public static void drawEntry(GuiGraphicsExtractor context, int x, int y, int width, int height, boolean active) {
        context.fill(x, y, x + width, y + height, active ? WbTheme.ACCENT_MUTED : WbTheme.ROW);
        context.fill(x, y, x + 3, y + height, active ? WbTheme.ACCENT : WbTheme.ACCENT_MUTED);
    }
}
