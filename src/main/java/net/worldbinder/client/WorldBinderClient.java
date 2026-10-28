package net.worldbinder.client;

import net.minecraft.client.Minecraft;
import net.worldbinder.WorldBinder;
import net.worldbinder.capture.SceneCaptureService;
import net.worldbinder.hud.WorldBinderHud;
import net.worldbinder.input.WorldBinderKeybinds;
import net.worldbinder.movement.FlightMotion;
import net.worldbinder.placement.ScenePlacementService;
import net.worldbinder.render.WorldBinderWorldOverlay;
import net.worldbinder.scene.SceneLibrary;
import net.worldbinder.selection.SelectionManager;
import net.worldbinder.status.WorldBinderNotifications;
import net.worldbinder.util.Chat;

public final class WorldBinderClient {
    private static SelectionManager selectionManager;
    private static SceneLibrary sceneLibrary;
    private static SceneCaptureService captureService;
    private static ScenePlacementService placementService;
    private static boolean recoveryNoticeShown;
    private static volatile String pendingCommand;
    private static long lastCaptureTick,lastReplayFrame;
    public static void captureTick(){lastCaptureTick=System.nanoTime();captureService.tick();}
    /** ReplayMod pauses client ticks as well as the timeline. Keep recording/UI responsive without advancing playback. */
    public static void replayFrame(){
        long now=System.nanoTime();if(captureService==null||now-lastReplayFrame<50_000_000L)return;lastReplayFrame=now;
        var replay=net.worldbinder.replay.ReplayPlayback.poll();
        if(!replay.present()||!replay.paused()||now-lastCaptureTick<50_000_000L)return;
        Minecraft client=Minecraft.getInstance();String command=pendingCommand;if(command!=null){pendingCommand=null;openCommand(client,command);}
        WorldBinderKeybinds.replayPausedKeys(client);captureTick();
        net.worldbinder.render.BlockMapColors.tick(client);net.worldbinder.render.MapExplorer.get().tick(client);net.worldbinder.render.MapVoxels.get().tick(client);
    }

    public static void initialize() {
        WorldBinder.init();
        net.worldbinder.ui.component.WbTheme.refresh();
        selectionManager = new SelectionManager();
        sceneLibrary = new SceneLibrary();
        captureService = new SceneCaptureService(selectionManager, sceneLibrary);
        placementService = new ScenePlacementService(sceneLibrary);
        WorldBinderHud.register();
        WorldBinderWorldOverlay.register();
        WorldBinderKeybinds.register(selectionManager, captureService, placementService, sceneLibrary);
        java.util.Map<String,Runnable> commands=new java.util.LinkedHashMap<>();
        for(String command:java.util.List.of("settings","map","archives","capture","movement","tools","profiler","status","help"))
            commands.put(command,()->pendingCommand=command);
        net.worldbinder.platform.Platform.get().clientCommand("worldbinder", () -> pendingCommand = "", commands);
        // Open after ChatScreen has finished sending/closing, on the next client tick.
        net.worldbinder.platform.Platform.get().endTick(client -> {
            String command=pendingCommand;
            if (command!=null) {pendingCommand=null;openCommand(client,command);}
        });
        net.worldbinder.platform.Platform.get().endTick(FlightMotion::tick);
        net.worldbinder.platform.Platform.get().endTick(WorldBinderClient::showRecoveryNoticeOnce);
        net.worldbinder.platform.Platform.get().endTick(client -> {
            net.worldbinder.render.BlockMapColors.tick(client);
            net.worldbinder.render.MapExplorer.get().tick(client);
            net.worldbinder.render.MapVoxels.get().tick(client);
            net.worldbinder.render.MapWaypoints.tick(client);
        });
        net.worldbinder.platform.Platform.get().joined(WorldBinderNotifications::clear);
        net.worldbinder.platform.Platform.get().joined(captureService::joined);
        net.worldbinder.platform.Platform.get().disconnected(() -> {
            Minecraft client = Minecraft.getInstance();
            captureService.onDisconnected();
            WorldBinderNotifications.clear();
            client.execute(() -> {
                net.worldbinder.render.MapRegionTextures.shared().clear();
                net.worldbinder.render.MapExplorer.get().clear();
                net.worldbinder.render.MapVoxels.get().clear();
                net.worldbinder.render.Map3DView.shared().reset();
            });
        });
    }

    public static void openControlCenter() {
        net.worldbinder.compat.ClientCompat.setScreen(Minecraft.getInstance(),
                new net.worldbinder.ui.WorldBinderScreen(selectionManager, captureService, placementService, sceneLibrary));
    }

    private static void openCommand(Minecraft client,String command) {
        var parent=net.worldbinder.ui.WorldBinderStorageProgressScreen.returnScreen(net.worldbinder.compat.ClientCompat.screen(client));
        switch(command) {
            case "settings" -> net.worldbinder.compat.ClientCompat.setScreen(client,new net.worldbinder.ui.WorldBinderConfigScreen(parent));
            case "map" -> net.worldbinder.compat.ClientCompat.setScreen(client,new net.worldbinder.ui.WorldBinderMapScreen(parent));
            case "movement" -> net.worldbinder.compat.ClientCompat.setScreen(client,net.worldbinder.ui.WorldBinderConfigScreen.movement(parent));
            case "tools" -> net.worldbinder.compat.ClientCompat.setScreen(client,new net.worldbinder.ui.WorldBinderRecordingToolsScreen(parent));
            case "profiler" -> net.worldbinder.compat.ClientCompat.setScreen(client,new net.worldbinder.ui.WorldBinderProfilerScreen(parent));
            case "status" -> {
                if(net.worldbinder.storage.StorageFlow.progress().stage()==net.worldbinder.storage.StorageStage.REVIEW&&captureService.isCapturing())captureService.requestFinishCapture();
                else if(net.worldbinder.storage.StorageFlow.progress().hasActivity())net.worldbinder.compat.ClientCompat.setScreen(client,new net.worldbinder.ui.WorldBinderStorageProgressScreen(parent));
                else net.worldbinder.compat.ClientCompat.clientSystemMessage(client,net.worldbinder.util.Lang.text("worldbinder.command.no_export"));
            }
            case "help" -> net.worldbinder.compat.ClientCompat.clientSystemMessage(client,net.worldbinder.util.Lang.text("worldbinder.command.help"));
            case "archives","capture" -> {
                var screen=new net.worldbinder.ui.WorldBinderScreen(selectionManager,captureService,placementService,sceneLibrary,parent);
                if(command.equals("archives"))screen.openArchives();else screen.openCapture();
                net.worldbinder.compat.ClientCompat.setScreen(client,screen);
            }
            default -> openControlCenter();
        }
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
        Chat.warnKey("worldbinder.chat.recovery_found", recoveries);
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
