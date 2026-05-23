package net.worldbinder.scene;

import java.time.Instant;
import java.util.Arrays;

public final class ChunkSnapshot {
    public int chunkX;
    public int chunkZ;
    public int[] colors = new int[16 * 16];
    public int[] heights = new int[16 * 16];
    public transient String[] states;

    public int scannedBlocks;
    public int savedBlocks;
    public int entityCount;
    public int blockEntityCount;
    public boolean hasBiomeData = true;
    public boolean lightEstimated = true;
    public boolean hasSnapshot;
    public boolean exportError;
    public String lastError;
    public long lastScannedAtMillis;
    public ChunkCaptureStatus status = ChunkCaptureStatus.UNKNOWN;
    public String queueReason;
    public String queueSource;
    public long queuedAtMillis;
    public String stateHistory;

    private transient long renderVersion = 1L;
    private transient boolean metricsDirty = true;
    private transient int cachedSampleCount = -1;
    private transient int cachedAverageColor = 0x55333344;
    private transient int cachedQualityExpectedHeight = Integer.MIN_VALUE;
    private transient double cachedQualityScore = 0.0D;

    public ChunkSnapshot() {
        Arrays.fill(heights, Integer.MIN_VALUE);
    }

    public ChunkSnapshot(int chunkX, int chunkZ) {
        this();
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public void markScanned(boolean saved, boolean blockEntity) {
        markScanned(1, saved ? 1 : 0, blockEntity ? 1 : 0);
    }

    public void markScanned(int scanned, int saved, int blockEntities) {
        scannedBlocks += Math.max(0, scanned);
        savedBlocks += Math.max(0, saved);
        blockEntityCount += Math.max(0, blockEntities);
        touchMetadata();
    }

    public void markEntity() {
        entityCount++;
        touchMetadata();
    }

    public void markQueued() {
        markQueued("unspecified", "unknown");
    }

    public void markQueued(String reason, String source) {
        queueReason = reason == null || reason.isBlank() ? "unspecified" : reason;
        queueSource = source == null || source.isBlank() ? "unknown" : source;
        queuedAtMillis = System.currentTimeMillis();
        appendStateHistory("queued:" + queueSource);
        setStatus(ChunkCaptureStatus.QUEUED);
    }

    public void markScanning() {
        appendStateHistory("scanning");
        setStatus(ChunkCaptureStatus.SCANNING);
    }

    public void markDone() {
        appendStateHistory("done");
        setStatus(ChunkCaptureStatus.DONE);
    }

    public void markPartial() {
        appendStateHistory("partial");
        setStatus(ChunkCaptureStatus.PARTIAL);
    }

    public void markRecovery() {
        appendStateHistory("recovery");
        setStatus(ChunkCaptureStatus.RECOVERY);
    }

    public void markError(String message) {
        exportError = true;
        lastError = message;
        appendStateHistory("failed");
        setStatus(ChunkCaptureStatus.FAILED);
    }

    public ChunkCaptureStatus effectiveStatus() {
        if (status == null) {
            return exportError ? ChunkCaptureStatus.FAILED : ChunkCaptureStatus.UNKNOWN;
        }
        if (exportError) {
            return ChunkCaptureStatus.FAILED;
        }
        return status;
    }

    public boolean isDone() {
        return effectiveStatus() == ChunkCaptureStatus.DONE;
    }

    public boolean isIncomplete() {
        return effectiveStatus().isOpenWork() || effectiveStatus() == ChunkCaptureStatus.FAILED;
    }

    public void sample(int localX, int localZ, int y, String state, int color) {
        if (localX < 0 || localX > 15 || localZ < 0 || localZ > 15) {
            return;
        }
        ensureSampleArrays();
        int index = localZ * 16 + localX;
        if (y >= heights[index]) {
            heights[index] = y;
            if (states != null) {
                states[index] = state;
            }
            colors[index] = color;
            hasSnapshot = true;
            touchVisual();
        }
    }

    public boolean hasSample(int localX, int localZ) {
        int index = localZ * 16 + localX;
        return index >= 0 && heights != null && index < heights.length && heights[index] != Integer.MIN_VALUE;
    }

    public int colorAt(int localX, int localZ) {
        int index = localZ * 16 + localX;
        if (colors == null || index < 0 || index >= colors.length || colors[index] == 0) {
            return cachedAverageColor == 0 ? 0x55333344 : cachedAverageColor;
        }
        return colors[index];
    }

    public int sampleCount() {
        rebuildMetricsIfNeeded();
        return cachedSampleCount;
    }

    public int highestY() {
        int highest = Integer.MIN_VALUE;
        if (heights == null || heights.length == 0) {
            return 0;
        }
        for (int height : heights) {
            if (height == Integer.MIN_VALUE) {
                continue;
            }
            highest = Math.max(highest, height);
        }
        return highest == Integer.MIN_VALUE ? 0 : highest;
    }

    public double blockCoverage(int expectedHeight) {
        int expected = Math.max(1, expectedHeight * 16 * 16);
        return Math.max(0.0D, Math.min(1.0D, scannedBlocks / (double) expected));
    }

    public double snapshotCoverage() {
        return sampleCount() / 256.0D;
    }

    public double qualityScore(int expectedHeight) {
        rebuildMetricsIfNeeded();
        if (cachedQualityExpectedHeight == expectedHeight) {
            return cachedQualityScore;
        }
        double score = blockCoverage(expectedHeight) * 0.55D;
        score += cachedSampleCount / 256.0D * 0.20D;
        score += hasBiomeData ? 0.08D : 0.0D;
        score += lightEstimated ? 0.05D : 0.10D;
        score += entityCount > 0 ? 0.05D : 0.03D;
        score += blockEntityCount > 0 ? 0.05D : 0.03D;
        if (exportError) {
            score -= 0.30D;
        }
        cachedQualityExpectedHeight = expectedHeight;
        cachedQualityScore = Math.max(0.0D, Math.min(1.0D, score));
        return cachedQualityScore;
    }

    public String lastScannedText() {
        if (lastScannedAtMillis <= 0L) {
            return "never";
        }
        long seconds = Math.max(0L, (System.currentTimeMillis() - lastScannedAtMillis) / 1000L);
        if (seconds < 60L) return seconds + "s ago";
        long minutes = seconds / 60L;
        if (minutes < 60L) return minutes + "m ago";
        return Instant.ofEpochMilli(lastScannedAtMillis).toString();
    }

    public int averageColor() {
        rebuildMetricsIfNeeded();
        return cachedAverageColor;
    }

    public long renderVersion() {
        return renderVersion;
    }

    private void rebuildMetricsIfNeeded() {
        if (!metricsDirty && cachedSampleCount >= 0) {
            return;
        }
        long r = 0;
        long g = 0;
        long b = 0;
        int count = 0;
        int colored = 0;
        if (heights == null || colors == null || heights.length == 0 || colors.length == 0) {
            cachedSampleCount = Math.max(0, cachedSampleCount);
            metricsDirty = false;
            return;
        }
        for (int i = 0; i < heights.length; i++) {
            if (heights[i] == Integer.MIN_VALUE) {
                continue;
            }
            count++;
            int color = colors[i];
            if (color == 0) {
                continue;
            }
            r += (color >> 16) & 255;
            g += (color >> 8) & 255;
            b += color & 255;
            colored++;
        }
        cachedSampleCount = count;
        cachedAverageColor = colored == 0 ? 0x55333344 : 0xDD000000 | (((int) (r / colored)) << 16) | (((int) (g / colored)) << 8) | ((int) (b / colored));
        cachedQualityExpectedHeight = Integer.MIN_VALUE;
        metricsDirty = false;
    }

    private void appendStateHistory(String event) {
        if (event == null || event.isBlank()) {
            return;
        }
        String stamped = (System.currentTimeMillis() / 1000L) + ":" + event;
        if (stateHistory == null || stateHistory.isBlank()) {
            stateHistory = stamped;
            return;
        }
        stateHistory = stateHistory + " > " + stamped;
        if (stateHistory.length() > 360) {
            stateHistory = stateHistory.substring(stateHistory.length() - 360);
            int separator = stateHistory.indexOf(" > ");
            if (separator >= 0 && separator + 3 < stateHistory.length()) {
                stateHistory = stateHistory.substring(separator + 3);
            }
        }
    }

    private void setStatus(ChunkCaptureStatus newStatus) {
        status = newStatus == null ? ChunkCaptureStatus.UNKNOWN : newStatus;
        touchMetadata();
    }

    private void touchMetadata() {
        lastScannedAtMillis = System.currentTimeMillis();
        cachedQualityExpectedHeight = Integer.MIN_VALUE;
    }

    public void dropDebugStateData() {
        states = null;
    }

    public void compactVisualData() {
        rebuildMetricsIfNeeded();
        colors = new int[0];
        heights = new int[0];
        states = null;
        renderVersion++;
    }

    private void ensureSampleArrays() {
        if (colors == null || colors.length != 256) {
            colors = new int[16 * 16];
        }
        if (heights == null || heights.length != 256) {
            heights = new int[16 * 16];
            Arrays.fill(heights, Integer.MIN_VALUE);
        }
    }

    private void touchVisual() {
        lastScannedAtMillis = System.currentTimeMillis();
        renderVersion++;
        metricsDirty = true;
    }
}
