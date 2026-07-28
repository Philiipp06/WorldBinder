package net.worldbinder.ui.component;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class WorldBinderMenuButton extends Button {
    public static final int SIZE = 20;
    private static final int ICON_SIZE = 16;
    private static final int DEFAULT_GAP = 4;
    private static final int SCREEN_MARGIN = 4;
    private static final Identifier ICON_TEXTURE =
            Identifier.fromNamespaceAndPath("worldbinder", "icon.png");
    private static final RenderPipeline ICON_PIPELINE = RenderPipelines.GUI_TEXTURED;

    public WorldBinderMenuButton(OnPress onPress) {
        super(
                0,
                0,
                SIZE,
                SIZE,
                Component.translatable("worldbinder.menu.open"),
                onPress,
                DEFAULT_NARRATION
        );
        setTooltip(Tooltip.create(Component.translatable("worldbinder.tooltip.menu_open")));
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractDefaultSprite(graphics);
        int iconX = getX() + (getWidth() - ICON_SIZE) / 2;
        int iconY = getY() + (getHeight() - ICON_SIZE) / 2;
        graphics.blit(
                ICON_PIPELINE,
                ICON_TEXTURE,
                iconX,
                iconY,
                0.0F,
                0.0F,
                ICON_SIZE,
                ICON_SIZE,
                128,
                128,
                128,
                128
        );
    }

    public void placeInIconRow(Screen screen) {
        List<AbstractWidget> row = findIconRow(screen);
        int rowY = row.isEmpty() ? fallbackY(screen) : row.getFirst().getY();

        row.sort(Comparator.comparingInt(AbstractWidget::getX));
        row.add(this);

        int availableWidth = Math.max(SIZE, screen.width - SCREEN_MARGIN * 2);
        int gap = row.size() <= 1
                ? 0
                : Math.min(DEFAULT_GAP, Math.max(0, (availableWidth - row.size() * SIZE) / (row.size() - 1)));
        int rowWidth = row.size() * SIZE + Math.max(0, row.size() - 1) * gap;
        int startX = Math.max(SCREEN_MARGIN, (screen.width - rowWidth) / 2);

        for (int index = 0; index < row.size(); index++) {
            row.get(index).setPosition(startX + index * (SIZE + gap), rowY);
        }
    }

    private static List<AbstractWidget> findIconRow(Screen screen) {
        Map<Integer, List<AbstractWidget>> rows = screen.children().stream()
                .filter(SpriteIconButton.class::isInstance)
                .map(SpriteIconButton.class::cast)
                .filter(widget -> widget.getWidth() == SIZE && widget.getHeight() == SIZE)
                .collect(Collectors.groupingBy(AbstractWidget::getY));

        return rows.values().stream()
                .max(Comparator
                        .<List<AbstractWidget>>comparingInt(List::size)
                        .thenComparingInt(row -> -distanceFromCenter(screen, row)))
                .map(ArrayList::new)
                .orElseGet(ArrayList::new);
    }

    private static int distanceFromCenter(Screen screen, List<AbstractWidget> row) {
        if (row.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        int left = row.stream().mapToInt(AbstractWidget::getX).min().orElse(0);
        int right = row.stream().mapToInt(AbstractWidget::getRight).max().orElse(0);
        return Math.abs((left + right) / 2 - screen.width / 2);
    }

    private static int fallbackY(Screen screen) {
        return Math.max(SCREEN_MARGIN, screen.height - SIZE - SCREEN_MARGIN);
    }
}
