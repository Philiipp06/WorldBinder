package net.worldbinder.test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.player.ClientInput;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.worldbinder.WorldBinder;
import net.worldbinder.config.WorldBinderConfig;
import net.worldbinder.movement.*;
import net.worldbinder.status.WorldBinderNotifications;
import net.worldbinder.hud.WorldBinderHud;
import net.worldbinder.hud.WorldBinderNotificationOverlay;
import net.worldbinder.client.WorldBinderClient;
import net.worldbinder.ui.WorldBinderConfigScreen;
import net.worldbinder.ui.WorldBinderScreen;
import net.worldbinder.ui.WorldBinderVersionPickerScreen;
import net.worldbinder.ui.WorldBinderLegalStartScreen;
import net.worldbinder.ui.component.WbLayout;
import net.worldbinder.ui.component.WbMotion;
import net.worldbinder.util.Chat;

public final class WorldBinderClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        String compatibilityJar = System.getProperty("worldbinder.compatibilityJar");
        if (compatibilityJar != null) {
            String codeSource = WorldBinder.class.getProtectionDomain().getCodeSource().getLocation().toString();
            check(codeSource.contains(compatibilityJar), "Compatibility test must load the packaged 26.3 JAR: " + codeSource);
        }
        net.worldbinder.export.common.WorldExportGameRulesTest.run();
        context.runOnClient(client -> {
            WorldBinderConfig config = new WorldBinderConfig();
            var largeUi = WbLayout.uiScale(1920, 1080);
            check(largeUi.virtualWidth() == 960 && largeUi.virtualHeight() == 540 && largeUi.scale() == 1.0F,
                    "Large screens keep the fixed virtual menu size");
            var smallUi = WbLayout.uiScale(427, 240);
            check(smallUi.virtualWidth() == 480 && smallUi.virtualHeight() == 270 && smallUi.scale() < 1.0F,
                    "Small screens uniformly scale down the minimum menu size");
            check(net.worldbinder.version.TargetMinecraftVersion.resolve("26.2").effectiveDataVersion() == 4903,
                    "26.2 export target retained independently from 26.3 runtime");
            for (MovementTool tool : MovementTool.values()) {
                check(!config.movement.enabled(tool), tool + " must default off");
                config.movement.value(tool, Double.NaN);
                check(config.movement.value(tool) == tool.initial, tool + " nonfinite clamp");
                config.movement.value(tool, 999);
                check(config.movement.value(tool) == tool.max, tool + " upper clamp");
                config.movement.value(tool, -999);
                check(config.movement.value(tool) == tool.min, tool + " lower clamp");
                config.movement.enabled(tool, true);
            }
            config.messageMode = WorldBinderConfig.MessageMode.CHAT;
            config.reloadDisplayMode = WorldBinderConfig.ReloadDisplayMode.HIDDEN;
            config.movementBadgeXPercent = 37;
            config.movementBadgeYPercent = 23;
            config.movementBadgeScalePercent = 135;
            config.highContrastUi = true;
            config.reducedUiMotion = true;
            config.save();
            WorldBinderConfig loaded = WorldBinderConfig.load();
            check(loaded.messageMode == config.messageMode, "Message mode save/load");
            check(loaded.reloadDisplayMode == config.reloadDisplayMode, "Reload display mode save/load");
            check(loaded.movementBadgeXPercent == 37 && loaded.movementBadgeYPercent == 23
                    && loaded.movementBadgeScalePercent == 135, "Movement badge widget save/load");
            check(loaded.highContrastUi && loaded.reducedUiMotion, "Display preferences save/load");
            for (MovementTool tool : MovementTool.values()) check(loaded.movement.enabled(tool), tool + " save/load");
            loaded.reloadDisplayMode = WorldBinderConfig.ReloadDisplayMode.COMPACT;
            loaded.save();
            check(WorldBinderConfig.load().reloadDisplayMode == WorldBinderConfig.ReloadDisplayMode.HIDDEN,
                    "Legacy compact reload choice migrates to interactive display");
            loaded.reloadDisplayMode = WorldBinderConfig.ReloadDisplayMode.VANILLA;
            loaded.reloadDisplayConfigured = true;
            loaded.save();
            check(WorldBinderConfig.load().reloadDisplayMode == WorldBinderConfig.ReloadDisplayMode.VANILLA,
                    "Explicit vanilla reload choice remains available");
            WorldBinder.replaceConfig(new WorldBinderConfig());
            WorldBinder.saveConfig();
            var radar = new WorldBinderHud.WidgetBounds(800, 100, 150, 150);
            var status = new WorldBinderHud.WidgetBounds(400, 0, 520, 76);
            int clearTop = WorldBinderNotificationOverlay.chooseStackTop(8, 64, 540, 700, 300, status, radar);
            check(clearTop >= 255, "Toasts avoid both status and radar");
            check(WorldBinderNotificationOverlay.chooseStackTop(100, 64, 540, 10, 300, status, radar) == 100,
                    "Toasts keep their position when widgets do not overlap horizontally");
            var hotbar = new WorldBinderHud.WidgetBounds(135, 195, 200, 45);
            check(WorldBinderNotificationOverlay.chooseStackTop(198, 34, 240, 209, 210, hotbar) <= 156,
                    "Toasts move above the hotbar on compact screens");
        });
        context.setScreen(() -> new WorldBinderScreen(WorldBinderClient.selections(), WorldBinderClient.capture(),
                WorldBinderClient.placement(), WorldBinderClient.scenes(), new TitleScreen()));
        context.waitTicks(3);
        context.takeScreenshot("menu-overview-1_5");
        context.clickScreenButton("Capture");
        context.waitTicks(2);
        context.takeScreenshot("menu-capture-1_5");
        context.runOnClient(client -> {
            var screen = net.worldbinder.compat.ClientCompat.screen(client);
            for (int i = 0; i < 24; i++) screen.mouseScrolled(390, 180, 0, -1);
        });
        context.waitTicks(20);
        context.runOnClient(client -> {
            var screen = net.worldbinder.compat.ClientCompat.screen(client);
            check(screen.children().stream().anyMatch(child -> child instanceof Button button && button.visible
                    && button.getMessage().getString().equals("Custom Settings")),
                    "Capture preset actions remain reachable after scrolling");
        });
        context.waitTicks(1);
        context.takeScreenshot("menu-capture-bottom-1_5");
        context.clickScreenButton("Map");
        context.waitTicks(1);
        context.takeScreenshot("menu-map-1_5");
        context.setScreen(() -> new net.worldbinder.ui.WorldBinderMapScreen(new TitleScreen()));
        context.waitTicks(1);
        context.takeScreenshot("map-empty-1_5");
        context.setScreen(() -> new WorldBinderScreen(WorldBinderClient.selections(), WorldBinderClient.capture(),
                WorldBinderClient.placement(), WorldBinderClient.scenes(), new TitleScreen()));
        context.clickScreenButton("Capture");
        context.runOnClient(client -> check(net.worldbinder.compat.ClientCompat.screen(client).children().stream()
                        .anyMatch(child -> child instanceof Button button && button.visible
                                && button.getMessage().getString().equals("Minecraft 26.3")),
                "Capture version picker is visible without scrolling on compact screens"));
        context.clickScreenButton("Minecraft 26.3");
        context.waitFor(client -> net.worldbinder.compat.ClientCompat.screen(client) instanceof WorldBinderVersionPickerScreen);
        context.setScreen(() -> WorldBinderConfigScreen.general(new TitleScreen()));
        context.waitTicks(3);
        context.takeScreenshot("settings-general-1_5");
        context.runOnClient(client -> {
            var settings = net.worldbinder.compat.ClientCompat.screen(client);
            settings.keyPressed(new net.minecraft.client.input.KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_TAB,
                    0, com.mojang.blaze3d.platform.InputConstants.MOD_CONTROL));
            check(net.worldbinder.compat.ClientCompat.screen(client).children().stream()
                    .anyMatch(child -> child instanceof Button button && button.getMessage().getString().equals("Performance")),
                    "Ctrl+Tab navigates settings sections");
        });
        context.setScreen(() -> WorldBinderConfigScreen.general(new TitleScreen()));
        context.clickScreenButton("Minecraft 26.3");
        context.waitFor(client -> net.worldbinder.compat.ClientCompat.screen(client) instanceof WorldBinderVersionPickerScreen);
        context.runOnClient(client -> {
            EditBox search = (EditBox) net.worldbinder.compat.ClientCompat.screen(client).children().stream()
                    .filter(EditBox.class::isInstance).findFirst().orElseThrow();
            search.setValue("26.2");
            check(net.worldbinder.compat.ClientCompat.screen(client).children().stream()
                    .anyMatch(child -> child instanceof Button button
                            && button.getMessage().getString().equals("26.2")), "Version search filters supported targets");
        });
        context.waitTicks(3);
        context.takeScreenshot("version-picker-1_5");
        context.clickScreenButton("26.2");
        context.clickScreenButton("worldbinder.config.save");
        context.runOnClient(client -> {
            check(WorldBinder.config().targetMinecraftVersion.equals("26.2"), "Version picker saves settings target");
            WorldBinder.replaceConfig(new WorldBinderConfig());
            WorldBinder.saveConfig();
        });
        context.setScreen(() -> WorldBinderConfigScreen.performance(new TitleScreen()));
        context.waitTicks(3);
        context.takeScreenshot("settings-performance-1_5");
        context.setScreen(() -> WorldBinderConfigScreen.general(new TitleScreen()));
        context.clickScreenButton("Movement Tools");
        context.waitTicks(3);
        context.takeScreenshot("movement-settings-top");
        MovementTool.Category[] categories = MovementTool.Category.values();
        int categorizedTools = 0;
        for (MovementTool.Category category : categories) categorizedTools += category.tools().size();
        check(categorizedTools == MovementTool.values().length && MovementTool.values().length == 24,
                "Every movement tool belongs to exactly one settings category");
        for (int group = 0; group < categories.length; group++) {
            context.clickScreenButton(net.minecraft.network.chat.Component.translatable(categories[group].titleKey).getString());
            MovementTool[] tools = categories[group].tools().toArray(MovementTool[]::new);
            context.runOnClient(client -> {
            for (MovementTool tool : tools) {
                String name = net.minecraft.network.chat.Component.translatable(tool.key()).getString();
                Button toggle = (Button) net.worldbinder.compat.ClientCompat.screen(client).children().stream()
                        .filter(child -> child instanceof Button button && button.getMessage().getString().startsWith(name + ": "))
                        .findFirst().orElseThrow();
                toggle.onPress(new net.minecraft.client.input.KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN,
                        0, 0));
            }
            var sliders = net.worldbinder.compat.ClientCompat.screen(client).children().stream().filter(AbstractSliderButton.class::isInstance).toList();
            check(sliders.size() == java.util.Arrays.stream(tools).filter(MovementTool::adjustable).count(), "Movement group sliders present");
            try {
                var setValue = AbstractSliderButton.class.getDeclaredMethod("setValue", double.class);
                setValue.setAccessible(true);
                for (var child : sliders) {
                    AbstractSliderButton slider = (AbstractSliderButton)child;
                    check(slider.getWidth() > 0, "Movement slider bounds");
                    setValue.invoke(slider, 0.75);
                }
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            });
            if (group == 0) {
                context.runOnClient(client -> {
                    var screen = net.worldbinder.compat.ClientCompat.screen(client);
                    for (int i = 0; i < 5; i++) screen.mouseScrolled(500, 300, 0, -1);
                });
                context.waitTicks(3);
                context.takeScreenshot("movement-cards-1_6");
            }
        }
        context.clickScreenButton("worldbinder.config.save");
        context.runOnClient(client -> {
            var saved = WorldBinderConfig.load().movement;
            for (MovementTool tool : MovementTool.values()) {
                check(saved.enabled(tool), tool + " UI toggle/save");
                if (tool.adjustable()) {
                    double expected = Math.round((tool.min + 0.75 * (tool.max - tool.min)) * 10) / 10.0;
                    check(Math.abs(saved.value(tool) - expected) < 0.001, tool + " UI slider/save");
                }
            }
            WorldBinder.replaceConfig(new WorldBinderConfig());
        });
        context.setScreen(() -> WorldBinderConfigScreen.safety(new TitleScreen()));
        context.clickScreenButton("worldbinder.config.safety.page.gamerules");
        context.waitTicks(2);
        context.takeScreenshot("settings-gamerules-1_5");
        context.runOnClient(client -> {
            var screen = net.worldbinder.compat.ClientCompat.screen(client);
            String preset = net.minecraft.network.chat.Component.translatable("worldbinder.config.rules.peaceful").getString();
            for (int i = 0; i < 30 && screen.children().stream().noneMatch(child -> child instanceof Button button
                    && button.visible && button.getMessage().getString().equals(preset)); i++) {
                screen.mouseScrolled(400, 150, 0, -1);
            }
            check(screen.children().stream().anyMatch(child -> child instanceof Button button
                    && button.visible && button.getMessage().getString().equals(preset)),
                    "Gamerule presets remain reachable on compact screens");
        });
        context.waitTicks(2);
        context.takeScreenshot("settings-gamerules-controls-1_5");
        context.clickScreenButton("worldbinder.config.rules.peaceful");
        context.clickScreenButton("worldbinder.config.save");
        context.runOnClient(client -> {
            String rules = WorldBinder.config().gameRulesOverride;
            check(rules.contains("mobGriefing=false") && rules.contains("disableRaids=true")
                    && rules.contains("keepInventory=true"), "Safe gamerule preset persists all protections");
            WorldBinder.replaceConfig(new WorldBinderConfig());
            WorldBinder.saveConfig();
        });
        context.setScreen(() -> WorldBinderConfigScreen.hud(new TitleScreen()));
        context.waitTicks(2);
        context.takeScreenshot("settings-hud-1_5");
        context.clickScreenButton("worldbinder.config.widget_editor");
        context.waitTicks(2);
        context.takeScreenshot("widget-editor-1_5");
        context.setScreen(() -> WorldBinderConfigScreen.hud(new TitleScreen()));
        context.runOnClient(client -> {
            for (int i = 0; i < 60; i++) net.worldbinder.compat.ClientCompat.screen(client).mouseScrolled(400, 150, 0, -1);
        });
        context.waitTicks(2);
        context.takeScreenshot("message-settings");
        context.setScreen(() -> WorldBinderConfigScreen.general(new TitleScreen()));
        context.clickScreenButton("Interface");
        context.waitTicks(2);
        context.takeScreenshot("settings-interface-1_6");
        context.runOnClient(client -> {
            pressButton(client, "High contrast: OFF");
            pressButton(client, "Reduce motion: OFF");
            pressButton(client, "Violet");
        });
        context.clickScreenButton("worldbinder.config.save");
        context.runOnClient(client -> {
            check(WorldBinderConfig.load().highContrastUi && WorldBinderConfig.load().reducedUiMotion,
                    "Display settings save from UI");
            check(WorldBinderConfig.load().effectiveUiAccent() == net.worldbinder.ui.component.WbTheme.Accent.VIOLET,
                    "Theme accent saves from UI");
            net.worldbinder.ui.component.WbTheme.refresh();
            check(net.worldbinder.ui.component.WbTheme.ACCENT == net.worldbinder.ui.component.WbTheme.Accent.VIOLET.primary,
                    "Theme accent applies to the palette");
            check(WbMotion.approach(0, 1, 16_000_000L, 5) == 1, "Reduced motion snaps transitions");
        });
        context.setScreen(() -> WorldBinderConfigScreen.general(new TitleScreen()));
        context.clickScreenButton("Interface");
        context.takeScreenshot("settings-contrast-1_6");
        context.runOnClient(client -> {
            WorldBinder.replaceConfig(new WorldBinderConfig());
            WorldBinder.saveConfig();
        });
        context.setScreen(TitleScreen::new);
        AtomicReference<CompletableFuture<Void>> titleReload = new AtomicReference<>();
        context.runOnClient(client -> {
            WorldBinder.config().reloadDisplayMode = WorldBinderConfig.ReloadDisplayMode.HIDDEN;
            titleReload.set(client.reloadResourcePacks());
        });
        context.waitFor(client -> currentOverlay(client) instanceof net.minecraft.client.gui.screens.LoadingOverlay);
        context.runOnClient(client -> check(net.worldbinder.reload.ReloadInteraction.forInput(currentOverlay(client)) == null,
                "Title reload leaves menu input available"));
        context.waitTicks(2);
        context.takeScreenshot("reload-title-1_5");
        context.runOnClient(client -> {
            check(currentOverlay(client) instanceof net.minecraft.client.gui.screens.LoadingOverlay,
                    "Title reload remains active during input test");
            Button options = (Button) net.worldbinder.compat.ClientCompat.screen(client).children().stream()
                    .filter(child -> child instanceof Button button && button.getMessage().getString().equals(
                            net.minecraft.network.chat.Component.translatable("menu.options").getString()))
                    .findFirst().orElseThrow();
            clickThroughMouseHandler(client, options);
            check(net.worldbinder.compat.ClientCompat.screen(client)
                    instanceof net.minecraft.client.gui.screens.options.OptionsScreen,
                    "Menu button remains clickable during resource reload");
            net.worldbinder.compat.ClientCompat.setScreen(client, new TitleScreen());
        });
        context.waitFor(client -> titleReload.get() != null && titleReload.get().isDone());
        context.runOnClient(client -> {
            check(!titleReload.get().isCompletedExceptionally(), "Title-screen resource reload completes");
            WorldBinder.config().reloadDisplayMode = WorldBinderConfig.ReloadDisplayMode.VANILLA;
        });
        try (var world = context.worldBuilder().create()) {
            context.waitFor(client -> client.player != null && client.level != null);
            world.getServer().runCommand("gamemode survival @a");
            world.getServer().runCommand("fill -8 99 -8 8 99 8 minecraft:stone");
            world.getServer().runCommand("tp @a 0 100 0");
            context.waitTicks(10);
            context.runOnClient(client -> {
                KeyMapping quick = KeyMapping.get("key.worldbinder.quick_capture_world");
                check(quick != null, "F6 quick capture registered");
                KeyMapping.click(quick.getDefaultKey());
            });
            context.waitFor(client -> net.worldbinder.compat.ClientCompat.screen(client) instanceof WorldBinderLegalStartScreen);
            context.takeScreenshot("capture-quick-start-1_5");
            context.clickScreenButton("Full legal notice");
            context.waitFor(client -> net.worldbinder.compat.ClientCompat.screen(client) instanceof WorldBinderScreen);
            context.takeScreenshot("capture-legal-details-1_5");
            context.runOnClient(client -> net.worldbinder.compat.ClientCompat.screen(client).mouseScrolled(390, 180, 0, -3));
            context.waitTicks(1);
            context.takeScreenshot("capture-legal-end-1_5");
            context.runOnClient(client -> {
                var screen = net.worldbinder.compat.ClientCompat.screen(client);
                for (int i = 0; i < 24; i++) screen.mouseScrolled(390, 180, 0, -1);
            });
            context.waitTicks(1);
            context.takeScreenshot("capture-legal-full-1_5");
            context.runOnClient(client -> net.worldbinder.compat.ClientCompat.screen(client).onClose());
            context.waitFor(client -> net.worldbinder.compat.ClientCompat.screen(client) instanceof WorldBinderLegalStartScreen);
            context.runOnClient(client -> net.worldbinder.compat.ClientCompat.setScreen(client, null));
            context.waitTicks(2);
            context.runOnClient(client -> check(!client.isPaused(), "World resumed before reload movement test"));
            AtomicReference<CompletableFuture<Void>> reload = new AtomicReference<>();
            context.runOnClient(client -> {
                WorldBinder.config().reloadDisplayMode = WorldBinderConfig.ReloadDisplayMode.HIDDEN;
                reload.set(client.reloadResourcePacks());
            });
            context.waitFor(client -> currentOverlay(client) instanceof net.minecraft.client.gui.screens.LoadingOverlay);
            context.runOnClient(client -> check(net.worldbinder.reload.ReloadInteraction.forInput(currentOverlay(client)) == null,
                    "In-world reload leaves movement input available"));
            AtomicReference<net.minecraft.world.phys.Vec3> beforeReloadMove = new AtomicReference<>();
            context.runOnClient(client -> {
                beforeReloadMove.set(client.player.position());
                client.options.keyUp.setDown(true);
            });
            context.waitTicks(2);
            context.runOnClient(client -> {
                client.options.keyUp.setDown(false);
                double dx = client.player.getX() - beforeReloadMove.get().x;
                double dz = client.player.getZ() - beforeReloadMove.get().z;
                check(dx * dx + dz * dz > 0.0001,
                        "Player can move while resources reload: paused=" + client.isPaused()
                                + " overlay=" + currentOverlay(client) + " dx=" + dx + " dz=" + dz);
            });
            context.takeScreenshot("reload-compact-1_5");
            context.waitFor(client -> reload.get() != null && reload.get().isDone());
            context.runOnClient(client -> {
                check(!reload.get().isCompletedExceptionally(), "Interactive resource reload completes normally");
                net.worldbinder.compat.ClientCompat.setScreen(client,
                        new WorldBinderScreen(WorldBinderClient.selections(), WorldBinderClient.capture(),
                                WorldBinderClient.placement(), WorldBinderClient.scenes()));
                WorldBinder.config().reloadDisplayMode = WorldBinderConfig.ReloadDisplayMode.HIDDEN;
                reload.set(client.reloadResourcePacks());
            });
            context.waitTicks(2);
            context.takeScreenshot("reload-menu-1_5");
            context.waitFor(client -> reload.get() != null && reload.get().isDone());
            context.runOnClient(client -> {
                check(!reload.get().isCompletedExceptionally(), "Open-menu resource reload completes normally");
                net.worldbinder.compat.ClientCompat.setScreen(client, null);
                WorldBinder.config().reloadDisplayMode = WorldBinderConfig.ReloadDisplayMode.VANILLA;
            });
            context.runOnClient(client -> {
                WorldBinder.config().messageMode = WorldBinderConfig.MessageMode.TOAST;
                WorldBinderNotifications.clear();
                Chat.info("WorldBinder notification preview");
            });
            context.waitTicks(2);
            context.takeScreenshot("toast-preview-1_5");
            context.runOnClient(client -> WorldBinderNotifications.clear());
            testMessages(context);
            context.runOnClient(client -> {
                KeyMapping shortcut = KeyMapping.get("key.worldbinder.open_movement");
                check(shortcut != null, "Movement shortcut registered");
                check(shortcut.getDefaultKey().getValue() == com.mojang.blaze3d.platform.InputConstants.KEY_F12,
                        "Movement shortcut defaults to F12");
                KeyMapping.click(shortcut.getDefaultKey());
            });
            context.waitFor(client -> net.worldbinder.compat.ClientCompat.screen(client) instanceof WorldBinderConfigScreen);
            context.runOnClient(client -> {
                check(net.worldbinder.compat.ClientCompat.screen(client).children().stream()
                        .anyMatch(child -> child instanceof Button button
                                && button.getMessage().getString().startsWith("Movement tools: ")), "Shortcut opens Movement Tools");
                net.worldbinder.compat.ClientCompat.setScreen(client, null);
            });
            context.runOnClient(client -> testMovement(client));
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                WorldBinder.config().movement.enabled(MovementTool.NO_FALL, true);
                float health = player.getHealth();
                player.causeFallDamage(20, 1, player.damageSources().fall());
                check(player.getHealth() == health, "No Fall integrated server");
                WorldBinder.config().movement.enabled(MovementTool.NO_FALL, false);
                player.causeFallDamage(8, 1, player.damageSources().fall());
                check(player.getHealth() < health, "No Fall disabled restores damage");
                player.setHealth(health);
            });
            context.runOnClient(client -> {
                WorldBinder.config().movement.enabled(MovementTool.SPEED, true);
                WorldBinder.config().movement.enabled(MovementTool.AUTO_SPRINT, true);
                WorldBinder.config().movement.enabled(MovementTool.SAFE_WALK, true);
            });
            context.waitFor(WorldBinderClientTest::loadingOverlayGone);
            context.waitTicks(2);
            context.takeScreenshot("movement-badge-1_5");
            context.runOnClient(client -> {
                WorldBinder.config().movement.enabled(MovementTool.SPEED, false);
                WorldBinder.config().movement.enabled(MovementTool.AUTO_SPRINT, false);
                WorldBinder.config().movement.enabled(MovementTool.SAFE_WALK, false);
            });
            context.takeScreenshot("movement-world-smoke");
            String mapPreview = "map_preview_" + java.util.UUID.randomUUID().toString().replace("-", "");
            context.runOnClient(client -> {
                WorldBinder.config().messageMode = WorldBinderConfig.MessageMode.NONE;
                WorldBinderClient.capture().toggleRoamingCapture(mapPreview);
            });
            context.waitTicks(100);
            context.setScreen(() -> new net.worldbinder.ui.WorldBinderMapScreen(null));
            context.waitTicks(20);
            context.takeScreenshot("map-live-1_6");
            context.runOnClient(client -> {
                check(!WorldBinderClient.capture().chunkSnapshotsView().isEmpty(), "Roaming capture feeds the live map");
                check(net.worldbinder.render.MapRegionTextures.shared().regionCount() > 0, "Live map renders captured terrain textures");
                net.worldbinder.compat.ClientCompat.setScreen(client, null);
                WorldBinderClient.capture().cancelActiveCapture();
            });
            context.waitFor(client -> !WorldBinderClient.capture().isCapturing() && !WorldBinderClient.capture().isSaving());
            context.runOnClient(client -> {
                WorldBinder.config().mapExploreMode = true;
                WorldBinder.config().radarShowOutsideCapture = true;
                WorldBinder.config().mapShowPassive = true;
            });
            context.waitTicks(60);
            context.takeScreenshot("radar-hud-1_6");
            context.runOnClient(client -> check(net.worldbinder.render.MapExplorer.get().size() > 0, "Explore mode maps loaded chunks without capturing"));
            context.setScreen(() -> new net.worldbinder.ui.WorldBinderMapScreen(null));
            context.waitTicks(20);
            context.takeScreenshot("map-explore-1_6");
            context.runOnClient(client -> {
                try {
                    var result = net.worldbinder.render.MapExporter.export(WorldBinderClient.capture().chunkSnapshotsView(),
                            net.worldbinder.render.MapExporter.Mode.FULL, true, null).get(60, java.util.concurrent.TimeUnit.SECONDS);
                    check(java.nio.file.Files.size(result.path()) > 0 && result.chunks() > 0, "Live map PNG export");
                    var tiles = net.worldbinder.render.MapExporter.export(WorldBinderClient.capture().chunkSnapshotsView(),
                            net.worldbinder.render.MapExporter.Mode.TILES, true, key -> 0x40FF0000).get(60, java.util.concurrent.TimeUnit.SECONDS);
                    check(tiles.files() > 0, "Live map tile export");
                } catch (Exception e) {
                    throw new AssertionError("Map export", e);
                }
                net.worldbinder.compat.ClientCompat.setScreen(client, null);
                WorldBinder.config().mapExploreMode = false;
                WorldBinder.config().radarShowOutsideCapture = false;
                net.worldbinder.render.MapExplorer.get().clear();
            });
            context.setScreen(() -> new WorldBinderLegalStartScreen(null, name -> { }, null, "preview"));
            context.waitTicks(10);
            context.takeScreenshot("capture-setup-1_6");
            context.setScreen(() -> new net.worldbinder.ui.WorldBinderWidgetEditorScreen(null, WorldBinder.config()));
            context.waitTicks(10);
            context.takeScreenshot("widget-editor-world-1_6");
            context.setScreen(() -> null);
            CaptureRegressionTest.run(context);
        }
        context.runOnClient(client -> { WorldBinder.replaceConfig(new WorldBinderConfig()); WorldBinder.saveConfig(); });
        context.setScreen(TitleScreen::new);
        WorldBinder.LOGGER.info("WORLDBINDER_CLIENT_TEST_PASSED: configuration, UI, message routing, movement and capture/cache/recovery/export smoke checks");
    }

    private static net.minecraft.client.gui.screens.Overlay currentOverlay(Minecraft client) {
        try {
            try {
                return (net.minecraft.client.gui.screens.Overlay) client.gui.getClass().getMethod("overlay").invoke(client.gui);
            } catch (NoSuchMethodException ignored) {
                return (net.minecraft.client.gui.screens.Overlay) client.getClass().getMethod("getOverlay").invoke(client);
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot inspect the resource reload overlay", e);
        }
    }

    private static boolean loadingOverlayGone(Minecraft client) {
        return currentOverlay(client) == null;
    }

    private static void clickThroughMouseHandler(Minecraft client, Button button) {
        try {
            var window = client.getWindow();
            var handler = client.mouseHandler;
            var x = handler.getClass().getDeclaredField("xpos");
            var y = handler.getClass().getDeclaredField("ypos");
            x.setAccessible(true);
            y.setAccessible(true);
            x.setDouble(handler, (button.getX() + button.getWidth() / 2.0) * window.getScreenWidth() / window.getGuiScaledWidth());
            y.setDouble(handler, (button.getY() + button.getHeight() / 2.0) * window.getScreenHeight() / window.getGuiScaledHeight());
            var onButton = handler.getClass().getDeclaredMethod("onButton", long.class,
                    net.minecraft.client.input.MouseButtonInfo.class, int.class);
            onButton.setAccessible(true);
            int leftButton = net.minecraft.SharedConstants.getCurrentVersion().name().equals("26.3") ? 1 : 0;
            var left = new net.minecraft.client.input.MouseButtonInfo(leftButton, 0);
            onButton.invoke(handler, window.handle(), left, 1);
            onButton.invoke(handler, window.handle(), left, 0);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot dispatch resource-reload menu click", e);
        }
    }

    private static void testMessages(ClientGameTestContext context) {
        for (WorldBinderConfig.MessageMode mode : WorldBinderConfig.MessageMode.values()) {
            context.setScreen(() -> WorldBinderConfigScreen.hud(null));
            context.runOnClient(client -> {
                String label = net.minecraft.network.chat.Component.translatable(
                        "worldbinder.messages." + mode.name().toLowerCase(java.util.Locale.ROOT)).getString();
                Button choice = (Button) net.worldbinder.compat.ClientCompat.screen(client).children().stream()
                        .filter(child -> child instanceof Button button && button.getMessage().getString().equals(label))
                        .findFirst().orElseThrow();
                choice.onPress(new net.minecraft.client.input.KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN,
                        0, 0));
            });
            context.clickScreenButton("worldbinder.config.save");
            context.runOnClient(client -> {
                check(WorldBinder.config().effectiveMessageMode() == mode, "Message mode UI selection");
                check(WorldBinderConfig.load().effectiveMessageMode() == mode, "Message mode persistence");
                WorldBinderNotifications.clear();
                int before = chatCount(client);
                Chat.info("routing-info-" + mode);
                Chat.warn("routing-warn-" + mode);
                Chat.error("routing-error-" + mode);
                Chat.savedArchive("world", "routing-success-" + mode, 1, 0, 0, null);
                check(chatCount(client) - before == (mode.chat() ? 4 : 0), "Chat routing: " + mode);
                check(WorldBinderNotifications.visible(4).size() == (mode.toast() ? 4 : 0), "Toast routing: " + mode);
            });
        }
        context.runOnClient(client -> WorldBinderNotifications.clear());
    }

    private static int chatCount(Minecraft client) {
        try {
            Object chat = net.worldbinder.compat.ClientCompat.chat(client);
            var field = chat.getClass().getDeclaredField("allMessages");
            field.setAccessible(true);
            return ((List<?>)field.get(chat)).size();
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static void testMovement(Minecraft client) {
        var player = client.player;
        MovementSettings settings = new MovementSettings();
        WorldBinder.config().movement = settings;
        double originalSpeed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        double originalStep = player.getAttributeValue(Attributes.STEP_HEIGHT);
        double originalJump = player.getAttributeValue(Attributes.JUMP_STRENGTH);
        settings.enabled(MovementTool.SPEED, true);
        settings.value(MovementTool.SPEED, 5);
        settings.enabled(MovementTool.STEP, true);
        settings.value(MovementTool.STEP, 3);
        settings.enabled(MovementTool.HIGH_JUMP, true);
        settings.value(MovementTool.HIGH_JUMP, 2);
        MovementAttributes.tick(player);
        check(Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - originalSpeed * 5) < 0.001, "Speed 5x");
        check(player.getAttributeValue(Attributes.STEP_HEIGHT) >= 3, "Step height");
        check(player.getAttributeValue(Attributes.JUMP_STRENGTH) > 0.8, "High Jump");
        for (MovementTool tool : MovementTool.values()) settings.enabled(tool, false);
        MovementAttributes.tick(player);
        check(Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - originalSpeed) < 0.001, "Speed restores");
        check(player.getAttributeValue(Attributes.STEP_HEIGHT) == originalStep, "Step restores");
        check(player.getAttributeValue(Attributes.JUMP_STRENGTH) == originalJump, "High Jump restores");
        settings.enabled(MovementTool.JESUS, true);
        check(!player.canStandOnFluid(Blocks.WATER.defaultBlockState().getFluidState()), "No water support on dry ground");
        settings.enabled(MovementTool.JESUS, false);
        check(!player.canStandOnFluid(Blocks.WATER.defaultBlockState().getFluidState()), "Water surface off");
        player.setPos(0, 110, 0);
        player.setOnGround(false);
        settings.enabled(MovementTool.FLY, true);
        boolean mayFly = player.getAbilities().mayfly;
        boolean flying = player.getAbilities().flying;
        FlightMotion.tick(client);
        check(!MovementController.flight(player), "Fly toggle only arms double-jump flight");
        client.options.keyJump.setDown(true);
        FlightMotion.tick(client);
        FlightMotion.tick(client);
        check(!MovementController.flight(player), "Holding Jump does not count as a second tap");
        client.options.keyJump.setDown(false);
        FlightMotion.tick(client);
        client.options.keyJump.setDown(true);
        FlightMotion.tick(client);
        check(MovementController.flight(player), "Double Jump starts flight");
        client.options.keyJump.setDown(false);
        FlightMotion.tick(client);
        Vec3 start = player.position();
        player.setDeltaMovement(Vec3.ZERO);
        player.travel(new Vec3(0, 0, 1));
        double normalDistance = player.position().subtract(start).horizontalDistance();
        check(normalDistance > 0, "Flight moves");
        check(Math.abs(player.getY() - 110) < 0.001, "Flight holds altitude");
        player.setDeltaMovement(Vec3.ZERO);
        client.options.keySprint.setDown(true);
        start = player.position();
        player.travel(new Vec3(0, 0, 1));
        double sprintDistance = player.position().subtract(start).horizontalDistance();
        client.options.keySprint.setDown(false);
        check(sprintDistance > normalDistance * 1.9, "Sprint boosts flight speed");
        client.options.keyJump.setDown(true);
        double beforeRise = player.getY();
        player.travel(Vec3.ZERO);
        client.options.keyJump.setDown(false);
        check(player.getY() > beforeRise, "Flight ascends");
        client.options.keyShift.setDown(true);
        double beforeDescent = player.getY();
        player.travel(Vec3.ZERO);
        client.options.keyShift.setDown(false);
        check(player.getY() < beforeDescent, "Flight descends");
        net.worldbinder.compat.ClientCompat.setScreen(client, new TitleScreen());
        Vec3 pausedPosition = player.position();
        client.options.keyJump.setDown(true);
        FlightMotion.tick(client);
        player.travel(new Vec3(0, 0, 1));
        client.options.keyJump.setDown(false);
        FlightMotion.tick(client);
        client.options.keyJump.setDown(true);
        FlightMotion.tick(client);
        check(MovementController.flight(player), "Menu input cannot toggle flight");
        client.options.keyJump.setDown(false);
        FlightMotion.tick(client);
        check(player.position().equals(pausedPosition), "Flight ignores menu input");
        net.worldbinder.compat.ClientCompat.setScreen(client, null);
        FlightMotion.tick(client);
        client.options.keyJump.setDown(true);
        FlightMotion.tick(client);
        client.options.keyJump.setDown(false);
        FlightMotion.tick(client);
        client.options.keyJump.setDown(true);
        FlightMotion.tick(client);
        check(!MovementController.flight(player), "Second Double Jump stops flight");
        client.options.keyJump.setDown(false);
        FlightMotion.tick(client);
        client.options.keyJump.setDown(true);
        FlightMotion.tick(client);
        client.options.keyJump.setDown(false);
        FlightMotion.tick(client);
        client.options.keyJump.setDown(true);
        FlightMotion.tick(client);
        check(MovementController.flight(player), "Fly can be re-entered");
        settings.enabled(MovementTool.FLY, false);
        FlightMotion.tick(client);
        check(!MovementController.flight(player), "Disabling Fly exits active flight");
        client.options.keyJump.setDown(false);
        settings.enabled(MovementTool.FLY, true);
        FlightMotion.tick(client);
        check(!MovementController.flight(player), "Re-enabling Fly requires another double tap");
        client.options.keyJump.setDown(true);
        FlightMotion.tick(client);
        client.options.keyJump.setDown(false);
        FlightMotion.tick(client);
        client.options.keyJump.setDown(true);
        FlightMotion.tick(client);
        check(MovementController.flight(player), "Fly restarts after a new double tap");
        client.options.keyJump.setDown(false);
        player.setOnGround(true);
        FlightMotion.tick(client);
        check(!MovementController.flight(player), "Landing ends flight");
        player.setOnGround(false);
        settings.enabled(MovementTool.FLY, false);
        FlightMotion.tick(client);
        check(player.getAbilities().mayfly == mayFly && player.getAbilities().flying == flying, "Flight does not mutate abilities");
        settings.enabled(MovementTool.GLIDE, true);
        player.setDeltaMovement(0, -1, 0);
        MovementController.afterTravel(player);
        check(player.getDeltaMovement().y >= -0.15, "Glide caps fall speed");
        player.setDeltaMovement(0, 0.4, 0);
        MovementController.afterTravel(player);
        check(player.getDeltaMovement().y == 0.4, "Glide preserves ascent");
        settings.enabled(MovementTool.GLIDE, false);
        ClientInput originalInput = player.input;
        player.input = new ClientInput() {
            @Override public boolean hasForwardImpulse() { return true; }
        };
        player.input.keyPresses = new Input(true, false, false, false, false, false, false);
        settings.enabled(MovementTool.SPIDER, true);
        player.horizontalCollision = true;
        player.setDeltaMovement(0, -0.1, 0);
        SurfaceMotion.beforeTravel(player, settings);
        check(player.getDeltaMovement().y == -0.1, "Stale collision flag does not cause Spider climb");
        player.setPos(4.5, 120, 4.72);
        client.level.setBlock(new BlockPos(4, 120, 5), Blocks.STONE.defaultBlockState(), 3);
        SurfaceMotion.beforeTravel(player, settings);
        check(player.getDeltaMovement().y > 0, "Spider climbs");
        player.setDeltaMovement(Vec3.ZERO);
        player.jumpFromGround();
        check(player.getDeltaMovement().y == 0, "Spider suppresses ground jump");
        settings.enabled(MovementTool.SPIDER, false);
        settings.enabled(MovementTool.AUTO_SPRINT, true);
        player.horizontalCollision = false;
        SurfaceMotion.beforeTravel(player, settings);
        check(player.isSprinting(), "Auto Sprint");
        settings.enabled(MovementTool.AUTO_SPRINT, false);
        SurfaceMotion.updateSprint(player);
        check(!player.isSprinting(), "Auto Sprint releases its sprint");
        settings.enabled(MovementTool.AUTO_JUMP, true);
        player.setPos(0.5, 100, 0.65);
        player.setYRot(0);
        client.level.setBlock(new BlockPos(0, 100, 1), Blocks.STONE.defaultBlockState(), 3);
        player.setOnGround(true);
        player.horizontalCollision = true;
        player.setDeltaMovement(Vec3.ZERO);
        SurfaceMotion.beforeTravel(player, settings);
        check(player.getDeltaMovement().y > 0, "Auto Jump");
        settings.enabled(MovementTool.STEP, true);
        player.setDeltaMovement(Vec3.ZERO);
        SurfaceMotion.beforeTravel(player, settings);
        check(player.getDeltaMovement().y == 0, "Step suppresses Auto Jump");
        settings.enabled(MovementTool.STEP, false);
        settings.enabled(MovementTool.AUTO_JUMP, false);
        settings.enabled(MovementTool.AIR_CONTROL, true);
        settings.value(MovementTool.AIR_CONTROL, 5);
        player.setOnGround(false);
        player.horizontalCollision = false;
        MovementController.travel(player, new Vec3(0, 0, 1));
        check(player.getDeltaMovement().horizontalDistanceSqr() > 0, "Air Control");
        settings.enabled(MovementTool.AIR_CONTROL, false);
        testNewMovementTools(client, settings);
        testSurfaces(client, settings);
        player.input = originalInput;
        for (MovementTool tool : MovementTool.values()) settings.enabled(tool, false);
        MovementAttributes.tick(player);
        player.setPos(0, 100, 0);
        player.setDeltaMovement(Vec3.ZERO);
    }

    private static void testSurfaces(Minecraft client, MovementSettings settings) {
        var player = client.player;
        try {
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                client.level.setBlock(new BlockPos(x, 109, z), Blocks.WATER.defaultBlockState(), 3);
                client.level.setBlock(new BlockPos(x, 110, z), Blocks.WATER.defaultBlockState(), 3);
            }
            settings.enabled(MovementTool.JESUS, true);
            check(Blocks.LAVA.defaultBlockState().getCollisionShape(client.level, new BlockPos(0, 110, 0),
                    net.minecraft.world.phys.shapes.CollisionContext.of(player)).isEmpty(), "Jesus never adds lava collision");
            player.setPos(0, 111.1, 0);
            player.setOnGround(false);
            player.move(MoverType.SELF, new Vec3(0, -0.5, 0));
            check(player.getY() > 110.7, "Jesus collides with water surface");
            check(net.worldbinder.movement.WaterSurface.supported(player), "Water support at surface");
            player.input.keyPresses = new Input(true, false, false, false, false, true, false);
            player.move(MoverType.SELF, new Vec3(0, -0.5, 0));
            check(player.getY() < 110.7, "Sneaking enters water with Jesus enabled");
            player.input.keyPresses = new Input(true, false, false, false, false, false, false);
            check(!net.worldbinder.movement.WaterSurface.supported(player), "Underwater release does not push player up");
            player.setPos(0, 111.1, 0);
            settings.enabled(MovementTool.JESUS, false);
            player.move(MoverType.SELF, new Vec3(0, -0.6, 0));
            check(player.getY() < 110.7, "Jesus off enters water");
            player.setPos(0, 109.2, 0);
            var fluidUpdate = net.minecraft.world.entity.Entity.class.getDeclaredMethod("updateFluidInteraction");
            fluidUpdate.setAccessible(true);
            fluidUpdate.invoke(player);
            check(player.isInWater(), "Water fixture");
            player.setDeltaMovement(Vec3.ZERO);
            settings.enabled(MovementTool.WATER_SPEED, true);
            settings.value(MovementTool.WATER_SPEED, 5);
            MovementController.travel(player, new Vec3(0, 0, 1));
            check(player.getDeltaMovement().horizontalDistanceSqr() > 0.005, "Water Speed acceleration");
            settings.enabled(MovementTool.WATER_SPEED, false);
            settings.enabled(MovementTool.JESUS, true);
            var flowingPos = new BlockPos(7, 115, 7);
            client.level.setBlock(flowingPos, Blocks.WATER.defaultBlockState().setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL, 3), 3);
            player.setPos(7.5, 116.1, 7.5);
            player.move(MoverType.SELF, new Vec3(0, -1.0, 0));
            check(player.getY() > 115.3, "Flowing water collision");
            player.setPos(9, 120, 9);
            check(!net.worldbinder.movement.WaterSurface.supported(player), "Leaving water clears support without stored state");
            settings.enabled(MovementTool.JESUS, false);

            client.level.setBlock(new BlockPos(4, 110, 0), Blocks.LADDER.defaultBlockState(), 3);
            player.setPos(4.5, 110, 0.5);
            fluidUpdate.invoke(player);
            check(player.onClimbable(), "Ladder fixture");
            settings.enabled(MovementTool.FAST_LADDER, true);
            settings.value(MovementTool.FAST_LADDER, 3);
            var climb = net.minecraft.world.entity.LivingEntity.class.getDeclaredMethod("handleOnClimbable", Vec3.class);
            climb.setAccessible(true);
            Vec3 up = (Vec3)climb.invoke(player, new Vec3(0, 0.1, 0));
            check(up.y >= 0.59, "Fast Ladder ascent");
            player.input = new ClientInput();
            Vec3 down = (Vec3)climb.invoke(player, new Vec3(0, -0.2, 0));
            check(down.y <= -0.44, "Fast Ladder descent");
            settings.enabled(MovementTool.FAST_LADDER, false);
            Vec3 vanillaDown = (Vec3)climb.invoke(player, new Vec3(0, -0.2, 0));
            check(vanillaDown.y > -0.16, "Fast Ladder off restores vanilla clamp");

            client.level.setBlock(new BlockPos(0, 119, 0), Blocks.STONE.defaultBlockState(), 3);
            player.setPos(0.5, 120, 0.5);
            player.setOnGround(true);
            settings.enabled(MovementTool.SAFE_WALK, true);
            player.move(MoverType.SELF, new Vec3(1.5, 0, 0));
            check(player.getX() < 1.3, "Safe Walk stops at edge");
            settings.enabled(MovementTool.SAFE_WALK, false);
            player.move(MoverType.SELF, new Vec3(1.5, 0, 0));
            check(player.getX() > 1.5, "Safe Walk off releases movement");
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static void testNewMovementTools(Minecraft client, MovementSettings settings) {
        var player = client.player;
        double originalSpeed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        double originalSneak = player.getAttributeValue(Attributes.SNEAKING_SPEED);
        double originalGravity = player.getAttributeValue(Attributes.GRAVITY);
        settings.enabled(MovementTool.SNEAK_SPEED, true);
        settings.value(MovementTool.SNEAK_SPEED, 2);
        settings.enabled(MovementTool.NO_SLOW, true);
        settings.enabled(MovementTool.LOW_GRAVITY, true);
        settings.value(MovementTool.LOW_GRAVITY, 0.5);
        MovementAttributes.tick(player);
        check(player.getAttributeValue(Attributes.SNEAKING_SPEED) > originalSneak * 1.5, "Sneak Speed");
        check(player.getAttributeValue(Attributes.MOVEMENT_EFFICIENCY) >= 0.999, "No Slow");
        check(Math.abs(player.getAttributeValue(Attributes.GRAVITY) - originalGravity * 0.5) < 0.0001, "Low Gravity");
        settings.enabled(MovementTool.SNEAK_SPEED, false);
        settings.enabled(MovementTool.NO_SLOW, false);
        settings.enabled(MovementTool.LOW_GRAVITY, false);
        MovementAttributes.tick(player);
        check(player.getAttributeValue(Attributes.SNEAKING_SPEED) == originalSneak, "Sneak Speed restores");
        check(player.getAttributeValue(Attributes.GRAVITY) == originalGravity, "Low Gravity restores");

        settings.enabled(MovementTool.SPEED, true);
        settings.value(MovementTool.SPEED, 3);
        WorldBinder.config().movementMasterEnabled = false;
        MovementAttributes.tick(player);
        check(Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - originalSpeed) < 0.001, "Master switch pauses tools");
        WorldBinder.config().movementMasterEnabled = true;
        MovementAttributes.tick(player);
        check(player.getAttributeValue(Attributes.MOVEMENT_SPEED) > originalSpeed * 2.5, "Master switch resumes tools");
        settings.enabled(MovementTool.SPEED, false);
        MovementAttributes.tick(player);

        player.setPos(0.5, 140, 0.5);
        player.setOnGround(false);
        settings.enabled(MovementTool.JETPACK, true);
        settings.value(MovementTool.JETPACK, 3);
        client.options.keyJump.setDown(true);
        player.setDeltaMovement(0, -0.3, 0);
        AirMotion.jetpack(player, settings);
        client.options.keyJump.setDown(false);
        check(player.getDeltaMovement().y > -0.3, "Jetpack thrust");
        settings.enabled(MovementTool.JETPACK, false);

        settings.enabled(MovementTool.LONG_JUMP, true);
        settings.value(MovementTool.LONG_JUMP, 3);
        player.setSprinting(true);
        player.setDeltaMovement(0.3, 0.42, 0);
        AirMotion.afterGroundJump(player);
        check(player.getDeltaMovement().x > 0.45 && player.getDeltaMovement().y == 0.42, "Long Jump extends sprint jumps");
        player.setSprinting(false);
        player.setDeltaMovement(0.3, 0.42, 0);
        AirMotion.afterGroundJump(player);
        check(Math.abs(player.getDeltaMovement().x - 0.3) < 1.0E-6, "Long Jump ignores walking jumps");
        settings.enabled(MovementTool.LONG_JUMP, false);
        player.setDeltaMovement(Vec3.ZERO);

        MovementSettings profileSettings = new MovementSettings();
        MovementProfile.BUILDER.applyTo(profileSettings);
        check(profileSettings.enabled(MovementTool.FLY) && profileSettings.enabled(MovementTool.SAFE_WALK)
                && !profileSettings.enabled(MovementTool.SPEED), "Movement profiles apply their tool set");
        check(profileSettings.enabledCount() == MovementProfile.BUILDER.toolValues().size(), "Profiles disable other tools");

        var snapshots = new java.util.HashMap<Long, net.worldbinder.scene.ChunkSnapshot>();
        var snapshot = new net.worldbinder.scene.ChunkSnapshot(1000, 1000);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) snapshot.sample(x, z, 60 + x, "minecraft:stone", 0xFF777777);
        snapshots.put(net.minecraft.world.level.ChunkPos.pack(1000, 1000), snapshot);
        var textures = net.worldbinder.render.MapRegionTextures.shared();
        textures.sync(snapshots, 999, 999, 1001, 1001, true);
        check(textures.pendingChunks() == 1, "Live map queues changed chunks only");
        textures.paint(snapshots, 50_000_000L);
        check(textures.pendingChunks() == 0 && textures.regionCount() >= 1, "Live map paints region textures");
        textures.sync(snapshots, 999, 999, 1001, 1001, true);
        check(textures.pendingChunks() == 0, "Unchanged chunks are not repainted");
        textures.clear();
    }

    private static void pressButton(Minecraft client, String label) {
        Button button = (Button) net.worldbinder.compat.ClientCompat.screen(client).children().stream()
                .filter(child -> child instanceof Button candidate && candidate.getMessage().getString().equals(label))
                .findFirst().orElseThrow(() -> new AssertionError("Missing button: " + label));
        button.onPress(new net.minecraft.client.input.KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN, 0, 0));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
