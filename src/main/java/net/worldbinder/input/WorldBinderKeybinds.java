package net.worldbinder.input;

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
    private static java.util.function.Consumer<Minecraft> captureKeys=client->{};
    public static void replayPausedKeys(Minecraft client){captureKeys.accept(client);}
    private static final KeyMapping.Category WORLD_BINDER_CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(WorldBinder.MOD_ID, WorldBinder.MOD_ID));

    private static long storagePopupJob = -1L;

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
            throw new net.worldbinder.validation.FindingStateException(net.worldbinder.validation.Finding.of("worldbinder.error.key_constant", field), exception);
        }
    }

    private WorldBinderKeybinds() {
    }

    public static void register(SelectionManager selections, SceneCaptureService capture, ScenePlacementService placement, SceneLibrary library) {
        KeyMapping openMenu = net.worldbinder.platform.Platform.get().registerKey(new KeyMapping(
                "key.worldbinder.open_menu",
                keyboardType(),
                keyCode("KEY_F9"),
                WORLD_BINDER_CATEGORY,
                0
        ));
        KeyMapping setFirst = net.worldbinder.platform.Platform.get().registerKey(new KeyMapping(
                "key.worldbinder.set_pos_1",
                keyboardType(),
                keyCode("KEY_F7"),
                WORLD_BINDER_CATEGORY,
                1
        ));
        KeyMapping setSecond = net.worldbinder.platform.Platform.get().registerKey(new KeyMapping(
                "key.worldbinder.set_pos_2",
                keyboardType(),
                keyCode("KEY_F8"),
                WORLD_BINDER_CATEGORY,
                2
        ));
        KeyMapping quickCapture = net.worldbinder.platform.Platform.get().registerKey(new KeyMapping(
                "key.worldbinder.quick_capture_world",
                keyboardType(),
                keyCode("KEY_F6"),
                WORLD_BINDER_CATEGORY,
                3
        ));
        KeyMapping openMap = net.worldbinder.platform.Platform.get().registerKey(new KeyMapping(
                "key.worldbinder.open_map",
                keyboardType(),
                keyCode("KEY_F10"),
                WORLD_BINDER_CATEGORY,
                4
        ));
        KeyMapping openMovement = net.worldbinder.platform.Platform.get().registerKey(new KeyMapping(
                "key.worldbinder.open_movement",
                keyboardType(),
                keyCode("KEY_F12"),
                WORLD_BINDER_CATEGORY,
                5
        ));

        KeyMapping toggleMovementMaster = net.worldbinder.platform.Platform.get().registerKey(new KeyMapping(
                "key.worldbinder.toggle_movement",
                keyboardType(),
                InputConstants.UNKNOWN.getValue(),
                WORLD_BINDER_CATEGORY,
                6
        ));
        java.util.EnumMap<net.worldbinder.movement.MovementTool, KeyMapping> toolKeys = new java.util.EnumMap<>(net.worldbinder.movement.MovementTool.class);
        int order = 7;
        for (net.worldbinder.movement.MovementTool tool : net.worldbinder.movement.MovementTool.values()) {
            toolKeys.put(tool, net.worldbinder.platform.Platform.get().registerKey(new KeyMapping(
                    tool.keybindKey(),
                    keyboardType(),
                    InputConstants.UNKNOWN.getValue(),
                    WORLD_BINDER_CATEGORY,
                    order++
            )));
        }
        MovementKeys.register(toggleMovementMaster, toolKeys);

        captureKeys=client -> {
            if (client.player == null && StorageFlow.progress().isRunning() && storagePopupJob != StorageFlow.progress().jobId()) {
                storagePopupJob = StorageFlow.progress().jobId();
                if (!(net.worldbinder.compat.ClientCompat.screen(client) instanceof WorldBinderStorageProgressScreen))
                net.worldbinder.compat.ClientCompat.setScreen(client, new WorldBinderStorageProgressScreen(net.worldbinder.compat.ClientCompat.screen(client)));
            }
            while (openMenu.consumeClick()) {
                net.worldbinder.client.WorldBinderClient.openControlCenter();
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
                            net.worldbinder.util.Lang.string("worldbinder.gui.start_saving"),
                            WorldBinder.config().defaultArchiveName));
                }
            }
            while (openMap.consumeClick()) {
                net.worldbinder.compat.ClientCompat.setScreen(Minecraft.getInstance(), new WorldBinderMapScreen(net.worldbinder.compat.ClientCompat.screen(Minecraft.getInstance())));
            }
            while (openMovement.consumeClick()) {
                net.worldbinder.compat.ClientCompat.setScreen(client, WorldBinderConfigScreen.movement(net.worldbinder.compat.ClientCompat.screen(client)));
            }
        };
        net.worldbinder.platform.Platform.get().endTick(client -> {
            MovementKeys.tick(client);
            captureKeys.accept(client);
            net.worldbinder.client.WorldBinderClient.captureTick();
            placement.tick();
        });
    }
}
