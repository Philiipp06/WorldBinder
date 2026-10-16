package net.worldbinder.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.worldbinder.WorldBinder;
import net.worldbinder.client.WorldBinderClient;
import net.worldbinder.config.WorldBinderConfig;
import net.worldbinder.ui.component.WbBrand;
import net.worldbinder.ui.component.WbButton;
import net.worldbinder.ui.component.WbChrome;
import net.worldbinder.ui.component.WbEditBox;
import net.worldbinder.ui.component.WbIcon;
import net.worldbinder.ui.component.WbLayout;
import net.worldbinder.ui.component.WbMotion;
import net.worldbinder.ui.component.WbSounds;
import net.worldbinder.ui.component.WbText;
import net.worldbinder.ui.component.WbTheme;
import net.worldbinder.ui.component.WbTooltips;
import net.worldbinder.ui.component.WbTransition;
import net.worldbinder.util.FileNames;
import net.worldbinder.util.Lang;
import net.worldbinder.version.TargetMinecraftVersion;
import net.worldbinder.version.VersionFamily;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * F6 capture setup: archive name with live folder preview, quick setup cards (target version, preset, radius,
 * entities), a compact permitted-use notice and a clear start action.
 */
public final class WorldBinderLegalStartScreen extends Screen {
    private static final int[] RADIUS_STEPS = {2, 3, 5, 8, 12};

    private final Screen parent;
    private final Consumer<String> startAction;
    private final String actionLabel;
    private final String initialName;
    private String pendingName;
    private EditBox archiveName;

    private record Card(int x, int y, int w, int h, WbIcon icon, String label, String value, int accent) {
    }

    private final java.util.List<Card> cards = new java.util.ArrayList<>();

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

    private int panelWidth() {
        return Math.min(600, width - 16);
    }

    private int panelHeight() {
        return Math.min(372, height - 16);
    }

