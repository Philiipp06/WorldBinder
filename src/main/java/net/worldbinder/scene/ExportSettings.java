package net.worldbinder.scene;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ExportSettings {
    public enum Generation { VOID, FLAT, NORMAL }
    public boolean includePlayer;
    /** Writes worldbinder/players.csv from the recorded player roster (needs recording in Settings). */
    public boolean includePlayerList;
    /** Writes worldbinder/dimensions.txt: every project dimension with its export folder and contents. */
    public boolean includeDimensionTree = true;
    public ExportFilterSettings filters=new ExportFilterSettings();
    public Generation generation = Generation.VOID;
    public String seed = "";
    public long defaultSeed = new java.util.Random().nextLong();
    /** Exact source states or block identifiers -> replacement states, applied only to exported copies. */
    public Map<String, String> replacements = new LinkedHashMap<>();

    public long seedValue() {
        if (seed == null || seed.isBlank()) return defaultSeed;
        try { return Long.parseLong(seed.trim()); }
        catch (NumberFormatException ignored) { return seed.hashCode(); }
    }
}
