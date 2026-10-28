package net.worldbinder.replay;

import java.io.*;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import net.worldbinder.WorldBinder;
import net.worldbinder.scene.WorldScene;
import net.worldbinder.util.Lang;

/** Optional, read-only playback adapters. No recorder, network or inventory behavior is replaced. */
public final class ReplayPlayback {
    public interface Guarded {}
    public record State(String provider,String identity,long millis,boolean paused,boolean seeking,String error) {
        public boolean present(){return !provider.isEmpty();}
        public boolean ready(){return present()&&identity!=null&&error==null&&!seeking;}
    }
    private static final State ABSENT=new State("",null,0,false,false,null);
    private static volatile State state=ABSENT;
    private static final AtomicLong seeks=new AtomicLong();
    private static final AtomicLong soughtMinimum=new AtomicLong(Long.MAX_VALUE);
    private static long observedSeek;
    private static int settling;
    private static Object identifiedFile;
    private static CompletableFuture<String> identifiedHash;
    private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"WorldBinder-ReplayIdentity");t.setDaemon(true);return t;});
    private static final Set<String> logged=new HashSet<>();
    private ReplayPlayback(){}
    public static State state(){return state;}
    /** Called before foreign seek methods can emit any packets, including on Flashback's replay server. */
    public static void seek(){seeks.incrementAndGet();}
    public static void seekTo(long millis){soughtMinimum.accumulateAndGet(Math.max(0,millis),Math::min);seek();}
    public static long consumeSeekTarget(){return soughtMinimum.getAndSet(Long.MAX_VALUE);}
    public static void startContext(){soughtMinimum.set(Long.MAX_VALUE);}
    public static boolean fenced(){return seeks.get()!=observedSeek||settling>0||state.seeking();}
    public static State poll(){
        State next=ABSENT;
        try{
            if(loaded("flashback")){
                Class<?> flash=Class.forName("com.moulberry.flashback.Flashback");
                Object server=flash.getMethod("getReplayServer").invoke(null);
                if(server!=null){
                    if(!(server instanceof Guarded))throw new IllegalStateException("Flashback seek guard unavailable");
                    Object meta=call(server,"getMetadata");
                    String id=Objects.toString(meta.getClass().getField("replayIdentifier").get(meta),null);
                    boolean seeking=(int)server.getClass().getField("jumpToTick").get(server)>=0
                        ||(boolean)server.getClass().getField("fastForwarding").get(server)
                        ||(boolean)server.getClass().getField("isProcessingSnapshot").get(server);
                    next=new State("Flashback",id,((Number)call(server,"getReplayTick")).longValue()*50L,
                        (boolean)server.getClass().getField("replayPaused").get(server),seeking,null);
                }
            }
            if(!next.present()&&loaded("replaymod")){
                Object module=Class.forName("com.replaymod.replay.ReplayModReplay").getField("instance").get(null);
                Object handler=module==null?null:call(module,"getReplayHandler");
                if(handler!=null){
                    if(!(handler instanceof Guarded))throw new IllegalStateException("ReplayMod seek guard unavailable");
                    Object sender=call(handler,"getReplaySender"),file=call(handler,"getReplayFile");
                    Path path=filePath(file);
                    if(path!=null&&identifiedFile!=file){identifiedFile=file;identifiedHash=CompletableFuture.supplyAsync(()->hash(path),IO);}
                    String identity=path==null?null:identifiedHash.getNow(null);
                    boolean seeking=false;
                    try{seeking=(boolean)call(sender,"isHurrying");}catch(NoSuchMethodException ignored){}
                    next=new State("ReplayMod",identity,((Number)call(sender,"currentTimeStamp")).longValue(),
                        ((Number)call(sender,"getReplaySpeed")).doubleValue()==0,seeking,path==null?"worldbinder.replay.identity_unavailable":null);
                }
            }
        }catch(ReflectiveOperationException|RuntimeException error){
            String provider=next.present()?next.provider():loaded("flashback")?"Flashback":"ReplayMod";
            next=new State(provider,null,0,true,true,"worldbinder.replay.unsupported");
            if(logged.add(provider))WorldBinder.LOGGER.warn("Optional replay adapter is unavailable; capture fenced",error);
        }
        long seek=seeks.get();
        if(seek!=observedSeek){observedSeek=seek;settling=5;}
        if(next.seeking())settling=5;else if(settling>0)settling--;
        state=next;
        if(!next.present()){identifiedFile=null;identifiedHash=null;}
        return next;
    }
    private static boolean loaded(String id){return net.worldbinder.platform.Platform.get().modLoaded(id);}
    private static Object call(Object receiver,String method)throws ReflectiveOperationException{return receiver.getClass().getMethod(method).invoke(receiver);}
    private static Path filePath(Object file)throws ReflectiveOperationException{
        Set<Object> visited=Collections.newSetFromMap(new IdentityHashMap<>());
        for(int depth=0;file!=null&&depth<16&&visited.add(file);depth++){
        Object delegate=null;
        for(Class<?> type=file.getClass();type!=null;type=type.getSuperclass()){
            for(String name:List.of("input","sourceFile","output"))try{
                Field field=type.getDeclaredField(name);field.setAccessible(true);Object value=field.get(file);
                if(value instanceof File actual&&actual.isFile())return actual.toPath().toAbsolutePath().normalize();
            }catch(NoSuchFieldException ignored){}
            try{Field field=type.getDeclaredField("delegate");field.setAccessible(true);delegate=field.get(file);}catch(NoSuchFieldException ignored){}
        }
        file=delegate;
        }
        return null;
    }
    private static String hash(Path path){
        try(var input=Files.newInputStream(path)){var digest=java.security.MessageDigest.getInstance("SHA-256");byte[] bytes=new byte[65536];for(int n;(n=input.read(bytes))>=0;)digest.update(bytes,0,n);return HexFormat.of().formatHex(digest.digest());}
        catch(Exception error){throw new CompletionException(error);}
    }
    public static boolean accepts(WorldScene scene){
        if(scene==null)return true;
        var current=state;
        if(scene.replayIdentity==null)return !current.present();
        return !scene.replayRewound&&current.ready()&&!fenced()&&Objects.equals(scene.replayIdentity,current.identity())
            &&Objects.equals(scene.replayProvider,current.provider())&&current.millis()>=scene.replayHighWater;
    }
    public static String status(WorldScene scene){
        var current=state;
        if(current.error()!=null)return Lang.string(current.error());
        if(scene!=null&&scene.replayIdentity!=null&&!current.present())return Lang.string("worldbinder.replay.closed");
        if(!current.present())return Lang.string("worldbinder.replay.none");
        String key=current.identity()==null?"worldbinder.replay.identifying":fenced()?"worldbinder.replay.seeking":scene!=null&&(scene.replayRewound||current.millis()<scene.replayHighWater)?"worldbinder.replay.rewound":current.paused()?"worldbinder.replay.paused":"worldbinder.replay.playing";
        return Lang.string("worldbinder.replay.status",current.provider(),current.millis()/1000,Lang.string(key));
    }
    public static void bind(WorldScene scene){
        var current=state;if(!current.present())return;
        startContext();
        scene.replayIdentity=current.identity();scene.replayProvider=current.provider();scene.replayHighWater=current.millis();
        scene.storageNotes.add(Lang.stored("worldbinder.replay.limits"));
    }
}
