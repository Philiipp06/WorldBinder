package net.worldbinder.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.worldbinder.ui.component.WbButton;
import net.worldbinder.ui.component.WbChrome;
import net.worldbinder.ui.component.WbEditBox;
import net.worldbinder.ui.component.WbIcon;
import net.worldbinder.ui.component.WbLayout;
import net.worldbinder.ui.component.WbMotion;
import net.worldbinder.ui.component.WbText;
import net.worldbinder.ui.component.WbTheme;
import net.worldbinder.ui.component.WbTooltips;
import net.worldbinder.ui.component.WbTransition;
import net.worldbinder.util.Lang;
import net.worldbinder.version.TargetMinecraftVersion;
import net.worldbinder.version.VersionFamily;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Export target picker grouped by official Minecraft updates: each update shows its name, year, color and
 * emblem, and the detail card explains what the chosen target changes in the export (DataVersion, folder
 * layout, item format, downgrade notes). Clicking a version uses it immediately.
 */
public final class WorldBinderVersionPickerScreen extends Screen {
    private static final int HEADER_H = 26;
    private static final int CHIP_W = 64;
    private static final int CHIP_H = 20;
    private static final int CHIP_GAP = 6;
    private static final int ROW_STEP = 24;

    private final Screen parent;
    private final Consumer<String> onChoose;
    private final String current;
    private final Map<Button, TargetMinecraftVersion.Entry> chipEntries = new IdentityHashMap<>();
    private EditBox search;
    private String query = "";
    private int scroll;
    private int maxScroll;
    private TargetMinecraftVersion.Entry hovered;

    private record Item(VersionFamily family, TargetMinecraftVersion.Entry entry, int x, int y) {
    }

    public WorldBinderVersionPickerScreen(Screen parent, String current, Consumer<String> onChoose) {
        super(Component.translatable("worldbinder.version_picker.title"));
        this.parent = parent;
        this.current = TargetMinecraftVersion.normalize(current);
        this.onChoose = onChoose;
    }

    // ------------------------------------------------------------------ layout

    private int panelWidth() {
        return Math.min(820, WbLayout.panelWidth(width));
    }

    private int panelHeight() {
        return Math.min(500, WbLayout.panelHeight(height));
    }

    private int panelX() {
        return WbLayout.left(width, panelWidth());
    }

    private int panelY() {
        return WbLayout.top(height, panelHeight());
    }

    private boolean showDetail() {
        return panelWidth() >= 560;
    }

    private int listX() {
        return panelX() + 16;
    }

    private int listW() {
        return showDetail() ? Math.round((panelWidth() - 32) * 0.6F) : panelWidth() - 32;
    }

    private int listTop() {
        return panelY() + 84;
    }

    private int listBottom() {
        return panelY() + panelHeight() - 44;
    }

    private List<Item> layout() {
        List<Item> items = new ArrayList<>();
        int y = 0;
        int perRow = Math.max(1, (listW() - 14 + CHIP_GAP) / (CHIP_W + CHIP_GAP));
        VersionFamily[] families = VersionFamily.values();
        for (int f = families.length - 1; f >= 0; f--) {
            VersionFamily family = families[f];
            String familyName = Lang.string(family.nameKey()).toLowerCase(Locale.ROOT);
            List<TargetMinecraftVersion.Entry> entries = new ArrayList<>();
            for (TargetMinecraftVersion.Entry entry : family.entries()) {
                if (query.isBlank() || entry.name().contains(query) || familyName.contains(query)
                        || Integer.toString(family.year).contains(query)) {
                    entries.add(entry);
                }
            }
            if (entries.isEmpty()) {
                continue;
            }
            items.add(new Item(family, null, 0, y));
            y += HEADER_H;
            for (int i = 0; i < entries.size(); i++) {
                items.add(new Item(family, entries.get(i), 10 + (i % perRow) * (CHIP_W + CHIP_GAP), y + (i / perRow) * ROW_STEP));
            }
            y += ((entries.size() + perRow - 1) / perRow) * ROW_STEP + 8;
        }
        maxScroll = Math.max(0, y - (listBottom() - listTop()));
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        return items;
    }

