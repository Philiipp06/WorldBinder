package net.worldbinder.scene;

public enum ChunkCaptureStatus {
    UNKNOWN,
    QUEUED,
    SCANNING,
    DONE,
    PARTIAL,
    FAILED,
    RECOVERY;

    public boolean isOpenWork() {
        return this == QUEUED || this == SCANNING || this == PARTIAL || this == RECOVERY;
    }

    public boolean isExportable() {
        return this == DONE || this == PARTIAL || this == RECOVERY;
    }
}
