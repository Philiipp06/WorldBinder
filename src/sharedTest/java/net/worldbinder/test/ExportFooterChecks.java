package net.worldbinder.test;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.worldbinder.WorldBinder;
import net.worldbinder.compat.ClientCompat;
import net.worldbinder.export.common.ExportCompatibility;
import net.worldbinder.scene.BlockRecord;
import net.worldbinder.scene.WorldScene;
import net.worldbinder.ui.WorldBinderExportScreen;
import net.worldbinder.util.Lang;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

/** Real Minecraft font and widget checks; called once after each EN/DE resource-language reload. */
final class ExportFooterChecks {
    private static final String SOURCE = "minecraft:copper_bulb[lit=true]";
    private static int checks;

    private ExportFooterChecks() {}

    static int run(Minecraft client, WorldScene source) throws Exception {
        checks = 0;
        Screen previous = ClientCompat.screen(client);
        String callerSettings = WorldBinder.GSON.toJson(source.exportSettings);
        String callerTarget = source.targetMinecraftVersion;
        WorldBinderExportScreen current = null;
        try {
            for (int[] size : new int[][]{{427, 240}, {320, 240}, {853, 480}, {260, 320}}) {
                WorldScene fixture = new WorldScene();
                fixture.targetMinecraftVersion = "1.20.4";
                fixture.blocks.add(new BlockRecord(0, 64, 0, SOURCE, false, null));
                fixture.exportSettings.replacements.put(SOURCE, "minecraft:dirt");
                String originalSettings = WorldBinder.GSON.toJson(fixture.exportSettings);
                current = new WorldBinderExportScreen(previous, fixture, "", () -> {
                    throw new AssertionError("Footer checks must never accept or write an export");
                });
                ClientCompat.setScreen(client, current);
                current.width = size[0]; current.height = size[1];
                // Populate enough review rows to catch a list/footer collision even in compact layouts.
                stopAnalysis(current);
                var impacts = new ArrayList<ExportCompatibility.Impact>();
                for (int i = 0; i < 30; i++) impacts.add(new ExportCompatibility.Impact("replacement", SOURCE, "minecraft:oak_planks", i + 1));
                field("impacts").set(current, impacts);
                rebuild(current);
                geometry(client, current, size);

                Button impact = current.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                        .filter(button -> button.active && button.getMessage().getString().contains(SOURCE + " → "))
                        .findFirst().orElseThrow(() -> new AssertionError("No actual replacement row in the review"));
                press(impact);
                EditBox input = (EditBox) field("block").get(current);
                input.setValue("minecraft:stnoe");
                Button apply = button(current, "worldbinder.export.review.apply");
                check(apply.active, "Selecting a known source enables the real replacement action");
                press(apply);
                WorldScene draft = (WorldScene) field("draft").get(current);
                check(draft.exportSettings.replacements.get(SOURCE).equals("minecraft:dirt"), "Invalid input retains the previous replacement");
                String error = Lang.resolve((String) field("error").get(current));
                check(error.contains("minecraft:stnoe") && !error.contains("worldbinder."), "Invalid input shows its translated error, including the bad ID");
                check(WorldBinder.GSON.toJson(fixture.exportSettings).equals(originalSettings), "Rejected edits do not mutate the source project's settings");

                input.setValue("minecraft:chest[facing=west]");
                press(apply);
                check(draft.exportSettings.replacements.get(SOURCE).startsWith("minecraft:chest[")
                        && draft.exportSettings.replacements.get(SOURCE).contains("facing=west"), "The real apply action keeps valid state properties");
                check(((String) field("error").get(current)).isEmpty(), "A successful replacement clears the invalid-input message");
                check(WorldBinder.GSON.toJson(fixture.exportSettings).equals(originalSettings), "Valid preview edits still leave the original source untouched");
                press(button(current, "worldbinder.export.review.reset"));
                check(draft.exportSettings.replacements.isEmpty() && !button(current, "worldbinder.export.review.apply").active,
                        "The real reset action clears the draft and requires a new source selection");
                check(WorldBinder.GSON.toJson(fixture.exportSettings).equals(originalSettings)
                        && fixture.targetMinecraftVersion.equals("1.20.4"), "Reset does not alter the source archive or its target");
                stopAnalysis(current);
                ClientCompat.setScreen(client, previous);
                current = null;
            }
        } finally {
            if (current != null) stopAnalysis(current);
            ClientCompat.setScreen(client, previous);
        }
        check(ClientCompat.screen(client) == previous, "Footer checks restore the caller's screen");
        check(WorldBinder.GSON.toJson(source.exportSettings).equals(callerSettings)
                && java.util.Objects.equals(source.targetMinecraftVersion, callerTarget), "All viewport and action checks preserve the caller's project");
        return checks;
    }

    private static void geometry(Minecraft client, WorldBinderExportScreen screen, int[] size) throws Exception {
        Button apply = button(screen, "worldbinder.export.review.apply");
        Button reset = button(screen, "worldbinder.export.review.reset");
        for (Button action : new Button[]{apply, reset})
            check(action.getWidth() >= client.font.width(action.getMessage()) + 14,
                    Lang.language() + " full replacement action fits at " + size[0] + "×" + size[1] + ": " + action.getMessage().getString());
        var widgets = screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .filter(widget -> widget.visible).toList();
        for (var widget : widgets)
            check(widget.getX() >= 0 && widget.getY() >= 0 && widget.getRight() <= screen.width && widget.getBottom() <= screen.height,
                    "Review control stays inside viewport " + size[0] + "×" + size[1]);
        for (int i = 0; i < widgets.size(); i++) for (int j = i + 1; j < widgets.size(); j++) {
            var a = widgets.get(i); var b = widgets.get(j);
            check(!(a.getX() < b.getRight() && a.getRight() > b.getX() && a.getY() < b.getBottom() && a.getBottom() > b.getY()),
                    "Review list, replacement input, actions and navigation do not overlap at " + size[0] + "×" + size[1]);
        }
        if (size[0] == 260) {
            EditBox input = (EditBox) field("block").get(screen);
            check(input.getY() < apply.getY() && input.getX() == apply.getX(), "Narrow tall review stacks the input above a full-width apply action");
        }
    }

    private static void stopAnalysis(WorldBinderExportScreen screen) throws Exception {
        var pending = (CompletableFuture<?>) field("analysis").get(screen);
        if (pending != null) pending.cancel(false);
        field("analysis").set(screen, null);
    }

    private static Field field(String name) throws Exception {
        Field field = WorldBinderExportScreen.class.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static void rebuild(WorldBinderExportScreen screen) throws Exception {
        var method = WorldBinderExportScreen.class.getDeclaredMethod("rebuild"); method.setAccessible(true); method.invoke(screen);
    }
    private static Button button(Screen screen, String key) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().getString().equals(Lang.string(key))).findFirst()
                .orElseThrow(() -> new AssertionError("Missing review button: " + key));
    }
    private static void press(Button button) { button.onPress(new KeyEvent(ClientCompat.key("KEY_RETURN"), 0, 0)); }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
