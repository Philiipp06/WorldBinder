package net.worldbinder.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.worldbinder.WorldBinder;
import net.worldbinder.config.WorldBinderConfig;
import net.worldbinder.util.BlockStateStrings;

import java.io.InputStream;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Map colors for block states. Besides the vanilla map palette, colors can be derived from the block textures of
 * the active resource packs. This makes server packs visible on the map that add custom blocks through unused
 * block states (for example note block instruments, mushroom block faces or tripwire) or replace existing blocks:
 * the blockstate file, model and texture are resolved through the resource manager exactly as the game does, and
 * the top face texture is averaged.
 *
 * <p>Only resource files are read (blockstates, models, textures), so this works the same on every supported
 * Minecraft version. Results are cached per block state and dropped when the loaded pack list changes.
 */
public final class BlockMapColors {
    private static final int UNRESOLVED = 0;
    private static final int NO_MAP_COLOR = 0xFF6F7680;
    private static final int FLAG_CUSTOM = 1;
    private static final int FLAG_TINTED = 2;
    private static final int MAX_PARENT_DEPTH = 12;

    /** Resolved top-face color of a state: {@code color} is 0 when no texture could be read. */
    private record Entry(int color, int flags) {
        boolean custom() { return (flags & FLAG_CUSTOM) != 0; }
        boolean tinted() { return (flags & FLAG_TINTED) != 0; }
    }

    private record Face(String texture, boolean tinted, boolean custom) {
    }

    private static final Map<BlockState, Entry> STATES = new ConcurrentHashMap<>();
    private record Blockstate(JsonObject json, boolean custom) {
    }

    private static final Map<String, Optional<Blockstate>> BLOCKSTATES = new ConcurrentHashMap<>();
    private static final Map<String, Face> MODELS = new ConcurrentHashMap<>();
    private static final Map<String, int[]> TEXTURES = new ConcurrentHashMap<>();
    private static volatile int packSignature;
    private static int tickCounter;
    private static volatile long revision;

    private BlockMapColors() {
    }

    /** Incremented whenever cached colors were dropped (pack change or mode change). */
    public static long revision() {
        return revision;
    }

    /** Checks every two seconds whether the loaded resource packs changed and drops the cache if so. */
    public static void tick(Minecraft client) {
        if (++tickCounter < 40) {
            return;
        }
        tickCounter = 0;
        try {
            int signature = client.getResourceManager().listPacks().map(PackResources::packId).toList().hashCode();
            if (signature != packSignature) {
                packSignature = signature;
                invalidate();
            }
        } catch (RuntimeException exception) {
            WorldBinder.LOGGER.debug("Could not read resource pack list for map colors", exception);
        }
    }

    public static void invalidate() {
        STATES.clear();
        BLOCKSTATES.clear();
        MODELS.clear();
        TEXTURES.clear();
        revision++;
    }

    /** Map color (opaque ARGB) for the top block of a column. */
    public static int color(BlockState state, BlockGetter level, BlockPos pos) {
        WorldBinderConfig.MapBlockColors mode = WorldBinder.config().mapBlockColors;
        if (mode == null) mode = WorldBinderConfig.MapBlockColors.AUTO;
        int vanilla = vanilla(state, level, pos);
        if (vanilla == NO_MAP_COLOR) {
            int marker = markerColor(state);
            if (marker != 0) {
                return marker;
            }
            Entry entry = STATES.computeIfAbsent(state, BlockMapColors::resolve);
            return entry.color() != UNRESOLVED ? entry.color() : vanilla;
        }
        if (mode == WorldBinderConfig.MapBlockColors.VANILLA) {
            return vanilla;
        }
        Entry entry = STATES.computeIfAbsent(state, BlockMapColors::resolve);
        if (entry.color() == UNRESOLVED) {
            return vanilla;
        }
        if (mode == WorldBinderConfig.MapBlockColors.AUTO && !entry.custom()) {
            return vanilla;
        }
        if (entry.tinted() && !entry.custom()) {
            // Grass, leaves, water: the texture is grayscale and biome tinted, the vanilla color fits better.
            return vanilla == 0 ? entry.color() : vanilla;
        }
        return entry.color();
    }

