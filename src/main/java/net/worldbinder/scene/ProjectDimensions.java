package net.worldbinder.scene;

import java.util.*;
import net.worldbinder.validation.*;

/** A project owns one backend with one independent coordinate space per dimension. */
public final class ProjectDimensions {
    private ProjectDimensions(){}
    public static String canonical(String id){
        if(id==null)return null;
        int at=id.lastIndexOf(" / ");
        if(at>=0&&id.endsWith("]"))id=id.substring(at+3,id.length()-1);
        var parsed=net.minecraft.resources.Identifier.tryParse(id);
        return parsed==null?null:parsed.toString();
    }
    public static List<WorldScene> all(WorldScene root){
        var result=new ArrayList<WorldScene>();result.add(root);
        if(root.dimensions!=null)result.addAll(root.dimensions.values());
        return result;
    }
    public static WorldScene find(WorldScene root,String dimension){
        String id=canonical(dimension);
        if(Objects.equals(canonical(root.dimension),id))return root;
        return root.dimensions.get(id);
    }
    public static WorldScene dimensionOnly(WorldScene source){
        var copy=ProjectData.metadataCopy(source);copy.dimensions.clear();
        copy.blocks=source.blocks;copy.entities=source.entities;copy.chunkSnapshots=source.chunkSnapshots;copy.pendingChunks=source.pendingChunks;
        return copy;
    }
    /** Frozen siblings are shared, never recursively serialized on the client thread. */
    public static WorldScene activate(WorldScene frozen,String target,WorldScene empty){
        String id=canonical(target);if(id==null)throw invalid(target);
        if(Objects.equals(canonical(frozen.dimension),id))return frozen;
        var siblings=new LinkedHashMap<>(frozen.dimensions);
        var next=siblings.remove(id);if(next==null)next=empty;
        var previous=dimensionOnly(frozen);siblings.put(canonical(previous.dimension),previous);
        next=dimensionOnly(next);next.dimensions=siblings;
        next.projectId=frozen.projectId;next.worldIdentity=frozen.worldIdentity;next.backendId=frozen.backendId;next.backendName=frozen.backendName;
        next.name=frozen.name;next.createdAt=frozen.createdAt;next.revision=frozen.revision;
        next.archiveType=frozen.archiveType;next.includesBlockEntityNbt=frozen.includesBlockEntityNbt;next.includesEntityNbt=frozen.includesEntityNbt;
        next.includesMapData=frozen.includesMapData;next.includesStats=frozen.includesStats;next.includesAdvancements=frozen.includesAdvancements;
        next.resourcePackExpected=frozen.resourcePackExpected;next.resourcePackFile=frozen.resourcePackFile;next.resourcePackSource=frozen.resourcePackSource;next.resourcePackSha256=frozen.resourcePackSha256;
        next.gameRulesNbt=frozen.gameRulesNbt;
        next.seenPlayers=new LinkedHashMap<>(frozen.seenPlayers==null?Map.of():frozen.seenPlayers);
        next.exportSettings=net.worldbinder.WorldBinder.GSON.fromJson(net.worldbinder.WorldBinder.GSON.toJson(frozen.exportSettings),ExportSettings.class);
        next.captureSettings=frozen.captureSettings.copy();
        next.replayProvider=frozen.replayProvider;next.replayIdentity=frozen.replayIdentity;next.replayHighWater=frozen.replayHighWater;
        next.replaySeekGaps=frozen.replaySeekGaps;
        next.replayRewound=frozen.replayRewound;
        next.player=frozen.player==null?null:net.worldbinder.WorldBinder.GSON.fromJson(net.worldbinder.WorldBinder.GSON.toJson(frozen.player),CapturedPlayer.class);
        return next;
    }
    static void validateChildren(WorldScene root){
        Set<String> keys=new HashSet<>();String active=canonical(root.dimension);if(active!=null)keys.add(active);
        for(var entry:root.dimensions.entrySet()){
            var child=entry.getValue();String id=canonical(entry.getKey());
            if(id==null||!id.equals(entry.getKey())||child==null||!id.equals(canonical(child.dimension))||!keys.add(id)
                ||child.dimensions!=null&&!child.dimensions.isEmpty()||!Objects.equals(root.projectId,child.projectId)
                ||!Objects.equals(root.worldIdentity,child.worldIdentity)||!Objects.equals(root.backendId,child.backendId))throw invalid(entry.getKey());
            ProjectData.normalize(child,false);
        }
    }
    private static FindingException invalid(String id){return new FindingException(Finding.of("worldbinder.dimensions.invalid",id));}
}
