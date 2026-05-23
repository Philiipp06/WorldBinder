package net.worldbinder.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
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
        int w = 360;
        int left = (width - w) / 2;
        int y = height / 2 + 70;
        addRenderableWidget(Button.builder(Component.translatable("worldbinder.storage.open_folder"), button -> {
            Path target = StorageFlow.progress().target();
            if (target != null) {
                PathOpener.open(target);
            }
        }).bounds(left, y, 170, 22).tooltip(Tooltip.create(Component.translatable("worldbinder.storage.open_folder.tooltip"))).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> {
            minecraft.setScreen(parent);
        }).bounds(left + 190, y, 170, 22).tooltip(Tooltip.create(Component.translatable("worldbinder.storage.done.tooltip"))).build());
    }

    public boolean shouldPause() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        StorageProgress progress = StorageFlow.progress();
        context.fill(0, 0, width, height, 0xD805050C);
        int w = 520;
        int h = 210;
        int left = (width - w) / 2;
        int top = (height - h) / 2;
        context.fill(left, top, left + w, top + h, 0xF0121020);
        context.fill(left, top, left + w, top + 3, progress.isTerminal() ? 0xFF55FFAA : 0xFFFF55FF);
        net.worldbinder.util.GuiText.drawCenteredTextWithShadow(context, font, Component.translatable("worldbinder.storage.title"), width / 2, top + 16, 0xFFFFFFFF);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Lang.text("worldbinder.storage.stage", progress.stage().label()), left + 28, top + 48, 0xFFEDE9FF);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Lang.text("worldbinder.storage.task", progress.detail()), left + 28, top + 68, 0xFFBDB6D9);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Lang.text("worldbinder.storage.elapsed", StorageProgress.formatMillis(progress.elapsedMillis())), left + 28, top + 88, 0xFFBDB6D9);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Lang.text("worldbinder.storage.remaining", progress.etaText()), left + 260, top + 88, 0xFFBDB6D9);
        Path target = progress.target();
        if (target != null) {
            String path = target.toAbsolutePath().toString();
            if (path.length() > 74) path = "..." + path.substring(path.length() - 71);
            net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Lang.text("worldbinder.storage.target", path), left + 28, top + 110, 0xFF8F86B8);
        }
        int barX = left + 28;
        int barY = top + 138;
        int barW = w - 56;
        int barH = 12;
        context.fill(barX, barY, barX + barW, barY + barH, 0x77000000);
        int filled = (int) (barW * Math.max(0.0D, Math.min(1.0D, progress.progress())));
        context.fill(barX, barY, barX + filled, barY + barH, progress.isTerminal() ? 0xFF55FFAA : 0xFFFF55FF);
        net.worldbinder.util.GuiText.drawCenteredTextWithShadow(context, font, Component.literal((int) (progress.progress() * 100.0D) + "%"), width / 2, barY + 2, 0xFFFFFFFF);
        net.worldbinder.util.GuiText.drawTextWithShadow(context, font, Component.translatable("worldbinder.storage.hint"), left + 28, top + 156, 0xFF8F86B8);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

}
