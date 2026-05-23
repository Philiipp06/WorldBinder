package net.worldbinder.profiling;

import net.worldbinder.storage.StorageStage;

import java.util.EnumMap;
import java.util.Map;

public final class StorageProfiler {
    private static final Map<StorageStage, Long> STAGE_TIME = new EnumMap<>(StorageStage.class);
    private static volatile StorageStage currentStage = StorageStage.IDLE;
    private static volatile long stageStartedAt = System.currentTimeMillis();
    private static volatile long jobStartedAt;
    private static volatile long lastJobDuration;
    private static volatile int completedJobs;
    private static volatile int failedJobs;
    private static volatile String lastDetail = "Idle";

    private StorageProfiler() {}

    public static synchronized void startJob() {
        STAGE_TIME.clear();
        currentStage = StorageStage.SNAPSHOT;
        stageStartedAt = System.currentTimeMillis();
        jobStartedAt = stageStartedAt;
        lastDetail = "Preparing export";
    }

    public static synchronized void stage(StorageStage stage, String detail) {
        long now = System.currentTimeMillis();
        STAGE_TIME.merge(currentStage, Math.max(0L, now - stageStartedAt), Long::sum);
        currentStage = stage == null ? StorageStage.IDLE : stage;
        stageStartedAt = now;
        lastDetail = detail == null ? currentStage.label() : detail;
    }

    public static synchronized void finish(boolean failed) {
        long now = System.currentTimeMillis();
        STAGE_TIME.merge(currentStage, Math.max(0L, now - stageStartedAt), Long::sum);
        lastJobDuration = Math.max(0L, now - jobStartedAt);
        currentStage = failed ? StorageStage.FAILED : StorageStage.FINISHED;
        stageStartedAt = now;
        if (failed) failedJobs++; else completedJobs++;
    }

    public static Map<StorageStage, Long> stageTimesSnapshot() {
        synchronized (StorageProfiler.class) {
            Map<StorageStage, Long> copy = new EnumMap<>(STAGE_TIME);
            long live = Math.max(0L, System.currentTimeMillis() - stageStartedAt);
            copy.merge(currentStage, live, Long::sum);
            return copy;
        }
    }

    public static StorageStage currentStage() { return currentStage; }
    public static String lastDetail() { return lastDetail; }
    public static long elapsedMillis() { return jobStartedAt == 0L ? 0L : System.currentTimeMillis() - jobStartedAt; }
    public static long lastJobDuration() { return lastJobDuration; }
    public static int completedJobs() { return completedJobs; }
    public static int failedJobs() { return failedJobs; }
}