    /**
     * Color of invisible marker blocks (barrier, light, structure void), 0 for everything else. These blocks are
     * invisible in game, so the map shows them only when "Invisible blocks" is enabled.
     */
    public static int markerColor(BlockState state) {
        if (state == null) return 0;
        net.minecraft.world.level.block.Block block = state.getBlock();
        if (block == net.minecraft.world.level.block.Blocks.BARRIER) return 0xFFD9534F;
        if (block == net.minecraft.world.level.block.Blocks.LIGHT) return 0xFFF2CF5B;
        if (block == net.minecraft.world.level.block.Blocks.STRUCTURE_VOID) return 0xFFA98BE0;
        return 0;
    }

    /**
     * True for blocks the vanilla map looks through: invisible markers and every block without a map color
     * (glass, panes, iron bars …). The map then shows the block below, exactly like a vanilla map item.
     */
    public static boolean isMapTransparent(BlockState state, BlockGetter level, BlockPos pos) {
        if (state == null || state.isAir() || markerColor(state) != 0) {
            return true;
        }
        try {
            MapColor mapColor = state.getMapColor(level, pos);
            return mapColor == null || mapColor == MapColor.NONE;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /** True when the state is drawn from a resource pack override (used by the map hover details). */
    public static boolean isCustom(BlockState state) {
        Entry entry = STATES.get(state);
        return entry != null && entry.custom();
    }

    private static int vanilla(BlockState state, BlockGetter level, BlockPos pos) {
        try {
            MapColor mapColor = state.getMapColor(level, pos);
            if (mapColor != null && mapColor != MapColor.NONE && mapColor.col != 0) {
                return 0xFF000000 | mapColor.col;
            }
        } catch (RuntimeException ignored) {
            // Some modded states compute map colors from world data that is not loaded client-side.
        }
        return NO_MAP_COLOR;
    }

    // ------------------------------------------------------------------ resolving

    private static Entry resolve(BlockState state) {
        try {
            ResourceManager resources = Minecraft.getInstance().getResourceManager();
            String command = BlockStateStrings.toCommandString(state);
            int bracket = command.indexOf('[');
            Identifier blockId = Identifier.parse(bracket < 0 ? command : command.substring(0, bracket));
            Map<String, String> properties = properties(bracket < 0 ? "" : command.substring(bracket + 1, command.length() - 1));
            Optional<Blockstate> blockstate = BLOCKSTATES.computeIfAbsent(blockId.toString(), id -> loadBlockstate(resources, blockId));
            if (blockstate.isEmpty()) {
                return new Entry(UNRESOLVED, 0);
            }
            boolean custom = blockstate.get().custom();
            String model = selectModel(blockstate.get().json(), properties);
            if (model == null) {
                return new Entry(UNRESOLVED, 0);
            }
            Face face = MODELS.computeIfAbsent(model, id -> resolveModel(resources, id));
            if (face.texture() == null) {
                return new Entry(UNRESOLVED, 0);
            }
            int[] texture = TEXTURES.computeIfAbsent(face.texture(), id -> averageTexture(resources, id));
            custom |= face.custom() || texture[1] != 0;
            int flags = (custom ? FLAG_CUSTOM : 0) | (face.tinted() ? FLAG_TINTED : 0);
            return new Entry(texture[0], flags);
        } catch (Exception exception) {
            WorldBinder.LOGGER.debug("Could not resolve map texture color for {}", state, exception);
            return new Entry(UNRESOLVED, 0);
        }
    }

    private static Optional<Blockstate> loadBlockstate(ResourceManager resources, Identifier blockId) {
        Identifier file = Identifier.fromNamespaceAndPath(blockId.getNamespace(), "blockstates/" + blockId.getPath() + ".json");
        Optional<Resource> resource = resources.getResource(file);
        if (resource.isEmpty()) {
            return Optional.empty();
        }
        try (Reader reader = resource.get().openAsReader()) {
            return Optional.of(new Blockstate(JsonParser.parseReader(reader).getAsJsonObject(), isPackOverride(resource.get())));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private static Map<String, String> properties(String list) {
        Map<String, String> map = new HashMap<>();
        if (list.isEmpty()) return map;
        for (String pair : list.split(",")) {
            int eq = pair.indexOf('=');
            if (eq > 0) map.put(pair.substring(0, eq), pair.substring(eq + 1));
        }
        return map;
    }

    /** Picks the model for the given properties from a {@code variants} or {@code multipart} blockstate file. */
    public static String selectModel(JsonObject root, Map<String, String> properties) {
        if (root.has("variants")) {
            JsonObject variants = root.getAsJsonObject("variants");
            String fallback = null;
            for (Map.Entry<String, JsonElement> variant : variants.entrySet()) {
                String model = modelOf(variant.getValue());
                if (fallback == null) fallback = model;
                if (variantMatches(variant.getKey(), properties)) {
                    return model;
                }
            }
            return fallback;
        }
        if (root.has("multipart")) {
            String first = null;
            for (JsonElement part : root.getAsJsonArray("multipart")) {
                JsonObject object = part.getAsJsonObject();
                String model = modelOf(object.get("apply"));
                if (first == null) first = model;
                if (!object.has("when") || conditionMatches(object.getAsJsonObject("when"), properties)) {
                    return model;
                }
            }
            return first;
        }
        return null;
    }

    private static String modelOf(JsonElement element) {
        if (element == null) return null;
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            return array.isEmpty() ? null : modelOf(array.get(0));
        }
        JsonObject object = element.getAsJsonObject();
        return object.has("model") ? object.get("model").getAsString() : null;
    }

    private static boolean variantMatches(String key, Map<String, String> properties) {
        if (key.isEmpty() || key.equals("normal")) return true;
        for (String pair : key.split(",")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            String value = properties.get(pair.substring(0, eq).trim());
            if (value == null || !value.equals(pair.substring(eq + 1).trim())) return false;
        }
        return true;
    }

    private static boolean conditionMatches(JsonObject when, Map<String, String> properties) {
        if (when.has("OR")) {
            for (JsonElement option : when.getAsJsonArray("OR")) {
                if (conditionMatches(option.getAsJsonObject(), properties)) return true;
            }
            return false;
        }
        if (when.has("AND")) {
            for (JsonElement option : when.getAsJsonArray("AND")) {
                if (!conditionMatches(option.getAsJsonObject(), properties)) return false;
            }
            return true;
        }
        for (Map.Entry<String, JsonElement> condition : when.entrySet()) {
            String value = properties.get(condition.getKey());
            String expected = condition.getValue().getAsString();
            boolean negate = expected.startsWith("!");
            boolean any = false;
            for (String option : (negate ? expected.substring(1) : expected).split("\\|")) {
                if (option.equals(value)) {
                    any = true;
                    break;
                }
            }
            if (any == negate) return false;
        }
        return true;
    }

    /** Follows the parent chain and returns the texture of the largest upward face (or a sensible fallback). */
    private static Face resolveModel(ResourceManager resources, String modelId) {
        Map<String, String> textures = new HashMap<>();
        JsonArray elements = null;
        boolean custom = false;
        String current = modelId;
        for (int depth = 0; depth < MAX_PARENT_DEPTH && current != null; depth++) {
            Identifier id = Identifier.parse(current);
            if (id.getPath().startsWith("builtin/")) break;
            Optional<Resource> resource = resources.getResource(Identifier.fromNamespaceAndPath(id.getNamespace(), "models/" + id.getPath() + ".json"));
            if (resource.isEmpty()) break;
            custom |= isPackOverride(resource.get());
            JsonObject model;
            try (Reader reader = resource.get().openAsReader()) {
                model = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (Exception exception) {
                break;
            }
            if (model.has("textures")) {
                for (Map.Entry<String, JsonElement> texture : model.getAsJsonObject("textures").entrySet()) {
                    if (texture.getValue().isJsonPrimitive()) {
                        textures.putIfAbsent(texture.getKey(), texture.getValue().getAsString());
                    } else if (texture.getValue().isJsonObject() && texture.getValue().getAsJsonObject().has("sprite")) {
                        textures.putIfAbsent(texture.getKey(), texture.getValue().getAsJsonObject().get("sprite").getAsString());
                    }
                }
            }
            if (elements == null && model.has("elements")) {
                elements = model.getAsJsonArray("elements");
            }
            current = model.has("parent") ? model.get("parent").getAsString() : null;
        }
        String reference = null;
        boolean tinted = false;
        if (elements != null) {
            double bestArea = -1;
            for (JsonElement element : elements) {
                JsonObject object = element.getAsJsonObject();
                if (!object.has("faces")) continue;
                JsonObject faces = object.getAsJsonObject("faces");
                JsonObject face = faces.has("up") ? faces.getAsJsonObject("up") : null;
                double area = 0.0D;
                if (face != null && object.has("from") && object.has("to")) {
                    JsonArray from = object.getAsJsonArray("from");
                    JsonArray to = object.getAsJsonArray("to");
                    area = Math.abs(to.get(0).getAsDouble() - from.get(0).getAsDouble()) * Math.abs(to.get(2).getAsDouble() - from.get(2).getAsDouble());
                }
                if (face == null) {
                    // Cross models (plants) have no up face: use their first face with a lower priority.
                    for (Map.Entry<String, JsonElement> any : faces.entrySet()) {
                        face = any.getValue().getAsJsonObject();
                        break;
                    }
                    area = 0.01D;
                }
                if (face != null && face.has("texture") && area > bestArea) {
                    bestArea = area;
                    reference = face.get("texture").getAsString();
                    tinted = face.has("tintindex");
                }
            }
        }
        if (reference == null) {
            for (String key : List.of("top", "up", "end", "all", "texture", "cross", "plant", "side", "particle")) {
                if (textures.containsKey(key)) {
                    reference = "#" + key;
                    break;
                }
            }
        }
        String texture = reference;
        for (int i = 0; i < 8 && texture != null && texture.startsWith("#"); i++) {
            texture = textures.get(texture.substring(1));
        }
        if (texture != null && texture.startsWith("#")) texture = null;
        return new Face(texture, tinted, custom);
    }

    /** Returns {averageArgb, overriddenFlag}; averageArgb is 0 when the texture could not be read. */
    private static int[] averageTexture(ResourceManager resources, String textureId) {
        try {
            Identifier id = Identifier.parse(textureId);
            Optional<Resource> resource = resources.getResource(Identifier.fromNamespaceAndPath(id.getNamespace(), "textures/" + id.getPath() + ".png"));
            if (resource.isEmpty()) return new int[] {0, 0};
            long r = 0, g = 0, b = 0, count = 0;
            try (InputStream input = resource.get().open(); NativeImage image = NativeImage.read(input)) {
                int w = image.getWidth();
                int h = Math.min(image.getHeight(), w); // first animation frame
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        int argb = image.getPixel(x, y);
                        if ((argb >>> 24) < 128) continue;
                        r += (argb >> 16) & 255;
                        g += (argb >> 8) & 255;
                        b += argb & 255;
                        count++;
                    }
                }
            }
            if (count == 0) return new int[] {0, 0};
            int color = 0xFF000000 | (int) (r / count) << 16 | (int) (g / count) << 8 | (int) (b / count);
            return new int[] {color, isPackOverride(resource.get()) ? 1 : 0};
        } catch (Exception exception) {
            return new int[] {0, 0};
        }
    }

    private static boolean isPackOverride(Resource resource) {
        try {
            String pack = resource.sourcePackId();
            return pack != null && !pack.equals("vanilla") && !pack.equals("fabric") && !pack.startsWith("fabric-");
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
