package net.worldbinder.capture;

import net.minecraft.nbt.*;
import net.worldbinder.scene.*;
import net.worldbinder.export.*;
import net.worldbinder.export.common.*;
import net.worldbinder.validation.*;
import java.nio.file.*;
import java.util.*;

final class LegacyMappingRegression {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static int run(Path root)throws Exception{
        checks=0;var scene=ProjectRegressionTest.fixture();scene.targetMinecraftVersion="1.13.2";
        ExportCompatibility.setReplacement(scene,"minecraft:stone","minecraft:grass");
        check(scene.exportSettings.replacements.get("minecraft:stone").equals("minecraft:grass"),"A real legacy-only block ID can be chosen from the exact target registry");
        String prior=scene.exportSettings.replacements.get("minecraft:stone");
        for(String bad:List.of("minecraft:stnoe","minecraft:stone[facing=east]","minecraft:sign[rotation=16]","minecraft:sign[rotation=1,rotation=2]","minecraft:copper_block")){
            boolean rejected=false;try{ExportCompatibility.setReplacement(scene,"minecraft:stone",bad);}catch(IllegalArgumentException expected){rejected=true;}
            check(rejected&&scene.exportSettings.replacements.get("minecraft:stone").equals(prior),"Invalid target replacement is rejected without changing the previous selection: "+bad);
        }
        scene.exportSettings.replacements.clear();
        var locked=new CapturedMap();locked.id=7;locked.nbt="{locked:1b}";locked.patch(0,0,1,1,new byte[]{42});scene.maps.put(7,locked);
        String originalMap=net.worldbinder.WorldBinder.GSON.toJson(locked);
        check(ExportCompatibility.analyze(scene).stream().anyMatch(i->i.source().equals(net.worldbinder.util.Lang.stored("worldbinder.legacy.locked_map",7))),"A received locked map warns that 1.13 can update its pixels when used");
        locked.nbt="{locked:0b}";
        check(ExportCompatibility.analyze(scene).stream().noneMatch(i->i.source().equals(net.worldbinder.util.Lang.stored("worldbinder.legacy.locked_map",7))),"Already unlocked maps do not claim loss of an unavailable map lock");
        locked.nbt="{locked:1b}";scene.targetMinecraftVersion="1.16.5";
        check(ExportCompatibility.analyze(scene).stream().noneMatch(i->i.source().equals(net.worldbinder.util.Lang.stored("worldbinder.legacy.locked_map",7))),"Targets with native map locking do not receive the 1.13 map warning");
        check(net.worldbinder.WorldBinder.GSON.toJson(locked).equals(originalMap),"Compatibility review never edits received map pixels or lock metadata");
        scene.targetMinecraftVersion="1.13.2";scene.maps.clear();
        scene.exportSettings.replacements.put("minecraft:stone","minecraft:glowstone");
        check(ExportCompatibility.analyze(scene).stream().anyMatch(i->i.source().equals(net.worldbinder.util.Lang.stored("worldbinder.legacy.relight"))),"A changed 1.13 chunk explicitly warns about native relighting limitations");
        scene.targetMinecraftVersion="1.16.5";
        check(ExportCompatibility.analyze(scene).stream().noneMatch(i->i.source().equals(net.worldbinder.util.Lang.stored("worldbinder.legacy.relight"))),"The observed 1.13 relighting limitation is not generalized to newer clients");
        scene.targetMinecraftVersion="1.13.2";scene.exportSettings.replacements.clear();
        check(ExportCompatibility.state(scene,"minecraft:short_grass").equals("minecraft:grass"),"Mojang's renamed short grass maps to the actual 1.13 registry identifier");
        check(ExportCompatibility.state(scene,"minecraft:water_cauldron[level=2]").equals("minecraft:cauldron[level=2]"),"Legacy cauldrons keep the received water level instead of becoming empty");
        check(!TargetBlockRegistry.available(1519)&&TargetBlockRegistry.available(1631),"Registry validation never treats a nearby unverified patch as an exact target");
        check(ExportCompatibility.biome("minecraft:giant_tree_taiga",3700).equals("minecraft:old_growth_pine_taiga")&&LegacyBiomes.numeric("minecraft:giant_tree_taiga")==32,"Archived historical biome names are recognized in both numeric legacy and modern target formats");
        String sign="{id:\"minecraft:sign\",front_text:{messages:[{text:\"WorldBinder QA\"},{text:\"Front line 2\"},\"\",\"\"],color:\"blue\",has_glowing_text:1b},back_text:{messages:[\"Back received\",\"\",\"\",\"\"],color:\"black\"},is_waxed:1b}";
        var nbt=AuxiliaryDataWriter.parse(sign);var legacy=BlockEntityDataConverter.convert(nbt,1631);
        check(legacy.getString("Text1").orElseThrow().contains("WorldBinder QA")&&legacy.getString("Color").orElseThrow().equals("blue")&&!legacy.contains("front_text"),"Known sign text/color NBT converts to the older structural schema");
        check(nbt.contains("front_text")&&BlockEntityDataConverter.losses(sign,1631).containsAll(List.of("worldbinder.legacy.sign_back","worldbinder.legacy.sign_wax")),"The original sign NBT remains intact and unavailable backside/wax data is listed as a loss");
        var spawner=BlockEntityDataConverter.convert(AuxiliaryDataWriter.parse("{id:\"minecraft:mob_spawner\",SpawnData:{entity:{id:\"minecraft:pig\"}},SpawnPotentials:[{data:{entity:{id:\"minecraft:cow\"}},weight:3}]}"),2586);
        var modded=AuxiliaryDataWriter.parse("{id:\"example:sign\",front_text:{messages:[\"custom\"]},SpawnData:{entity:{id:\"example:mob\"}}}");
        check(BlockEntityDataConverter.convert(modded,1631).equals(modded),"Unknown mod block entities never inherit guessed vanilla NBT conversions");
        check(spawner.getCompound("SpawnData").orElseThrow().getString("id").orElseThrow().equals("minecraft:pig")&&spawner.getList("SpawnPotentials").orElseThrow().getCompound(0).orElseThrow().getInt("Weight").orElseThrow()==3,"Known spawner entity/weight nesting converts without inventing spawn data");
        scene.blocks.add(new BlockRecord(4,64,4,"minecraft:oak_sign[rotation=0,waterlogged=false]",true,sign));scene.blocks.add(new BlockRecord(5,64,4,"minecraft:short_grass",false,null));scene.blocks.add(new BlockRecord(6,64,4,"minecraft:water_cauldron[level=2]",false,null));
        for(String version:List.of("1.13.2","1.16.5","1.20.4")){
            scene.targetMinecraftVersion=version;Path world=root.resolve("legacy-mappings-"+version);VanillaWorldExporter.export(scene,world);AuxiliaryDataWriter.write(scene,world);
            check(ExportValidator.validate(scene,world).filesValid,"Exact target palettes and converted block entity payloads survive full world read-back: "+version);
            if(version.equals("1.13.2")){
                var chunk=RegionReadback.read(world.resolve("region"),new ArrayList<>()).get(0L).getCompound("Level").orElseThrow();
                check(chunk.getString("Status").orElseThrow().equals("fullchunk"),"1.13 uses its actual final generation stage so Nether terrain cannot overwrite received chunks");
                Path region=world.resolve("region/r.0.0.mca");byte[] valid=Files.readAllBytes(region);
                ProjectRegressionTest.rewriteRegion(world,false,rootTag->rootTag.getCompound("Level").orElseThrow().putString("Status","full"));
                check(!ExportValidator.validate(scene,world).filesValid,"Read-back rejects a modern full status in a 1.13 chunk even when all palette bytes remain readable");Files.write(region,valid);
            }
        }
        var orphan=ProjectRegressionTest.fixture();var entity=new EntityRecord();entity.type="minecraft:pig";entity.uuid=UUID.randomUUID().toString();entity.x=31.5;entity.y=65;entity.z=31.5;entity.fullNbt="{id:\"minecraft:pig\",NoGravity:1b}";orphan.entities.add(entity);
        Path modern=root.resolve("entities-with-unknown-terrain");VanillaWorldExporter.export(orphan,modern);AuxiliaryDataWriter.write(orphan,modern);var report=ExportValidator.validate(orphan,modern);
        check(report.filesValid&&report.expectedChunks==1&&report.entities==1&&report.warnings.stream().anyMatch(f->f.key().equals("worldbinder.export.orphan_terrain")),"Received entities outside confirmed terrain are retained in modern entity files without fabricating terrain chunks");
        orphan.targetMinecraftVersion="1.13.2";var view=ExportFiltering.apply(orphan);
        check(view.entities.isEmpty()&&orphan.entities.size()==1&&view.exportNotices.stream().anyMatch(f->f.key().equals("worldbinder.export.orphan_legacy")),"Legacy entity embedding reports unavailable terrain and leaves the original received entity in the archive");
        var config=new net.worldbinder.config.WorldBinderConfig();check(!config.autoCaptureOnJoin&&config.networkProfiles.isEmpty(),"Automatic capture remains explicitly opt-in for existing and new default configurations");
        return checks;
    }
}
