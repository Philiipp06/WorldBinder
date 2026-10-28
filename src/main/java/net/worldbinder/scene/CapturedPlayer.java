package net.worldbinder.scene;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CapturedPlayer {
    public String uuid;
    public String nbt;
    public long observedAt;
    public ContainerRecord enderChest;
    public boolean statsReceived;
    public boolean advancementsReceived;
    public Map<String, Map<String, Integer>> stats = new LinkedHashMap<>();
    public Map<String, String> advancements = new LinkedHashMap<>();
}
