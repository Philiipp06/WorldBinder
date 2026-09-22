package net.worldbinder.capture;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.worldbinder.WorldBinder;
import net.worldbinder.scene.EntityRecord;
import net.worldbinder.util.Lang;

final class CapturedEntityFactory {
    private CapturedEntityFactory() {
    }

    static boolean isExportCandidate(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (entity.isAlive()) {
            return true;
        }
        String type = typeId(entity);
        return type.equals("minecraft:block_display")
                || type.equals("minecraft:item_display")
                || type.equals("minecraft:text_display")
                || type.equals("minecraft:interaction")
                || type.equals("minecraft:armor_stand")
                || type.endsWith("display");
    }

    static EntityRecord create(Entity entity, BlockPos origin) {
        Vec3 pos = entity.position();
        EntityRecord record = new EntityRecord();
        record.uuid = entity.getUUID().toString();
        record.runtimeId = entity.getId();
        record.type = typeId(entity);
        record.x = pos.x - origin.getX();
        record.y = pos.y - origin.getY();
        record.z = pos.z - origin.getZ();
        record.yaw = entity.getYRot();
        record.pitch = entity.getXRot();
        record.customName = entity.hasCustomName() && entity.getCustomName() != null
                ? entity.getCustomName().getString() : null;
        record.glowing = false;
        record.invisible = entity.isInvisible();
        record.noGravity = entity.isNoGravity();
        record.fullNbt = serialize(entity);
        return record;
    }

    static String cacheKey(Entity entity) {
        String uuid = entity.getUUID().toString();
        return uuid == null || uuid.isBlank() ? "runtime:" + entity.getId() : uuid;
    }

    static boolean hasUsefulNbt(EntityRecord record) {
        return record != null && record.fullNbt != null
                && !record.fullNbt.isBlank() && record.fullNbt.contains("Pos");
    }

    private static String serialize(Entity entity) {
        try {
            TagValueOutput output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
            output.putString("id", typeId(entity));
            entity.saveWithoutId(output);
            return output.buildResult().toString();
        } catch (Throwable throwable) {
            WorldBinder.LOGGER.warn(Lang.string("worldbinder.log.capture.entity_serialize_failed", entity.getType()), throwable);
            return null;
        }
    }

    private static String typeId(Entity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }
}
