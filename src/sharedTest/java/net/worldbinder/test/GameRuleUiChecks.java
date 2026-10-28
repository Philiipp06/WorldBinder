package net.worldbinder.test;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.worldbinder.compat.ClientCompat;
import net.worldbinder.config.GameRuleSettings;
import net.worldbinder.config.WorldBinderConfig;
import net.worldbinder.ui.WorldBinderConfigScreen;
import net.worldbinder.util.Lang;

/** Exercise the real game-rule tab and every category, including controls below the scroll viewport. */
final class GameRuleUiChecks {
    private int step;
    int checks;
    private final String configBefore = net.worldbinder.WorldBinder.GSON.toJson(net.worldbinder.WorldBinder.config());
    private void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }

    private static Object field(Screen screen, String name) throws Exception {
        for (Class<?> type = screen.getClass(); type != null; type = type.getSuperclass()) {
            try { var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(screen); }
            catch (NoSuchFieldException ignored) {}
        }
        throw new AssertionError("Missing config field " + name);
    }
    private static Button button(Screen screen, String label) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().getString().equals(label)).findFirst().orElseThrow(() -> new AssertionError("Missing actual config control: " + label));
    }
    private static void press(Button button) { button.onPress(new KeyEvent(ClientCompat.key("KEY_RETURN"), 0, 0)); }

    boolean tick(Minecraft client, String language) throws Exception {
        if (step == 0) {
            var screen = WorldBinderConfigScreen.safety(null); ClientCompat.setScreen(client, screen);
            ((WorldBinderConfig) field(screen, "draft")).targetMinecraftVersion = "26.3";
        } else if (step == 1) {
            press(button(ClientCompat.screen(client), Lang.string("worldbinder.config.safety.page.gamerules")));
        } else if (step >= 2 && step < 2 + GameRuleSettings.categories() * 2) {
            var screen = (WorldBinderConfigScreen) ClientCompat.screen(client);
            int category = (step - 2) / 2;
            if (step % 2 == 0) {
                var settings = (GameRuleSettings) field(screen, "gameRules");
                for (String rule : GameRuleSettings.rules("26.3", category)) {
                    String label = Lang.string(GameRuleSettings.labelKey(rule));
                    check(!Lang.string(GameRuleSettings.tooltipKey(rule)).contains(Lang.string("worldbinder.language.unknown")), "Every rule has a localized description: " + rule);
                    if (GameRuleSettings.isInteger(rule)) {
                        String prefix = Lang.string("worldbinder.gamerules.int_value", label, "");
                        check(screen.children().stream().anyMatch(child -> child instanceof net.minecraft.client.gui.components.AbstractWidget widget
                                && widget.getMessage().getString().startsWith(prefix)), "Integer rule has a value control: " + rule);
                        continue;
                    }
                    check(!label.contains(Lang.string("worldbinder.language.unknown")) && !label.contains("worldbinder.")
                            && !label.equals("gamerule." + rule), "Every canonical rule has an actual localized label: " + rule);
                    boolean before = settings.value(rule);
                    var toggle = button(screen, Lang.string("worldbinder.config.toggle_value", label,
                            Lang.string(before ? "worldbinder.common.on" : "worldbinder.common.off")));
                    check(!toggle.getMessage().getString().contains(Lang.string("worldbinder.language.unknown")), "Rule UI never masks an absent label: " + rule);
                    press(toggle); check(settings.value(rule) != before, "Labeled rule still toggles its original canonical value: " + rule);
                    check(!toggle.getMessage().getString().contains(Lang.string("worldbinder.language.unknown")), "Rule label remains translated after toggle: " + rule);
                    press(toggle); check(settings.value(rule) == before, "Restoring the toggle retains its existing value: " + rule);
                }
                ExpansionUiChecks.shot(client, language + "-gamerules-" + category + "-top");
                for (int i = 0; i < 40; i++) screen.mouseScrolled(screen.width * .7, screen.height * .6, 0, -1);
            } else {
                ExpansionUiChecks.shot(client, language + "-gamerules-" + category + "-bottom");
                if (category < GameRuleSettings.categories() - 1) press(button(screen, Lang.string("worldbinder.gamerules.category." + (category + 1))));
            }
        } else {
            check(configBefore.equals(net.worldbinder.WorldBinder.GSON.toJson(net.worldbinder.WorldBinder.config())), "Inspecting and restoring every rule does not change the user's original config");
            return true;
        }
        step++; return false;
    }
}
