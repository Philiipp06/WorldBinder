package net.worldbinder.config;

import net.worldbinder.WorldBinder;
import net.worldbinder.io.WorldBinderPaths;
import net.worldbinder.version.TargetMinecraftVersion;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;

public final class WorldBinderConfig {
    private static final int MAX_ROAMING_RADIUS_CHUNKS = 12;

    public enum PerformancePreset {
        SAFE,
        BALANCED,
        FAST,
        EXTREME,
        CUSTOM
    }

    public enum MapLayerMode {
        BOTH,
        CHUNKS_ONLY,
        MAP_ONLY
    }

    public enum RadarDetailMode {
        AUTO,
        LOW,
        MEDIUM,
        HIGH
    }

    public enum ResourcePackFallbackMode {
        DISABLED,
        ENABLED,
        LOWER_PROTOCOL_ONLY
    }

    public PerformancePreset performancePreset = PerformancePreset.BALANCED;
    public int blocksPerTick = 65536;
    public int commandsPerTick = 512;
    public int tickBudgetMillis = 18;
    public int chunkQueueLimit = 1024;
    public int newChunksPerTick = 8;
    public int hotChunksPerTick = 3;
    public int roamingRadiusChunks = 5;
    public int visibleChunkPriorityRadius = 2;
    public int customBlocksPerTick = 65536;
    public int customCommandsPerTick = 512;
    public int customTickBudgetMillis = 18;
    public int customChunkQueueLimit = 1024;
    public int customNewChunksPerTick = 8;
    public int customHotChunksPerTick = 3;
    public int customRoamingRadiusChunks = 5;
    public int customVisibleChunkPriorityRadius = 2;
    public boolean showLegalStartReminder = true;
    public ResourcePackFallbackMode resourcePackFallbackMode = ResourcePackFallbackMode.LOWER_PROTOCOL_ONLY;
    public boolean showResourcePackFallbackWarning = true;
    public boolean queueDebugDiagnostics = false;
    public int queueLoadedChunkGraceTicks = 40;
    public int targetFps = 90;
    public boolean adaptivePerformance = true;
    public boolean adaptiveThrottle = true;
    public int maxUiWorkMs = 4;
    public int maxCaptureWorkMs = 8;
    public int maxArchiveWorkMs = 12;
    public boolean serverSafetyMode = true;
    public boolean autoSaveOnDisconnect = true;
    public boolean crashRecovery = true;
    public int recoveryAutosaveSeconds = 30;
    public int captureMinY = -64;
    public int captureMaxY = 319;

    public boolean captureEntities = true;
    public boolean captureAir = false;
    public boolean captureBlockEntities = true;
    public boolean includeEntityPlayers = false;
    public boolean useFullEntityNbtForPlacement = true;
    public boolean sendPlacementCommands = true;
    public boolean showDetailedChatFeedback = true;
    public boolean showBossbarOverlay = true;
    public boolean showArchiveStatsInMenu = true;
    public boolean autoOpenMenuFromPause = true;
    public boolean showWorldGizmos = true;
    public boolean includeServerResourcePack = true;
    public boolean exportGameRules = true;
    public String gameRulesOverride = "doDaylightCycle=false;doWeatherCycle=false;doMobSpawning=false;keepInventory=true;randomTickSpeed=0";
    public boolean autoDeleteRecovery = true;
    public boolean exportMaps = true;
    public boolean exportAdvancements = true;
    public boolean exportStats = true;
    public boolean exportMetadata = true;
    public boolean zipWorldExport = false;
    public boolean confirmExistingWorld = true;

    public int bossbarScalePercent = 100;
    public int bossbarOffsetY = 14;
    public int chunkRadarScalePercent = 100;
    public MapLayerMode f10MapLayerMode = MapLayerMode.BOTH;
    public MapLayerMode radarLayerMode = MapLayerMode.BOTH;

