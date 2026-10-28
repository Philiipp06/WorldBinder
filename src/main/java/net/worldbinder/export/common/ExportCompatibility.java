package net.worldbinder.export.common;

import net.worldbinder.scene.*;
import net.worldbinder.version.TargetMinecraftVersion;
import java.util.*;

/** One policy drives both the preview and the actual exporter. Uncatalogued downgrade behavior stays unknown. */
public final class ExportCompatibility {
    public record Impact(String kind, String source, String replacement, int count) {}

    /** Validate completely before changing the project's existing choice. */
    public static void setReplacement(WorldScene scene, String source, String input) {
        int version = TargetMinecraftVersion.resolve(scene.targetMinecraftVersion).effectiveDataVersion();
        String parsed = TargetBlockRegistry.available(version)?TargetBlockRegistry.replacement(input,version):net.worldbinder.util.BlockStateStrings.toCommandString(net.worldbinder.util.BlockStateStrings.parseStrict(input));
        if (!id(parsed).startsWith("minecraft:") || !automatic(parsed, version).equals(parsed))
            throw new net.worldbinder.validation.FindingException(net.worldbinder.validation.Finding.of("worldbinder.error.replacement_is_not_supported_by_the_known_target_policy"));
        scene.exportSettings.replacements.put(source, parsed);
    }
    private ExportCompatibility() {}
    public static String id(String state) { int at = state.indexOf('['); return at < 0 ? state : state.substring(0, at); }

    public static String state(WorldScene scene, String raw) {
        return state(scene, raw, TargetMinecraftVersion.resolve(scene.targetMinecraftVersion).effectiveDataVersion());
    }

    /** The target is resolved once for a bulk operation; choices still belong to this export's settings. */
    public static String state(WorldScene scene, String raw, int version) {
        if (raw == null || raw.isBlank()) return "minecraft:air";
        String selected = scene.exportSettings.replacements.getOrDefault(raw, scene.exportSettings.replacements.get(id(raw)));
        if (selected != null && !selected.isBlank()) return TargetBlockRegistry.constrain(automatic(selected, version),version);
        return TargetBlockRegistry.constrain(automatic(raw, version),version);
    }

    public static String automatic(String state, int version) {
        String name = id(state);
        if (!name.startsWith("minecraft:")) return state;
        if(version<1952&&name.equals("minecraft:oak_sign"))return state.replace("minecraft:oak_sign","minecraft:sign");
        if(version<1952&&name.equals("minecraft:oak_wall_sign"))return state.replace("minecraft:oak_wall_sign","minecraft:wall_sign");
        if(version<3698&&name.equals("minecraft:short_grass"))return "minecraft:grass";
        if(version<2724&&name.equals("minecraft:water_cauldron"))return state.replace("minecraft:water_cauldron","minecraft:cauldron");
        if (version < 4189 && (name.contains("pale_oak") || name.contains("resin") || name.contains("creaking_heart"))) return "minecraft:oak_planks";
        if (version < 3953 && (name.contains("trial_spawner") || name.contains("vault") || name.contains("crafter")
                || name.contains("heavy_core") || name.contains("copper_bulb") || name.contains("copper_grate")
                || name.contains("copper_door") || name.contains("copper_trapdoor") || name.contains("chiseled_copper")
                || name.contains("polished_tuff") || name.contains("tuff_brick") || name.contains("chiseled_tuff"))) return "minecraft:stone";
        if (version < 3463 && (name.contains("cherry") || name.contains("bamboo_") && !name.equals("minecraft:bamboo_sapling") || name.contains("pink_petals")
                || name.contains("decorated_pot") || name.contains("suspicious_") || name.contains("chiseled_bookshelf")
                || name.contains("hanging_sign") || name.contains("pitcher") || name.contains("torchflower"))) return "minecraft:oak_planks";
        if (version < 3105 && (name.contains("mangrove") || name.contains("mud") || name.contains("sculk") || name.contains("froglight"))) return "minecraft:dirt";
        if (version < 2724 && (name.contains("deepslate") || name.contains("tuff") || name.contains("calcite") || name.contains("amethyst")
                || name.contains("copper") || name.contains("dripstone") || name.contains("azalea") || name.contains("moss") || name.contains("candle")
                || name.contains("glow_lichen") || name.contains("powder_snow") || name.contains("rooted_dirt") || name.contains("hanging_roots"))) return "minecraft:cobblestone";
        if (version < 2724 && name.endsWith("_cauldron")) return "minecraft:cauldron[level=0]";
        if (version < 2724 && name.equals("minecraft:dirt_path")) return state.replace("dirt_path", "grass_path");
        if (version < 2566 && (name.contains("crimson") || name.contains("warped") || name.contains("basalt") || name.contains("blackstone")
                || name.contains("soul_soil") || name.contains("netherite") || name.contains("ancient_debris") || name.contains("shroomlight")
                || name.contains("soul_torch") || name.contains("soul_lantern") || name.contains("soul_fire") || name.contains("respawn_anchor")
                || name.contains("lodestone") || name.contains("crying_obsidian"))) return "minecraft:netherrack";
        if (version < 2225 && (name.contains("bee_") || name.contains("beehive") || name.contains("honey"))) return "minecraft:oak_planks";
        if (version < 1952 && (name.contains("barrel") || name.contains("blast_furnace") || name.contains("smoker") || name.contains("lectern")
                || name.contains("composter") || name.contains("bell") || name.contains("campfire") || name.contains("lantern")
                || name.contains("scaffolding") || name.contains("bamboo") || name.contains("grindstone") || name.contains("stonecutter")
                || name.contains("smithing_table") || name.contains("fletching_table") || name.contains("cartography_table")
                || name.contains("sweet_berry") || name.contains("cornflower") || name.contains("lily_of_the_valley") || name.contains("wither_rose"))) return "minecraft:oak_planks";
        if (version < 3105 && name.endsWith("_leaves")) state = removeProperty(state, "waterlogged");
        if (version < 2566 && name.endsWith("_wall")) state = state.replace("=low", "=true").replace("=tall", "=true").replace("=none", "=false");
        return state;
    }

