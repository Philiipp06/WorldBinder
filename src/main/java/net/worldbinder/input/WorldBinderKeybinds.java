package net.worldbinder.input;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.resources.Identifier;
import net.worldbinder.WorldBinder;
import net.worldbinder.capture.SceneCaptureService;
import net.worldbinder.placement.ScenePlacementService;
import net.worldbinder.scene.SceneLibrary;
import net.worldbinder.selection.SelectionManager;
import net.worldbinder.ui.WorldBinderScreen;
import net.worldbinder.ui.WorldBinderMapScreen;
import net.worldbinder.ui.WorldBinderStorageProgressScreen;
import net.worldbinder.ui.WorldBinderLegalStartScreen;
import net.worldbinder.storage.StorageFlow;
import net.worldbinder.storage.StorageStage;
import org.lwjgl.glfw.GLFW;

public final class WorldBinderKeybinds {
    private static final KeyMapping.Category WORLD_BINDER_CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(WorldBinder.MOD_ID, WorldBinder.MOD_ID));

    private static boolean storagePopupOpenedForCurrentJob;
    private static StorageStage lastStorageStage = StorageStage.IDLE;

    private WorldBinderKeybinds() {
    }

    public static void register(SelectionManager selections, SceneCaptureService capture, ScenePlacementService placement, SceneLibrary library) {
        KeyMapping openMenu = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.worldbinder.open_menu",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F9,
                WORLD_BINDER_CATEGORY,
                0
        ));
        KeyMapping setFirst = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.worldbinder.set_pos_1",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F7,
                WORLD_BINDER_CATEGORY,
                1
        ));
        KeyMapping setSecond = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.worldbinder.set_pos_2",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F8,
                WORLD_BINDER_CATEGORY,
                2
        ));
        KeyMapping quickCapture = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.worldbinder.quick_capture_world",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F6,
                WORLD_BINDER_CATEGORY,
                3
        ));
        KeyMapping openMap = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.worldbinder.open_map",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F10,
                WORLD_BINDER_CATEGORY,
                4
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (StorageFlow.progress().stage() != lastStorageStage) {
                lastStorageStage = StorageFlow.progress().stage();
                if (lastStorageStage == StorageStage.SNAPSHOT) {
                    storagePopupOpenedForCurrentJob = false;
                }
            }
            if (client.player == null && StorageFlow.progress().isRunning() && !storagePopupOpenedForCurrentJob && !(client.screen instanceof WorldBinderStorageProgressScreen)) {
                storagePopupOpenedForCurrentJob = true;
                client.setScreen(new WorldBinderStorageProgressScreen(client.screen));
            }
            while (openMenu.consumeClick()) {
                Minecraft.getInstance().setScreen(new WorldBinderScreen(selections, capture, placement, library));
            }
            while (setFirst.consumeClick()) {
                selections.setFirstFromCrosshair();
            }
            while (setSecond.consumeClick()) {
                selections.setSecondFromCrosshair();
            }
            while (quickCapture.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (capture.isRoamingCapture()) {
                    capture.finishActiveCapture();
                } else {
                    mc.setScreen(new WorldBinderLegalStartScreen(mc.screen,
                            name -> capture.toggleRoamingCapture(name),
                            "Start saving",
                            WorldBinder.config().defaultArchiveName));
                }
            }
            while (openMap.consumeClick()) {
                Minecraft.getInstance().setScreen(new WorldBinderMapScreen(Minecraft.getInstance().screen));
            }
            capture.tick();
            placement.tick();
        });
    }
}
