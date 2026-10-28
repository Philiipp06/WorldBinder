package net.worldbinder.validation;

import java.util.ArrayList;
import java.util.List;

public final class ExportValidationReport {
    public String exportName = "unknown";
    public String folder = "";
    public String status = "UNKNOWN";
    public boolean filesValid;
    public String completeness = "UNKNOWN";
    public List<Finding> errors = new ArrayList<>();
    public int unknownContainers;
    public int missingMapImages;
    public int partialMapImages;
    public int missingBiomeSections;
    public int missingLightSections;
    public int relightChunks;
    public int score;
    public int chunks;
    public int expectedChunks;
    public int missingChunks;
    public long unknownSelectionBlocks;
    public long excludedBlocks,excludedBlockEntities,excludedEntities;
    public int blocks;
    public int blockEntities;
    public int entities;
    public int snapshots;
    public int mapIds;
    public boolean levelDat;
    public boolean levelDatOld;
    public boolean sessionLock;
    public boolean regionFiles;
    public boolean entityFiles;
    public boolean poiFiles;
    public boolean archiveJson;
    public boolean manifestJson;
    public boolean metadataJson;
    public boolean exportReadme;
    public boolean modernSavedData;
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
    public List<Finding> warnings = new ArrayList<>();
    public List<Finding> passed = new ArrayList<>();

    public String grade() {
        return net.worldbinder.util.Lang.string(gradeKey());
    }
    public String gradeKey(){return "worldbinder.quality."+(score>=92?"excellent":score>=80?"good":score>=65?"usable":score>=45?"partial":"review");}
}
