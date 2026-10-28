package net.worldbinder.render;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.worldbinder.WorldBinder;
import net.worldbinder.config.WorldBinderConfig;
import net.worldbinder.scene.ContainerRecord;
import net.worldbinder.scene.WorldScene;
import net.worldbinder.util.Lang;

/**
 * Highlights received containers in the world by what the current project knows about their contents:
 * <ul>
 *     <li><b>Unopened</b> (amber, gently pulsing): never opened, contents unknown – open it to archive the items.</li>
 *     <li><b>Partial</b> (blue, with "known/total" label): only some slots were received.</li>
 *     <li><b>Verified</b> (green, optional): every slot was received.</li>
 * </ul>
 * Only chunks and block entities the client received are inspected; nothing is requested from the server. Drawn
 * with vanilla gizmos on both loaders, next to the chunk overlay.
 */
public final class ContainerHighlight {
    public enum State { UNOPENED, PARTIAL, VERIFIED }

    public static final int UNOPENED_COLOR = 0xFFFFB13B;
    public static final int PARTIAL_COLOR = 0xFF4FC3F7;
    public static final int VERIFIED_COLOR = 0xFF39D879;
    private static final int MAX_OUTLINES = 384;
    private static final double LABEL_DISTANCE = 12.0D;

    public static int lastUnopened;
    public static int lastPartial;
    public static int lastVerified;

    private ContainerHighlight() {
    }

    /** Knowledge state of one container, or {@code null} for blocks that are no archivable containers. */
    public static State state(WorldScene scene, String type, String key, String pairedKey) {
        if (type == null || net.worldbinder.capture.ContainerCapture.expectedSlots(type) < 0) return null;
        if (type.equals("minecraft:ender_chest")) {
            var ender = scene.player == null ? null : scene.player.enderChest;
            return ender == null || ender.knownCount() == 0 ? State.UNOPENED : ender.complete() ? State.VERIFIED : State.PARTIAL;
        }
        if (!net.worldbinder.validation.ExportValidator.isContainer(type)) return null;
        ContainerRecord record = scene.containers.get(key);
        ContainerRecord paired = pairedKey == null ? null : scene.containers.get(pairedKey);
        int known = (record == null ? 0 : record.knownCount()) + (paired == null ? 0 : paired.knownCount());
        boolean complete = record != null && record.complete() && (pairedKey == null || paired != null && paired.complete());
        if (complete) return State.VERIFIED;
        return known > 0 ? State.PARTIAL : State.UNOPENED;
    }

    static void emit(Minecraft client, WorldScene scene) {
        lastUnopened = lastPartial = lastVerified = 0;
        WorldBinderConfig config = WorldBinder.config();
        if (!config.containerHighlight || scene == null || client.level == null || client.player == null) return;
        int radius = Math.clamp(config.containerHighlightRadius, 4, 64);
        BlockPos center = client.player.blockPosition();
        int minChunkX = (center.getX() - radius) >> 4, maxChunkX = (center.getX() + radius) >> 4;
        int minChunkZ = (center.getZ() - radius) >> 4, maxChunkZ = (center.getZ() + radius) >> 4;
        double radiusSqr = (double) radius * radius;
        float pulse = (float) (0.5D + 0.5D * Math.sin(System.currentTimeMillis() / 320.0D));
        int outlines = 0;
        for (int cz = minChunkZ; cz <= maxChunkZ && outlines < MAX_OUTLINES; cz++) {
            for (int cx = minChunkX; cx <= maxChunkX && outlines < MAX_OUTLINES; cx++) {
                var chunk = net.worldbinder.compat.ReceivedChunks.chunk(client.level, cx, cz);
                if (chunk == null) continue;
                for (BlockPos pos : chunk.getBlockEntities().keySet()) {
                    if (outlines >= MAX_OUTLINES) break;
                    if (pos.distSqr(center) > radiusSqr) continue;
                    if (scene.selectionBounds != null && !scene.selectionBounds.contains(pos.getX(), pos.getY(), pos.getZ())) continue;
                    var blockState = client.level.getBlockState(pos);
                    String type = BuiltInRegistries.BLOCK.getKey(blockState.getBlock()).toString();
                    BlockPos other = null;
                    if (blockState.getBlock() instanceof ChestBlock && blockState.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                        other = pos.relative(ChestBlock.getConnectedDirection(blockState));
                        if (other.asLong() < pos.asLong()) continue; // the other half draws the pair
                    }
                    String key = ContainerRecord.blockKey(pos.getX(), pos.getY(), pos.getZ());
                    String pairedKey = other == null ? null : ContainerRecord.blockKey(other.getX(), other.getY(), other.getZ());
                    State state = state(scene, type, key, pairedKey);
                    if (state == null) continue;
                    AABB box = new AABB(pos);
                    if (other != null) box = box.minmax(new AABB(other));
                    if (draw(client, box, pos, state, pulse, total(scene, type, key, pairedKey))) outlines++;
                }
            }
        }
        for (var entity : client.level.entitiesForRendering()) {
            if (outlines >= MAX_OUTLINES) break;
            if (entity.distanceToSqr(center.getX() + 0.5D, center.getY(), center.getZ() + 0.5D) > radiusSqr) continue;
            String type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
            if (scene.captureSettings != null && scene.captureSettings.excludes(entity)) continue;
            String key = ContainerRecord.entityKey(entity.getUUID().toString());
            State state = state(scene, type, key, null);
            if (state == null) continue;
            if (draw(client, entity.getBoundingBox().inflate(0.05D), entity.blockPosition(), state, pulse, total(scene, type, key, null))) outlines++;
        }
    }

