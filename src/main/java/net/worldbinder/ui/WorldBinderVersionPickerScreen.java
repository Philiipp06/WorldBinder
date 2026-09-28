package net.worldbinder.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.worldbinder.ui.component.WbButton;
import net.worldbinder.ui.component.WbChrome;
import net.worldbinder.ui.component.WbEditBox;
import net.worldbinder.ui.component.WbLayout;
import net.worldbinder.ui.component.WbText;
import net.worldbinder.ui.component.WbTheme;
import net.worldbinder.ui.component.WbTooltips;
import net.worldbinder.util.Lang;
import net.worldbinder.version.TargetMinecraftVersion;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class WorldBinderVersionPickerScreen extends Screen {
    private final Screen parent;
    private final Consumer<String> onChoose;
    private final String current;
    private EditBox search;
    private String query = "";
    private int rowOffset;

    public WorldBinderVersionPickerScreen(Screen parent, String current, Consumer<String> onChoose) {
        super(Component.translatable("worldbinder.version_picker.title"));
        this.parent = parent;
        this.current = TargetMinecraftVersion.normalize(current);
        this.onChoose = onChoose;
    }

    @Override
    protected void init() {
        int realWidth = width;
        int realHeight = height;
        WbLayout.UiScale scale = WbLayout.uiScale(realWidth, realHeight);
        width = scale.virtualWidth();
        height = scale.virtualHeight();
        try {
            int left = panelX();
            int top = panelY();
            search = new WbEditBox(font, left + 20, top + 62, panelWidth() - 40, 24);
            search.setBordered(false);
            search.setMaxLength(24);
            search.setTextColor(WbTheme.TEXT);
            search.setTextShadow(false);
            search.setValue(query);
            search.setResponder(value -> {
                query = value.strip().toLowerCase(Locale.ROOT);
                rowOffset = 0;
                rebuildRows();
            });
            rebuildRows();
        } finally {
            width = realWidth;
            height = realHeight;
        }
    }

    private void rebuildRows() {
        int realWidth = width;
        int realHeight = height;
        WbLayout.UiScale scale = WbLayout.uiScale(realWidth, realHeight);
        width = scale.virtualWidth();
        height = scale.virtualHeight();
        try {
            rebuildRowsScaled();
        } finally {
            width = realWidth;
            height = realHeight;
        }
    }

    private void rebuildRowsScaled() {
        clearWidgets();
        addRenderableWidget(search);
        int left = panelX();
        int top = panelY();
        int panelW = panelWidth();
        int columns = columns();
        int gap = 8;
        int itemW = (panelW - 40 - gap * (columns - 1)) / columns;
        List<TargetMinecraftVersion.Entry> entries = entries();
        int maxOffset = Math.max(0, (entries.size() + columns - 1) / columns - visibleRows());
        rowOffset = Math.min(rowOffset, maxOffset);
        for (int row = 0; row < visibleRows(); row++) {
            for (int col = 0; col < columns; col++) {
                int index = (row + rowOffset) * columns + col;
                if (index >= entries.size()) break;
                TargetMinecraftVersion.Entry entry = entries.get(index);
                boolean selected = entry.name().equals(current);
                int x = left + 20 + col * (itemW + gap);
                int y = rowsTop() + row * 27;
                Component tooltip = Component.translatable("worldbinder.version_picker.version_tooltip",
                        entry.name(), Component.translatable(TargetMinecraftVersion.profileTranslationKey(entry.name())));
                addRenderableWidget(WbButton.tab(WbButton.create(x, y, itemW, 22,
                        entry.name(), tooltip, button -> {
                            onChoose.accept(entry.name());
                            net.worldbinder.compat.ClientCompat.setScreen(minecraft, parent);
                        }), selected ? WbTheme.ACCENT : WbTheme.INFO, selected));
            }
        }
        addRenderableWidget(WbButton.quiet(WbButton.create(left + panelW - 112, top + panelHeight() - 32,
                92, 22, Component.translatable("worldbinder.config.cancel"),
                Component.translatable("worldbinder.tooltip.config.back"), button -> onClose())));
        setFocused(search);
    }

    private List<TargetMinecraftVersion.Entry> entries() {
        return TargetMinecraftVersion.FINAL_RELEASES.stream()
                .filter(entry -> query.isBlank() || entry.name().contains(query))
                .sorted(Comparator.comparingInt(TargetMinecraftVersion.FINAL_RELEASES::indexOf).reversed())
                .toList();
    }

    private int columns() {
        return panelWidth() < 520 ? 2 : 3;
    }

    private int visibleRows() {
        return Math.max(1, (panelHeight() - 162) / 27);
    }

    private int rowsTop() {
        return panelY() + 112;
    }

    private int panelWidth() {
        return Math.min(620, WbLayout.panelWidth(width));
    }

    private int panelHeight() {
        return Math.min(460, WbLayout.panelHeight(height));
    }

    private int panelX() {
        return WbLayout.left(width, panelWidth());
    }

    private int panelY() {
        return WbLayout.top(height, panelHeight());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int realWidth = width;
        int realHeight = height;
        WbLayout.UiScale scale = WbLayout.uiScale(realWidth, realHeight);
        int virtualMouseX = scale.toVirtualX(mouseX);
        int virtualMouseY = scale.toVirtualY(mouseY);
        WbChrome.drawBackdrop(context, realWidth, realHeight);
        context.pose().pushMatrix();
        context.pose().translate(scale.offsetX(), scale.offsetY());
        context.pose().scale(scale.scale(), scale.scale());
        width = scale.virtualWidth();
        height = scale.virtualHeight();
        try {
            int left = panelX();
            int top = panelY();
            int panelW = panelWidth();
            int panelH = panelHeight();
            WbChrome.drawPanel(context, left, top, panelW, panelH);
            WbText.drawClipped(context, font, Lang.string("worldbinder.version_picker.title"), left + 20, top + 17,
                    panelW - 40, WbTheme.TEXT);
            WbText.drawClipped(context, font, Lang.string("worldbinder.version_picker.subtitle", current),
                    left + 20, top + 36, panelW - 40, WbTheme.TEXT_MUTED);
            WbText.drawClipped(context, font, Lang.string("worldbinder.version_picker.search"),
                    left + 20, top + 50, panelW - 40, WbTheme.TEXT_DIM);
            WbChrome.drawField(context, left + 20, top + 62, panelW - 40, 24, WbTheme.ACCENT,
                    search.isFocused(), search.isMouseOver(virtualMouseX, virtualMouseY));
            List<TargetMinecraftVersion.Entry> entries = entries();
            WbText.drawClipped(context, font, Lang.string("worldbinder.version_picker.results", entries.size()),
                    left + 20, top + 96, panelW - 40, WbTheme.TEXT_DIM);
            if (entries.isEmpty()) {
                WbText.drawClipped(context, font, Lang.string("worldbinder.version_picker.no_results"),
                        left + 20, rowsTop() + 8, panelW - 40, WbTheme.TEXT_MUTED);
            }
            WbChrome.drawDivider(context, left + 20, top + panelH - 40, panelW - 40);
            int viewport = visibleRows() * 27;
            int maxScroll = Math.max(0, ((entries.size() + columns() - 1) / columns() - visibleRows()) * 27);
            WbChrome.drawScrollBar(context, left + panelW - 12, rowsTop(), viewport,
                    rowOffset * 27, maxScroll, viewport);
            super.extractRenderState(context, virtualMouseX, virtualMouseY, delta);
        } finally {
            width = realWidth;
            height = realHeight;
            context.pose().popMatrix();
        }
        WbTooltips.showHovered(this, context, font, virtualMouseX, virtualMouseY, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return super.mouseClicked(WbLayout.virtualMouseEvent(event, width, height), doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return super.mouseReleased(WbLayout.virtualMouseEvent(event, width, height));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double offsetX, double offsetY) {
        WbLayout.UiScale scale = WbLayout.uiScale(width, height);
        return super.mouseDragged(WbLayout.virtualMouseEvent(event, width, height),
                scale.toVirtualDelta(offsetX), scale.toVirtualDelta(offsetY));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        WbLayout.UiScale scale = WbLayout.uiScale(width, height);
        int realWidth = width;
        int realHeight = height;
        width = scale.virtualWidth();
        height = scale.virtualHeight();
        try {
            int maxOffset = Math.max(0, (entries().size() + columns() - 1) / columns() - visibleRows());
            int next = Math.max(0, Math.min(maxOffset, rowOffset + (verticalAmount < 0 ? 1 : -1)));
            if (next != rowOffset) {
                rowOffset = next;
                rebuildRows();
                return true;
            }
            return super.mouseScrolled(scale.toVirtualX(mouseX), scale.toVirtualY(mouseY), horizontalAmount, verticalAmount);
        } finally {
            width = realWidth;
            height = realHeight;
        }
    }

    @Override
    public void onClose() {
        net.worldbinder.compat.ClientCompat.setScreen(minecraft, parent);
    }
}
