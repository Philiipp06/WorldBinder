package net.worldbinder.test;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import java.nio.file.*;
import java.util.function.Consumer;

/** Wall-clock driver: optional replay servers own their tick loop and cannot use Fabric's server phaser. */
final class NativeClientContext {
    private net.minecraft.client.gui.screens.Screen acceptedWarning;
    void runOnClient(Consumer<Minecraft> action){
        // Minecraft disconnect() drops pending tasks, including a submit() future queued just
        // before a native transfer. Keep completion outside that queue; an action can start once.
        var started=new java.util.concurrent.atomic.AtomicBoolean();
        var completion=new java.util.concurrent.CompletableFuture<Void>();
        Runnable guarded=()->{if(!started.compareAndSet(false,true))return;try{action.accept(Minecraft.getInstance());completion.complete(null);}catch(Throwable error){completion.completeExceptionally(error);}};
        try{
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(60);
            Minecraft.getInstance().execute(guarded);
            while(true){
                try{completion.get(1,java.util.concurrent.TimeUnit.SECONDS);break;}
                catch(java.util.concurrent.TimeoutException timeout){
                    if(System.nanoTime()>=deadline)throw timeout;
                    if(!started.get()){
                        net.worldbinder.WorldBinder.LOGGER.info("Native QA action has not started; resubmitting its single-execution guard after a possible disconnect queue clear");
                        Minecraft.getInstance().execute(guarded);
                    }
                }
            }
        }
        catch(Exception error){
            if(error instanceof InterruptedException)Thread.currentThread().interrupt();
            try{var dump=new StringBuilder("monotonicNanos="+System.nanoTime()+"\n");try{var field=Class.forName("net.minecraft.client.FramerateLimiter").getDeclaredField("lastFrameTime");field.setAccessible(true);dump.append("lastFrameTime=").append(field.getLong(null)).append('\n');}catch(ReflectiveOperationException ignored){}Thread.getAllStackTraces().forEach((thread,stack)->{dump.append(thread.getName()).append(' ').append(thread.getState()).append('\n');for(var frame:stack)dump.append("  ").append(frame).append('\n');});Files.writeString(net.worldbinder.io.WorldBinderPaths.GAME_DIR.resolve("native-timeout-thread-dump.txt"),dump);}catch(Exception ignored){}
            throw new AssertionError(error);
        }
    }
    void waitTicks(int ticks){
        try{Thread.sleep(ticks*50L);}catch(InterruptedException error){Thread.currentThread().interrupt();throw new AssertionError(error);}
        runOnClient(c->{var screen=net.worldbinder.compat.ClientCompat.screen(c);if(screen==null||screen==acceptedWarning)return;
            if(screen.getClass().getSimpleName().equals("FileFixerAbortedScreen"))throw new AssertionError("Native Minecraft world-structure upgrade failed; inspect the FileFixerUpper error in the client log");
            String accept=null;
            if(screen instanceof BackupConfirmScreen)accept=Component.translatable("selectWorld.backupJoinSkipButton").getString();
            else if(screen instanceof net.minecraft.client.gui.screens.ConfirmScreen&&(screen.getTitle().getString().equals(Component.translatable("selectWorld.warning.experimental.title").getString())||screen.getTitle().getString().equals(Component.translatable("selectWorld.warning.deprecated.title").getString())||screen.getTitle().getString().equals(Component.translatable("upgradeWorld.done").getString())))accept=Component.translatable("gui.yes").getString();
            if(accept!=null)for(var child:screen.children())if(child instanceof Button b&&b.active&&b.getMessage().getString().equals(accept)){acceptedWarning=screen;b.onPress(new KeyEvent(257,0,0));break;}
        });
    }
    AutoCloseable openFixture()throws Exception{
        Path source;
        String fixture="export-"+net.minecraft.SharedConstants.getCurrentVersion().name();
        try(var runs=Files.list(Path.of("../regression-runtime"))){source=runs.filter(p->{try{return Files.isDirectory(p.resolve(fixture))&&Files.readString(p.resolve("RESULT.txt")).startsWith("PASS");}catch(java.io.IOException missing){return false;}}).max(java.util.Comparator.comparingLong(p->p.toFile().lastModified())).orElseThrow(()->new java.io.IOException("Run regressionTest to prepare the exact native fixture "+fixture)).resolve(fixture);}
        Path target=net.worldbinder.io.WorldBinderPaths.MINECRAFT_SAVES.resolve("replay_source_"+java.util.UUID.randomUUID().toString().substring(0,8));
        try(var files=Files.walk(source)){for(var file:files.toList()){Path dest=target.resolve(source.relativize(file));if(Files.isDirectory(file))Files.createDirectories(dest);else if(!file.getFileName().toString().equals("session.lock"))Files.copy(file,dest);}}
        return openWorld(target);
    }
    AutoCloseable openWorld(Path path){
        runOnClient(c->c.createWorldOpenFlows().openWorld(path.getFileName().toString(),()->{throw new AssertionError("Native fixture did not open: "+path);}));
        return ()->{runOnClient(c->c.disconnectWithSavingScreen());for(int i=0;i<900;i++){boolean[] closed={false};runOnClient(c->closed[0]=c.level==null&&c.getSingleplayerServer()==null);if(closed[0])return;waitTicks(1);}throw new AssertionError("World failed to close");};
    }
    void takeScreenshot(String name){
        waitTicks(3);runOnClient(c->ExpansionUiChecks.shot(c,name));
    }
}
