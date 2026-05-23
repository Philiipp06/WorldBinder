package net.worldbinder.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
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
        int panelHeight = Math.min(WorldBinder.config().showLegalStartReminder ? 292 : 214, Math.max(204, height - 32));
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        int fieldY = top + 64;
        int buttonY = top + panelHeight - 36;
        int gap = 8;
        int buttons = WorldBinder.config().showLegalStartReminder ? 3 : 2;
        int buttonW = Math.max(82, (panelWidth - 40 - gap * (buttons - 1)) / buttons);

        archiveName = new EditBox(font, left + 18, fieldY, panelWidth - 36, 22, Lang.text("worldbinder.gui.archive_name"));
        archiveName.setMaxLength(80);
        archiveName.setValue(initialName);
        addRenderableWidget(archiveName);

        addRenderableWidget(WbButton.create(left + 16, buttonY, buttonW, 24, actionLabel,
                Lang.text("worldbinder.legal.start.tooltip"),
                b -> startWithName(false)));

        if (WorldBinder.config().showLegalStartReminder) {
            addRenderableWidget(WbButton.create(left + 16 + buttonW + gap, buttonY, buttonW, 24, Lang.string("worldbinder.legal.dont_show"),
                    Lang.text("worldbinder.legal.dont_show.tooltip"),
                    b -> startWithName(true)));
            addRenderableWidget(WbButton.create(left + 16 + (buttonW + gap) * 2, buttonY, buttonW, 24, Lang.string("worldbinder.gui.cancel"),
                    Lang.text("worldbinder.legal.cancel.tooltip"),
                    b -> minecraft.setScreen(parent)));
        } else {
            addRenderableWidget(WbButton.create(left + 16 + buttonW + gap, buttonY, buttonW, 24, Lang.string("worldbinder.gui.cancel"),
                    Lang.text("worldbinder.legal.cancel.tooltip"),
                    b -> minecraft.setScreen(parent)));
        }
    }

    private void startWithName(boolean hideFutureLegalReminder) {
        String name = archiveName == null ? initialName : archiveName.getValue();
        name = FileNames.cleanBaseName(name == null || name.isBlank() ? WorldBinder.config().defaultArchiveName : name);
        if (name.isBlank()) {
            name = FileNames.cleanBaseName(WorldBinder.config().defaultArchiveName);
        }
        if (hideFutureLegalReminder) {
            WorldBinder.config().showLegalStartReminder = false;
            WorldBinder.config().save();
        }
        minecraft.setScreen(parent);
        startAction.accept(name);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xAA05050C);
        int panelWidth = Math.min(580, Math.max(280, width - 32));
        int panelHeight = Math.min(WorldBinder.config().showLegalStartReminder ? 292 : 214, Math.max(204, height - 32));
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
        y = top + 96;
        if (WorldBinder.config().showLegalStartReminder) {
            y += WbText.drawWrapped(context, font,
                    Lang.string("worldbinder.legal.notice_1"),
                    x, y, textW, WbTheme.TEXT_SOFT, 2);
            y += 8;
            y += WbText.drawWrapped(context, font,
                    Lang.string("worldbinder.legal.notice_2"),
                    x, y, textW, WbTheme.WARN, 3);
            y += 8;
            WbText.drawWrapped(context, font,
                    Lang.string("worldbinder.legal.notice_3"),
                    x, y, textW, WbTheme.TEXT_MUTED, 2);
        } else {
            WbText.drawWrapped(context, font,
                    Lang.string("worldbinder.legal.hidden"),
                    x, y, textW, WbTheme.TEXT_MUTED, 3);
        }

        super.extractRenderState(context, mouseX, mouseY, delta);
    }
}