    public int chunkRadarSize = 11;
    public int chunkRadarCellSize = 5;
    public int chunkRadarOffsetX = 12;
    public int chunkRadarOffsetY = 56;
    public boolean chunkRadarRightAligned = true;
    public int radarMaxRenderedChunks = 169;
    public RadarDetailMode radarDetailMode = RadarDetailMode.AUTO;
    public int radarUpdateRate = 12;

    public String defaultArchiveName = "worldbinder_export";
    public boolean appendTimestampToArchiveName = true;
    public String targetMinecraftVersion = TargetMinecraftVersion.CURRENT;
    public String languageMode = "system";


    public static WorldBinderConfig load() {
        WorldBinderPaths.ensureBaseFolders();
        if (!Files.exists(WorldBinderPaths.CONFIG_FILE)) {
            WorldBinderConfig config = new WorldBinderConfig();
            config.save();
            return config;
        }

        try (Reader reader = Files.newBufferedReader(WorldBinderPaths.CONFIG_FILE)) {
            WorldBinderConfig config = WorldBinder.GSON.fromJson(reader, WorldBinderConfig.class);
            return config == null ? new WorldBinderConfig() : config.normalized();
        } catch (Exception exception) {
            WorldBinder.LOGGER.warn("Failed to load WorldBinder config. Falling back to defaults.", exception);
            return new WorldBinderConfig();
        }
    }

    public void save() {
        normalized();
        WorldBinderPaths.ensureBaseFolders();
        try (Writer writer = Files.newBufferedWriter(WorldBinderPaths.CONFIG_FILE)) {
            WorldBinder.GSON.toJson(this, writer);
        } catch (IOException exception) {
            WorldBinder.LOGGER.error("Failed to save WorldBinder config", exception);
        }
    }

    public int effectiveBlocksPerTick() {
        return switch (performancePreset) {
            case SAFE -> 8192;
            case BALANCED -> 65536;
            case FAST -> 262144;
            case EXTREME -> Integer.MAX_VALUE;
            case CUSTOM -> blocksPerTick < 0 ? Integer.MAX_VALUE : blocksPerTick;
        };
    }

    public int effectiveCommandsPerTick() {
        return switch (performancePreset) {
            case SAFE -> 64;
            case BALANCED -> 512;
            case FAST -> 2048;
            case EXTREME -> Integer.MAX_VALUE;
            case CUSTOM -> commandsPerTick < 0 ? Integer.MAX_VALUE : commandsPerTick;
        };
    }

    public int effectiveTickBudgetMillis() {
        return switch (performancePreset) {
            case SAFE -> 6;
            case BALANCED -> 18;
            case FAST -> 40;
            case EXTREME -> 90;
            case CUSTOM -> tickBudgetMillis < 0 ? 250 : tickBudgetMillis;
        };
    }

    public int effectiveNewChunksPerTick() {
        return switch (performancePreset) {
            case SAFE -> 4;
            case BALANCED -> 8;
            case FAST -> 16;
            case EXTREME -> 32;
            case CUSTOM -> newChunksPerTick < 0 ? 32 : newChunksPerTick;
        };
    }

    public int effectiveHotChunksPerTick() {
        return switch (performancePreset) {
            case SAFE -> 1;
            case BALANCED -> 3;
            case FAST -> 6;
            case EXTREME -> 12;
            case CUSTOM -> hotChunksPerTick < 0 ? 12 : hotChunksPerTick;
        };
    }

    public int effectiveChunkQueueLimit() {
        return switch (performancePreset) {
            case SAFE -> 256;
            case BALANCED -> 1024;
            case FAST -> 4096;
            case EXTREME -> 16384;
            case CUSTOM -> chunkQueueLimit < 0 ? 16384 : chunkQueueLimit;
        };
    }


    public int effectiveRoamingRadiusChunks() {
        if (roamingRadiusChunks < 0) {
            return MAX_ROAMING_RADIUS_CHUNKS;
        }
        return Math.max(1, Math.min(MAX_ROAMING_RADIUS_CHUNKS, roamingRadiusChunks));
    }

