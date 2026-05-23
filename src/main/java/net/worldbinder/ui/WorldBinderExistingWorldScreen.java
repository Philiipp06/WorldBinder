package net.worldbinder.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.worldbinder.WorldBinder;
import net.worldbinder.capture.SceneCaptureService;
import net.worldbinder.util.Lang;

public final class WorldBinderExistingWorldScreen extends Screen {
    private final Screen parent;
    private final SceneCaptureService capture;

    public WorldBinderExistingWorldScreen(Screen parent, SceneCaptureService capture) {
        super(Lang.text("worldbinder.existing.title"));
        this.parent = parent;
        this.capture = capture;
    }

    @Override
    protected void init() {
        int w = 420;
        int left = (width - w) / 2;
        int y = height / 2 + 44;
        addRenderableWidget(Button.builder(Lang.text("worldbinder.existing.overwrite"), b -> {
            capture.saveNowConfirmed();
            minecraft.setScreen(parent);
        }).bounds(left, y, 200, 22).tooltip(Tooltip.create(Lang.text("worldbinder.existing.overwrite.tooltip"))).build());
        addRenderableWidget(Button.builder(Lang.text("worldbinder.gui.cancel"), b -> {
            minecraft.setScreen(parent);
        }).bounds(left + 220, y, 200, 22).tooltip(Tooltip.create(Lang.text("worldbinder.existing.cancel.tooltip"))).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xD905050C);
        int w = 500;
        int h = 180;
        int left = (width - w) / 2;
        int top = (height - h) / 2;
        context.fill(left, top, left + w, top + h, 0xEE11101C);
        context.fill(left, top, left + w, top + 3, 0xFFFF55FF);
        net.worldbinder.util.GuiText.drawCenteredTextWithShadow(context, font, Lang.text("worldbinder.existing.header"), width / 2, top + 18, 0xFFFFFFFF);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Lang.text("worldbinder.existing.message"), left + 30, top + 52, 0xFFE6E6F0);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Lang.text("worldbinder.existing.target", capture.targetFolderName()), left + 30, top + 74, 0xFFBDB6D9);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Lang.text("worldbinder.existing.help"), left + 30, top + 96, 0xFF8F86B8);
        if (WorldBinder.config().confirmExistingWorld) {
            super.extractRenderState(context, mouseX, mouseY, delta);
        }
    }
}
