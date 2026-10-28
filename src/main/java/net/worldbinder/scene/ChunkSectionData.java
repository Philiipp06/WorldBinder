package net.worldbinder.scene;

/** Null means unavailable, never an invented plains biome or full skylight array. */
public final class ChunkSectionData {
    public String[] biomes;
    public byte[] blockLight;
    public byte[] skyLight;

    public boolean hasBiomes() { return biomes != null && biomes.length == 64; }
    public boolean hasBlockLight() { return blockLight != null && blockLight.length == 2048; }
    public boolean hasSkyLight() { return skyLight != null && skyLight.length == 2048; }
}
