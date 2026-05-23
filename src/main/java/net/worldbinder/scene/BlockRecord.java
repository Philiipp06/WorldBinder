package net.worldbinder.scene;

public final class BlockRecord {
    public int x;
    public int y;
    public int z;
    public String state;
    public boolean hasBlockEntity;
    public String blockEntityNbt;

    public BlockRecord() {
    }

    public BlockRecord(int x, int y, int z, String state, boolean hasBlockEntity, String blockEntityNbt) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.state = state;
        this.hasBlockEntity = hasBlockEntity;
        this.blockEntityNbt = blockEntityNbt;
    }
}