    public int effectiveVisibleChunkPriorityRadius() {
        if (visibleChunkPriorityRadius < 0) {
            return Math.max(1, Math.min(8, effectiveRoamingRadiusChunks()));
        }
        return Math.max(1, Math.min(visibleChunkPriorityRadius, effectiveRoamingRadiusChunks()));
    }

    public int effectiveQueueLoadedChunkGraceTicks() {
        if (queueLoadedChunkGraceTicks < 0) {
            return 200;
        }
        return Math.max(1, Math.min(400, queueLoadedChunkGraceTicks));
    }

    public boolean resourcePackFallbackEnabled() {
        return resourcePackFallbackMode != ResourcePackFallbackMode.DISABLED;
    }

    public int effectiveRadarMaxRenderedChunks() {
        // -1 keeps the old automatic behavior, while the HUD still needs a runtime cap.
        int requested = radarMaxRenderedChunks < 0 ? 1024 : radarMaxRenderedChunks;
        return Math.max(9, Math.min(1024, requested));
    }

    public int effectiveRadarUpdateIntervalMillis() {
        if (radarUpdateRate < 0) {
            return 0;
        }
        return Math.max(16, 1000 / Math.max(1, radarUpdateRate));
    }

    public boolean effectiveAdaptiveThrottleEnabled() {
        return adaptivePerformance && adaptiveThrottle;
    }

    public int effectiveMaxUiWorkMs() {
        return Math.max(1, maxUiWorkMs < 0 ? 50 : maxUiWorkMs);
    }

    public int effectiveMaxCaptureWorkMs() {
        return Math.max(1, maxCaptureWorkMs < 0 ? 250 : maxCaptureWorkMs);
    }

    public int effectiveMaxArchiveWorkMs() {
        return Math.max(1, maxArchiveWorkMs < 0 ? 250 : maxArchiveWorkMs);
    }

    public int effectiveCaptureMinY() {
        return Math.min(captureMinY, captureMaxY);
    }

    public int effectiveCaptureMaxY() {
        return Math.max(captureMinY, captureMaxY);
    }

    public int effectiveCaptureHeight() {
        return Math.max(1, effectiveCaptureMaxY() - effectiveCaptureMinY() + 1);
    }

    public String presetDescription() {
        return switch (performancePreset) {
            case SAFE -> net.worldbinder.util.Lang.string("worldbinder.config.preset_desc.safe");
            case BALANCED -> net.worldbinder.util.Lang.string("worldbinder.config.preset_desc.balanced");
            case FAST -> net.worldbinder.util.Lang.string("worldbinder.config.preset_desc.fast");
            case EXTREME -> net.worldbinder.util.Lang.string("worldbinder.config.preset_desc.extreme");
            case CUSTOM -> net.worldbinder.util.Lang.string("worldbinder.config.preset_desc.custom");
        };
    }

    public TargetMinecraftVersion.Entry targetVersion() {
        return TargetMinecraftVersion.resolve(targetMinecraftVersion);
    }

    public String targetVersionLabel() {
        TargetMinecraftVersion.Entry version = targetVersion();
        return version.name() + " • " + version.profile().label();
    }

    public void cycleTargetVersion(boolean backwards) {
        targetMinecraftVersion = backwards
                ? TargetMinecraftVersion.previous(targetMinecraftVersion)
                : TargetMinecraftVersion.next(targetMinecraftVersion);
        save();
    }

    public void setPreset(PerformancePreset preset) {
        PerformancePreset next = preset == null ? PerformancePreset.BALANCED : preset;
        if (performancePreset == PerformancePreset.CUSTOM) {
            rememberCurrentCustomValues();
        }
        performancePreset = next;
        if (next == PerformancePreset.CUSTOM) {
            restoreRememberedCustomValues();
        } else {
            applyPresetValues(next);
        }
        save();
    }

