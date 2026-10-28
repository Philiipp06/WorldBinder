package net.worldbinder.test;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.worldbinder.client.WorldBinderClient;
import net.worldbinder.scene.*;
import java.util.function.Predicate;

/** Received original translator packets, never injected WorldBinder records. */
final class ProtocolContentChecks {
    interface Console {void send(String command)throws Exception;}
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void waitFor(NativeClientContext context,Predicate<Minecraft> condition,String label){
        for(int i=0;i<900;i++){boolean[] ready={false};context.runOnClient(c->ready[0]=condition.test(c));if(ready[0])return;context.waitTicks(1);}throw new AssertionError("Protocol content timeout: "+label);
    }
    private static void open(NativeClientContext context,Console console,BlockPos pos)throws Exception{
        console.send("tp @a "+(pos.getX()+.5)+" "+(pos.getY()+1)+" "+(pos.getZ()+.5));
        waitFor(context,c->c.player.distanceToSqr(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5)<3,"received teleport before interaction");context.waitTicks(8);
        context.runOnClient(c->c.gameMode.useItemOn(c.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false)));
        waitFor(context,c->WorldBinderClient.capture().containerTotalSlots()==27&&WorldBinderClient.capture().containerKnownSlots()==27,"complete received inventory "+pos);
        context.runOnClient(c->c.player.closeContainer());context.waitTicks(3);
    }
    static int run(NativeClientContext context,JsonObject control,Console console)throws Exception{
        checks=0;
        String version=control.get("serverMinecraft").getAsString();if(!version.equals("1.20.4"))throw new AssertionError("Explicit protocol fixture version required");
        for(String command:new String[]{"gamemode creative @a","clear @a","kill @e[type=item_frame]","kill @e[type=armor_stand,tag=wb_equipment]","fill 5 64 3 9 67 8 air",
            "setblock 2 64 2 chest","item replace block 2 64 2 container.5 with diamond{display:{Name:'{\"text\":\"Protocol Diamond\"}'}} 3",
            "setblock 4 64 4 barrel","setblock 5 64 5 ender_chest","setblock 5 64 10 glowstone",
            "item replace entity @a hotbar.0 with filled_map{map:7}",
            "item replace entity @a inventory.0 with diamond_sword{Damage:12,display:{Name:'{\"text\":\"Protocol Blade\"}'}}",
            "item replace entity @a armor.chest with iron_chestplate",
            "item replace entity @a enderchest.3 with gold_ingot 5","experience set @a 12 levels",
            "advancement grant @a only minecraft:story/mine_stone",
            "setblock 7 65 8 stone","summon armor_stand 9 65 4 {Tags:[\"wb_equipment\"],NoGravity:1b}",
            "summon item_frame 7.5 65.5 7.03125 {Pos:[7.5d,65.5d,7.03125d],TileX:7,TileY:65,TileZ:7,Facing:2b,Fixed:1b,Item:{id:\"minecraft:filled_map\",Count:1b,tag:{map:7}}}"})console.send(command);
        waitFor(context,c->c.player.getInventory().getItem(9).getHoverName().getString().contains("Protocol Blade"),"actual converted item components");
        open(context,console,new BlockPos(2,64,2));open(context,console,new BlockPos(4,64,4));open(context,console,new BlockPos(5,64,5));
        console.send("tp @a 1.5 65 1.5");
        waitFor(context,c->c.level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,new BlockPos(5,65,10))>=12,"received original glowstone light");
        context.runOnClient(c->{c.player.getInventory().setSelectedSlot(0);WorldBinderClient.capture().queueChunkForRescan(0,0);c.getConnection().send(new net.minecraft.network.protocol.game.ServerboundClientCommandPacket(net.minecraft.network.protocol.game.ServerboundClientCommandPacket.Action.REQUEST_STATS));});
        waitFor(context,c->{var scene=WorldBinderClient.capture().exportPreview();return scene.maps.containsKey(7)&&scene.maps.get(7).receivedPixels()==16384&&WorldBinderClient.capture().downloadedChunksView().contains(0L)&&!WorldBinderClient.capture().cacheWritePending(0L);},"original map pixels and confirmed chunk");
        waitFor(context,c->{for(var entity:c.level.entitiesForRendering())if(entity instanceof net.minecraft.world.entity.decoration.ItemFrame frame&&frame.getItem().is(net.minecraft.world.item.Items.FILLED_MAP))return true;return false;},"actual translated frame item metadata");
        net.worldbinder.WorldBinder.LOGGER.info("WORLDBINDER_PROTOCOL_FRAME_METADATA_RECEIVED");
        console.send("data merge entity @e[type=item_frame,limit=1] {ItemRotation:3b}");
        waitFor(context,c->{for(var entity:c.level.entitiesForRendering())if(entity instanceof net.minecraft.world.entity.decoration.ItemFrame frame&&frame.getRotation()==3)return true;return false;},"later original frame metadata update");
        waitFor(context,c->{for(var entity:c.level.entitiesForRendering())if(entity instanceof net.minecraft.world.entity.decoration.ArmorStand&&entity.distanceToSqr(9,65,4)<1)return true;return false;},"original armor stand before equipment update");
        console.send("item replace entity @e[type=armor_stand,tag=wb_equipment,limit=1] armor.head with diamond_helmet");
        waitFor(context,c->{for(var entity:c.level.entitiesForRendering())if(entity instanceof net.minecraft.world.entity.decoration.ArmorStand stand&&stand.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(net.minecraft.world.item.Items.DIAMOND_HELMET))return true;return false;},"later original entity equipment packet");
        var pending=new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CompletableFuture<WorldScene>>();
        context.runOnClient(c->pending.set(WorldBinderClient.capture().prepareAreaExport()));waitFor(context,c->pending.get().isDone(),"immutable received-content snapshot");
        var scene=pending.get().join();
        java.nio.file.Files.writeString(net.worldbinder.io.WorldBinderPaths.GAME_DIR.resolve("protocol-received-debug.json"),net.worldbinder.WorldBinder.GSON.toJson(scene));
        var filled=scene.containers.get(ContainerRecord.blockKey(2,64,2));var empty=scene.containers.get(ContainerRecord.blockKey(4,64,4));
        check(filled!=null&&filled.complete()&&filled.items.stream().anyMatch(s->s.contains("Protocol Diamond")&&s.contains("minecraft:diamond")),"Translated full item and known slots survive the capture pipeline");
        check(empty!=null&&empty.complete()&&empty.items.isEmpty(),"A translated confirmed empty barrel remains known empty");
        check(scene.player!=null&&scene.player.nbt.contains("Protocol Blade")&&scene.player.nbt.contains("minecraft:iron_chestplate"),"Actual own translated inventory and equipment captured");
        check(scene.player.enderChest!=null&&scene.player.enderChest.complete()&&scene.player.enderChest.items.stream().anyMatch(s->s.contains("minecraft:gold_ingot")),"Translated received ender chest captured");
        check(scene.player.statsReceived&&scene.player.advancementsReceived&&scene.player.advancements.containsKey("minecraft:story/mine_stone"),"Actual own statistics and completed advancement reply captured");
        check(scene.entities.stream().anyMatch(e->e.type.equals("minecraft:item_frame")&&e.fullNbt!=null&&e.fullNbt.contains("minecraft:filled_map")),"Translated item-frame entity and map reference captured");
        check(scene.entities.stream().anyMatch(e->e.type.equals("minecraft:item_frame")&&e.fullNbt!=null&&e.fullNbt.contains("ItemRotation:3b")),"A later received entity metadata update replaces the initial cached frame state");
        check(scene.entities.stream().anyMatch(e->e.type.equals("minecraft:armor_stand")&&e.fullNbt!=null&&e.fullNbt.contains("minecraft:diamond_helmet")),"A later received equipment packet replaces the initial cached entity equipment");
        var map=scene.maps.get(7);check(map!=null&&map.receivedPixels()==16384,"A real translated full map image was received");
        for(int i=0;i<16384;i++)if(map.colors[i]!=(byte)(i%128))throw new AssertionError("Translated map pixel differs at "+i);
        check(true,"All 16,384 actually received locked Map-Art pixels match the original server image");
        check(scene.chunkSnapshots.values().stream().anyMatch(s->s.sections.values().stream().anyMatch(v->v.hasBiomes()&&v.hasBlockLight()&&v.hasSkyLight())),"Original translated section biomes and both light layers captured");
        context.takeScreenshot("protocol-"+control.get("proxy").getAsString()+"-received-map-and-world");
        context.runOnClient(c->{var active=WorldBinderClient.capture().exportPreview();active.targetMinecraftVersion=version;active.exportSettings.includePlayer=true;});
        return checks;
    }
}