    private static boolean draw(Minecraft client, AABB box, BlockPos labelPos, State state, float pulse, String label) {
        WorldBinderConfig config = WorldBinder.config();
        if (state == State.VERIFIED && !config.containerHighlightVerified) {
            lastVerified++;
            return false;
        }
        if (state == State.PARTIAL && !config.containerHighlightPartial) {
            lastPartial++;
            return false;
        }
        int color = switch (state) {
            case UNOPENED -> UNOPENED_COLOR;
            case PARTIAL -> PARTIAL_COLOR;
            case VERIFIED -> VERIFIED_COLOR;
        };
        switch (state) {
            case UNOPENED -> lastUnopened++;
            case PARTIAL -> lastPartial++;
            case VERIFIED -> lastVerified++;
        }
        boolean reduced = net.worldbinder.ui.component.WbMotion.reduced();
        int fillAlpha = state == State.UNOPENED ? (reduced ? 0x30 : 0x18 + Math.round(0x28 * pulse)) : 0x1C;
        float width = state == State.VERIFIED ? 1.5F : 2.5F;
        var properties = Gizmos.cuboid(box.inflate(0.004D), GizmoStyle.strokeAndFill(color, width, (fillAlpha << 24) | (color & 0xFFFFFF)));
        if (config.containerHighlightThroughWalls) properties.setAlwaysOnTop();
        if (config.containerHighlightLabels && label != null && client.player.distanceToSqr(labelPos.getX() + 0.5D, labelPos.getY() + 0.5D, labelPos.getZ() + 0.5D) <= LABEL_DISTANCE * LABEL_DISTANCE) {
            Gizmos.billboardTextOverBlock(label, labelPos, 0, color, 0.32F);
        }
        return true;
    }

    /** "known/total" for partially known containers, a short word for unopened ones, nothing for verified. */
    private static String total(WorldScene scene, String type, String key, String pairedKey) {
        int slots = net.worldbinder.capture.ContainerCapture.expectedSlots(type);
        if (type.equals("minecraft:ender_chest")) {
            var ender = scene.player == null ? null : scene.player.enderChest;
            int known = ender == null ? 0 : ender.knownCount();
            return known == 0 ? Lang.string("worldbinder.containers.label.unopened") : known >= slots ? null : known + "/" + slots;
        }
        ContainerRecord record = scene.containers.get(key);
        ContainerRecord paired = pairedKey == null ? null : scene.containers.get(pairedKey);
        int total = slots * (pairedKey == null ? 1 : 2);
        int known = (record == null ? 0 : record.knownCount()) + (paired == null ? 0 : paired.knownCount());
        if (known == 0) return Lang.string("worldbinder.containers.label.unopened");
        return known >= total ? null : known + "/" + total;
    }
}
