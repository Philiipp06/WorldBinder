package net.worldbinder.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.worldbinder.WorldBinder;
import net.worldbinder.capture.SceneCaptureService;
import net.worldbinder.hud.WorldBinderHud;
import net.worldbinder.input.WorldBinderKeybinds;
import net.worldbinder.placement.ScenePlacementService;
import net.worldbinder.render.WorldBinderWorldOverlay;
import net.worldbinder.scene.SceneLibrary;
import net.worldbinder.selection.SelectionManager;
import net.worldbinder.util.Chat;

public final class WorldBinderClient implements ClientModInitializer {
    private static SelectionManager selectionManager;
    private static SceneLibrary sceneLibrary;
    private static SceneCaptureService captureService;
    private static ScenePlacementService placementService;
    private static boolean recoveryNoticeShown;

    @Override
    public void onInitializeClient() {
        WorldBinder.init();
        selectionManager = new SelectionManager();
        sceneLibrary = new SceneLibrary();
        captureService = new SceneCaptureService(selectionManager, sceneLibrary);
        placementService = new ScenePlacementService(sceneLibrary);
        WorldBinderHud.register();
        WorldBinderWorldOverlay.register();
        WorldBinderKeybinds.register(selectionManager, captureService, placementService, sceneLibrary);
        ClientTickEvents.END_CLIENT_TICK.register(WorldBinderClient::showRecoveryNoticeOnce);
    }

    private static void showRecoveryNoticeOnce(Minecraft client) {
        if (recoveryNoticeShown || client.player == null || sceneLibrary == null) {
            return;
        }
        recoveryNoticeShown = true;
        sceneLibrary.refresh();
        int recoveries = sceneLibrary.recoveryCount();
        if (recoveries <= 0) {
            return;
        }
        Chat.warnKey("worldbinder.chat.recovery_available", recoveries, recoveries == 1 ? "" : "s");
        Chat.infoKey("worldbinder.chat.recovery_actions");
    }

    public static SelectionManager selections() {
        return selectionManager;
    }

    public static SceneLibrary scenes() {
        return sceneLibrary;
    }

    public static SceneCaptureService capture() {
        return captureService;
    }

    public static ScenePlacementService placement() {
        return placementService;
    }
}
