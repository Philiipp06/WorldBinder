package net.worldbinder.scene;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/** Display names never identify recovery ownership. Unknown legacy identities are retained conservatively. */
public final class RecoveryProjects {
    private RecoveryProjects() {}

    public static Path folder(Path root, WorldScene scene) {
        if (scene.projectId == null || scene.projectId.isBlank()) throw new net.worldbinder.validation.FindingException(net.worldbinder.validation.Finding.of("worldbinder.error.missing_persistent_project_id"));
        String identity = scene.projectId + "\n" + scene.worldIdentity + "\n" + (scene.backendId==null?scene.dimension:scene.backendId+(scene.captureSettings.multiDimension?"":":"+scene.dimension));
        return root.resolve("_recovery_" + UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)));
    }

    public static boolean sameOwner(WorldScene first, WorldScene second) {
        return first.projectId != null && !first.projectId.isBlank() && first.projectId.equals(second.projectId)
                && Objects.equals(first.worldIdentity, second.worldIdentity) && Objects.equals(first.backendId,second.backendId)
                && (first.backendId!=null&&first.captureSettings.multiDimension&&second.captureSettings.multiDimension||Objects.equals(first.dimension, second.dimension));
    }
}