    @Override
    protected void init() {
        int realWidth = width;
        int realHeight = height;
        WbLayout.UiScale scale = WbLayout.uiScale(realWidth, realHeight);
        width = scale.virtualWidth();
        height = scale.virtualHeight();
        try {
            search = new WbEditBox(font, listX(), panelY() + 52, listW(), 22);
            search.setBordered(false);
            search.setMaxLength(24);
            search.setTextColor(WbTheme.TEXT);
            search.setTextShadow(false);
            search.setHint(Lang.text("worldbinder.version_picker.search_hint"));
            search.setValue(query);
            search.setResponder(value -> {
                query = value.strip().toLowerCase(Locale.ROOT);
                scroll = 0;
                rebuildRows();
            });
            rebuildRowsScaled();
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
        chipEntries.clear();
        addRenderableWidget(search);
        int top = listTop();
        int bottom = listBottom();
        for (Item item : layout()) {
            if (item.entry() == null) {
                continue;
            }
            int y = top + item.y() - scroll;
            if (y < top || y + CHIP_H > bottom) {
                continue;
            }
            TargetMinecraftVersion.Entry entry = item.entry();
            boolean selected = entry.name().equals(current);
            Component tooltip = Component.translatable("worldbinder.version_picker.chip_tooltip", entry.name(),
                    Component.translatable(item.family().nameKey()), item.family().year);
            Button chip = WbButton.create(listX() + item.x(), y, CHIP_W, CHIP_H, entry.name(), tooltip, button -> choose(entry));
            WbButton.style(chip, WbButton.Style.DEFAULT, item.family().color, selected);
            addRenderableWidget(chip);
            chipEntries.put(chip, entry);
        }
        Button cancel = WbButton.icon(WbButton.quiet(WbButton.create(panelX() + panelWidth() - 112, panelY() + panelHeight() - 32,
                96, 22, Component.translatable("worldbinder.config.cancel"), Component.translatable("worldbinder.tooltip.config.back"),
                button -> onClose())), WbIcon.CLOSE);
        WbTooltips.hint(cancel, Component.literal("Esc"));
        addRenderableWidget(cancel);
        Button latest = WbButton.icon(WbButton.primary(WbButton.create(panelX() + panelWidth() - 260, panelY() + panelHeight() - 32,
                140, 22, Component.translatable("worldbinder.version_picker.use_latest", TargetMinecraftVersion.CURRENT),
                Component.translatable("worldbinder.version_picker.use_latest.tooltip"), button -> choose(TargetMinecraftVersion.resolve(TargetMinecraftVersion.CURRENT)))), WbIcon.STAR);
        addRenderableWidget(latest);
        setFocused(search);
    }

    private void choose(TargetMinecraftVersion.Entry entry) {
        onChoose.accept(entry.name());
        net.worldbinder.compat.ClientCompat.setScreen(minecraft, parent);
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int realWidth = width;
        int realHeight = height;
        boolean transition = WbTransition.begin(context, this, realWidth, realHeight);
        WbLayout.UiScale scale = WbLayout.uiScale(realWidth, realHeight);
        int vmx = scale.toVirtualX(mouseX);
        int vmy = scale.toVirtualY(mouseY);
        context.fill(0, 0, realWidth, realHeight, WbTheme.BACKDROP);
        context.pose().pushMatrix();
        context.pose().translate(scale.offsetX(), scale.offsetY());
        context.pose().scale(scale.scale(), scale.scale());
        width = scale.virtualWidth();
        height = scale.virtualHeight();
        try {
            WbChrome.drawBackdrop(context, width, height);
            int left = panelX();
            int top = panelY();
            int panelW = panelWidth();
            int panelH = panelHeight();
            WbChrome.drawPanel(context, left, top, panelW, panelH);
            WbChrome.rounded(context, left + 16, top + 14, 26, 26, WbTheme.withAlpha(WbTheme.ACCENT, 0x2C));
            WbIcon.GLOBE.draw(context, left + 19, top + 17, 3, WbTheme.ACCENT);
            context.text(font, Component.translatable("worldbinder.version_picker.title").withStyle(ChatFormatting.BOLD), left + 50, top + 16, WbTheme.TEXT, true);
            WbText.drawClipped(context, font, Lang.string("worldbinder.version_picker.subtitle_rich", current,
                    Lang.string(VersionFamily.of(current).nameKey())), left + 50, top + 29, panelW - 70, WbTheme.mutedText());
            WbChrome.drawField(context, listX(), top + 52, listW(), 22, WbTheme.ACCENT, search.isFocused(), search.isMouseOver(vmx, vmy));
            WbIcon.SEARCH.draw(context, listX() + listW() - 14, top + 59, 1, WbTheme.TEXT_DIM);

            hovered = null;
            for (GuiEventListener child : children()) {
                if (child instanceof Button button && button.isMouseOver(vmx, vmy) && chipEntries.containsKey(button)) {
                    hovered = chipEntries.get(button);
                }
            }

            int listTop = listTop();
            int listBottom = listBottom();
            List<Item> items = layout();
            context.enableScissor(listX() - 2, listTop, listX() + listW() + 2, listBottom);
            for (Item item : items) {
                if (item.entry() != null) {
                    continue;
                }
                int y = listTop + item.y() - scroll;
                if (y + HEADER_H < listTop || y > listBottom) {
                    continue;
                }
                drawFamilyHeader(context, item.family(), listX(), y, listW());
            }
            context.disableScissor();
            if (items.isEmpty()) {
                WbIcon.SEARCH.draw(context, listX() + 6, listTop + 10, 2, WbTheme.TEXT_DIM);
                WbText.drawClipped(context, font, Lang.string("worldbinder.version_picker.no_results"), listX() + 26, listTop + 13, listW() - 30, WbTheme.TEXT_MUTED);
            }
            int viewport = listBottom - listTop;
            WbChrome.drawScrollBar(context, listX() + listW() + 3, listTop, viewport, scroll, maxScroll, viewport);

            if (showDetail()) {
                TargetMinecraftVersion.Entry shown = hovered != null ? hovered : TargetMinecraftVersion.resolve(current);
                int dx = listX() + listW() + 14;
                drawDetail(context, shown, dx, top + 52, left + panelW - 16 - dx, listBottom - top - 52);
            }
            WbChrome.drawDivider(context, left + 16, top + panelH - 40, panelW - 32);
            WbIcon.INFO.draw(context, left + 18, top + panelH - 26, 1, WbTheme.TEXT_DIM);
            WbText.drawClipped(context, font, Lang.string("worldbinder.version_picker.footer_hint"), left + 30, top + panelH - 26,
                    panelW - 300, WbTheme.TEXT_DIM);
            super.extractRenderState(context, vmx, vmy, delta);
        } finally {
            width = realWidth;
            height = realHeight;
            context.pose().popMatrix();
        }
        WbTooltips.showHovered(this, context, font, vmx, vmy, mouseX, mouseY);
        WbTransition.end(context, transition);
        WbTransition.veil(context, this, realWidth, realHeight);
    }

    private void drawFamilyHeader(GuiGraphicsExtractor context, VersionFamily family, int x, int y, int w) {
        WbChrome.hGradient(context, x, y + 2, w, 20, WbTheme.withAlpha(family.color, 0x30), WbTheme.withAlpha(family.color, 0x00));
        context.fill(x, y + 2, x + 2, y + 22, family.color);
        WbChrome.rounded(context, x + 6, y + 4, 16, 16, WbTheme.withAlpha(family.color, 0x48));
        icon(family).draw(context, x + 7, y + 5, 2, WbMotion.mix(family.color, 0xFFFFFFFF, 0.25F));
        String name = Lang.string(family.nameKey());
        context.text(font, Component.literal(name).withStyle(ChatFormatting.BOLD), x + 28, y + 8, WbMotion.mix(family.color, WbTheme.TEXT, 0.45F), true);
        String year = Integer.toString(family.year);
        context.text(font, year, x + w - 8 - font.width(year), y + 8, WbTheme.TEXT_DIM, false);
    }

    private void drawDetail(GuiGraphicsExtractor context, TargetMinecraftVersion.Entry entry, int x, int y, int w, int h) {
        VersionFamily family = VersionFamily.of(entry.name());
        WbChrome.drawCardSurface(context, x, y, w, h, family.color, false, entry.name().equals(current));
        context.fillGradient(x + 3, y + 1, x + w - 3, y + 61, WbTheme.withAlpha(family.color, 0x38), 0x00000000);
        WbChrome.rounded(context, x + 12, y + 12, 40, 40, WbTheme.withAlpha(family.color, 0x50));
        WbChrome.roundedOutline(context, x + 12, y + 12, 40, 40, WbTheme.withAlpha(family.color, 0xC0));
        icon(family).draw(context, x + 18, y + 18, 4, WbMotion.mix(family.color, 0xFFFFFFFF, 0.3F));
        context.pose().pushMatrix();
        context.pose().translate(x + 62, y + 13);
        context.pose().scale(1.5F, 1.5F);
        context.text(font, Component.literal("Minecraft " + entry.name()).withStyle(ChatFormatting.BOLD), 0, 0, WbTheme.TEXT, true);
        context.pose().popMatrix();
        WbText.drawClipped(context, font, Lang.string(family.nameKey()) + "  •  " + family.year, x + 62, y + 32, w - 74,
                WbMotion.mix(family.color, WbTheme.TEXT, 0.35F));
        int cy = y + 64;
        cy += WbText.drawWrapped(context, font, Lang.string(family.descriptionKey()), x + 12, cy, w - 24, WbTheme.mutedText(), 3) + 4;
        WbChrome.drawDivider(context, x + 10, cy, w - 20);
        cy += 8;
        boolean selected = entry.name().equals(current);
        boolean native26 = entry.name().equals(TargetMinecraftVersion.CURRENT);
        int px = x + 12;
        if (selected) {
            px += WbChrome.drawPill(context, font, px, cy, Lang.string("worldbinder.version_picker.selected"), WbTheme.ACCENT, WbIcon.CHECK) + 4;
        }
        WbChrome.drawPill(context, font, px, cy, Lang.string(native26 ? "worldbinder.version_picker.native" : "worldbinder.version_picker.downgrade"),
                native26 ? WbTheme.OK : WbTheme.WARN, native26 ? WbIcon.STAR : WbIcon.WARNING);
        cy += 20;
        detailRow(context, x + 12, cy, w - 24, Lang.string("worldbinder.version_picker.data_version"), Integer.toString(entry.effectiveDataVersion())); cy += 13;
        detailRow(context, x + 12, cy, w - 24, Lang.string("worldbinder.version_picker.generation"),
                Lang.string(TargetMinecraftVersion.profileTranslationKey(entry.name()))); cy += 17;
        cy = featureRow(context, x + 12, cy, w - 24, entry.supportsModernItemComponents(), "worldbinder.version_picker.feature.components");
        cy = featureRow(context, x + 12, cy, w - 24, entry.usesSeparateEntityFiles(), "worldbinder.version_picker.feature.entities");
        cy = featureRow(context, x + 12, cy, w - 24, entry.usesPoiFiles(), "worldbinder.version_picker.feature.poi");
        cy = featureRow(context, x + 12, cy, w - 24, entry.usesModernDimensionFolders(), "worldbinder.version_picker.feature.dimensions");
        cy = featureRow(context, x + 12, cy, w - 24, entry.usesGameRulesFile(), "worldbinder.version_picker.feature.gamerules");
        if (!native26 && cy + 24 < y + h) {
            WbText.drawWrapped(context, font, Lang.string("worldbinder.version_picker.downgrade_hint", entry.name()), x + 12, cy + 4,
                    w - 24, WbTheme.TEXT_DIM, Math.max(1, (y + h - cy - 8) / 14));
        }
    }

    private void detailRow(GuiGraphicsExtractor context, int x, int y, int w, String label, String value) {
        WbText.drawClipped(context, font, label, x, y, w / 2, WbTheme.TEXT_DIM);
        WbText.drawClipped(context, font, value, x + w / 2, y, w / 2, WbTheme.TEXT_SOFT);
    }

    private int featureRow(GuiGraphicsExtractor context, int x, int y, int w, boolean enabled, String key) {
        (enabled ? WbIcon.CHECK : WbIcon.MINUS).draw(context, x, y + 1, 1, enabled ? WbTheme.OK : WbTheme.TEXT_DISABLED);
        WbText.drawClipped(context, font, Lang.string(key), x + 12, y, w - 12, enabled ? WbTheme.TEXT_SOFT : WbTheme.TEXT_DIM);
        return y + 12;
    }

    private static WbIcon icon(VersionFamily family) {
        try {
            return WbIcon.valueOf(family.iconName);
        } catch (IllegalArgumentException ignored) {
            return WbIcon.GLOBE;
        }
    }

    // ------------------------------------------------------------------ input

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
        return super.mouseDragged(WbLayout.virtualMouseEvent(event, width, height), scale.toVirtualDelta(offsetX), scale.toVirtualDelta(offsetY));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int next = Math.max(0, Math.min(maxScroll, scroll + (verticalAmount < 0 ? ROW_STEP * 2 : -ROW_STEP * 2)));
        if (next != scroll) {
            scroll = next;
            rebuildRows();
            return true;
        }
        WbLayout.UiScale scale = WbLayout.uiScale(width, height);
        return super.mouseScrolled(scale.toVirtualX(mouseX), scale.toVirtualY(mouseY), horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_RETURN && !query.isBlank()) {
            for (Map.Entry<Button, TargetMinecraftVersion.Entry> entry : chipEntries.entrySet()) {
                choose(entry.getValue());
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        net.worldbinder.compat.ClientCompat.setScreen(minecraft, parent);
    }
}
