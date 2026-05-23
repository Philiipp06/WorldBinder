package net.worldbinder.validation;

import java.util.ArrayList;
import java.util.List;

public final class ExportValidationReport {
    public String exportName = "unknown";
    public String folder = "";
    public String status = "UNKNOWN";
    public int score;
    public int chunks;
    public int expectedChunks;
    public int missingChunks;
    public int blocks;
    public int blockEntities;
    public int entities;
    public int snapshots;
    public int mapIds;
    public boolean levelDat;
    public boolean sessionLock;
    public boolean regionFiles;
    public boolean entityFiles;
    public boolean poiFiles;
    public boolean archiveJson;
    public boolean manifestJson;
    public boolean metadataJson;
    public boolean resourcePack;
    public boolean zipFile;
    public boolean vanillaWorld;
    public boolean lightEstimated;
    public int exportableSnapshots;
    public int partialSnapshots;
    public int activeSnapshots;
    public int failedSnapshots;
    public boolean entityDataMissing;
    public int regionWriteErrors;
    public int lowQualitySnapshots;
    public List<String> warnings = new ArrayList<>();
    public List<String> passed = new ArrayList<>();

    public String grade() {
        if (score >= 92) return "Excellent";
        if (score >= 80) return "Good";
        if (score >= 65) return "Usable";
        if (score >= 45) return "Partial";
        return "Needs review";
    }
}
