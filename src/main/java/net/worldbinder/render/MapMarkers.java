package net.worldbinder.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.worldbinder.config.WorldBinderConfig;
import net.worldbinder.ui.component.WbChrome;
import net.worldbinder.ui.component.WbMotion;
import net.worldbinder.ui.component.WbTheme;

/** Player arrow, view cone and entity markers shared by the live map and the HUD radar. */
public final class MapMarkers {
    public enum Kind {
        HOSTILE(0xFFFF5D5D),
        PASSIVE(0xFF78E08A),
        ITEM(0xFFFFD34D),
        PLAYER(0xFFFFB45E),
        OTHER(0xFFB4C2D0);

        public final int color;

        Kind(int color) {
            this.color = color;
        }
    }

    private MapMarkers() {
    }

    public static Kind kind(Entity entity) {
        if (entity instanceof Player) return Kind.PLAYER;
        if (entity instanceof ItemEntity) return Kind.ITEM;
        MobCategory category = entity.getType().getCategory();
        if (category == MobCategory.MONSTER) return Kind.HOSTILE;
        if (category == MobCategory.MISC) return Kind.OTHER;
        return Kind.PASSIVE;
    }

    public static boolean visible(Kind kind, WorldBinderConfig config) {
        return switch (kind) {
            case HOSTILE -> config.mapShowHostile;
            case PASSIVE -> config.mapShowPassive;
            case ITEM -> config.mapShowItems;
            case OTHER -> config.mapShowOtherEntities;
            case PLAYER -> config.mapShowPlayers;
        };
    }

    public static void drawEntity(GuiGraphicsExtractor context, int x, int y, Kind kind, int radius) {
        WbChrome.fillCircle(context, x, y, radius + 1, 0xD0050709);
        WbChrome.fillCircle(context, x, y, radius, kind.color);
        if (radius >= 3) {
            context.fill(x - 1, y - radius + 1, x, y - radius + 2, 0x80FFFFFF);
        }
    }

    /**
     * Navigation arrow with drop shadow, two-tone body, pulsing halo and a soft view cone. {@code yaw} is the
     * Minecraft yaw in degrees; {@code rotateMap} draws the arrow pointing up (map already rotated).
     */
    public static void drawPlayerArrow(GuiGraphicsExtractor context, float x, float y, float yaw, float scale, int accent, boolean cone) {
        context.pose().pushMatrix();
        context.pose().translate(x, y);
        context.pose().scale(scale, scale);
        float pulse = WbMotion.pulse(1800L);
        int halo = 9 + Math.round(pulse * 4);
        WbChrome.fillCircle(context, 0, 0, halo, WbTheme.withAlpha(accent, Math.round(0x34 * (1.0F - pulse))));
        WbChrome.fillCircle(context, 0, 0, 7, WbTheme.withAlpha(accent, 0x30));
        context.pose().rotate((float) Math.toRadians(yaw + 180.0F));
        if (cone) {
            int length = 30;
            for (int i = 2; i < length; i++) {
                float half = i * 0.62F;
                int alpha = Math.round(0x46 * (1.0F - i / (float) length));
                context.fill(Math.round(-half), -i - 1, Math.round(half), -i, WbTheme.withAlpha(accent, alpha));
            }
        }
        // shadow
        WbChrome.fillTriangle(context, 1, -8, -6, 8, 1, 5, 0x70000000);
        WbChrome.fillTriangle(context, 1, -8, 1, 5, 8, 8, 0x70000000);
        // outline
        WbChrome.fillTriangle(context, 0, -10, -8, 8, 0, 5, 0xFF05080C);
        WbChrome.fillTriangle(context, 0, -10, 0, 5, 8, 8, 0xFF05080C);
        // two-tone body
        WbChrome.fillTriangle(context, 0, -8, -6, 6, 0, 3, WbMotion.mix(accent, 0xFFFFFFFF, 0.55F));
        WbChrome.fillTriangle(context, 0, -8, 0, 3, 6, 6, accent);
        context.fill(0, -6, 1, 2, 0x55FFFFFF);
        context.pose().popMatrix();
        WbChrome.fillCircle(context, Math.round(x), Math.round(y), 1, 0xFFFFFFFF);
    }
}
