package net.worldbinder.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.worldbinder.WorldBinder;
import net.worldbinder.ui.component.WbButton;
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
        int panelWidth = Math.min(580, Math.max(280, width - 32));
        int panelHeight = Math.min(236, Math.max(204, height - 32));
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        int fieldY = top + 64;
        int buttonY = top + panelHeight - 36;
        int gap = 8;
        int buttonW = Math.max(96, (panelWidth - 40 - gap) / 2);

        archiveName = new EditBox(font, left + 18, fieldY, panelWidth - 36, 22, Lang.text("worldbinder.gui.archive_name"));
        archiveName.setMaxLength(80);
        archiveName.setValue(initialName);
        addRenderableWidget(archiveName);

        addRenderableWidget(WbButton.create(left + 16, buttonY, buttonW, 24, actionLabel,
                Lang.text("worldbinder.legal.start.tooltip"),
                b -> startWithName()));
        addRenderableWidget(WbButton.create(left + 16 + buttonW + gap, buttonY, buttonW, 24, Lang.string("worldbinder.gui.cancel"),
                Lang.text("worldbinder.legal.cancel.tooltip"),
                b -> minecraft.setScreen(parent)));
    }

    private void startWithName() {
        String name = archiveName == null ? initialName : archiveName.getValue();
        name = FileNames.cleanBaseName(name == null || name.isBlank() ? WorldBinder.config().defaultArchiveName : name);
        if (name.isBlank()) {
            name = FileNames.cleanBaseName(WorldBinder.config().defaultArchiveName);
        }
        minecraft.setScreen(parent);
        startAction.accept(name);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xAA05050C);
        int panelWidth = Math.min(580, Math.max(280, width - 32));
        int panelHeight = Math.min(236, Math.max(204, height - 32));
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;

        context.fill(left, top, left + panelWidth, top + panelHeight, WbTheme.PANEL);
        context.fill(left + 1, top + 1, left + panelWidth - 1, top + panelHeight - 1, WbTheme.PANEL_INNER);
        context.fill(left, top, left + panelWidth, top + 3, WbTheme.ACCENT);
        context.fill(left, top + panelHeight - 3, left + panelWidth, top + panelHeight, WbTheme.ACCENT_DARK);

        int x = left + 18;
        int y = top + 18;
        int textW = panelWidth - 36;
        WbText.drawClipped(context, font, Lang.string("worldbinder.legal.header"), x, y, textW, WbTheme.ACCENT);
        WbText.drawClipped(context, font, Lang.string("worldbinder.gui.archive_name"), x, top + 50, textW, WbTheme.TEXT_MUTED);

        int legalLeft = left + 18;
        int legalTop = top + 104;
        int legalWidth = panelWidth - 36;
        int legalHeight = 68;
        context.fill(legalLeft - 2, legalTop - 2, legalLeft + legalWidth + 2, legalTop + legalHeight + 2, 0x33202030);
        y = legalTop + 8;
        y += WbText.drawWrapped(context, font,
                Lang.string("worldbinder.legal.short_notice"),
                legalLeft + 8, y, legalWidth - 16, WbTheme.TEXT_SOFT, 3);
        WbText.drawClipped(context, font, Lang.string("worldbinder.legal.about_location"), legalLeft + 8, y + 4, legalWidth - 16, WbTheme.INFO);

        super.extractRenderState(context, mouseX, mouseY, delta);
    }
}