    /** Short windows: one row of setup cards and the legal link moves into the footer. */
    private boolean compact() {
        return panelHeight() < 330;
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
        WorldBinderConfig config = WorldBinder.config();
        cards.clear();
        int panelW = panelWidth();
        int panelH = panelHeight();
        int left = (width - panelW) / 2;
        int top = (height - panelH) / 2;
        int innerX = left + 18;
        int innerW = panelW - 36;

        int fieldY = top + 86;
        int fieldW = innerW - 52;
        archiveName = new WbEditBox(font, innerX, fieldY, fieldW, 24);
        archiveName.setBordered(false);
        archiveName.setMaxLength(80);
        archiveName.setTextColor(WbTheme.TEXT);
        archiveName.setTextShadow(false);
        archiveName.setValue(pendingName == null ? initialName : pendingName);
        WbTooltips.register(archiveName, Component.translatable("worldbinder.tooltip.archive_name"));
        addRenderableWidget(archiveName);
        addRenderableWidget(WbButton.iconOnly(WbButton.quiet(WbButton.create(innerX + fieldW + 4, fieldY + 1, 22, 22,
                Lang.string("worldbinder.capture_setup.use_world_name"), Component.translatable("worldbinder.capture_setup.use_world_name.tooltip"),
                b -> archiveName.setValue(FileNames.cleanBaseName(worldName())))), WbIcon.GLOBE));
        addRenderableWidget(WbButton.iconOnly(WbButton.quiet(WbButton.create(innerX + fieldW + 28, fieldY + 1, 22, 22,
                Lang.string("worldbinder.capture_setup.reset_name"), Component.translatable("worldbinder.capture_setup.reset_name.tooltip"),
                b -> archiveName.setValue(config.defaultArchiveName))), WbIcon.RESET));

        int cardY = top + 146;
        int gap = 8;
        int cols = compact() || innerW >= 440 ? 4 : 2;
        int cardW = (innerW - gap * (cols - 1)) / cols;
        int cardH = 40;
        String target = TargetMinecraftVersion.normalize(config.targetMinecraftVersion);
        VersionFamily family = VersionFamily.of(target);
        addCard(innerX, cardY, cardW, cardH, 0, cols, gap, WbIcon.GLOBE, "worldbinder.capture_setup.target", target, family.color,
                "worldbinder.tooltip.target_version", b -> {
                    pendingName = archiveName.getValue();
                    net.worldbinder.compat.ClientCompat.setScreen(minecraft, new WorldBinderVersionPickerScreen(this, config.targetMinecraftVersion, selected -> {
                        config.targetMinecraftVersion = selected;
                        config.save();
                    }));
                });
        addCard(innerX, cardY, cardW, cardH, 1, cols, gap, WbIcon.PERFORMANCE, "worldbinder.capture_setup.preset", presetLabel(config.performancePreset),
                WbTheme.ACCENT_RIGHT, "worldbinder.capture_setup.preset.tooltip", b -> {
                    WorldBinderConfig.PerformancePreset[] presets = {
                            WorldBinderConfig.PerformancePreset.SAFE, WorldBinderConfig.PerformancePreset.BALANCED,
                            WorldBinderConfig.PerformancePreset.FAST, WorldBinderConfig.PerformancePreset.EXTREME};
                    int index = 0;
                    for (int i = 0; i < presets.length; i++) if (presets[i] == config.performancePreset) index = i;
                    config.setPreset(presets[(index + 1) % presets.length]);
                    rebuild();
                });
        addCard(innerX, cardY, cardW, cardH, 2, cols, gap, WbIcon.TARGET, "worldbinder.capture_setup.radius",
                Lang.string("worldbinder.capture_setup.radius_value", config.effectiveRoamingRadiusChunks()), WbTheme.INFO,
                "worldbinder.tooltip.config.radius", b -> {
                    int current = config.effectiveRoamingRadiusChunks();
                    int next = RADIUS_STEPS[0];
                    for (int step : RADIUS_STEPS) {
                        if (step > current) {
                            next = step;
                            break;
                        }
                    }
                    config.roamingRadiusChunks = next;
                    config.save();
                    rebuild();
                });
        addCard(innerX, cardY, cardW, cardH, 3, cols, gap, WbIcon.ENTITY, "worldbinder.capture_setup.entities",
                Lang.string(config.captureEntities ? "worldbinder.common.on" : "worldbinder.common.off"),
                config.captureEntities ? WbTheme.OK : WbTheme.TEXT_DIM, "worldbinder.tooltip.config.entities", b -> {
                    config.captureEntities = !config.captureEntities;
                    config.save();
                    WbSounds.toggle(config.captureEntities);
                    rebuild();
                });

        int rows = (4 + cols - 1) / cols;
        int legalY = cardY + rows * (cardH + gap) + 6;
        int legalH = panelH - (legalY - top) - 48;
        int buttonY = top + panelH - 34;
        int startW = compact() ? Math.max(140, innerW * 2 / 5) : Math.max(160, innerW / 2);
        int cancelW = Math.min(compact() ? 100 : 140, innerW - startW - 8);
        if (compact()) {
            int linkX = innerX + cancelW + 8;
            int linkW = innerW - startW - cancelW - 16;
            addRenderableWidget(WbButton.icon(WbButton.quiet(WbButton.create(linkX, buttonY + 2, linkW, 20,
                    Lang.text("worldbinder.legal.details"), Component.translatable("worldbinder.about.legal_tooltip"),
                    b -> openLegalDetails())), WbIcon.SHIELD));
        } else {
            int linkW = Math.min(180, innerW / 3);
            addRenderableWidget(WbButton.icon(WbButton.quiet(WbButton.create(innerX + innerW - linkW - 10, legalY + Math.max(8, legalH - 30),
                    linkW, 20, Lang.text("worldbinder.legal.details"), Component.translatable("worldbinder.about.legal_tooltip"),
                    b -> openLegalDetails())), WbIcon.SHIELD));
        }
        Button start = WbButton.icon(WbButton.primary(WbButton.create(innerX + innerW - startW, buttonY, startW, 24, actionLabel,
                Lang.text("worldbinder.legal.start.tooltip"), b -> startWithName())), WbIcon.PLAY);
        WbTooltips.hint(start, Component.literal("Enter"));
        addRenderableWidget(start);
        Button cancel = WbButton.icon(WbButton.quiet(WbButton.create(innerX, buttonY, cancelW, 24,
                Lang.string("worldbinder.gui.cancel"), Lang.text("worldbinder.legal.cancel.tooltip"),
                b -> net.worldbinder.compat.ClientCompat.setScreen(minecraft, parent))), WbIcon.CLOSE);
        WbTooltips.hint(cancel, Component.literal("Esc"));
        addRenderableWidget(cancel);
        setInitialFocus(archiveName);
    }

