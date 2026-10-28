package net.worldbinder.capture;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

public final class WorldIdentity {
    private WorldIdentity() {}
    public static String current(Minecraft client) {
        String source;
        var replay=net.worldbinder.replay.ReplayPlayback.state();
        if(replay.present()){
            if(replay.identity()==null)return null;
            source="replay:"+replay.provider()+":"+replay.identity();
        } else if (client.getSingleplayerServer() != null) {
            source = "local:" + client.getSingleplayerServer().getWorldPath(LevelResource.ROOT)
                    .toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
        } else if (client.getCurrentServer() != null) {
            source = "server:" + client.getCurrentServer().ip.trim().toLowerCase(Locale.ROOT);
            if (source.endsWith(":25565")) source = source.substring(0, source.length() - 6);
        } else return null;
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    public static String dimension(Minecraft client) {
        return client.level == null ? null : client.level.dimension().identifier().toString();
    }

    public static boolean dimensionMatches(String stored, String current) {
        // Older archives stored ResourceKey#toString rather than the identifier.
        return stored != null && current != null && (stored.equals(current) || stored.endsWith(" / " + current + "]"));
    }
}
