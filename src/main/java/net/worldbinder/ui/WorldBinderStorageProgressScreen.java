package net.worldbinder.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.worldbinder.ui.component.WbButton;
import net.worldbinder.ui.component.WbChrome;
import net.worldbinder.ui.component.WbLayout;
import net.worldbinder.ui.component.WbTheme;
import net.worldbinder.ui.component.WbTooltips;
import net.worldbinder.util.Lang;
import net.worldbinder.util.PathOpener;
import net.worldbinder.storage.StorageFlow;
import net.worldbinder.storage.StorageProgress;

import java.nio.file.Path;

public final class WorldBinderStorageProgressScreen extends Screen {
    private final Screen parent;

    public WorldBinderStorageProgressScreen(Screen parent) {
        super(Component.translatable("worldbinder.storage.title"));
        this.parent = parent;
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
        int panelW = 500;
        int panelH = 206;
        int left = (width - panelW) / 2;
        int y = (height - panelH) / 2 + panelH - 36;
        int buttonW = Math.max(90, Math.min(170, (panelW - 40) / 2));
        addRenderableWidget(WbButton.create(left + 10, y, buttonW, 22, Component.translatable("worldbinder.storage.open_folder"),
                Component.translatable("worldbinder.storage.open_folder.tooltip"), button -> {
            Path target = StorageFlow.progress().target();
            if (target != null) {
                PathOpener.open(target);
            }
        }));
        addRenderableWidget(WbButton.create(left + panelW - buttonW - 10, y, buttonW, 22, Component.translatable("gui.done"),
                Component.translatable("worldbinder.storage.done.tooltip"), button -> {
            minecraft.gui.setScreen(parent);
        }));
    }

    public boolean shouldPause() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int realWidth = width;
        int realHeight = height;
        WbLayout.UiScale uiScale = WbLayout.uiScale(realWidth, realHeight);
        int virtualMouseX = uiScale.toVirtualX(mouseX);
        int virtualMouseY = uiScale.toVirtualY(mouseY);
        context.fill(0, 0, realWidth, realHeight, WbTheme.BACKDROP);
        context.pose().pushMatrix();
        context.pose().translate(uiScale.offsetX(), uiScale.offsetY());
        context.pose().scale(uiScale.scale(), uiScale.scale());
        width = WbLayout.DESIGN_WIDTH;
        height = WbLayout.DESIGN_HEIGHT;
        try {
        StorageProgress progress = StorageFlow.progress();
        int w = 500;
        int h = 206;
        int left = (width - w) / 2;
        int top = (height - h) / 2;
        WbChrome.drawPanel(context, left, top, w, h);
        context.fill(left, top, left + w, top + 2, progress.isTerminal() ? WbTheme.OK : WbTheme.ACCENT);
        net.worldbinder.util.GuiText.drawCenteredTextWithShadow(context, font, Component.translatable("worldbinder.storage.title"), width / 2, top + 16, 0xFFFFFFFF);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Lang.text("worldbinder.storage.stage", progress.stage().label()), left + 28, top + 48, 0xFFEDE9FF);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Lang.text("worldbinder.storage.task", progress.detail()), left + 28, top + 68, 0xFFBDB6D9);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Lang.text("worldbinder.storage.elapsed", StorageProgress.formatMillis(progress.elapsedMillis())), left + 28, top + 88, 0xFFBDB6D9);
        int remainingX = w > 430 ? left + 260 : left + 28;
        int remainingY = w > 430 ? top + 88 : top + 104;
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Lang.text("worldbinder.storage.remaining", progress.etaText()), remainingX, remainingY, 0xFFBDB6D9);
        Path target = progress.target();
        if (target != null) {
            String path = target.toAbsolutePath().toString();
            int maxChars = w < 360 ? 38 : 74;
            if (path.length() > maxChars) path = "..." + path.substring(path.length() - maxChars + 3);
            net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Lang.text("worldbinder.storage.target", path), left + 28, top + 122, 0xFF8F86B8);
        }
        int barX = left + 28;
        int barY = top + 138;
        int barW = w - 56;
        int barH = 12;
        int filled = (int) (barW * Math.max(0.0D, Math.min(1.0D, progress.progress())));
        WbChrome.drawProgressTrack(context, barX, barY, barW, barH, filled, progress.isTerminal() ? WbTheme.OK : WbTheme.ACCENT);
        net.worldbinder.util.GuiText.drawCenteredTextWithShadow(context, font, Component.literal((int) (progress.progress() * 100.0D) + "%"), width / 2, barY + 2, 0xFFFFFFFF);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Component.translatable("worldbinder.storage.hint"), left + 28, top + 154, 0xFF8F86B8);
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
