package net.worldbinder.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.worldbinder.WorldBinder;
import net.worldbinder.capture.SceneCaptureService;
import net.worldbinder.ui.component.WbLayout;
import net.worldbinder.ui.component.WbTooltips;
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
        int realWidth = width;
        int realHeight = height;
        width = WbLayout.DESIGN_WIDTH;
        height = WbLayout.DESIGN_HEIGHT;
        try {
            initScaled();
        } finally {
            width = realWidth;
            height = realHeight;
        }
    }

    private void initScaled() {
        int panelW = Math.max(260, Math.min(500, width - 24));
        int left = (width - panelW) / 2;
        int y = height / 2 + 44;
        int gap = 10;
        int buttonW = Math.max(90, (panelW - 60 - gap) / 2);
        int buttonX = left + 30;
        addRenderableWidget(WbTooltips.register(Button.builder(Lang.text("worldbinder.existing.overwrite"), b -> {
            capture.saveNowConfirmed();
            minecraft.setScreen(parent);
        }).bounds(buttonX, y, buttonW, 22).build(), Lang.text("worldbinder.existing.overwrite.tooltip")));
        addRenderableWidget(WbTooltips.register(Button.builder(Lang.text("worldbinder.gui.cancel"), b -> {
            minecraft.setScreen(parent);
        }).bounds(buttonX + buttonW + gap, y, buttonW, 22).build(), Lang.text("worldbinder.existing.cancel.tooltip")));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int realWidth = width;
        int realHeight = height;
        WbLayout.UiScale uiScale = WbLayout.uiScale(realWidth, realHeight);
        int virtualMouseX = uiScale.toVirtualX(mouseX);
        int virtualMouseY = uiScale.toVirtualY(mouseY);
        context.fill(0, 0, realWidth, realHeight, 0x00000000);
        context.pose().pushMatrix();
        context.pose().translate(uiScale.offsetX(), uiScale.offsetY());
        context.pose().scale(uiScale.scale(), uiScale.scale());
        width = WbLayout.DESIGN_WIDTH;
        height = WbLayout.DESIGN_HEIGHT;
        try {
        context.fill(0, 0, width, height, 0xD905050C);
        int w = Math.max(260, Math.min(500, width - 24));
        int h = Math.max(156, Math.min(180, height - 24));
        int left = (width - w) / 2;
        int top = (height - h) / 2;
        context.fill(left, top, left + w, top + h, 0xEE11101C);
        context.fill(left, top, left + w, top + 3, 0xFFFF55FF);
        net.worldbinder.util.GuiText.drawCenteredTextWithShadow(context, font, Lang.text("worldbinder.existing.header"), width / 2, top + 18, 0xFFFFFFFF);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Lang.text("worldbinder.existing.message"), left + 30, top + 52, 0xFFE6E6F0);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Lang.text("worldbinder.existing.target", capture.targetFolderName()), left + 30, top + 74, 0xFFBDB6D9);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Lang.text("worldbinder.existing.help"), left + 30, top + 96, 0xFF8F86B8);
        if (WorldBinder.config().confirmExistingWorld) {
            super.extractRenderState(context, virtualMouseX, virtualMouseY, delta);
        }
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
