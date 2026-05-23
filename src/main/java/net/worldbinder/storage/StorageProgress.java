package net.worldbinder.storage;

import net.worldbinder.profiling.StorageProfiler;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

public final class StorageProgress {
    private volatile StorageStage stage = StorageStage.IDLE;
    private volatile String detail = net.worldbinder.util.Lang.string("worldbinder.storage.detail.ready");
    private volatile Path target;
    private volatile double progress;
    private final AtomicLong startedAt = new AtomicLong();

    public void start(Path target) {
        this.target = target;
        this.stage = StorageStage.SNAPSHOT;
        this.detail = net.worldbinder.util.Lang.string("worldbinder.storage.detail.preparing_export");
        this.progress = 0.0D;
        this.startedAt.set(System.currentTimeMillis());
        StorageProfiler.startJob();
    }

    public void update(StorageStage stage, String detail, double progress) {
        this.stage = stage;
        this.detail = detail == null ? stage.label() : detail;
        this.progress = Math.max(0.0D, Math.min(1.0D, progress));
        StorageProfiler.stage(this.stage, this.detail);
    }

    public void finish(Path target) {
        this.target = target;
        update(StorageStage.FINISHED, net.worldbinder.util.Lang.string("worldbinder.storage.detail.export_complete"), 1.0D);
        StorageProfiler.finish(false);
    }

    public void fail(String message) {
        update(StorageStage.FAILED, message, 1.0D);
        StorageProfiler.finish(true);
    }

    public StorageStage stage() { return stage; }
    public String detail() { return detail; }
    public Path target() { return target; }
    public double progress() { return progress; }
    public long elapsedMillis() { return startedAt.get() == 0L ? 0L : System.currentTimeMillis() - startedAt.get(); }

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
        return minutes + "m " + rest + "s";
    }

    public boolean isRunning() {
        return stage != StorageStage.IDLE && stage != StorageStage.FINISHED && stage != StorageStage.FAILED;
    }

    public boolean isTerminal() {
        return stage == StorageStage.FINISHED || stage == StorageStage.FAILED;
    }

    public boolean hasActivity() {
        return stage != StorageStage.IDLE;
    }
}

