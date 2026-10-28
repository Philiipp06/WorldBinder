package net.worldbinder.scene;

/**
 * A player the client saw during a server download: in the received tab list and/or as a player entity in the
 * world. Only data the client received is stored; recording is opt-in because these are other people's data.
 */
public final class SeenPlayer {
    public String uuid;
    public String name;
    /** Tab-list display name as plain text, if the server sent one. */
    public String displayName;
    public boolean self;
    public boolean inTabList;
    public boolean seenInWorld;
    public long firstSeen;
    public long lastSeen;
    /** Lower-case game mode id from the tab list ("survival", "creative" …), or null. */
    public String gameMode;
    public int latencyMs = -1;
    public String dimension;
    public boolean hasPosition;
    public int x;
    public int y;
    public int z;
    public int sightings;
}
