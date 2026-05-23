package net.worldbinder.placement;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.worldbinder.WorldBinder;
import net.worldbinder.scene.BlockRecord;
import net.worldbinder.scene.EntityRecord;
import net.worldbinder.scene.SceneLibrary;
import net.worldbinder.scene.WorldScene;
import net.worldbinder.status.OperationStatus;
import net.worldbinder.util.Chat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.regex.Pattern;

public final class ScenePlacementService {
    private static final Pattern POS_TAG = Pattern.compile(",?Pos:\\[[^]]*]", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROTATION_TAG = Pattern.compile(",?Rotation:\\[[^]]*]", Pattern.CASE_INSENSITIVE);
    private static final Pattern UUID_TAG = Pattern.compile(",?UUID:\\[[^]]*]", Pattern.CASE_INSENSITIVE);

    private final SceneLibrary library;
    private final Queue<String> commandQueue = new ArrayDeque<>();
    private int queuedTotal;

    public ScenePlacementService(SceneLibrary library) {
        this.library = library;
    }

    public void placeLatestAtPlayer() {
        library.latest().ifPresentOrElse(this::placeAtPlayer, () -> Chat.warnKey("worldbinder.chat.no_saved_archive"));
    }

    public void placeAtPlayer(Path path) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            Chat.errorKey("worldbinder.chat.no_world_loaded");
            return;
        }
        try {
            WorldScene scene = library.read(path);
            BlockPos target = client.player.blockPosition();
            queuePlacement(scene, target);
            OperationStatus.begin("WorldBinder", net.worldbinder.util.Lang.string("worldbinder.status.placing", scene.name));
            Chat.infoKey("worldbinder.chat.placement_queued", scene.name, target.getX() + " " + target.getY() + " " + target.getZ());
        } catch (IOException exception) {
            WorldBinder.LOGGER.error("Failed to read archive", exception);
            Chat.errorKey("worldbinder.chat.archive_read_failed");
        }
    }

    public void tick() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.player.connection == null) {
            commandQueue.clear();
            queuedTotal = 0;
            return;
        }
        if (!WorldBinder.config().sendPlacementCommands) {
            return;
        }

        int limit = Math.max(1, WorldBinder.config().commandsPerTick);
        for (int i = 0; i < limit && !commandQueue.isEmpty(); i++) {
            client.player.connection.sendCommand(commandQueue.poll());
        }
        if (queuedTotal > 0) {
            int done = queuedTotal - commandQueue.size();
            OperationStatus.update(net.worldbinder.util.Lang.string("worldbinder.status.placed_commands", done, queuedTotal), done / (double) queuedTotal);
            if (commandQueue.isEmpty()) {
                OperationStatus.finish(net.worldbinder.util.Lang.string("worldbinder.status.placement_complete"));
                queuedTotal = 0;
            }
        }
    }

    public int queuedCommands() {
        return commandQueue.size();
    }

    private void queuePlacement(WorldScene scene, BlockPos target) {
        commandQueue.clear();
        for (BlockRecord block : scene.blocks) {
            int x = target.getX() + block.x;
            int y = target.getY() + block.y;
            int z = target.getZ() + block.z;
            commandQueue.add("setblock " + x + " " + y + " " + z + " " + block.state + blockEntitySuffix(block) + " replace");
        }
        for (EntityRecord entity : scene.entities) {
            double x = target.getX() + entity.x;
            double y = target.getY() + entity.y;
            double z = target.getZ() + entity.z;
            commandQueue.add("summon " + entity.type + " " + trim(x) + " " + trim(y) + " " + trim(z) + buildEntityNbt(entity));
        }
        queuedTotal = commandQueue.size();
    }

    private static String blockEntitySuffix(BlockRecord block) {
        if (block.blockEntityNbt == null || block.blockEntityNbt.isBlank()) {
            return "";
        }
        // For command placement the x/y/z/id keys are harmless for most blocks, but the command still expects the NBT directly after the block state.
        return sanitizeBlockEntityNbt(block.blockEntityNbt);
    }

    private static String sanitizeBlockEntityNbt(String nbt) {
        String result = nbt;
        result = result.replaceAll(",?x:-?\\d+", "");
        result = result.replaceAll(",?y:-?\\d+", "");
        result = result.replaceAll(",?z:-?\\d+", "");
        result = result.replaceAll(",?id:\"[^\"]+\"", "");
        result = result.replace("{,", "{").replace(",}", "}");
        return result.equals("{}") ? "" : result;
    }

    private static String buildEntityNbt(EntityRecord entity) {
        String raw = WorldBinder.config().useFullEntityNbtForPlacement ? sanitizeEntityNbt(entity.fullNbt) : null;
        if (raw != null && !raw.isBlank() && !raw.equals("{}")) {
            return raw;
        }

        StringBuilder nbt = new StringBuilder("{");
        nbt.append("Rotation:").append("[").append(entity.yaw).append("f,").append(entity.pitch).append("f]");
        if (entity.customName != null && !entity.customName.isBlank()) {
            nbt.append(",CustomName:'{\"text\":\"").append(escape(entity.customName)).append("\"}'");
        }
        if (entity.glowing) {
            nbt.append(",Glowing:1b");
        }
        if (entity.invisible) {
            nbt.append(",Invisible:1b");
        }
        if (entity.noGravity) {
            nbt.append(",NoGravity:1b");
        }
        nbt.append("}");
        return nbt.toString();
    }

    private static String sanitizeEntityNbt(String nbt) {
        if (nbt == null || nbt.isBlank()) {
            return null;
        }
        String result = nbt;
        result = POS_TAG.matcher(result).replaceAll("");
        result = ROTATION_TAG.matcher(result).replaceAll("");
        result = UUID_TAG.matcher(result).replaceAll("");
        result = result.replace("{,", "{").replace(",}", "}");
        return result;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String trim(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }
}
