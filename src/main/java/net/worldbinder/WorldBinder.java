package net.worldbinder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.worldbinder.config.WorldBinderConfig;
import net.worldbinder.io.WorldBinderPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WorldBinder {
    public static final String MOD_ID = "worldbinder";
    public static final String MOD_NAME = "WorldBinder";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static WorldBinderConfig config;

    private WorldBinder() {
    }

    public static void init() {
        WorldBinderPaths.ensureBaseFolders();
        config = WorldBinderConfig.load();
        LOGGER.info("{} initialized", MOD_NAME);
    }

    public static WorldBinderConfig config() {
        if (config == null) {
            config = WorldBinderConfig.load();
        }
        return config;
    }

    public static void saveConfig() {
        config().save();
    }
}
