package net.worldbinder.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.worldbinder.WorldBinder;
import net.worldbinder.compat.ClientCompat;
import net.worldbinder.scene.ProjectDimensions;
import net.worldbinder.scene.WorldScene;
import net.worldbinder.ui.component.*;
import net.worldbinder.util.*;

/** Export submenu for the optional server-download lists: player list (CSV) and dimension tree (TXT). */
public final class WorldBinderExportExtrasScreen extends Screen {
    private final Screen parent;
    private final WorldScene scene;
    private final Runnable changed;

    public WorldBinderExportExtrasScreen(Screen parent, WorldScene scene, Runnable changed) {
        super(Lang.text("worldbinder.export.extras.title"));
        this.parent = parent;
        this.scene = scene;
        this.changed = changed;
    }

    private int panelWidth() {
        return Math.min(460, width - 36);
    }

    private int recordedPlayers() {
        return scene.seenPlayers == null ? 0 : scene.seenPlayers.size();
    }

    @Override
    protected void init() {
        int w = panelWidth(), x = (width - w) / 2, half = (w - 8) / 2;
        var settings = scene.exportSettings;
        addRenderableWidget(WbButton.icon(WbButton.toggle(WbButton.create(x, 46, w, 22,
                Lang.text("worldbinder.export.extras.players", Lang.string(settings.includePlayerList ? "worldbinder.export.enabled" : "worldbinder.export.disabled"), recordedPlayers()),
                Lang.text("worldbinder.export.extras.players.tooltip"), b -> {
                    settings.includePlayerList = !settings.includePlayerList;
                    changed.run();
                    rebuild();
                }), WbTheme.ACCENT, settings.includePlayerList), WbIcon.PLAYER));
        addRenderableWidget(WbButton.icon(WbButton.toggle(WbButton.create(x, 120, w, 22,
                Lang.text("worldbinder.export.extras.dimensions", Lang.string(settings.includeDimensionTree ? "worldbinder.export.enabled" : "worldbinder.export.disabled"), ProjectDimensions.all(scene).size()),
                Lang.text("worldbinder.export.extras.dimensions.tooltip"), b -> {
                    settings.includeDimensionTree = !settings.includeDimensionTree;
                    changed.run();
                    rebuild();
                }), WbTheme.ACCENT, settings.includeDimensionTree), WbIcon.LAYERS));
        var recording = WbButton.create(x, 72, w, 20,
                Lang.text("worldbinder.export.extras.recording", Lang.string(WorldBinder.config().recordPlayerList ? "worldbinder.export.enabled" : "worldbinder.export.disabled")),
                Lang.text("worldbinder.export.extras.recording.tooltip"), b -> {
                    WorldBinder.config().recordPlayerList = !WorldBinder.config().recordPlayerList;
                    WorldBinder.config().save();
                    rebuild();
                });
        addRenderableWidget(WbButton.quiet(recording));
        addRenderableWidget(WbButton.create(x, height - 28, half, 20, Lang.text("worldbinder.gui.back"), null, b -> onClose()));
        addRenderableWidget(WbButton.primary(WbButton.create(x + half + 8, height - 28, half, 20, Lang.text("worldbinder.export.extras.done"), null, b -> onClose())));
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        graphics.fill(0, 0, width, height, WbTheme.BACKDROP);
        int w = panelWidth(), x = (width - w) / 2;
        WbChrome.drawPanel(graphics, x - 10, 8, w + 20, height - 16);
        GuiText.drawCenteredTextWithShadow(graphics, font, title, width / 2, 18, WbTheme.TEXT);
        WbText.drawClipped(graphics, font, Lang.text("worldbinder.export.extras.players.file"), x, 98, w, WbTheme.TEXT_SOFT);
        WbText.drawClipped(graphics, font, Lang.text("worldbinder.export.extras.dimensions.file"), x, 146, w, WbTheme.TEXT_SOFT);
        WbText.drawClipped(graphics, font, Lang.text("worldbinder.export.extras.privacy"), x, 170, w, WbTheme.WARN);
        WbText.drawClipped(graphics, font, Lang.text("worldbinder.export.extras.location"), x, 184, w, WbTheme.TEXT_SOFT);
        super.extractRenderState(graphics, mouseX, mouseY, partial);
    }

    @Override
    public void onClose() {
        ClientCompat.setScreen(minecraft, parent);
    }
}
