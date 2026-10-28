package net.worldbinder.scene;

/** Pixel coverage is independent of color: zero can be a legitimately received transparent pixel. */
public final class CapturedMap {
    public int id;
    public byte[] colors = new byte[128 * 128];
    public boolean[] received = new boolean[128 * 128];
    public String nbt;
    public String decorationsNbt;
    public boolean centerKnown;
    public long observedAt;

    public int receivedPixels() {
        int count = 0;
        if (received != null) for (boolean pixel : received) if (pixel) count++;
        return count;
    }

    public void patch(int x, int y, int width, int height, byte[] pixels) {
        if (x < 0 || y < 0 || width < 0 || height < 0 || x + width > 128 || y + height > 128
                || pixels == null || pixels.length != width * height) throw new net.worldbinder.validation.FindingException(net.worldbinder.validation.Finding.of("worldbinder.error.invalid_map_patch"));
        for (int row = 0; row < height; row++) for (int col = 0; col < width; col++) {
            int destination = (y + row) * 128 + x + col;
            colors[destination] = pixels[row * width + col];
            received[destination] = true;
        }
        observedAt = System.currentTimeMillis();
    }
}
