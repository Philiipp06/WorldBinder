package net.worldbinder.render;

public final class WorldBinderWorldOverlay {
    public enum State { SECURED, PROCESSING, FAILED }
    public record Frame(int chunkX,int chunkZ,int minY,int maxY,State state,boolean previousSecured) {}
    private static boolean registered;
    private static java.util.List<Frame> remembered=java.util.List.of();
    private static String world,dimension;
    public static int lastEmittedFrames;
    private WorldBinderWorldOverlay() {
    }

    public static void register() {
        registered=true;
    }

    public static State state(net.worldbinder.scene.ChunkSnapshot current,net.worldbinder.scene.ChunkSnapshot committed,boolean queued,boolean failed){
        if(failed||current!=null&&current.exportError)return State.FAILED;
        if(queued||current!=null&&!current.isDone())return State.PROCESSING;
        return committed!=null&&committed.isDone()?State.SECURED:null;
    }
    public static java.util.List<Frame> frames(net.worldbinder.capture.SceneCaptureService capture,int cx,int cz,int radius){
        var result=new java.util.ArrayList<Frame>();var scene=capture.exportPreview();if(scene==null)return result;
        var live=capture.chunkSnapshotsView();
        for(int z=cz-radius;z<=cz+radius;z++)for(int x=cx-radius;x<=cx+radius;x++){
            long key=net.worldbinder.scene.ProjectData.chunkKey(x,z);var current=live.get(key);var committed=scene.chunkSnapshots.get(x+","+z);
            var state=state(current,committed,capture.queuedChunksView().contains(key)||capture.cacheWritePending(key),capture.failedChunksView().contains(key)||capture.cacheWriteFailed(key));if(state==null)continue;
            var snapshot=current!=null?current:committed;
            int min=snapshot==null||snapshot.captureMinY==Integer.MIN_VALUE?net.worldbinder.WorldBinder.config().effectiveCaptureMinY():snapshot.captureMinY;
            int max=snapshot==null||snapshot.captureMaxY==Integer.MIN_VALUE?net.worldbinder.WorldBinder.config().effectiveCaptureMaxY():snapshot.captureMaxY;
            result.add(new Frame(x,z,min,max,state,committed!=null&&committed.isDone()&&capture.cachePreviouslyDurable(key)));
        }
        return result;
    }
    /** Called while vanilla's gizmo collector is active, on both loaders. No exploration data enters this path. */
    public static void emit(){
        lastEmittedFrames=0;if(!registered)return;
        var client=net.minecraft.client.Minecraft.getInstance();if(client.level==null||client.player==null)return;
        var active=net.worldbinder.client.WorldBinderClient.capture();
        // Container knowledge only makes sense for the project that belongs to this world and dimension.
        ContainerHighlight.emit(client,active!=null&&active.isCapturing()&&active.projectMatchesCurrentWorld()?active.exportPreview():null);
        if(!net.worldbinder.WorldBinder.config().worldChunkOverlay)return;
        var nextWorld=net.worldbinder.capture.WorldIdentity.current(client);var nextDimension=net.worldbinder.capture.WorldIdentity.dimension(client);
        if(!java.util.Objects.equals(world,nextWorld)||!java.util.Objects.equals(dimension,nextDimension)){remembered=java.util.List.of();world=nextWorld;dimension=nextDimension;}
        var capture=net.worldbinder.client.WorldBinderClient.capture();int radius=Math.clamp(net.worldbinder.WorldBinder.config().worldChunkOverlayRadius,1,8);
        int cx=client.player.blockPosition().getX()>>4,cz=client.player.blockPosition().getZ()>>4;
        if(capture!=null&&capture.isCapturing())remembered=capture.projectMatchesCurrentWorld()?frames(capture,cx,cz,radius):java.util.List.of();
        for(var frame:remembered){
            if(Math.abs(frame.chunkX()-cx)>radius||Math.abs(frame.chunkZ()-cz)>radius)continue;
            int color=switch(frame.state()){case SECURED->0xFF39D879;case PROCESSING->0xFFEF5350;case FAILED->0xFFFFD54F;};
            double x=frame.chunkX()*16.0,z=frame.chunkZ()*16.0;
            var box=new net.minecraft.world.phys.AABB(x+.03,frame.minY()+.03,z+.03,x+15.97,frame.maxY()+.97,z+15.97);
            net.minecraft.gizmos.Gizmos.cuboid(box,net.minecraft.gizmos.GizmoStyle.stroke(color,2));
            if(frame.previousSecured()&&frame.state()!=State.SECURED)
                net.minecraft.gizmos.Gizmos.cuboid(new net.minecraft.world.phys.AABB(x+.2,Math.clamp(client.player.getY(),frame.minY(),frame.maxY())+.15,z+.2,x+15.8,Math.clamp(client.player.getY(),frame.minY(),frame.maxY())+.35,z+15.8),net.minecraft.gizmos.GizmoStyle.stroke(0xFF39D879,3));
            lastEmittedFrames++;
        }
    }
}