    public void rememberCurrentCustomValues() {
        customBlocksPerTick = blocksPerTick;
        customCommandsPerTick = commandsPerTick;
        customTickBudgetMillis = tickBudgetMillis;
        customChunkQueueLimit = chunkQueueLimit;
        customNewChunksPerTick = newChunksPerTick;
        customHotChunksPerTick = hotChunksPerTick;
        customRoamingRadiusChunks = roamingRadiusChunks;
        customVisibleChunkPriorityRadius = visibleChunkPriorityRadius;
    }

    private void restoreRememberedCustomValues() {
        blocksPerTick = customBlocksPerTick;
        commandsPerTick = customCommandsPerTick;
        tickBudgetMillis = customTickBudgetMillis;
        chunkQueueLimit = customChunkQueueLimit;
        newChunksPerTick = customNewChunksPerTick;
        hotChunksPerTick = customHotChunksPerTick;
        roamingRadiusChunks = customRoamingRadiusChunks;
        visibleChunkPriorityRadius = customVisibleChunkPriorityRadius;
    }

    private void applyPresetValues(PerformancePreset preset) {
        blocksPerTick = switch (preset) {
            case SAFE -> 8192;
            case BALANCED -> 65536;
            case FAST -> 262144;
            case EXTREME -> -1;
            case CUSTOM -> customBlocksPerTick;
        };
        commandsPerTick = switch (preset) {
            case SAFE -> 64;
            case BALANCED -> 512;
            case FAST -> 2048;
            case EXTREME -> -1;
            case CUSTOM -> customCommandsPerTick;
        };
        tickBudgetMillis = switch (preset) {
            case SAFE -> 6;
            case BALANCED -> 18;
            case FAST -> 40;
            case EXTREME -> 90;
            case CUSTOM -> customTickBudgetMillis;
        };
        newChunksPerTick = switch (preset) {
            case SAFE -> 4;
            case BALANCED -> 8;
            case FAST -> 16;
            case EXTREME -> 32;
            case CUSTOM -> customNewChunksPerTick;
        };
        hotChunksPerTick = switch (preset) {
            case SAFE -> 1;
            case BALANCED -> 3;
            case FAST -> 6;
            case EXTREME -> 12;
            case CUSTOM -> customHotChunksPerTick;
        };
        chunkQueueLimit = switch (preset) {
            case SAFE -> 256;
            case BALANCED -> 1024;
            case FAST -> 4096;
            case EXTREME -> 16384;
            case CUSTOM -> customChunkQueueLimit;
        };
        roamingRadiusChunks = switch (preset) {
            case SAFE -> 3;
            case BALANCED -> 5;
            case FAST -> 8;
            case EXTREME -> MAX_ROAMING_RADIUS_CHUNKS;
            case CUSTOM -> customRoamingRadiusChunks;
        };
        visibleChunkPriorityRadius = switch (preset) {
            case SAFE -> 1;
            case BALANCED -> 2;
            case FAST -> 4;
            case EXTREME -> 8;
            case CUSTOM -> customVisibleChunkPriorityRadius;
        };
    }

    private WorldBinderConfig normalized() {
        if (performancePreset == null) {
            performancePreset = PerformancePreset.BALANCED;
        }
        if (f10MapLayerMode == null) {
            f10MapLayerMode = MapLayerMode.BOTH;
        }
        if (radarLayerMode == null) {
            radarLayerMode = MapLayerMode.BOTH;
        }
        if (radarDetailMode == null) {
            radarDetailMode = RadarDetailMode.AUTO;
        }
        if (resourcePackFallbackMode == null) {
            resourcePackFallbackMode = ResourcePackFallbackMode.LOWER_PROTOCOL_ONLY;
        }
        // Keep raw custom numbers as entered; runtime accessors apply safety limits.
        if (defaultArchiveName == null || defaultArchiveName.isBlank()) {
            defaultArchiveName = "worldbinder_export";
        }
        targetMinecraftVersion = TargetMinecraftVersion.normalize(targetMinecraftVersion);
        if (gameRulesOverride == null) {
            gameRulesOverride = "";
        }
        return this;
    }
}
