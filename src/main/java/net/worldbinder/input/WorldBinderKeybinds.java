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
import net.worldbinder.ui.WorldBinderConfigScreen;
import net.worldbinder.ui.WorldBinderMapScreen;
import net.worldbinder.ui.WorldBinderStorageProgressScreen;
import net.worldbinder.ui.WorldBinderLegalStartScreen;
import net.worldbinder.storage.StorageFlow;
import net.worldbinder.storage.StorageStage;

public final class WorldBinderKeybinds {
    private static final KeyMapping.Category WORLD_BINDER_CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(WorldBinder.MOD_ID, WorldBinder.MOD_ID));

    private static boolean storagePopupOpenedForCurrentJob;
    private static StorageStage lastStorageStage = StorageStage.IDLE;

    private static InputConstants.Type keyboardType() {
        try {
            return InputConstants.Type.valueOf("KEYBOARD");
        } catch (IllegalArgumentException ignored) {
            return InputConstants.Type.valueOf("KEYSYM");
        }
    }

    private static int keyCode(String field) {
        try {
            return InputConstants.class.getField(field).getInt(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Missing Minecraft key: " + field, exception);
        }
    }

    private WorldBinderKeybinds() {
    }

    public static void register(SelectionManager selections, SceneCaptureService capture, ScenePlacementService placement, SceneLibrary library) {
        KeyMapping openMenu = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.worldbinder.open_menu",
                keyboardType(),
                keyCode("KEY_F9"),
                WORLD_BINDER_CATEGORY,
                0
        ));
        KeyMapping setFirst = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.worldbinder.set_pos_1",
                keyboardType(),
                keyCode("KEY_F7"),
                WORLD_BINDER_CATEGORY,
                1
        ));
        KeyMapping setSecond = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.worldbinder.set_pos_2",
                keyboardType(),
                keyCode("KEY_F8"),
                WORLD_BINDER_CATEGORY,
                2
        ));
        KeyMapping quickCapture = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.worldbinder.quick_capture_world",
                keyboardType(),
                keyCode("KEY_F6"),
                WORLD_BINDER_CATEGORY,
                3
        ));
        KeyMapping openMap = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.worldbinder.open_map",
                keyboardType(),
                keyCode("KEY_F10"),
                WORLD_BINDER_CATEGORY,
                4
        ));
        KeyMapping openMovement = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.worldbinder.open_movement",
                keyboardType(),
                keyCode("KEY_F12"),
                WORLD_BINDER_CATEGORY,
                5
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (StorageFlow.progress().stage() != lastStorageStage) {
                lastStorageStage = StorageFlow.progress().stage();
                if (lastStorageStage == StorageStage.SNAPSHOT) {
                    storagePopupOpenedForCurrentJob = false;
                }
            }
            if (client.player == null && StorageFlow.progress().isRunning() && !storagePopupOpenedForCurrentJob && !(net.worldbinder.compat.ClientCompat.screen(client) instanceof WorldBinderStorageProgressScreen)) {
                storagePopupOpenedForCurrentJob = true;
                net.worldbinder.compat.ClientCompat.setScreen(client, new WorldBinderStorageProgressScreen(net.worldbinder.compat.ClientCompat.screen(client)));
            }
            while (openMenu.consumeClick()) {
                net.worldbinder.compat.ClientCompat.setScreen(Minecraft.getInstance(), new WorldBinderScreen(selections, capture, placement, library));
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
                    net.worldbinder.compat.ClientCompat.setScreen(mc, new WorldBinderLegalStartScreen(net.worldbinder.compat.ClientCompat.screen(mc),
                            name -> capture.toggleRoamingCapture(name),
                            "Start saving",
                            WorldBinder.config().defaultArchiveName));
                }
            }
            while (openMap.consumeClick()) {
                net.worldbinder.compat.ClientCompat.setScreen(Minecraft.getInstance(), new WorldBinderMapScreen(net.worldbinder.compat.ClientCompat.screen(Minecraft.getInstance())));
            }
            while (openMovement.consumeClick()) {
                net.worldbinder.compat.ClientCompat.setScreen(client, WorldBinderConfigScreen.movement(net.worldbinder.compat.ClientCompat.screen(client)));
            }
            capture.tick();
            placement.tick();
        });
    }
}
