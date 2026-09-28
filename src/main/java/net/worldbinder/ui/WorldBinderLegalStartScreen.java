package net.worldbinder.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.worldbinder.WorldBinder;
import net.worldbinder.client.WorldBinderClient;
import net.worldbinder.ui.component.WbButton;
import net.worldbinder.ui.component.WbBrand;
import net.worldbinder.ui.component.WbChrome;
import net.worldbinder.ui.component.WbEditBox;
import net.worldbinder.ui.component.WbLayout;
import net.worldbinder.ui.component.WbTooltips;
import net.worldbinder.ui.component.WbText;
import net.worldbinder.ui.component.WbTheme;
import net.worldbinder.util.FileNames;
import net.worldbinder.util.Lang;

import java.util.function.Consumer;

public final class WorldBinderLegalStartScreen extends Screen {
    private final Screen parent;
    private final Consumer<String> startAction;
    private final String actionLabel;
    private final String initialName;
    private String pendingName;
    private EditBox archiveName;

    public WorldBinderLegalStartScreen(Screen parent, Runnable startAction, String actionLabel) {
        this(parent, ignored -> startAction.run(), actionLabel, WorldBinder.config().defaultArchiveName);
    }

    public WorldBinderLegalStartScreen(Screen parent, Consumer<String> startAction, String actionLabel, String initialName) {
        super(Lang.text("worldbinder.legal.title"));
        this.parent = parent;
        this.startAction = startAction;
        this.actionLabel = actionLabel == null || actionLabel.isBlank() ? Lang.string("worldbinder.gui.start_saving") : actionLabel;
        this.initialName = initialName == null || initialName.isBlank() ? WorldBinder.config().defaultArchiveName : initialName;
    }

    @Override
    protected void init() {
        int realWidth = width;
        int realHeight = height;
        WbLayout.UiScale uiScale = WbLayout.uiScale(realWidth, realHeight);
        width = uiScale.virtualWidth();
        height = uiScale.virtualHeight();
        try {
            initScaled();
        } finally {
            width = realWidth;
            height = realHeight;
        }
    }

    private void initScaled() {
        int panelWidth = Math.min(560, width - 16);
        int panelHeight = Math.min(310, height - 16);
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        int fieldY = top + 76;
        int buttonY = top + panelHeight - 34;
        int gap = 8;
        int buttonW = Math.max(96, (panelWidth - 40 - gap) / 2);

        archiveName = new WbEditBox(font, left + 18, fieldY, panelWidth - 36, 24);
        archiveName.setBordered(false);
        archiveName.setMaxLength(80);
        archiveName.setTextColor(WbTheme.TEXT);
        archiveName.setTextShadow(false);
        archiveName.setValue(pendingName == null ? initialName : pendingName);
        addRenderableWidget(archiveName);

        int legalTop = top + 111;
        int legalHeight = panelHeight - 156;
        addRenderableWidget(WbButton.quiet(WbButton.create(left + 30, legalTop + legalHeight - 28,
                Math.min(230, panelWidth - 60), 22, Lang.text("worldbinder.legal.details"),
                null, b -> openLegalDetails())));
        addRenderableWidget(WbButton.primary(WbButton.create(left + 16, buttonY, buttonW, 24, actionLabel,
                Lang.text("worldbinder.legal.start.tooltip"),
                b -> startWithName())));
        addRenderableWidget(WbButton.quiet(WbButton.create(left + 16 + buttonW + gap, buttonY, buttonW, 24, Lang.string("worldbinder.gui.cancel"),
                Lang.text("worldbinder.legal.cancel.tooltip"),
                b -> net.worldbinder.compat.ClientCompat.setScreen(minecraft, parent))));
        setInitialFocus(archiveName);
    }

    private void openLegalDetails() {
        pendingName = archiveName.getValue();
        WorldBinderScreen details = new WorldBinderScreen(WorldBinderClient.selections(), WorldBinderClient.capture(),
                WorldBinderClient.placement(), WorldBinderClient.scenes(), this);
        details.openLegalNotice();
        net.worldbinder.compat.ClientCompat.setScreen(minecraft, details);
    }

    private void startWithName() {
        String name = archiveName == null ? initialName : archiveName.getValue();
        name = FileNames.cleanBaseName(name == null || name.isBlank() ? WorldBinder.config().defaultArchiveName : name);
        if (name.isBlank()) {
            name = FileNames.cleanBaseName(WorldBinder.config().defaultArchiveName);
        }
        net.worldbinder.compat.ClientCompat.setScreen(minecraft, parent);
        startAction.accept(name);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int realWidth = width;
        int realHeight = height;
        WbLayout.UiScale uiScale = WbLayout.uiScale(realWidth, realHeight);
        int virtualMouseX = uiScale.toVirtualX(mouseX);
        int virtualMouseY = uiScale.toVirtualY(mouseY);
        WbChrome.drawBackdrop(context, realWidth, realHeight);
        context.pose().pushMatrix();
        context.pose().translate(uiScale.offsetX(), uiScale.offsetY());
        context.pose().scale(uiScale.scale(), uiScale.scale());
        width = uiScale.virtualWidth();
        height = uiScale.virtualHeight();
        try {
        int panelWidth = Math.min(560, width - 16);
        int panelHeight = Math.min(310, height - 16);
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;

        WbChrome.drawPanel(context, left, top, panelWidth, panelHeight);

        WbBrand.draw(context, font, left + 18, top + 14, panelWidth - 36);
        WbText.drawClipped(context, font, Lang.string("worldbinder.legal.header"), left + 18, top + 52,
                panelWidth - 36, WbTheme.TEXT_SOFT);
        WbText.drawClipped(context, font, Lang.string("worldbinder.gui.archive_name"), left + 18, top + 65,
                panelWidth - 36, WbTheme.mutedText());
        if (archiveName instanceof WbEditBox styled) {
            WbChrome.drawField(context, styled.chromeX(), styled.chromeY(), styled.chromeWidth(),
                    styled.chromeHeight(), WbTheme.ACCENT, archiveName.isFocused(), false);
        }

        int legalLeft = left + 18;
        int legalTop = top + 111;
        int legalWidth = panelWidth - 36;
        int legalHeight = panelHeight - 156;
        WbChrome.drawInset(context, legalLeft, legalTop, legalWidth, legalHeight, WbTheme.WARN, true);
        WbText.drawClipped(context, font, Lang.string("worldbinder.about.legal_title"), legalLeft + 12,
                legalTop + 9, legalWidth - 24, WbTheme.WARN);
        WbText.drawWrapped(context, font,
                Lang.string("worldbinder.legal.short_notice"),
                legalLeft + 12, legalTop + 27, legalWidth - 24, WbTheme.TEXT_SOFT, 3);

        super.extractRenderState(context, virtualMouseX, virtualMouseY, delta);
        } finally {
            width = realWidth;
            height = realHeight;
            context.pose().popMatrix();
        }
        WbTooltips.showHovered(this, context, font, virtualMouseX, virtualMouseY, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        return super.mouseClicked(WbLayout.virtualMouseEvent(event, width, height), doubleClick);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        return super.mouseReleased(WbLayout.virtualMouseEvent(event, width, height));
    }
}
