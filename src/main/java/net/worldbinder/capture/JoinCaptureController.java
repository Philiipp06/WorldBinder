package net.worldbinder.capture;

import net.minecraft.client.Minecraft;
import net.worldbinder.WorldBinder;
import net.worldbinder.replay.ReplayPlayback;
import net.worldbinder.util.Chat;

/** Once per actual play connection. A manual stop never causes an immediate automatic restart. */
final class JoinCaptureController {
    private Object connection,stopped;
    private int readyTicks;
    private boolean attempted;
    void boundary(String reason){if(reason.equals("login"))readyTicks=0;}
    void joined(){readyTicks=0;}
    void disconnected(){connection=null;readyTicks=0;attempted=false;}
    void manualStop(Minecraft client){if(client.getConnection()!=null)stopped=client.getConnection().getConnection();attempted=true;}
    void tick(Minecraft client,SceneCaptureService capture){
        if(client.level==null||client.player==null||client.getConnection()==null||ReplayPlayback.state().present())return;
        Object actual=client.getConnection().getConnection();
        if(connection!=actual){connection=actual;readyTicks=0;attempted=false;}
        if(attempted||actual==stopped||capture.isCapturing()||capture.isSaving()||capture.routingPending())return;
        String identity=WorldIdentity.current(client);if(identity==null)return;
        var profile=WorldBinder.config().networkProfiles.get(identity);
        boolean enabled=profile!=null&&profile.autoCapture!=null?profile.autoCapture:WorldBinder.config().autoCaptureOnJoin;
        if(!enabled||net.worldbinder.compat.ReceivedChunks.chunk(client.level,client.player.getBlockX()>>4,client.player.getBlockZ()>>4)==null)return;
        if(++readyTicks<20)return;
        attempted=true;
        var backend=profile==null||profile.joinBackend==null?null:profile.backends.get(profile.joinBackend);
        if(backend!=null){
            capture.assignBackend(backend.id,backend.name,false);
            if(backend.projectPath!=null){capture.resumeAssignedProject(java.nio.file.Path.of(backend.projectPath));Chat.infoKey("worldbinder.auto_capture.resuming",backend.name);return;}
        }
        String name=backend==null?(client.getCurrentServer()==null?"WorldBinder":client.getCurrentServer().name)+"_"+java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")):backend.name;
        capture.startAutoCapture(name,profile==null?null:profile.captureSettings);
        if(capture.isCapturing())Chat.infoKey("worldbinder.auto_capture.started",name);
    }
}
