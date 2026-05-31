package net.worldbinder.export.common;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.ListTag;
import net.worldbinder.version.VersionProfile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

public final class ExportSavedDataWriter {
    private static final byte[] DEFAULT_ICON = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAAAmklEQVR4nO3QQRHAIADAMMAFLwTgX9+QkccaBb3Ofe43fmzpAK0BOkBrgA7QGqADtAboAK0BOkBrgA7QGqADtAboAK0BOkBrgA7QGqADtAboAK0BOkBrgA7QGqADtAboAK0BOkBrgA7QGqADtAboAK0BOkBrgA7QGqADtAboAK0BOkBrgA7QGqADtAboAK0BOkBrgA7QGqADtAcqEwHivkU5agAAAABJRU5ErkJggg==");

    private ExportSavedDataWriter() {
    }

    public static void writeReleaseFiles(Path worldFolder, VersionProfile profile) throws IOException {
        writeLevelDatBackup(worldFolder);
        writeIcon(worldFolder);
        if (profile != null && profile.writesModernDimensionFolders()) {
            writeModernSavedData(worldFolder, profile.dataVersion());
        }
        removeLegacyExportNotes(worldFolder);
    }

    public static void writeDimensionSavedData(Path dimensionFolder, int dataVersion) throws IOException {
        if (dimensionFolder == null) {
            return;
        }
        Path data = dimensionFolder.resolve("data").resolve("minecraft");
        Files.createDirectories(data);
        writeSimpleSavedData(data.resolve("chunk_tickets.dat"), dataVersion, new CompoundTag());
        writeSimpleSavedData(data.resolve("raids.dat"), dataVersion, new CompoundTag());
        writeSimpleSavedData(data.resolve("world_border.dat"), dataVersion, worldBorderData());
    }

    private static void writeModernSavedData(Path worldFolder, int dataVersion) throws IOException {
        Path data = worldFolder.resolve("data").resolve("minecraft");
        Files.createDirectories(data);
        writeSimpleSavedData(data.resolve("custom_boss_events.dat"), dataVersion, new CompoundTag());
        writeSimpleSavedData(data.resolve("random_sequences.dat"), dataVersion, new CompoundTag());
        writeSimpleSavedData(data.resolve("scheduled_events.dat"), dataVersion, new CompoundTag());
        writeSimpleSavedData(data.resolve("scoreboard.dat"), dataVersion, scoreboardData());
        writeSimpleSavedData(data.resolve("stopwatches.dat"), dataVersion, new CompoundTag());
        writeSimpleSavedData(data.resolve("weather.dat"), dataVersion, weatherData());
        writeSimpleSavedData(data.resolve("world_clocks.dat"), dataVersion, new CompoundTag());
    }

    private static void writeSimpleSavedData(Path target, int dataVersion, CompoundTag data) throws IOException {
        if (Files.isRegularFile(target)) {
            return;
        }
        CompoundTag root = new CompoundTag();
        root.putInt("DataVersion", dataVersion);
        root.put("data", data);
        Files.createDirectories(target.getParent());
        NbtIo.writeCompressed(root, target);
    }

    private static CompoundTag scoreboardData() {
        CompoundTag data = new CompoundTag();
        data.put("Objectives", new ListTag());
        data.put("PlayerScores", new ListTag());
        data.put("Teams", new ListTag());
        data.put("DisplaySlots", new CompoundTag());
        return data;
    }

    private static CompoundTag weatherData() {
        CompoundTag data = new CompoundTag();
        data.putInt("clearWeatherTime", 0);
        data.putInt("rainTime", 0);
        data.putInt("thunderTime", 0);
        data.putBoolean("raining", false);
        data.putBoolean("thundering", false);
        return data;
    }

    private static CompoundTag worldBorderData() {
        CompoundTag data = new CompoundTag();
        data.putDouble("BorderCenterX", 0.0D);
        data.putDouble("BorderCenterZ", 0.0D);
        data.putDouble("BorderSize", 59999968.0D);
        data.putDouble("BorderSafeZone", 5.0D);
        data.putDouble("BorderDamagePerBlock", 0.2D);
        data.putDouble("BorderSizeLerpTarget", 59999968.0D);
        data.putLong("BorderSizeLerpTime", 0L);
        data.putInt("BorderWarningBlocks", 5);
        data.putInt("BorderWarningTime", 15);
        return data;
    }

    private static void writeLevelDatBackup(Path worldFolder) throws IOException {
        Path level = worldFolder.resolve("level.dat");
        Path backup = worldFolder.resolve("level.dat_old");
        if (Files.isRegularFile(level) && !Files.exists(backup)) {
            Files.copy(level, backup);
        }
    }

    private static void writeIcon(Path worldFolder) throws IOException {
        Path icon = worldFolder.resolve("icon.png");
        if (!Files.exists(icon)) {
            Files.write(icon, DEFAULT_ICON);
        }
    }

    private static void removeLegacyExportNotes(Path worldFolder) throws IOException {
        Files.deleteIfExists(worldFolder.resolve("WORLD_BINDER_README.txt"));
        Files.deleteIfExists(worldFolder.resolve("WORLD_BINDER_VANILLA_EXPORT.txt"));
        Files.deleteIfExists(worldFolder.resolve("WORLD_BINDER_RESOURCEPACK.txt"));
        Files.deleteIfExists(worldFolder.resolve("SERVER_IMPORT_NOTES.txt"));
    }
}
