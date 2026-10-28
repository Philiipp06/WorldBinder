package net.worldbinder.test;

import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.worldbinder.WorldBinder;
import net.worldbinder.client.WorldBinderClient;
import net.worldbinder.compat.ClientCompat;
import net.worldbinder.config.WorldBinderConfig;
import net.worldbinder.io.WorldBinderPaths;
import net.worldbinder.ui.WorldBinderExportScreen;
import net.worldbinder.validation.ExportValidator;
import java.nio.file.*;

/** This runs in a second Minecraft JVM; the first game's memory and caches are gone. */
final class ProjectRestartClientTest {
    static void run(ClientGameTestContext context) {
        try {
            var fixture=JsonParser.parseString(Files.readString(WorldBinderPaths.GAME_DIR.resolve("restart-fixture.json"))).getAsJsonObject();
            String name=fixture.get("name").getAsString();int map=fixture.get("map").getAsInt();
            Path source=WorldBinderPaths.MINECRAFT_SAVES.resolve(name);
            var archive=WorldBinderClient.scenes().read(source);
            check(archive.maps.get(map).receivedPixels()>0,"Archived map survives full client process restart");
            check(archive.player!=null&&archive.player.nbt.contains("XpLevel:12"),"Archived player survives full client process restart");
            check(archive.player.enderChest!=null&&!archive.player.enderChest.items.isEmpty(),"Archived ender chest survives full client process restart");
            try(var world=new net.fabricmc.fabric.impl.client.gametest.world.TestWorldSaveImpl(context,Path.of(fixture.get("world").getAsString())).open()) {
                context.runOnClient(client->{
                    WorldBinder.replaceConfig(new WorldBinderConfig());
                    WorldBinder.config().crashRecovery=false;WorldBinder.config().appendTimestampToArchiveName=false;
                    WorldBinder.config().captureMinY=63;WorldBinder.config().captureMaxY=66;WorldBinder.config().roamingRadiusChunks=1;
                    WorldBinder.config().includeServerResourcePack=false;
                    WorldBinderClient.capture().continueRecovery(source);
                });
                context.waitFor(client->WorldBinderClient.capture().isCapturing());
                context.runOnClient(client->{
                    var project=WorldBinderClient.capture().exportPreview();
                    check(project.containers.size()>=7&&project.maps.get(map).receivedPixels()>0,"Resume restores stored container and map knowledge without opening menus");
                    WorldBinderClient.capture().queueChunkForRescan(0,0);
                });
                context.waitFor(client->WorldBinderClient.capture().downloadedChunksView().contains(0L));
                context.runOnClient(client->WorldBinderClient.capture().abortQueueAndSaveNow());
                context.waitFor(client->ClientCompat.screen(client) instanceof WorldBinderExportScreen);context.clickScreenButton("Save and export");
                context.waitFor(client->!WorldBinderClient.capture().isCapturing()&&!WorldBinderClient.capture().isSaving());
                context.runOnClient(client->{try {
                    var latest=WorldBinderClient.scenes().scenes().stream().filter(path->path.getFileName().toString().startsWith(name)&&!path.getFileName().toString().equals(name)).findFirst().orElseThrow();
                    var again=WorldBinderClient.scenes().read(latest);
                    check(again.containers.size()>=7&&again.maps.get(map).receivedPixels()>0,"Auxiliary data persists through restart, update and export");
                    check(ExportValidator.validate(again,latest).filesValid,"Post-restart export reads back");
                }catch(Exception e){throw new AssertionError(e);}});
            }
            WorldBinder.LOGGER.info("WORLDBINDER_FULL_PROCESS_RESTART_PASS: archive/player/maps/container persistence, resume and re-export in a new Minecraft JVM");
        }catch(Exception e){throw new AssertionError("Full restart regression",e);}
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
