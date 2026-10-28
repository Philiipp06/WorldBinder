package net.worldbinder.test;

import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.worldbinder.hud.WorldBinderHud;
import net.worldbinder.scene.ChunkSnapshot;
import net.worldbinder.storage.*;
import net.worldbinder.util.Lang;
import net.worldbinder.validation.*;

/** Runs after an actual resource-pack language reload on both loaders, not a mocked language table. */
final class LanguageRuntimeChecks {
    private static final FindingStateException retained = new FindingStateException(Finding.of("worldbinder.error.received_chunks"));
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }

    static int verify(Minecraft client, String language) throws Exception {
        checks = 0;
        // NeoForge isolates the QA mod's resources from the production mod. Read through the
        // production class, so this checks the shipped catalog rather than the helper's classpath.
        try (var input = Lang.class.getResourceAsStream("/assets/worldbinder/lang/" + language + ".json")) {
            if (input == null) throw new AssertionError("Missing production language catalog: " + language);
            var bundle = JsonParser.parseReader(new java.io.InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            for (var entry : bundle.entrySet()) {
                var matcher = java.util.regex.Pattern.compile("%(?:(\\d+)\\$)?([%s])").matcher(entry.getValue().getAsString());
                int count = 0, cursor = 0;
                while (matcher.find()) if (!matcher.group(2).equals("%")) count = Math.max(count, matcher.group(1) == null ? ++cursor : Integer.parseInt(matcher.group(1)));
                Object[] args = new Object[count];
                for (int i = 0; i < count; i++) args[i] = "argument " + i + " % \\";
                String expected = Lang.in(language, entry.getKey(), args);
                check(Component.translatable(entry.getKey(), args).getString().equals(expected)
                                && Lang.string(entry.getKey(), args).equals(expected),
                        language + " native Minecraft component / language formatter differ: " + entry.getKey());
            }
        }
        check(retained.getMessage().equals(Lang.in(language, "worldbinder.error.received_chunks")), "Retained exceptions follow resource language changes");
        String[] presetKeys={"worldbinder.gui.preset_safe","worldbinder.gui.preset_balanced","worldbinder.gui.preset_fast","worldbinder.gui.preset_extreme","worldbinder.gui.custom_mode"};
        var presets=net.worldbinder.config.WorldBinderConfig.PerformancePreset.values();
        for(int i=0;i<presets.length;i++)check(presets[i].label().equals(Lang.in(language,presetKeys[i])),"Dashboard/chat preset names follow the actual resource language");
        var snapshot = new ChunkSnapshot(0, 0); snapshot.lastScannedAtMillis = System.currentTimeMillis() - 125000L;
        check(snapshot.lastScannedText().equals(Lang.in(language, "worldbinder.time.minutes_ago", 2)), "Map chunk age follows the selected language");
        var facing = WorldBinderHud.class.getDeclaredMethod("facing", float.class); facing.setAccessible(true);
        check(facing.invoke(null, -90F).equals(Lang.in(language, "worldbinder.direction.east"))
                && facing.invoke(null, 270F).equals(facing.invoke(null, -90F)), "Localized compass directions retain yaw wrap behavior");
        var progress = new StorageProgress(); progress.start(client.gameDirectory.toPath()); progress.update(StorageStage.VALIDATE, null, 0.1);
        check(progress.detail().equals(Lang.in(language, "worldbinder.storage.stage.validate"))
                && net.worldbinder.profiling.StorageProfiler.lastDetail().equals(progress.detail()), "Stage and profiler details resolve stored status keys");
        progress.finish(client.gameDirectory.toPath());
        return checks;
    }
}
