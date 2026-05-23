package net.worldbinder.integration;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.worldbinder.ui.WorldBinderConfigScreen;

public final class WorldBinderModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return WorldBinderConfigScreen::new;
    }
}
