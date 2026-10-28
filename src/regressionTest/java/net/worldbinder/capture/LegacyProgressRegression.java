package net.worldbinder.capture;

import com.google.gson.*;
import net.worldbinder.WorldBinder;
import net.worldbinder.export.VanillaWorldExporter;
import net.worldbinder.export.common.*;
import net.worldbinder.scene.*;
import net.worldbinder.validation.*;
import java.nio.file.*;
import java.util.*;

/** Verified schema boundary, collisions, immutable source and actual written player files. */
final class LegacyProgressRegression {
    private static int checks;
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    static int run(Path root)throws Exception{
        checks=0;var scene=ProjectRegressionTest.fixture();
        scene.player.stats.put("minecraft:custom",new LinkedHashMap<>(Map.of("minecraft:play_time",1234,"minecraft:total_world_time",2000,"minecraft:jump",12)));
        String before=WorldBinder.GSON.toJson(scene.player);
        for(int version:new int[]{1631,2586,2709}){
            var custom=PlayerProgressConverter.statistics(scene.player,version).getAsJsonObject("minecraft:custom");
            check(custom.get("minecraft:play_one_minute").getAsInt()==1234&&!custom.has("minecraft:play_time"),"Exact tick count uses the verified pre-2710 statistic name: "+version);
            check(custom.get("minecraft:total_world_time").getAsInt()==2000&&custom.get("minecraft:jump").getAsInt()==12,"Unverified and unchanged counters are retained without fabricated mappings: "+version);
        }
        check(WorldBinder.GSON.toJson(scene.player).equals(before),"Target conversion never edits original received player progress");
        var legacy=new CapturedPlayer();legacy.stats.put("minecraft:custom",new LinkedHashMap<>(Map.of("minecraft:play_one_minute",1234)));
        for(int version:new int[]{2710,3700,5023}){
            var custom=PlayerProgressConverter.statistics(legacy,version).getAsJsonObject("minecraft:custom");
            check(custom.get("minecraft:play_time").getAsInt()==1234&&!custom.has("minecraft:play_one_minute"),"The same verified rename works forward at and after schema 2710: "+version);
        }
        legacy.stats.get("minecraft:custom").put("minecraft:play_time",1234);
        check(PlayerProgressConverter.statistics(legacy,2586).getAsJsonObject("minecraft:custom").size()==1,"Equal aliases collapse to one exact count instead of being added");
        legacy.stats.get("minecraft:custom").put("minecraft:play_time",999);String conflicting=WorldBinder.GSON.toJson(legacy);boolean rejected=false;
        try{PlayerProgressConverter.statistics(legacy,2586);}catch(FindingException error){rejected=error.finding().key().equals("worldbinder.finding.statistic_alias_conflict");}
        check(rejected&&WorldBinder.GSON.toJson(legacy).equals(conflicting),"Unequal aliases fail transparently without choosing, summing or modifying received values");
        scene.targetMinecraftVersion="1.16.5";scene.exportSettings.includePlayer=true;
        Path folder=root.resolve("legacy-player-progress");VanillaWorldExporter.export(scene,folder);AuxiliaryDataWriter.write(DimensionExportView.prepare(ExportFiltering.apply(scene)),folder);
        Path stats=folder.resolve("stats/"+scene.player.uuid+".json");var written=JsonParser.parseString(Files.readString(stats)).getAsJsonObject();
        var counters=written.getAsJsonObject("stats").getAsJsonObject("minecraft:custom");
        check(written.get("DataVersion").getAsInt()==2586&&counters.get("minecraft:play_one_minute").getAsInt()==1234&&!counters.has("minecraft:play_time"),"Actual 1.16.5 JSON contains the target identifier and unchanged tick count");
        check(ExportValidator.validate(scene,folder).filesValid,"Target-aware read-back accepts the actual converted statistics");
        check(ExportCompatibility.analyze(scene).stream().anyMatch(i->i.source().equals(net.worldbinder.util.Lang.stored("worldbinder.version.player_progress"))),"Cross-version progress limitations appear in version review and the export report");
        counters.addProperty("minecraft:play_one_minute",1233);Files.writeString(stats,WorldBinder.GSON.toJson(written));
        check(!ExportValidator.validate(scene,folder).filesValid,"Read-back rejects a changed value even when the legacy statistic name is correct");
        check(WorldBinder.GSON.toJson(scene.player).equals(before),"Export and failed read-back leave every original counter intact");
        return checks;
    }
}