    public static String biome(String biome, int version) {
        if(version>=2860)biome=LegacyBiomes.modernName(biome);
        if (version < 4189 && biome.equals("minecraft:pale_garden")) return "minecraft:dark_forest";
        if (version < 3463 && biome.equals("minecraft:cherry_grove")) return "minecraft:forest";
        if (version < 3105 && biome.equals("minecraft:mangrove_swamp")) return "minecraft:swamp";
        if (version < 3105 && biome.equals("minecraft:deep_dark")) return version >= 2860 ? "minecraft:dripstone_caves" : "minecraft:plains";
        return biome;
    }

    public static String entityType(String id, int version) {
        if(id==null||id.isBlank())return "minecraft:armor_stand";
        if(version<3337&&(id.endsWith(":block_display")||id.endsWith(":item_display")||id.endsWith(":text_display")||id.endsWith(":interaction")))return "minecraft:armor_stand";
        if(version<3463&&(id.equals("minecraft:camel")||id.equals("minecraft:sniffer")))return "minecraft:pig";
        if(version<3837&&id.equals("minecraft:armadillo"))return "minecraft:pig";
        if(version<3953&&(id.equals("minecraft:bogged")||id.equals("minecraft:breeze")||id.equals("minecraft:wind_charge")||id.equals("minecraft:ominous_item_spawner")))return version<2724?"minecraft:armor_stand":"minecraft:marker";
        if(version<2724&&id.equals("minecraft:glow_item_frame"))return "minecraft:item_frame";
        if(version<2724&&id.equals("minecraft:glow_squid"))return "minecraft:squid";
        if(version<2225&&id.equals("minecraft:bee"))return "minecraft:pig";
        return id;
    }

    private static String removeProperty(String state, String property) {
        int start = state.indexOf('[');
        if (start < 0) return state;
        var kept = Arrays.stream(state.substring(start + 1, state.length() - 1).split(",")).filter(p -> !p.startsWith(property + "=")).toList();
        return id(state) + (kept.isEmpty() ? "" : "[" + String.join(",", kept) + "]");
    }

