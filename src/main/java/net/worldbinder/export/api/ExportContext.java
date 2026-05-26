package net.worldbinder.export.api;

import net.worldbinder.scene.WorldScene;
import net.worldbinder.version.VersionProfile;

import java.nio.file.Path;

public record ExportContext(WorldScene scene, Path worldFolder, VersionProfile profile) {
}
