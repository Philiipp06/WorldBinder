package net.worldbinder.test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.worldbinder.WorldBinder;
import net.worldbinder.client.WorldBinderClient;
import net.worldbinder.config.WorldBinderConfig;
import net.worldbinder.io.WorldBinderPaths;
import net.worldbinder.storage.BlockRecordChunkCache;

final class CaptureRegressionTest {
    private CaptureRegressionTest() {}

    static void run(ClientGameTestContext context) {
        String name = "port_smoke_" + UUID.randomUUID().toString().replace("-", "");
        context.runOnClient(client -> {
            WorldBinder.replaceConfig(new WorldBinderConfig());
            WorldBinder.config().messageMode = WorldBinderConfig.MessageMode.NONE;
            WorldBinder.config().appendTimestampToArchiveName = false;
            WorldBinder.config().autoDeleteRecovery = false;
            client.player.setPos(0, 99, 0);
            WorldBinderClient.selections().setFirstFromCrosshair();
            client.player.setPos(1, 99, 0);
            WorldBinderClient.selections().setSecondFromCrosshair();
            client.player.setPos(0, 100, 0);
            WorldBinderClient.capture().captureWorldArchive(name);
            check(WorldBinderClient.capture().isCapturing(), "Selection capture started");
        });
        context.waitFor(client -> !WorldBinderClient.capture().isCapturing() && !WorldBinderClient.capture().isSaving());
        context.runOnClient(client -> {
            try {
                var library = WorldBinderClient.scenes();
                var folder = WorldBinderPaths.MINECRAFT_SAVES.resolve(name);
                var scene = library.read(folder);
                check(scene.blockCount() >= 2, "Captured selection retained blocks");
                check(scene.blocks.stream().anyMatch(block -> block.state.contains("minecraft:stone")), "Captured stone state");
                check("26.3".equals(scene.targetMinecraftVersion), "Export target version");
                check(Files.size(folder.resolve("level.dat")) > 0, "Exported level.dat");
                try (var regions = Files.walk(folder.resolve("region"))) {
                    check(regions.anyMatch(path -> path.toString().endsWith(".mca")), "Exported region data");
                }
                var cacheFolder = WorldBinderPaths.CACHE_ROOT.resolve(name + "_roundtrip");
                var cache = new BlockRecordChunkCache(cacheFolder);
                cache.writeChunk(0L, scene.blocks);
                cache.flushWrites();
                var reopened = new BlockRecordChunkCache(cacheFolder);
                check(reopened.readChunk(0L).size() == scene.blockCount(), "Durable cache reopen");
                var recoveryFolder = WorldBinderPaths.RECOVERY_ROOT.resolve("_recovery_" + name);
                library.saveRecoverySnapshot(scene, recoveryFolder);
                check(library.canFinalizeRecovery(recoveryFolder), "Recovery manifest complete");
                check(library.read(recoveryFolder).blockCount() == scene.blockCount(), "Recovery archive roundtrip");
            } catch (IOException e) { throw new AssertionError("Capture regression", e); }
        });
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
