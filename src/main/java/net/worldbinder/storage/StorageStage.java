package net.worldbinder.storage;

import net.worldbinder.util.Lang;

public enum StorageStage {
    IDLE("worldbinder.storage.stage.idle"),
    SNAPSHOT("worldbinder.storage.stage.snapshot"),
    VALIDATE("worldbinder.storage.stage.validate"),
    METADATA("worldbinder.storage.stage.metadata"),
    VANILLA_WORLD("worldbinder.storage.stage.vanilla_world"),
    MAP_DATA("worldbinder.storage.stage.map_data"),
    PLAYER_DATA("worldbinder.storage.stage.player_data"),
    RESOURCE_PACK("worldbinder.storage.stage.resource_pack"),
    ZIP("worldbinder.storage.stage.zip"),
    FINISHED("worldbinder.storage.stage.finished"),
    FAILED("worldbinder.storage.stage.failed");

    private final String key;

    StorageStage(String key) {
        this.key = key;
    }

    public String label() {
        return Lang.string(key);
    }
}
