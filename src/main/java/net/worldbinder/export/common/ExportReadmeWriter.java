package net.worldbinder.export.common;

import net.worldbinder.scene.WorldScene;
import net.worldbinder.util.Lang;
import net.worldbinder.version.TargetMinecraftVersion;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;

/** Generated help travels with the world and is always bilingual, including offline/headless exports. */
public final class ExportReadmeWriter {
    private ExportReadmeWriter(){}
    public static void write(WorldScene scene,Path folder,int chunks)throws IOException{
        var target=TargetMinecraftVersion.resolve(scene.targetMinecraftVersion);
        var keys=ExportPathUtil.serverImportKeys(scene,folder);
        String keyText=keys.isEmpty()?"minecraft:overworld":"minecraft:"+String.join(", minecraft:",keys);
        var text=new StringBuilder("# WorldBinder 1.7.0\n\n");
        for(String language:new String[]{"de_de","en_us"}){
            text.append(Lang.in(language,"worldbinder.generated.readme_intro")).append("\n\n");
            text.append(Lang.in(language,"worldbinder.generated.project",scene.projectId,scene.backendName==null?Lang.in(language,"worldbinder.generated.unknown"):scene.backendName,scene.projectDimensions())).append("\n\n");
            text.append(Lang.in(language,"worldbinder.generated.readme_summary",scene.name,Instant.now(),scene.minecraftVersion,target.name(),target.profile().label(language),chunks,scene.projectBlockCount(),scene.projectBlockEntityCount(),scene.projectEntityCount())).append("\n\n");
            if(scene.filterCounts!=null)text.append(Lang.in(language,"worldbinder.filters.summary",scene.filterCounts.blocks(),scene.filterCounts.blockEntities(),scene.filterCounts.entities())).append("\n\n");
            text.append(Lang.in(language,"worldbinder.generated.readme_layout")).append("\n\n");
            text.append(Lang.in(language,"worldbinder.generated.readme_server",keyText)).append("\n\n");
            text.append(Lang.in(language,"worldbinder.generated.readme_limits")).append("\n\n");
            if(scene.replayIdentity!=null)text.append(Lang.in(language,"worldbinder.replay.limits")).append("\n\n");
            if(target.effectiveDataVersion()>=2566&&scene.exportSettings.generation!=net.worldbinder.scene.ExportSettings.Generation.NORMAL&&net.worldbinder.scene.ProjectDimensions.all(scene).stream().anyMatch(s->"minecraft:the_nether".equals(s.dimension)||"minecraft:the_end".equals(s.dimension)))text.append(Lang.in(language,"worldbinder.dimensions.experimental")).append("\n\n");
            for(var notice:scene.exportNotices)text.append(notice.render(language)).append("\n\n");
        }
        Files.writeString(folder.resolve("README.md"),text.append(ExportLegalNotice.markdown()).toString());
    }
}
