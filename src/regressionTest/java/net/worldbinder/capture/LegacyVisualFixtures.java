package net.worldbinder.capture;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.worldbinder.WorldBinder;
import net.worldbinder.platform.*;
import net.worldbinder.scene.*;
import net.worldbinder.export.VanillaWorldExporter;
import net.worldbinder.export.common.*;
import net.worldbinder.validation.ExportValidator;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;

/** Re-export an actual received protocol fixture; this prepares inputs and makes no visual-test claim. */
public final class LegacyVisualFixtures {
    private static int receivedChecks;
    private static void check(boolean value,String message){receivedChecks++;if(!value)throw new AssertionError(message);}
    private static net.minecraft.nbt.CompoundTag item(ContainerRecord container,int slot,String id,int count){
        if(container==null)throw new AssertionError("Missing received container");
        var items=container.items.stream().map(AuxiliaryDataWriter::parse).filter(n->n.getInt("Slot").orElse(-1)==slot).toList();
        check(items.size()==1,"Exactly one received item at slot "+slot);var item=items.getFirst();
        check(item.getString("id").orElse("").equals(id)&&item.getInt("count").orElse(0)==count,"Received item ID/count at slot "+slot);return item;
    }
    private static void received(WorldScene scene,Path actualTarget){
        var filled=scene.containers.get(ContainerRecord.blockKey(2,64,2));
        check(filled!=null&&filled.slotCount==27&&filled.complete()&&filled.items.size()==1,"Actual translated chest contains exactly the expected known slots and item");
        check(item(filled,5,"minecraft:diamond",3).toString().contains("Protocol Diamond"),"Original received container item name remains intact");
        var empty=scene.containers.get(ContainerRecord.blockKey(4,64,4));check(empty!=null&&empty.complete()&&empty.items.isEmpty(),"Actual translated empty barrel is confirmed empty");
        item(scene.player.enderChest,3,"minecraft:gold_ingot",5);
        var player=AuxiliaryDataWriter.parse(scene.player.nbt);var inventory=player.getList("Inventory").orElseThrow();
        net.minecraft.nbt.CompoundTag blade=null;for(var value:inventory)if(value instanceof net.minecraft.nbt.CompoundTag entry&&entry.getInt("Slot").orElse(-1)==9)blade=entry;
        check(blade!=null&&blade.getString("id").orElse("").equals("minecraft:diamond_sword")&&blade.getInt("count").orElse(0)==1,"Actual translated own inventory keeps its expected slot, ID and count");
        check(blade.getCompound("components").orElseThrow().getInt("minecraft:damage").orElse(-1)==12&&blade.toString().contains("Protocol Blade"),"Original received item damage and custom name remain intact");
        check(player.getCompound("equipment").orElseThrow().getCompound("chest").orElseThrow().getString("id").orElse("").equals("minecraft:iron_chestplate"),"Actual own equipped chestplate remains intact");
        check(player.getInt("XpLevel").orElse(-1)==12&&scene.player.statsReceived&&scene.player.advancementsReceived,"Actual received own XP/statistics/advancement state survives restart");
        var progress=com.google.gson.JsonParser.parseString(scene.player.advancements.get("minecraft:story/mine_stone")).getAsJsonObject();
        check(progress.getAsJsonObject("criteria").size()>0&&progress.get("done").getAsBoolean(),"Actual completed advancement criteria survive restart");
        var map=scene.maps.get(7);check(map!=null&&map.receivedPixels()==16384,"All actual Map-Art pixels remain known");boolean same=true;for(int i=0;i<16384;i++)same&=map.colors[i]==(byte)(i%128);check(same,"All actual 16,384 Map-Art pixels retain the original server image");
        check(scene.chunkSnapshots.values().stream().anyMatch(s->s.sections.values().stream().anyMatch(v->v.hasBiomes()&&v.hasBlockLight()&&v.hasSkyLight())),"Actual received section biomes and both light layers survive restart");
        check(ExportValidator.validate(scene,actualTarget).filesValid,"Actual untouched 1.20.4 target files contain the expected received data");
    }
    public static void main(String[] args)throws Exception{
        Path root=Path.of(".").toAbsolutePath().normalize().resolve("run-"+System.currentTimeMillis());Files.createDirectories(root);
        Platform.install(new ClientPlatform(){
            public Path gameDirectory(){return root;}public Path configDirectory(){return root.resolve("config");}
            public boolean modLoaded(String id){return false;}public KeyMapping registerKey(KeyMapping key){return key;}
            public void endTick(Consumer<Minecraft> action){}public void hud(BiConsumer<GuiGraphicsExtractor,DeltaTracker> action){}
            public void joined(Runnable action){}public void disconnected(Runnable action){}
        });
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();WorldBinder.init();WorldBinder.config().includeServerResourcePack=false;
        var manifest=com.google.gson.JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();
        Path original=Path.of(manifest.get("sourceArchive").getAsString()),archive=original.resolve("worldbinder/worldbinder_archive.json");
        byte[] before=Files.readAllBytes(archive);var library=new SceneLibrary();var source=library.read(original);
        received(source,Path.of(manifest.get("world").getAsString()));
        if(args.length>1){var other=com.google.gson.JsonParser.parseString(Files.readString(Path.of(args[1]))).getAsJsonObject();received(library.read(Path.of(other.get("sourceArchive").getAsString())),Path.of(other.get("world").getAsString()));}
        if(source.player==null||!source.player.nbt.contains("Protocol Blade")||source.maps.get(7)==null||source.maps.get(7).receivedPixels()!=16384)throw new AssertionError("Use a completed actual received-data protocol scenario");
        var outputs=new LinkedHashMap<String,String>();
        boolean display=args.length>2&&args[2].equals("--night-survival");
        for(String variant:List.of("1.13.2","1.16.5","1.20.4","1.20.4-light-replaced")){
            String version=variant.startsWith("1.20.4")?"1.20.4":variant;
            var scene=WorldBinder.GSON.fromJson(WorldBinder.GSON.toJson(source),WorldScene.class);scene.targetMinecraftVersion=version;scene.exportSettings.includePlayer=true;
            scene.exportSettings.generation=ExportSettings.Generation.NORMAL;
            if(display){var player=AuxiliaryDataWriter.parse(scene.player.nbt);player.putInt("playerGameType",0);scene.player.nbt=player.toString();}
            // Deliberate fixture export choice, using the same strict API as the replacement menu.
            // Nether gold ore did not exist in 1.13; this choice is not a newly invented automatic mapping.
            if(version.equals("1.13.2"))ExportCompatibility.setReplacement(scene,"minecraft:nether_gold_ore","minecraft:netherrack");
            if(variant.endsWith("light-replaced")){if(scene.blocks.stream().noneMatch(b->b.state.equals("minecraft:glowstone")))throw new AssertionError("No actually recorded light emitter");scene.exportSettings.replacements.put("minecraft:glowstone","minecraft:stone");}
            Path folder=root.resolve("received-export-"+variant);net.worldbinder.export.VanillaWorldExporter.export(scene,folder);
            AuxiliaryDataWriter.write(DimensionExportView.prepare(ExportFiltering.apply(scene)),folder);
            library.save(scene,folder.resolve("worldbinder/worldbinder_archive.json"));
            if(display){var level=net.minecraft.nbt.NbtIo.readCompressed(folder.resolve("level.dat"),net.minecraft.nbt.NbtAccounter.unlimitedHeap());level.getCompound("Data").orElseThrow().putLong("DayTime",18000);net.minecraft.nbt.NbtIo.writeCompressed(level,folder.resolve("level.dat"));}
            var report=ExportValidator.validate(scene,folder);if(!report.filesValid)throw new AssertionError(version+": "+report.errors);
            outputs.put(variant,folder.toString());
        }
        if(!Arrays.equals(before,Files.readAllBytes(archive)))throw new AssertionError("Preparing legacy exports changed the original recorded archive");
        Path artifact=Path.of(WorldBinder.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        var evidence=new com.google.gson.JsonObject();evidence.addProperty("productionArtifact",artifact.toString());evidence.addProperty("sha256",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(artifact))));evidence.addProperty("actualCaptureArchive",original.toString());evidence.addProperty("playerUUID",source.player.uuid);evidence.addProperty("receivedContentChecks",receivedChecks);evidence.add("worlds",WorldBinder.GSON.toJsonTree(outputs));evidence.addProperty("scope","Received-data content comparisons, export preparation and read-back only; actual native client visual checks are separate");
        if(display)evidence.addProperty("displaySetup","Disposable derived exports: Survival mode and midnight for visible XP/armor and lighting comparison. Received items, XP, maps, blocks and lighting data are unchanged except the explicit glowstone replacement variant. The source capture archive stays byte-identical.");
        Files.writeString(root.getParent().resolve("current.json"),WorldBinder.GSON.toJson(evidence));System.out.println(WorldBinder.GSON.toJson(evidence));
    }
}