    public static List<Impact> analyze(WorldScene scene) {
        if(!scene.dimensions.isEmpty()){
            var impacts=new LinkedHashMap<String,Impact>();
            for(var scope:ProjectDimensions.all(scene)){
                var view=ProjectDimensions.dimensionOnly(scope);view.exportSettings=scene.exportSettings;view.targetMinecraftVersion=scene.targetMinecraftVersion;
                for(var impact:analyze(view)){
                    String key=impact.kind+"\n"+impact.source+"\n"+impact.replacement;
                    var previous=impacts.get(key);impacts.put(key,new Impact(impact.kind,impact.source,impact.replacement,impact.count+(previous==null?0:previous.count)));
                }
            }
            return impacts.values().stream().sorted(Comparator.comparingInt(Impact::count).reversed()).toList();
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, Impact> details = new LinkedHashMap<>();
        int version = TargetMinecraftVersion.resolve(scene.targetMinecraftVersion).effectiveDataVersion();
        if(version<1952){
            for(var map:scene.maps.values())if(map.receivedPixels()>0&&map.nbt!=null&&AuxiliaryDataWriter.parse(map.nbt).getBoolean("locked").orElse(false)){
                String key="legacy_locked_map:"+map.id;counts.put(key,1);details.put(key,new Impact("unknown",net.worldbinder.util.Lang.stored("worldbinder.legacy.locked_map",map.id),net.worldbinder.util.Lang.stored("worldbinder.legacy.locked_map_unsupported"),0));
            }
            Set<Long> recordedChunks=new HashSet<>();
            for(var snapshot:scene.chunkSnapshots.values())recordedChunks.add(ProjectData.chunkKey(snapshot.chunkX,snapshot.chunkZ));
            for(var block:scene.blocks)recordedChunks.add(ProjectData.chunkKey((scene.originX+block.x)>>4,(scene.originZ+block.z)>>4));
            int changedLight=(int)ExportLightPolicy.invalidatedChunks(scene).stream().filter(recordedChunks::contains).count();
            if(changedLight>0){counts.put("legacy_relight",changedLight);details.put("legacy_relight",new Impact("unknown",net.worldbinder.util.Lang.stored("worldbinder.legacy.relight"),net.worldbinder.util.Lang.stored("worldbinder.legacy.relight_unverified"),0));}
        }
        if(version>=2566&&scene.exportSettings.generation!=ExportSettings.Generation.NORMAL&&("minecraft:the_nether".equals(scene.dimension)||"minecraft:the_end".equals(scene.dimension))){
            counts.put("generation",1);details.put("generation",new Impact("generation",scene.dimension,net.worldbinder.util.Lang.stored("worldbinder.dimensions.experimental"),0));
        }
        for(var notice:scene.exportNotices){String key="scope:"+notice.key();counts.put(key,1);details.put(key,new Impact("scope",net.worldbinder.util.Lang.stored(notice.key(),notice.arguments().toArray()),net.worldbinder.util.Lang.stored("worldbinder.generated.inspect_report"),0));}
        Map<String,String> states = new HashMap<>();
        for (BlockRecord block : scene.blocks) {
            String changed = states.computeIfAbsent(block.state, raw -> state(scene, raw, version));
            if(!id(changed).startsWith("minecraft:")){String key="custom:"+id(changed);counts.merge(key,1,Integer::sum);details.put(key,new Impact("custom",id(changed),net.worldbinder.util.Lang.stored("worldbinder.version.custom_content_unknown"),0));}
            if(Boolean.FALSE.equals(TargetBlockRegistry.readable(changed,version))){String key="palette:"+block.state;counts.merge(key,1,Integer::sum);details.put(key,new Impact("palette",block.state,net.worldbinder.util.Lang.stored("worldbinder.legacy.palette_unknown"),0));}
            String kind = !id(changed).equals(id(block.state)) ? "replacement" : "properties";
            if (version < 2860 && (scene.originY + block.y < 0 || scene.originY + block.y > 255)) { changed = net.worldbinder.util.Lang.stored("worldbinder.version.outside_height"); kind = "height"; }
            if (!Objects.equals(changed, block.state)) {
                String key = kind + ":" + block.state + ":" + changed;
                counts.merge(key, 1, Integer::sum); details.put(key, new Impact(kind, block.state, changed, 0));
                if (block.blockEntityNbt != null && !BlockEntityDataConverter.compatible(id(block.state),id(changed),version)) {
                    String data = "block entity:" + id(block.state);
                    counts.merge(data, 1, Integer::sum); details.put(data, new Impact("data", id(block.state), net.worldbinder.util.Lang.stored("worldbinder.version.block_entity_removed"), 0));
                }
            }
        }
        int sourceVersion = TargetMinecraftVersion.resolve(scene.minecraftVersion).effectiveDataVersion();
        for(BlockRecord block:scene.blocks)for(String loss:BlockEntityDataConverter.losses(block.blockEntityNbt,version)){
            String key="nbt:"+loss;counts.merge(key,1,Integer::sum);details.put(key,new Impact("data",net.worldbinder.util.Lang.stored(loss),net.worldbinder.util.Lang.stored("worldbinder.version.block_entity_removed"),0));
        }
        for(EntityRecord entity:scene.entities){
            if(!entity.type.startsWith("minecraft:")){String key="custom:"+entity.type;counts.merge(key,1,Integer::sum);details.put(key,new Impact("custom",entity.type,net.worldbinder.util.Lang.stored("worldbinder.version.custom_content_unknown"),0));}
            String changed=entityType(entity.type,version);if(changed.equals(entity.type))continue;
            String key="entity:"+entity.type+":"+changed;counts.merge(key,1,Integer::sum);details.put(key,new Impact("entity",entity.type,changed,0));
        }
        if(version<3837){
            for(BlockRecord block:scene.blocks) itemImpacts(block.blockEntityNbt,counts,details);
            for(EntityRecord entity:scene.entities) itemImpacts(entity.fullNbt,counts,details);
            for(ContainerRecord container:scene.containers.values())for(String snbt:container.items)itemImpacts(snbt,counts,details);
            if(scene.exportSettings.includePlayer&&scene.player!=null){itemImpacts(scene.player.nbt,counts,details);if(scene.player.enderChest!=null)for(String snbt:scene.player.enderChest.items)itemImpacts(snbt,counts,details);}
        }
        for (var snapshot : scene.chunkSnapshots.values()) for (var section : snapshot.sections.values()) {
            if (!section.hasBiomes()) continue;
            for (String original : section.biomes) {
                String changed = version < 2860 ? net.worldbinder.util.Lang.stored(LegacyBiomes.known(original)?"worldbinder.version.legacy_biome":"worldbinder.version.legacy_biome_unknown",LegacyBiomes.numeric(original, version)) : biome(original, version);
                if (version >= 2860 && changed.equals(original)) continue;
                String key = "biome:" + original + ":" + changed;
                counts.merge(key, 1, Integer::sum); details.put(key, new Impact("biome",original,changed,0));
            }
        }
        if (version != sourceVersion) {
            if(scene.exportSettings.includePlayer&&scene.player!=null&&(scene.player.statsReceived||scene.player.advancementsReceived)){
                counts.put("player_progress",1);
                details.put("player_progress",new Impact("unknown",net.worldbinder.util.Lang.stored("worldbinder.version.player_progress"),net.worldbinder.util.Lang.stored("worldbinder.version.player_progress_unverified"),0));
            }
            String unknown = "unknown";
            counts.put(unknown, Math.max(1, scene.blockCount() + scene.entityCount()));
            details.put(unknown, new Impact("unknown", net.worldbinder.util.Lang.stored("worldbinder.version.unknown_source"), net.worldbinder.util.Lang.stored("worldbinder.version.unknown_target"), 0));
        }
        return details.entrySet().stream().map(e -> new Impact(e.getValue().kind, e.getValue().source, e.getValue().replacement, counts.get(e.getKey())))
                .sorted(Comparator.comparingInt(Impact::count).reversed()).toList();
    }

    private static final Set<String> LEGACY_COMPONENTS=Set.of("minecraft:damage","minecraft:repair_cost","minecraft:map_id","minecraft:custom_model_data","minecraft:block_entity_data","minecraft:custom_data",
            "minecraft:custom_name","minecraft:lore","minecraft:dyed_color","minecraft:unbreakable","minecraft:enchantments","minecraft:stored_enchantments","minecraft:potion_contents","minecraft:container","minecraft:charged_projectiles","minecraft:bundle_contents");
    private static void itemImpacts(String snbt,Map<String,Integer> counts,Map<String,Impact> details){
        if(snbt==null||!snbt.contains("components"))return;
        try{itemImpacts(net.minecraft.nbt.TagParser.parseCompoundFully(snbt),counts,details);}catch(com.mojang.brigadier.exceptions.CommandSyntaxException ignored){}
    }
    private static void itemImpacts(net.minecraft.nbt.Tag tag,Map<String,Integer> counts,Map<String,Impact> details){
        if(tag instanceof net.minecraft.nbt.CompoundTag compound){
            if(compound.get("components") instanceof net.minecraft.nbt.CompoundTag components)for(String name:components.keySet())if(!LEGACY_COMPONENTS.contains(name)){
                String key="item:"+name;counts.merge(key,1,Integer::sum);details.put(key,new Impact("item",name,net.worldbinder.util.Lang.stored("worldbinder.version.legacy_item_component"),0));
            }
            for(String key:compound.keySet())itemImpacts(compound.get(key),counts,details);
        }else if(tag instanceof net.minecraft.nbt.ListTag list)for(var entry:list)itemImpacts(entry,counts,details);
    }
}
