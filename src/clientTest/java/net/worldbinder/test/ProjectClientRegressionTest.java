package net.worldbinder.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.item.Items;
import net.worldbinder.WorldBinder;
import net.worldbinder.client.WorldBinderClient;
import net.worldbinder.compat.ClientCompat;
import net.worldbinder.config.WorldBinderConfig;
import net.worldbinder.scene.*;
import net.worldbinder.ui.*;
import net.worldbinder.io.WorldBinderPaths;
import net.worldbinder.validation.ExportValidator;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/** Actual client/server packets, GUI opt-in, save/restart/resume and loading the exported world. */
final class ProjectClientRegressionTest {
    static void run(ClientGameTestContext context) {
        String name="project_1_7_"+UUID.randomUUID().toString().substring(0,8);
        AtomicReference<WorldScene> saved=new AtomicReference<>(); AtomicInteger mapId=new AtomicInteger(-1);
        var world=context.worldBuilder().create(); var originalSave=world.getWorldSave();
        try {
            context.waitFor(c->c.player!=null&&c.level!=null);
            var server=world.getServer();
            server.runCommand("gamemode creative @a");server.runCommand("fill -16 63 -16 16 63 16 minecraft:stone");
            server.runCommand("fill -16 64 -16 16 90 16 minecraft:air");
            server.runCommand("tp @a 1.5 65 1.5");server.runCommand("clear @a");
            server.runCommand("setblock 2 64 2 chest{Items:[{Slot:5b,id:\"minecraft:diamond_pickaxe\",count:1,components:{\"minecraft:damage\":12,\"minecraft:custom_name\":\"Received tool\",\"minecraft:enchantments\":{\"minecraft:unbreaking\":2}}}]}");
            server.runCommand("setblock 4 64 4 barrel");server.runCommand("setblock 6 64 6 purple_shulker_box{Items:[{Slot:2b,id:\"minecraft:emerald\",count:4}]}");
            server.runCommand("setblock 8 64 8 furnace{Items:[{Slot:0b,id:\"minecraft:iron_ore\",count:2}]}");
            server.runCommand("setblock 2 64 8 chest[facing=north,type=left]");
            server.runCommand("setblock 3 64 8 chest[facing=north,type=right]{Items:[{Slot:1b,id:\"minecraft:gold_ingot\",count:7}]}");
            server.runCommand("setblock 12 64 12 ender_chest");
            server.runCommand("item replace entity @a enderchest.3 with minecraft:gold_ingot 5");
            server.runCommand("experience set @a 12 levels");
            server.runCommand("summon chest_minecart 10.5 65 10.5 {NoGravity:1b,Items:[{Slot:4b,id:\"minecraft:iron_ingot\",count:6}]}");
            context.waitTicks(15);
            MapClientRegressionTest.run(context);
            context.runOnClient(c->{
                WorldBinder.replaceConfig(new WorldBinderConfig());
                var cfg=WorldBinder.config();cfg.appendTimestampToArchiveName=false;cfg.crashRecovery=false;cfg.autoDeleteRecovery=false;
                cfg.targetMinecraftVersion=net.minecraft.SharedConstants.getCurrentVersion().name();
                cfg.captureMinY=63;cfg.captureMaxY=66;cfg.roamingRadiusChunks=1;cfg.includeServerResourcePack=false;
                WorldBinderClient.capture().startRoamingCapture(name);
            });
            context.waitFor(c->WorldBinderClient.capture().downloadedChunksView().contains(0L));
            open(context,2,64,2); close(context);
            open(context,4,64,4); close(context);
            open(context,6,64,6); close(context);
            open(context,8,64,8); close(context);
            open(context,2,64,8); close(context);
            open(context,12,64,12); close(context);
            context.runOnClient(c->c.player.setPos(10.5,65,11.5));context.waitTicks(4);
            context.runOnClient(c->{
                var entity=c.level.entitiesForRendering().iterator();
                while(entity.hasNext()) { var cart=entity.next(); if(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(cart.getType()).toString().equals("minecraft:chest_minecart")) {
                    c.gameMode.interact(c.player,cart,new EntityHitResult(cart),InteractionHand.MAIN_HAND); break;
                }}
            });
            context.waitFor(c->ClientCompat.screen(c) instanceof AbstractContainerScreen<?>);context.waitTicks(4);close(context);
            context.runOnClient(c->{
                WorldScene scene=WorldBinderClient.capture().exportPreview();
                check(scene.containers.get(ContainerRecord.blockKey(2,64,2)).items.getFirst().contains("Received tool"),"Full received item components");
                check(scene.containers.get(ContainerRecord.blockKey(4,64,4)).items.isEmpty(),"Verified empty barrel");
                check(scene.containers.get(ContainerRecord.blockKey(6,64,6)).items.size()==1,"Shulker inventory");
                check(scene.containers.get(ContainerRecord.blockKey(8,64,8)).items.size()==1,"Furnace inventory");
                check(scene.containers.get(ContainerRecord.blockKey(3,64,8)).items.getFirst().contains("7"),"Double chest RIGHT half uses first menu slots");
                check(scene.containers.get(ContainerRecord.blockKey(2,64,8)).items.isEmpty(),"Double chest LEFT half is verified empty");
                check(scene.player.enderChest.items.getFirst().contains("Slot:3b"),"Own ender chest uses actual received slot");
                check(scene.containers.values().stream().anyMatch(r->r.type.equals("minecraft:chest_minecart")&&r.items.getFirst().contains("Slot:4b")),"Received entity container slots");
            });
            server.runCommand("give @a minecraft:map");context.waitTicks(8);
            context.runOnClient(c->{
                for(int i=0;i<9;i++)if(c.player.getInventory().getItem(i).is(Items.MAP))c.player.getInventory().setSelectedSlot(i);
                c.gameMode.useItem(c.player,InteractionHand.MAIN_HAND);
            });
            context.waitFor(c->{ for(int i=0;i<9;i++) if(c.player.getInventory().getItem(i).get(DataComponents.MAP_ID)!=null) return true; return false; });
            context.runOnClient(c->{
                for(int i=0;i<9;i++) if(c.player.getInventory().getItem(i).get(DataComponents.MAP_ID)!=null) {
                    c.player.getInventory().setSelectedSlot(i);mapId.set(c.player.getInventory().getItem(i).get(DataComponents.MAP_ID).id());break;
                }
            });
            context.waitFor(c->WorldBinderClient.capture().exportPreview().maps.containsKey(mapId.get())
                    &&WorldBinderClient.capture().exportPreview().maps.get(mapId.get()).receivedPixels()>0);
            context.takeScreenshot("project-1_7-map-received");
            server.runCommand("setblock 7 65 8 minecraft:stone");
            server.runCommand("summon item_frame 7.5 65.5 7.03125 {Pos:[7.5d,65.5d,7.03125d],block_pos:[I;7,65,7],Fixed:1b,Facing:2b,Item:{id:\"minecraft:filled_map\",count:1,components:{\"minecraft:map_id\":"+mapId.get()+"}}}");context.waitTicks(6);
            // The frame's support was placed after the initial scan: deliberately commit that update as a user would.
            context.runOnClient(c->WorldBinderClient.capture().queueChunkForRescan(0,0));
            context.waitFor(c->WorldBinderClient.capture().downloadedChunksView().contains(0L));
            context.runOnClient(c->WorldBinderClient.capture().abortQueueAndSaveNow());
            context.waitFor(c->ClientCompat.screen(c) instanceof WorldBinderExportScreen);
            context.clickScreenButton("Include my player state: Off");
            context.runOnClient(c->{
                EditBox seed=(EditBox)ClientCompat.screen(c).children().stream().filter(EditBox.class::isInstance).findFirst().orElseThrow();seed.setValue("12345");
            });
            context.takeScreenshot("project-1_7-export-review");context.clickScreenButton("Save and export");
            context.waitFor(c->!WorldBinderClient.capture().isCapturing()&&!WorldBinderClient.capture().isSaving());
            context.runOnClient(c->{try {
                var folder=WorldBinderPaths.MINECRAFT_SAVES.resolve(name);WorldScene scene=WorldBinderClient.scenes().read(folder);saved.set(scene);
                check(scene.exportSettings.includePlayer&&scene.exportSettings.seed.equals("12345"),"Export choices survive archive");
                check(scene.player.statsReceived&&scene.player.advancementsReceived,"Actually received statistics/advancements retained");
                check(scene.entities.stream().anyMatch(e->e.type.equals("minecraft:item_frame")&&e.fullNbt.contains("minecraft:map_id")),"Framed map keeps its map ID");
                check(ExportValidator.validate(scene,folder).filesValid,"Live packet capture reads back");
            }catch(Exception e){throw new AssertionError(e);}});
        } finally { world.close(); }

        // A new integrated-server session loads the same persistent world path before resume.
        try(var reopened=originalSave.open()) {
            context.waitFor(c->c.player!=null&&c.level!=null);
            context.runOnClient(c->WorldBinderClient.capture().continueRecovery(WorldBinderPaths.MINECRAFT_SAVES.resolve(name)));
            context.waitFor(c->WorldBinderClient.capture().isCapturing());
            reopened.getServer().runCommand("setblock 1 63 1 air");
            context.waitFor(c->c.level.getBlockState(new BlockPos(1,63,1)).isAir());
            context.runOnClient(c->WorldBinderClient.capture().queueChunkForRescan(0,0));
            context.waitFor(c->WorldBinderClient.capture().downloadedChunksView().contains(0L));
            context.runOnClient(c->{
                check(WorldBinderClient.capture().exportPreview().maps.get(mapId.get()).receivedPixels()>0,"Map survives disconnect and resume");
                WorldBinderClient.capture().abortQueueAndSaveNow();
            });
            context.waitFor(c->ClientCompat.screen(c) instanceof WorldBinderExportScreen);context.clickScreenButton("Save and export");
            context.waitFor(c->!WorldBinderClient.capture().isCapturing()&&!WorldBinderClient.capture().isSaving());
            context.runOnClient(c->{try {
                var paths=WorldBinderClient.scenes().scenes();
                var latest=paths.stream().filter(p->p.getFileName().toString().startsWith(name)&&!p.getFileName().toString().equals(name)).findFirst().orElseThrow();
                WorldScene scene=WorldBinderClient.scenes().read(latest);
                check(scene.revision==1&&scene.blocks.stream().noneMatch(b->scene.originX+b.x==1&&scene.originY+b.y==63&&scene.originZ+b.z==1),"Removed block retained after resume/update/save");
                check(scene.containers.size()>=6&&scene.player.enderChest!=null,"Known container and ender data survive resume");
                check(ExportValidator.validate(scene,latest).filesValid,"Updated revision read-back passes");
            }catch(Exception e){throw new AssertionError(e);}});
        }
        // Open the actual exported save in the running target game, with its archived player and map files.
        try(var exported=new net.fabricmc.fabric.impl.client.gametest.world.TestWorldSaveImpl(context,WorldBinderPaths.MINECRAFT_SAVES.resolve(name)).open()) {
            context.waitFor(c->c.player!=null&&c.level!=null);
            context.waitFor(c->c.player.getMainHandItem().get(DataComponents.MAP_ID)!=null&&c.level.getMapData(c.player.getMainHandItem().get(DataComponents.MAP_ID))!=null);
            context.waitFor(c->{
                for(var entity:c.level.entitiesForRendering())if(entity instanceof net.minecraft.world.entity.decoration.ItemFrame frame
                        &&frame.getItem().get(DataComponents.MAP_ID)!=null&&frame.getItem().get(DataComponents.MAP_ID).id()==mapId.get())return true;
                return false;
            });
            context.runOnClient(c->{
                check(c.player.experienceLevel==12,"Archived own XP loads in target Minecraft");
                check(c.level.getBlockState(new BlockPos(1,63,1)).is(net.minecraft.world.level.block.Blocks.STONE),"Exported blocks load in target Minecraft");
                boolean frameFound=false;
                for(var entity:c.level.entitiesForRendering())if(entity instanceof net.minecraft.world.entity.decoration.ItemFrame frame
                        &&frame.getItem().get(DataComponents.MAP_ID)!=null&&frame.getItem().get(DataComponents.MAP_ID).id()==mapId.get())frameFound=true;
                check(frameFound,"Exported frame retains the actual received map ID in the target game");
            });
            open(context,2,64,2);
            context.runOnClient(c->{var menu=((AbstractContainerScreen<?>)ClientCompat.screen(c)).getMenu();check(menu.slots.get(5).getItem().is(Items.DIAMOND_PICKAXE)&&menu.slots.get(5).getItem().getDamageValue()==12,"Exported container item/components load in target Minecraft");});
            close(context);open(context,12,64,12);
            context.runOnClient(c->{var menu=((AbstractContainerScreen<?>)ClientCompat.screen(c)).getMenu();check(menu.slots.get(3).getItem().is(Items.GOLD_INGOT)&&menu.slots.get(3).getItem().getCount()==5,"Exported ender chest loads for archived player");});
            close(context);context.takeScreenshot("project-1_7-export-opened");
        }
        try {
            com.google.gson.JsonObject restart=new com.google.gson.JsonObject();restart.addProperty("name",name);
            restart.addProperty("world",originalSave.getSaveDirectory().toAbsolutePath().toString());restart.addProperty("map",mapId.get());
            Files.writeString(WorldBinderPaths.GAME_DIR.resolve("restart-fixture.json"),WorldBinder.GSON.toJson(restart));
        }catch(Exception exception){throw new AssertionError("Persist restart test fixture",exception);}
        WorldBinder.LOGGER.info("WORLDBINDER_PROJECT_CLIENT_PASS: actual menus, items, double chest, biome/light sections, maps, player, restart/resume and removed blocks");
    }
    private static void open(ClientGameTestContext context,int x,int y,int z) {
        context.runOnClient(c->c.player.setPos(x+0.5,y+1,z+0.5));
        context.waitTicks(4);
        context.runOnClient(c->{
            c.gameMode.useItemOn(c.player,InteractionHand.MAIN_HAND,new BlockHitResult(new Vec3(x+.5,y+.5,z+.5),Direction.UP,new BlockPos(x,y,z),false));
        });
        context.waitFor(c->ClientCompat.screen(c) instanceof AbstractContainerScreen<?>);context.waitTicks(4);
    }
    private static void close(ClientGameTestContext context) {
        context.runOnClient(c->c.player.closeContainer());context.waitTicks(2);
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