    private void addCard(int baseX, int baseY, int w, int h, int index, int cols, int gap, WbIcon icon, String labelKey, String value, int accent,
                         String tooltipKey, Button.OnPress action) {
        int x = baseX + (index % cols) * (w + gap);
        int y = baseY + (index / cols) * (h + gap);
        cards.add(new Card(x, y, w, h, icon, Lang.string(labelKey), value, accent));
        Button hit = WbButton.ghost(WbButton.create(x, y, w, h, Component.translatable(labelKey), Component.translatable(tooltipKey), action));
        WbTooltips.hint(hit, Component.translatable("worldbinder.capture_setup.click_to_change"));
        addRenderableWidget(WbButton.style(hit, WbButton.Style.DEFAULT, accent, false));
    }

    private void rebuild() {
        pendingName = archiveName == null ? pendingName : archiveName.getValue();
        clearWidgets();
        init();
    }

    private static String presetLabel(WorldBinderConfig.PerformancePreset preset) {
        return Lang.string(switch (preset == null ? WorldBinderConfig.PerformancePreset.CUSTOM : preset) {
            case SAFE -> "worldbinder.gui.preset_safe";
            case BALANCED -> "worldbinder.gui.preset_balanced";
            case FAST -> "worldbinder.gui.preset_fast";
            case EXTREME -> "worldbinder.gui.preset_extreme";
            case CUSTOM -> "worldbinder.gui.custom_mode";
        });
    }

    private String worldName() {
        try {
            if (minecraft.getSingleplayerServer() != null) {
                return minecraft.getSingleplayerServer().getWorldData().getLevelName();
            }
            if (minecraft.getCurrentServer() != null) {
                return minecraft.getCurrentServer().ip.replace(':', '_');
            }
        } catch (RuntimeException ignored) {
            // Fall back to the configured default name.
        }
        return WorldBinder.config().defaultArchiveName;
    }

