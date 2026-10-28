package net.worldbinder.test;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.worldbinder.client.WorldBinderClient;
import net.worldbinder.compat.*;
import net.worldbinder.scene.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/** Native End portals and an original-server datapack dimension; no injected capture records. */
final class NativeDimensionChecks {
    private static int checks;
    private static final String CUSTOM="worldbinder:received_custom";
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private static void waitFor(NativeClientContext context,Predicate<Minecraft> condition,String label){
        net.worldbinder.WorldBinder.LOGGER.info("Dimension QA waiting: {}",label);
        for(int i=0;i<1800;i++){boolean[] ready={false};context.runOnClient(c->ready[0]=condition.test(c));if(ready[0])return;context.waitTicks(1);}
        throw new AssertionError("Native dimension timeout: "+label);
    }
    private static boolean in(Minecraft c,String dimension){return c.level!=null&&c.level.dimension().identifier().toString().equals(dimension)&&!WorldBinderClient.capture().routingPending();}
    private static WorldScene snapshot(NativeClientContext context){
        var future=new AtomicReference<CompletableFuture<WorldScene>>();context.runOnClient(c->future.set(WorldBinderClient.capture().prepareAreaExport()));
        waitFor(context,c->future.get().isDone()&&!WorldBinderClient.capture().isSaving(),"immutable dimension snapshot");return future.get().join();
    }
    private static boolean marker(WorldScene root,String dimension,String block){
        var scope=ProjectDimensions.find(root,dimension);return scope!=null&&scope.blocks.stream().anyMatch(b->scope.originX+b.x==1&&scope.originY+b.y==64&&scope.originZ+b.z==1&&b.state.equals(block));
    }
    private static void committed(NativeClientContext context,String dimension){
        waitFor(context,c->in(c,dimension)&&ReceivedChunks.chunk(c.level,0,0)!=null&&WorldBinderClient.capture().downloadedChunksView().contains(0L)&&!WorldBinderClient.capture().cacheWritePending(0L),"received and committed "+dimension);
    }
    private static void chest(NativeClientContext context,ProtocolContentChecks.Console console,String dimension,String item)throws Exception{
        console.send("execute in "+dimension+" run tp @a 3.5 65 3.5");
        waitFor(context,c->c.player.distanceToSqr(3.5,65,3.5)<2,"server teleport before opening dimension chest");context.waitTicks(5);
        context.runOnClient(c->c.gameMode.useItemOn(c.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(new BlockPos(3,64,3)),Direction.UP,new BlockPos(3,64,3),false)));
        waitFor(context,c->WorldBinderClient.capture().containerKnownSlots()==27,"full dimension chest reply");context.runOnClient(c->c.player.closeContainer());
        var scope=ProjectDimensions.find(snapshot(context),dimension);var container=scope.containers.get(ContainerRecord.blockKey(3,64,3));
        check(container!=null&&container.complete()&&container.items.size()==1&&container.items.getFirst().contains(item),"Confirmed chest contents belong to "+dimension);
    }
    static int run(NativeClientContext context,ProtocolContentChecks.Console console,String project)throws Exception{
        checks=0;console.send("gamemode creative @a");console.send("spawnpoint @a 1 65 1");
        console.send("execute in minecraft:the_end run forceload add 0 0");
        console.send("execute in minecraft:the_end run fill -2 63 -2 6 63 6 end_stone");
        console.send("execute in minecraft:the_end run setblock 1 64 1 emerald_block");
        console.send("execute in minecraft:the_end run setblock 3 64 3 chest{Items:[{Slot:0b,id:\"minecraft:diamond\",count:4}]}");
        console.send("setblock 0 64 3 end_portal");console.send("tp @a 0.5 64 3.5");
        waitFor(context,c->in(c,"minecraft:the_end"),"actual End entry portal");
        console.send("execute in minecraft:the_end run tp @a 1.5 65 1.5");
        waitFor(context,c->in(c,"minecraft:the_end")&&c.level.getBlockState(new BlockPos(1,64,1)).is(Blocks.EMERALD_BLOCK),"actual End marker");committed(context,"minecraft:the_end");
        check(snapshot(context).projectId.equals(project),"Native End portal preserves the original backend project");chest(context,console,"minecraft:the_end","minecraft:diamond");
        console.send("execute in minecraft:the_end run setblock 0 64 3 end_portal");console.send("execute in minecraft:the_end run tp @a 0.5 64 3.5");
        waitFor(context,c->{if(ClientCompat.screen(c) instanceof net.minecraft.client.gui.screens.WinScreen screen)screen.onClose();return in(c,"minecraft:overworld");},"actual End return portal and normal credits dismissal");
        console.send("tp @a 1.5 65 1.5");committed(context,"minecraft:overworld");
        var returned=snapshot(context);check(returned.projectId.equals(project)&&marker(returned,"minecraft:the_end","minecraft:emerald_block")&&marker(returned,"minecraft:overworld","minecraft:stone"),"End return keeps independent same-coordinate states");
        console.send("execute in "+CUSTOM+" run forceload add 0 0");
        console.send("execute in "+CUSTOM+" run fill -2 63 -2 6 63 6 stone_bricks");
        console.send("execute in "+CUSTOM+" run setblock 1 64 1 lapis_block");
        console.send("execute in "+CUSTOM+" run setblock 3 64 3 chest{Items:[{Slot:0b,id:\"minecraft:gold_ingot\",count:5}]}");
        console.send("execute in "+CUSTOM+" run tp @a 1.5 65 1.5");
        waitFor(context,c->in(c,CUSTOM)&&c.level.getBlockState(new BlockPos(1,64,1)).is(Blocks.LAPIS_BLOCK),"original server's custom dimension packets");committed(context,CUSTOM);
        check(snapshot(context).projectId.equals(project),"Datapack dimension continues the same project");chest(context,console,CUSTOM,"minecraft:gold_ingot");
        var custom=ProjectDimensions.find(snapshot(context),CUSTOM);check(custom.dimensionType.equals("minecraft:overworld")&&custom.dimensionTypeNbt!=null,"Custom dimension retains its actually received vanilla type definition");
        context.takeScreenshot("native-custom-dimension");
        console.send("execute in minecraft:overworld run tp @a 1.5 65 1.5");
        waitFor(context,c->in(c,"minecraft:overworld")&&c.level.getBlockState(new BlockPos(1,64,1)).is(Blocks.STONE),"custom dimension return");committed(context,"minecraft:overworld");
        var all=snapshot(context);check(ProjectDimensions.all(all).size()==4&&all.projectId.equals(project)&&marker(all,CUSTOM,"minecraft:lapis_block")&&marker(all,"minecraft:the_end","minecraft:emerald_block"),"All four dimension snapshots remain isolated in one project");
        check(ProjectDimensions.find(all,"minecraft:the_end").containers.get(ContainerRecord.blockKey(3,64,3)).items.getFirst().contains("minecraft:diamond"),"Custom chest never overwrites the End chest at identical coordinates");
        context.runOnClient(c->WorldBinderClient.capture().exportPreview().exportSettings.generation=ExportSettings.Generation.NORMAL);
        return checks;
    }
    static int opened(NativeClientContext context){
        checks=0;
        for(String dimension:new String[]{"minecraft:the_end",CUSTOM}){
            context.runOnClient(c->c.getConnection().sendCommand("execute in "+dimension+" run tp @s 1.5 65 1.5"));
            waitFor(context,c->c.level!=null&&c.level.dimension().identifier().toString().equals(dimension)&&c.level.getBlockState(new BlockPos(1,64,1)).is(dimension.equals(CUSTOM)?Blocks.LAPIS_BLOCK:Blocks.EMERALD_BLOCK),"vanilla export opens "+dimension);
            check(true,"The actual exported world opens its received "+dimension+" marker");context.takeScreenshot("export-opened-"+(dimension.equals(CUSTOM)?"custom":"end"));
        }
        return checks;
    }
}
