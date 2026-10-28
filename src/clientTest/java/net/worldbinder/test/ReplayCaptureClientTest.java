package net.worldbinder.test;


import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.worldbinder.WorldBinder;
import net.worldbinder.client.WorldBinderClient;
import net.worldbinder.compat.*;
import net.worldbinder.config.WorldBinderConfig;
import net.worldbinder.io.WorldBinderPaths;
import net.worldbinder.replay.ReplayPlayback;
import net.worldbinder.scene.*;
import net.worldbinder.ui.*;
import net.worldbinder.util.Lang;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;

/** Real optional upstream recorders -> actual saved replay -> playback capture. No synthetic replay API mocks. */
final class ReplayCaptureClientTest {
    private static int checks;
    private static final BlockPos MARK=new BlockPos(1,64,1);
    private static String provider;
    private interface Read<T>{T get(Minecraft client)throws Exception;}
    private static <T>T client(NativeClientContext context,Read<T> read){var value=new java.util.concurrent.atomic.AtomicReference<T>();context.runOnClient(c->{try{value.set(read.get(c));}catch(Exception error){throw new AssertionError(error);}});return value.get();}
    private static String screenDescription(Minecraft client){
        var screen=ClientCompat.screen(client);
        if(screen==null)return "none";
        return screen.getClass().getSimpleName()+" title="+screen.getTitle().getString()+" buttons="+screen.children().stream().filter(child->child instanceof Button).map(child->((Button)child).getMessage().getString()).toList();
    }
    private static void waitFor(NativeClientContext context,Read<Boolean> condition,String label){WorldBinder.LOGGER.info("Replay QA waiting: {}",label);for(int i=0;i<2400;i++){if(client(context,condition)){WorldBinder.LOGGER.info("Replay QA completed: {}",label);return;}if(i%200==0)WorldBinder.LOGGER.info("Replay QA {}: {}",label,client(context,c->ReplayPlayback.state()+" screen="+screenDescription(c)+" block="+(c.level==null?null:c.level.getBlockState(MARK))+" server="+c.getSingleplayerServer()));context.waitTicks(1);}throw new AssertionError("Replay QA timeout: "+label+" state="+ReplayPlayback.state());}
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private static Object invoke(Object receiver,String name,Object... args)throws Exception{
        Class<?> type=receiver instanceof Class<?> c?c:receiver.getClass();
        for(var method:type.getMethods())if(method.getName().equals(name)&&method.getParameterCount()==args.length)try{return method.invoke(receiver instanceof Class<?>?null:receiver,args);}catch(IllegalArgumentException ignored){}catch(InvocationTargetException failure){throw new Exception(type.getName()+"."+name,failure.getCause());}
        throw new NoSuchMethodException(type.getName()+"."+name);
    }
    private static Class<?> flash()throws Exception{return Class.forName("com.moulberry.flashback.Flashback");}
    private static Object replay()throws Exception{return Class.forName("com.replaymod.replay.ReplayModReplay").getField("instance").get(null);}
    private static Object recorder()throws Exception{
        if(provider.equals("flashback"))return flash().getField("RECORDER").get(null);
        Object recording=Class.forName("com.replaymod.recording.ReplayModRecording").getField("instance").get(null);
        return invoke(invoke(recording,"getConnectionEventHandler"),"getPacketListener");
    }
    private static long recordingMillis()throws Exception{
        Object recorder=recorder();if(recorder==null)return -1;
        if(!provider.equals("flashback"))return ((Number)invoke(recorder,"getCurrentDuration")).longValue();
        var field=recorder.getClass().getDeclaredField("writtenTicks");field.setAccessible(true);return ((Number)field.get(recorder)).longValue()*50;
    }
    private static void recordingSettings(boolean enabled)throws Exception{
        if(provider.equals("flashback"))return;
        Object core=Class.forName("com.replaymod.core.ReplayMod").getField("instance").get(null),settings=invoke(core,"getSettingsRegistry");
        Class<?> keys=Class.forName("com.replaymod.recording.Setting");
        for(String key:List.of("RECORD_SINGLEPLAYER","RECORD_SERVER","AUTO_START_RECORDING"))invoke(settings,"set",keys.getField(key).get(null),enabled);
        for(String key:List.of("AUTO_POST_PROCESS","RENAME_DIALOG"))invoke(settings,"set",keys.getField(key).get(null),false);
    }
    private static Path folder()throws Exception{
        if(provider.equals("flashback"))return (Path)invoke(flash(),"getReplayFolder");
        Object core=Class.forName("com.replaymod.core.ReplayMod").getField("instance").get(null);return (Path)invoke(core.getClass().getField("folders").get(core),"getReplayFolder");
    }
    private static Set<Path> recordings()throws Exception{
        Path folder=folder();if(!Files.isDirectory(folder))return Set.of();try(var files=Files.walk(folder,3)){return files.filter(Files::isRegularFile).filter(p->p.toString().endsWith(provider.equals("flashback")?".zip":".mcpr")).collect(java.util.stream.Collectors.toSet());}
    }
    private static boolean valid(Path path){try(var zip=new java.util.zip.ZipFile(path.toFile())){return zip.size()>2;}catch(Exception ignored){return false;}}
    private static boolean button(Minecraft client,String label){var screen=ClientCompat.screen(client);if(screen==null)return false;for(var child:screen.children())if(child instanceof Button b&&b.active&&b.visible&&b.getMessage().getString().equals(label)){b.onPress(new KeyEvent(257,0,0));return true;}return false;}
    private static void open(Path file)throws Exception{if(provider.equals("flashback"))invoke(flash(),"openReplayWorld",file);else invoke(replay(),"startReplay",file.toFile());}
    private static void seek(long millis)throws Exception{
        if(provider.equals("flashback")){Object server=invoke(flash(),"getReplayServer");server.getClass().getField("replayPaused").set(server,true);invoke(server,"goToReplayTick",(int)(millis/50));}
        else{Object handler=invoke(replay(),"getReplayHandler");invoke(invoke(handler,"getReplaySender"),"setReplaySpeed",0D);invoke(handler,"doJump",(int)millis,true);}
    }
    private static void playing(boolean value)throws Exception{
        if(provider.equals("flashback")){Object server=invoke(flash(),"getReplayServer");server.getClass().getField("replayPaused").set(server,!value);}
        else invoke(invoke(invoke(replay(),"getReplayHandler"),"getReplaySender"),"setReplaySpeed",value?1D:0D);
    }
    private static WorldScene snapshot(NativeClientContext context){var pending=client(context,c->WorldBinderClient.capture().prepareAreaExport());waitFor(context,c->pending.isDone()&&!WorldBinderClient.capture().isSaving(),"committed replay snapshot");return pending.join();}
    private static boolean has(WorldScene scene,String id){return scene.blocks.stream().anyMatch(b->scene.originX+b.x==1&&scene.originY+b.y==64&&scene.originZ+b.z==1&&b.state.equals(id));}
    private static Path finish(NativeClientContext context,String project,Set<Path> before){
        client(context,c->{WorldBinderClient.capture().requestFinishCapture();return null;});
        waitFor(context,c->{if(ClientCompat.screen(c) instanceof WorldBinderFinishScreen)button(c,Lang.string("worldbinder.finish.save_now"));if(ClientCompat.screen(c) instanceof WorldBinderExportScreen)button(c,Lang.string("worldbinder.export.review.export"));if(net.worldbinder.storage.StorageFlow.progress().stage()==net.worldbinder.storage.StorageStage.FAILED)throw new AssertionError(net.worldbinder.storage.StorageFlow.progress().detail());return !WorldBinderClient.capture().isCapturing()&&!WorldBinderClient.capture().isSaving();},"save actual replay project");
        return client(context,c->WorldBinderClient.scenes().scenes().stream().filter(p->!before.contains(p)).filter(p->{try{return WorldBinderClient.scenes().read(p).projectId.equals(project)&&!WorldBinderClient.scenes().isRecovery(p);}catch(Exception error){return false;}}).findFirst().orElseThrow());
    }
    static void run(NativeClientContext context,String requestedProvider,String phase){
        provider=requestedProvider;checks=0;
        try{
            client(context,c->{c.options.pauseOnLostFocus=false;var config=new WorldBinderConfig();config.targetMinecraftVersion=net.minecraft.SharedConstants.getCurrentVersion().name();config.captureMinY=63;config.captureMaxY=65;config.roamingRadiusChunks=1;config.confirmExistingWorld=false;config.appendTimestampToArchiveName=false;config.crashRecovery=true;config.recoveryAutosaveSeconds=1;config.autoDeleteRecovery=false;config.includeServerResourcePack=false;WorldBinder.replaceConfig(config);recordingSettings(!phase.equals("resume"));return null;});
            Path fixture=WorldBinderPaths.GAME_DIR.resolve("replay-fixture.json");Path recording,archive;long stoneTime,goldTime;
            if(phase.equals("record")){
                Set<Path> old=recordings();
                try(var world=context.openFixture()){
                    waitFor(context,c->c.level!=null&&c.player!=null,"recording source world");
                    client(context,c->{if(provider.equals("flashback")&&recorder()!=null)invoke(flash(),"cancelRecordingReplay");ExpansionScenario.command(c,"gamemode creative @a","fill -16 63 -16 16 63 16 stone","setblock 1 64 1 stone","setblock 2 64 1 diamond_block","tp @a 1.5 65 1.5");return null;});
                    waitFor(context,c->c.level.getBlockState(MARK).is(Blocks.STONE)&&c.level.getBlockState(new BlockPos(2,64,1)).is(Blocks.DIAMOND_BLOCK)&&ReceivedChunks.chunk(c.level,0,0)!=null,"received source stone and diamond before starting the recorder");
                    client(context,c->{if(provider.equals("flashback"))invoke(flash(),"startRecordingReplay");return null;});
                    waitFor(context,c->recorder()!=null,"actual upstream recorder starts");context.waitTicks(30);stoneTime=client(context,c->recordingMillis());context.waitTicks(40);
                    client(context,c->{ExpansionScenario.command(c,"setblock 1 64 1 gold_block");return null;});waitFor(context,c->c.level.getBlockState(MARK).is(Blocks.GOLD_BLOCK),"source gold update received");context.waitTicks(20);goldTime=client(context,c->recordingMillis());context.waitTicks(40);
                    client(context,c->{ExpansionScenario.command(c,"setblock 2 64 1 air");return null;});context.waitTicks(30);
                    if(provider.equals("flashback")){
                        client(context,c->{invoke(flash(),"finishRecordingReplay");return null;});
                        waitFor(context,c->{var screen=ClientCompat.screen(c);if(screen!=null&&screen.getClass().getName().endsWith("SaveReplayScreen"))for(var child:screen.children())if(child instanceof Button b&&b.active&&b.getMessage().getString().toLowerCase(Locale.ROOT).contains("save")&&!b.getMessage().getString().toLowerCase(Locale.ROOT).contains("cancel")){b.onPress(new KeyEvent(257,0,0));break;}return recordings().stream().anyMatch(p->!old.contains(p)&&valid(p));},"Flashback writes a real replay ZIP");
                    }
                }
                waitFor(context,c->recordings().stream().anyMatch(p->!old.contains(p)&&valid(p)),"upstream replay archive finalized after source disconnect");recording=recordings().stream().filter(p->!old.contains(p)&&valid(p)).findFirst().orElseThrow();check(stoneTime>=0&&goldTime>stoneTime,"Recorder timestamps describe two different received world states");
                long first=stoneTime,later=goldTime;client(context,c->{recordingSettings(false);open(recording);return null;});
                waitFor(context,c->ReplayPlayback.state().ready()&&!ReplayPlayback.fenced()&&c.level!=null&&c.player!=null,"real playback detected with seek guard and file identity");
                client(context,c->{seek(later);return null;});waitFor(context,c->ReplayPlayback.state().ready()&&!ReplayPlayback.fenced()&&ReceivedChunks.chunk(c.level,0,0)!=null&&c.level.getBlockState(MARK).is(Blocks.GOLD_BLOCK),"actual playback reaches later gold state");
                client(context,c->{c.getConnection().sendCommand("worldbinder");return null;});waitFor(context,c->ClientCompat.screen(c) instanceof WorldBinderScreen,"client command remains responsive while the actual replay is paused");check(true,"Paused replay opens the normal Control Center through the client command");client(context,c->{ClientCompat.setScreen(c,null);return null;});
                client(context,c->{WorldBinder.config().autoCaptureOnJoin=true;return null;});context.waitTicks(30);check(!client(context,c->WorldBinderClient.capture().isCapturing()),"Auto capture excludes actual replay playback");
                String name="replay_"+provider+"_"+UUID.randomUUID().toString().substring(0,8);client(context,c->{WorldBinder.config().autoCaptureOnJoin=false;WorldBinderClient.capture().startRoamingCapture(name);return null;});
                waitFor(context,c->WorldBinderClient.capture().downloadedChunksView().contains(0L)&&!WorldBinderClient.capture().cacheWritePending(0L),"capture receives complete replay chunk");var future=snapshot(context);check(has(future,"minecraft:gold_block")&&future.player==null&&future.replayIdentity!=null,"Actual playback capture retains later blocks and never captures the spectator camera as your own player");String protectedId=future.projectId;
                client(context,c->{playing(true);return null;});waitFor(context,c->c.level.getBlockState(new BlockPos(2,64,1)).isAir(),"normal playback delivers the removed block");context.waitTicks(20);client(context,c->{playing(false);return null;});
                waitFor(context,c->WorldBinderClient.capture().downloadedChunksView().contains(0L)&&!WorldBinderClient.capture().cacheWritePending(0L),"replayed block removal is committed");var changed=snapshot(context);check(changed.blocks.stream().noneMatch(b->changed.originX+b.x==2&&changed.originY+b.y==64&&changed.originZ+b.z==1),"Normal playback block updates remove captured blocks through the received-data pipeline");
                client(context,c->{seek(first);return null;});waitFor(context,c->!ReplayPlayback.fenced()&&c.level!=null&&ReceivedChunks.chunk(c.level,0,0)!=null&&c.level.getBlockState(MARK).is(Blocks.STONE)&&c.level.getBlockState(new BlockPos(2,64,1)).is(Blocks.DIAMOND_BLOCK),"actual playback rewinds to the recorded stone and diamond state");context.waitTicks(10);
                check(client(context,c->WorldBinderClient.capture().isCapturing()&&WorldBinderClient.capture().exportPreview().replayRewound),"Backward seek and internal replay resets retain a protected active project");check(has(snapshot(context),"minecraft:gold_block"),"An older actual playback state cannot overwrite a newer committed recording");
                client(context,c->{WorldBinderClient.capture().branchReplay();return null;});waitFor(context,c->WorldBinderClient.capture().isCapturing()&&!WorldBinderClient.capture().isSaving()&&!WorldBinderClient.capture().exportPreview().projectId.equals(protectedId)&&WorldBinderClient.capture().downloadedChunksView().contains(0L),"fresh earlier-time project branch");
                var branch=snapshot(context);check(has(branch,"minecraft:stone")&&!has(branch,"minecraft:gold_block"),"A fresh earlier-time branch uses only received earlier data and inherits no future chunks");check(branch.blocks.stream().anyMatch(b->branch.originX+b.x==2&&branch.originY+b.y==64&&branch.originZ+b.z==1&&b.state.equals("minecraft:diamond_block")),"Future confirmed air never leaks backwards into a fresh earlier branch");
                context.takeScreenshot("replay-"+provider+"-capture");Set<Path> before=client(context,c->new HashSet<>(WorldBinderClient.scenes().scenes()));archive=finish(context,branch.projectId,before);
                var json=new com.google.gson.JsonObject();json.addProperty("recording",recording.toString());json.addProperty("archive",archive.toString());json.addProperty("stoneTime",stoneTime);json.addProperty("goldTime",goldTime);json.addProperty("identity",branch.replayIdentity);Files.writeString(fixture,WorldBinder.GSON.toJson(json));
                client(context,c->{if(provider.equals("flashback"))c.disconnectWithSavingScreen();else invoke(invoke(replay(),"getReplayHandler"),"endReplay");return null;});waitFor(context,c->c.level==null,"replay closes");
            }else{
                var json=com.google.gson.JsonParser.parseString(Files.readString(fixture)).getAsJsonObject();recording=Path.of(json.get("recording").getAsString());Path sourceArchive=Path.of(json.get("archive").getAsString());long later=json.get("goldTime").getAsLong();byte[] previous=Files.readAllBytes(sourceArchive.resolve("worldbinder/worldbinder_archive.json"));
                client(context,c->{open(recording);return null;});waitFor(context,c->ReplayPlayback.state().ready()&&!ReplayPlayback.fenced()&&c.level!=null,"same replay opens after full process restart without original source world");client(context,c->{seek(later);return null;});waitFor(context,c->!ReplayPlayback.fenced()&&ReceivedChunks.chunk(c.level,0,0)!=null&&c.level.getBlockState(MARK).is(Blocks.GOLD_BLOCK),"later replay world after restart");
                client(context,c->{WorldBinderClient.capture().continueRecovery(sourceArchive);return null;});waitFor(context,c->WorldBinderClient.capture().isCapturing(),"saved replay project resumes");check(client(context,c->Objects.equals(WorldBinderClient.capture().exportPreview().replayIdentity,json.get("identity").getAsString())),"Replay file identity survives full restart and archive resume");
                client(context,c->{WorldBinderClient.capture().queueChunkForRescan(0,0);return null;});waitFor(context,c->WorldBinderClient.capture().downloadedChunksView().contains(0L)&&!WorldBinderClient.capture().cacheWritePending(0L),"explicit resumed replay update");var updated=snapshot(context);check(has(updated,"minecraft:gold_block"),"Resumed playback can deliberately update its earlier saved block state");
                client(context,c->{if(provider.equals("flashback"))c.disconnectWithSavingScreen();else invoke(invoke(replay(),"getReplayHandler"),"endReplay");return null;});waitFor(context,c->c.level==null&&!ReplayPlayback.state().present(),"playback closes while capturing");context.waitTicks(10);check(client(context,c->WorldBinderClient.capture().isCapturing()),"Closing playback retains the project instead of auto-exporting a normal server disconnect");
                Set<Path> before=client(context,c->new HashSet<>(WorldBinderClient.scenes().scenes()));archive=finish(context,updated.projectId,before);check(Arrays.equals(previous,Files.readAllBytes(Path.of(json.get("archive").getAsString()).resolve("worldbinder/worldbinder_archive.json"))),"Resume and offline re-export leave the earlier complete revision unchanged");
            }
            Path exported=archive;try(var world=context.openWorld(exported)){
                waitFor(context,c->c.level!=null&&ReceivedChunks.chunk(c.level,0,0)!=null,"actual vanilla export opens");check(client(context,c->c.level.getBlockState(MARK).is(phase.equals("resume")?Blocks.GOLD_BLOCK:Blocks.STONE)),"The actual Minecraft export has the expected reconstructed block");context.takeScreenshot("replay-"+provider+"-"+phase+"-export-opened");
            }
            Files.writeString(WorldBinderPaths.GAME_DIR.resolve("replay-"+phase+"-RESULT.txt"),"PASS actual "+provider+" "+phase+": "+checks+" checks; recording="+recording+"; export="+archive+"\n");WorldBinder.LOGGER.info("WORLDBINDER_REPLAY_PASS {} {} {} checks",provider,phase,checks);
        }catch(Exception error){throw new AssertionError(error);}
    }
}
