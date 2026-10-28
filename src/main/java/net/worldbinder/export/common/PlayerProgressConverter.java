package net.worldbinder.export.common;

import com.google.gson.JsonObject;
import net.worldbinder.WorldBinder;
import net.worldbinder.scene.CapturedPlayer;
import net.worldbinder.validation.*;

/** Only verified statistic renames; unknown counters and criteria remain received data. */
public final class PlayerProgressConverter {
    private PlayerProgressConverter(){}
    public static JsonObject statistics(CapturedPlayer player,int version){
        JsonObject result=WorldBinder.GSON.toJsonTree(player.stats).getAsJsonObject();
        JsonObject custom=result.getAsJsonObject("minecraft:custom");if(custom==null)return result;
        // Mojang DataFixers registers the play_one_minute -> play_time StatsRenameFix at
        // schema 2710. Both names count ticks; changing the name must never scale or add values.
        String source=version<2710?"minecraft:play_time":"minecraft:play_one_minute";
        String target=version<2710?"minecraft:play_one_minute":"minecraft:play_time";
        var value=custom.remove(source);if(value==null)return result;
        if(custom.has(target)&&!custom.get(target).equals(value))throw new FindingException(Finding.of("worldbinder.finding.statistic_alias_conflict",source,target));
        custom.add(target,value);return result;
    }
}
