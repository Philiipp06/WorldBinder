package net.worldbinder.storage;

import net.worldbinder.profiling.StorageProfiler;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

public final class StorageProgress {
    private volatile StorageStage stage = StorageStage.IDLE;
    private volatile String detail = net.worldbinder.util.Lang.stored("worldbinder.storage.detail.ready");
    private volatile Path target;
    private volatile double progress;
    private final AtomicLong startedAt = new AtomicLong();
    private final AtomicLong sequence = new AtomicLong();
    private volatile long stoppedAt;
    private volatile StorageStage failureStage=StorageStage.IDLE;
    private volatile Context context = new Context("", "", "", 0, 0, 0, 0, false);
    public record Context(String name, String version, String dimension, int chunks, int blocks, int entities, int maps, boolean disconnected) {}

    public void start(Path target) {
        start(target, null, false);
    }

    public void start(Path target, net.worldbinder.scene.WorldScene scene, boolean disconnected) {
        this.target = target;
        this.context = scene == null ? new Context("", "", "", 0, 0, 0, 0, disconnected)
                : new Context(scene.name, scene.targetMinecraftVersion, scene.projectDimensions(), scene.projectChunkCount(), scene.projectBlockCount(), scene.projectEntityCount(), scene.projectMapCount(), disconnected);
        this.stage = StorageStage.SNAPSHOT;
        this.detail = net.worldbinder.util.Lang.stored("worldbinder.storage.detail.preparing_export");
        this.progress = 0.0D;
        this.startedAt.set(System.currentTimeMillis());
        this.stoppedAt = 0L;
        this.failureStage=StorageStage.IDLE;
        sequence.incrementAndGet();
        StorageProfiler.startJob();
    }

    public long jobId() { return sequence.get(); }
    public Context context() { return context; }
    public void describe(net.worldbinder.scene.WorldScene scene) {
        context=new Context(scene.name,scene.targetMinecraftVersion,scene.projectDimensions(),scene.projectChunkCount(),scene.projectBlockCount(),scene.projectEntityCount(),scene.projectMapCount(),context.disconnected());
    }

    public void reviewReady() {
        update(StorageStage.REVIEW, net.worldbinder.util.Lang.stored("worldbinder.storage.detail.review_ready"), progress);
        stoppedAt = System.currentTimeMillis();
    }

    public void dismissReview() {
        if (stage == StorageStage.REVIEW) stage = StorageStage.IDLE;
    }

    public void update(StorageStage stage, String detail, double progress) {
        this.stage = stage;
        this.detail = detail == null ? net.worldbinder.util.Lang.stored("worldbinder.storage.stage." + stage.name().toLowerCase(java.util.Locale.ROOT)) : detail;
        this.progress = Math.max(this.progress, Math.max(0.0D, Math.min(1.0D, progress)));
        StorageProfiler.stage(this.stage, this.detail);
    }

    public void finish(Path target) {
        this.target = target;
        this.stoppedAt = System.currentTimeMillis();
        update(StorageStage.FINISHED, net.worldbinder.util.Lang.stored("worldbinder.storage.detail.export_complete"), 1.0D);
        StorageProfiler.finish(false);
    }

    public void fail(String message) {
        this.failureStage=stage;
        this.stoppedAt = System.currentTimeMillis();
        update(StorageStage.FAILED, message, progress);
        StorageProfiler.finish(true);
    }

    public StorageStage stage() { return stage; }
    public StorageStage failureStage() { return failureStage; }
    public String detail() { return net.worldbinder.util.Lang.resolve(detail); }
    public Path target() { return target; }
    public double progress() { return progress; }
    public long elapsedMillis() { return startedAt.get() == 0L ? 0L : Math.max(0L, (stoppedAt == 0L ? System.currentTimeMillis() : stoppedAt) - startedAt.get()); }

    public long estimatedRemainingMillis() {
        if (!isRunning()) {
            return 0L;
        }
        double current = Math.max(0.0D, Math.min(0.99D, progress));
        long elapsed = elapsedMillis();
        if (elapsed < 1500L || current <= 0.02D) {
            return -1L;
        }
        long estimatedTotal = (long) (elapsed / current);
        return Math.max(0L, estimatedTotal - elapsed);
    }

    public String etaText() {
        long remaining = estimatedRemainingMillis();
        if (!isRunning()) {
            return net.worldbinder.util.Lang.string("worldbinder.storage.eta.done");
        }
        if (remaining < 0L) {
            return net.worldbinder.util.Lang.string("worldbinder.storage.eta.calculating");
        }
        return formatMillis(remaining);
    }

    public static String formatMillis(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long minutes = seconds / 60L;
        long rest = seconds % 60L;
        return net.worldbinder.util.Lang.string("worldbinder.time.minutes_seconds", minutes, rest);
    }

    public boolean isRunning() {
        return stage != StorageStage.IDLE && stage != StorageStage.REVIEW && stage != StorageStage.FINISHED && stage != StorageStage.FAILED;
    }

    public boolean isTerminal() {
        return stage == StorageStage.FINISHED || stage == StorageStage.FAILED;
    }

    public boolean hasActivity() {
        return stage != StorageStage.IDLE;
    }
}

