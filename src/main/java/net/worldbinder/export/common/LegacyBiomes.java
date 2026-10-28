package net.worldbinder.export.common;

import java.util.Map;

/** Stable vanilla legacy IDs. New/uncatalogued biomes are reported as conversions to plains. */
public final class LegacyBiomes {
    private LegacyBiomes() {}
    private static final Map<String,String> RENAMES=Map.ofEntries(
        Map.entry("mountains","windswept_hills"),Map.entry("snowy_tundra","snowy_plains"),
        Map.entry("stone_shore","stony_shore"),Map.entry("jungle_edge","sparse_jungle"),
        Map.entry("giant_tree_taiga","old_growth_pine_taiga"),Map.entry("giant_spruce_taiga","old_growth_spruce_taiga"),
        Map.entry("tall_birch_forest","old_growth_birch_forest"),Map.entry("wooded_badlands_plateau","wooded_badlands"),
        Map.entry("nether","nether_wastes"));
    public static String modernName(String name){
        if(name.contains(":")&&!name.startsWith("minecraft:"))return name;
        String raw=name.startsWith("minecraft:")?name.substring(10):name;
        return "minecraft:"+RENAMES.getOrDefault(raw,raw);
    }
    private static String key(String name){String modern=modernName(name);return modern.startsWith("minecraft:")?modern.substring(10):modern;}
    private static final Map<String, Integer> IDS = Map.ofEntries(
            Map.entry("ocean",0), Map.entry("plains",1), Map.entry("desert",2), Map.entry("mountains",3), Map.entry("windswept_hills",3),
            Map.entry("forest",4), Map.entry("taiga",5), Map.entry("swamp",6), Map.entry("river",7), Map.entry("nether_wastes",8),
            Map.entry("the_end",9), Map.entry("frozen_ocean",10), Map.entry("frozen_river",11), Map.entry("snowy_tundra",12), Map.entry("snowy_plains",12),
            Map.entry("mushroom_fields",14), Map.entry("beach",16), Map.entry("wooded_hills",18), Map.entry("jungle",21),
            Map.entry("sparse_jungle",23), Map.entry("deep_ocean",24), Map.entry("stony_shore",25), Map.entry("snowy_beach",26),
            Map.entry("birch_forest",27), Map.entry("dark_forest",29), Map.entry("snowy_taiga",30), Map.entry("old_growth_pine_taiga",32),
            Map.entry("savanna",35), Map.entry("savanna_plateau",36), Map.entry("badlands",37), Map.entry("wooded_badlands",38),
            Map.entry("small_end_islands",40), Map.entry("end_midlands",41), Map.entry("end_highlands",42), Map.entry("end_barrens",43),
            Map.entry("warm_ocean",44), Map.entry("lukewarm_ocean",45), Map.entry("cold_ocean",46), Map.entry("deep_lukewarm_ocean",48),
            Map.entry("deep_cold_ocean",49), Map.entry("deep_frozen_ocean",50), Map.entry("the_void",127), Map.entry("sunflower_plains",129),
            Map.entry("flower_forest",132), Map.entry("ice_spikes",140), Map.entry("old_growth_birch_forest",155), Map.entry("old_growth_spruce_taiga",160),
            Map.entry("windswept_savanna",163), Map.entry("eroded_badlands",165), Map.entry("bamboo_jungle",168),
            Map.entry("soul_sand_valley",170), Map.entry("crimson_forest",171), Map.entry("warped_forest",172), Map.entry("basalt_deltas",173),
            Map.entry("dripstone_caves",174), Map.entry("lush_caves",175));
    public static int numeric(String name) { return IDS.getOrDefault(key(name), 1); }
    public static int numeric(String name, int version) {
        int id = numeric(name);
        if (version < 2724 && id >= 174) return 1;
        if (version < 2566 && id >= 170) return 8;
        if (version < 1952 && id >= 168) return 21;
        return id;
    }
    public static boolean known(String name) { return IDS.containsKey(key(name)); }
}