    private String dimensionName() {
        if (minecraft.level == null) {
            return "";
        }
        String id = minecraft.level.dimension().identifier().getPath();
        return switch (id) {
            case "overworld" -> Lang.string("worldbinder.capture_setup.dimension.overworld");
            case "the_nether" -> Lang.string("worldbinder.capture_setup.dimension.nether");
            case "the_end" -> Lang.string("worldbinder.capture_setup.dimension.end");
            default -> id;
        };
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
        WbSounds.success();
        net.worldbinder.compat.ClientCompat.setScreen(minecraft, parent);
        startAction.accept(name);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
            startWithName();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int realWidth = width;
        int realHeight = height;
        boolean transition = WbTransition.begin(context, this, realWidth, realHeight);
        WbLayout.UiScale uiScale = WbLayout.uiScale(realWidth, realHeight);
        int vmx = uiScale.toVirtualX(mouseX);
        int vmy = uiScale.toVirtualY(mouseY);
        context.fill(0, 0, realWidth, realHeight, WbTheme.BACKDROP);
        context.pose().pushMatrix();
        context.pose().translate(uiScale.offsetX(), uiScale.offsetY());
        context.pose().scale(uiScale.scale(), uiScale.scale());
        width = uiScale.virtualWidth();
        height = uiScale.virtualHeight();
        try {
            WbChrome.drawBackdrop(context, width, height);
            int panelW = panelWidth();
            int panelH = panelHeight();
            int left = (width - panelW) / 2;
            int top = (height - panelH) / 2;
            int innerX = left + 18;
            int innerW = panelW - 36;
            WbChrome.drawPanel(context, left, top, panelW, panelH);
            context.fillGradient(left + 2, top + 3, left + panelW - 2, top + 62, WbTheme.withAlpha(WbTheme.ACCENT, 0x24), 0x00000000);

            WbBrand.draw(context, font, innerX, top + 14, 140);
            int titleX = innerX + 150;
            context.text(font, Component.literal(actionLabel).withStyle(ChatFormatting.BOLD), titleX, top + 16, WbTheme.TEXT, true);
            WbText.drawClipped(context, font, Lang.string("worldbinder.capture_setup.subtitle"), titleX, top + 29, innerW - 150, WbTheme.mutedText());
            String world = worldName();
            String dimension = dimensionName();
            String place = dimension.isEmpty() ? world : world + "  •  " + dimension;
            int placeX = titleX;
            placeX += WbChrome.drawPill(context, font, placeX, top + 42, WbText.ellipsize(font, place, innerW - 260), WbTheme.INFO, WbIcon.GLOBE) + 4;
            if (WorldBinderClient.capture() != null && WorldBinderClient.capture().multiplayerSafetyActive()) {
                WbChrome.drawPill(context, font, placeX, top + 42, Lang.string("worldbinder.capture_setup.server_safety"), WbTheme.WARN, WbIcon.SHIELD);
            }

            context.text(font, Component.translatable("worldbinder.gui.archive_name").withStyle(ChatFormatting.BOLD), innerX, top + 74, WbTheme.TEXT_SOFT, true);
            if (archiveName instanceof WbEditBox styled) {
                WbChrome.drawField(context, styled.chromeX(), styled.chromeY(), styled.chromeWidth(), styled.chromeHeight(), WbTheme.ACCENT,
                        archiveName.isFocused(), archiveName.isMouseOver(vmx, vmy));
            }
            String raw = archiveName == null ? initialName : archiveName.getValue();
            String cleaned = FileNames.cleanBaseName(raw);
            String folder = "saves/WorldBinder/" + cleaned + (WorldBinder.config().appendTimestampToArchiveName ? "_" + Lang.string("worldbinder.capture.name_timestamp") : "");
            WbIcon.FOLDER.draw(context, innerX + 2, top + 116, 1, WbTheme.TEXT_DIM);
            WbText.drawClipped(context, font, folder, innerX + 14, top + 116, innerW - 14, WbTheme.TEXT_DIM);
            boolean changed = raw != null && !raw.isBlank() && !cleaned.equals(raw.strip().toLowerCase(Locale.ROOT).replace(' ', '_'));
            if (changed) {
                WbIcon.WARNING.draw(context, innerX + 2, top + 128, 1, WbTheme.WARN);
                WbText.drawClipped(context, font, Lang.string("worldbinder.capture_setup.name_cleaned"), innerX + 14, top + 128, innerW - 14, WbTheme.WARN);
            }

            for (Card card : cards) {
                boolean hovered = WbChrome.contains(card.x(), card.y(), card.w(), card.h(), vmx, vmy);
                WbChrome.drawCardSurface(context, card.x(), card.y(), card.w(), card.h(), card.accent(), hovered, false);
                card.icon().draw(context, card.x() + 10, card.y() + 8, 1, card.accent());
                WbText.drawClipped(context, font, card.label().toUpperCase(Locale.ROOT), card.x() + 21, card.y() + 8, card.w() - 30, WbTheme.TEXT_DIM);
                context.text(font, Component.literal(WbText.ellipsize(font, card.value(), card.w() - 30)).withStyle(ChatFormatting.BOLD),
                        card.x() + 10, card.y() + 23, WbMotion.mix(card.accent(), WbTheme.TEXT, 0.5F), true);
                WbIcon.CHEVRON_RIGHT.draw(context, card.x() + card.w() - 12, card.y() + 23, 1, hovered ? card.accent() : WbTheme.TEXT_DISABLED);
            }

            int cols = compact() || innerW >= 440 ? 4 : 2;
            int rows = (4 + cols - 1) / cols;
            int legalY = top + 146 + rows * 48 + 6;
            int legalH = panelH - (legalY - top) - 48;
            if (!compact() && legalH > 30) {
                WbChrome.rounded(context, innerX, legalY, innerW, legalH, 0x30FFC27D & 0x00FFFFFF | 0x1E000000);
                WbChrome.roundedOutline(context, innerX, legalY, innerW, legalH, WbTheme.withAlpha(WbTheme.WARN, 0x60));
                context.fill(innerX + 1, legalY + 3, innerX + 3, legalY + legalH - 3, WbTheme.WARN);
                WbIcon.SHIELD.draw(context, innerX + 12, legalY + 9, 1, WbTheme.WARN);
                context.text(font, Component.translatable("worldbinder.about.legal_title").withStyle(ChatFormatting.BOLD), innerX + 24, legalY + 8, WbTheme.WARN, true);
                int linkW = Math.min(180, innerW / 3);
                WbText.drawWrapped(context, font, Lang.string("worldbinder.legal.short_notice"), innerX + 12, legalY + 22,
                        innerW - linkW - 34, WbTheme.TEXT_SOFT, Math.max(1, (legalH - 26) / 14));
            }
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

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        return super.mouseClicked(WbLayout.virtualMouseEvent(event, width, height), doubleClick);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        return super.mouseReleased(WbLayout.virtualMouseEvent(event, width, height));
    }
}
