package net.worldbinder.scene;

import net.minecraft.client.Minecraft;
import net.worldbinder.WorldBinder;
import net.worldbinder.io.WorldBinderPaths;
import net.worldbinder.util.Lang;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class ServerResourcePackLocator {
    private static final Pattern UUID_PATTERN = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private ServerResourcePackLocator() {
    }

    public static Optional<Path> findBestPack() {
        Optional<Path> active = findActiveServerPack();
        if (active.isPresent()) {
            return active;
        }
        return findLatestLikelyPack();
    }

    private static Optional<Path> findActiveServerPack() {
        Set<String> selectedIds = selectedPackIds();
        for (String id : selectedIds) {
            Optional<Path> byServerId = findByServerPackId(id);
            if (byServerId.isPresent()) {
                return byServerId;
            }
        }
        return Optional.empty();
    }

    private static Optional<Path> findByServerPackId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }

        Set<String> tokens = new LinkedHashSet<>();
        Matcher matcher = UUID_PATTERN.matcher(id);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }

        String cleaned = id.replace('\\', '/');
        int lastSlash = cleaned.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < cleaned.length() - 1) {
            tokens.add(cleaned.substring(lastSlash + 1));
        }

        for (String token : tokens) {
            Optional<Path> match = findDownloadPackContaining(token);
            if (match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }

    private static Optional<Path> findDownloadPackContaining(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Path downloads = WorldBinderPaths.GAME_DIR.resolve("downloads");
        if (!Files.isDirectory(downloads)) {
            return Optional.empty();
        }

        try (Stream<Path> stream = Files.walk(downloads, 4)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(ServerResourcePackLocator::isLikelyPack)
                    .filter(path -> path.toString().contains(token))
                    .max(Comparator.comparingLong(ServerResourcePackLocator::lastModified));
        } catch (IOException exception) {
            WorldBinder.LOGGER.warn(Lang.string("worldbinder.log.resource_pack.downloads_inspect_failed"), exception);
            return Optional.empty();
        }
    }

    private static Optional<Path> findLatestLikelyPack() {
        Path[] roots = {
                WorldBinderPaths.GAME_DIR.resolve("server-resource-packs"),
                WorldBinderPaths.GAME_DIR.resolve("downloads"),
                WorldBinderPaths.GAME_DIR.resolve("resourcepacks")
        };

        Optional<Path> latest = Optional.empty();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> stream = Files.walk(root, 4)) {
                Optional<Path> candidate = stream
                        .filter(Files::isRegularFile)
                        .filter(ServerResourcePackLocator::isLikelyPack)
                        .max(Comparator.comparingLong(ServerResourcePackLocator::lastModified));
                if (candidate.isPresent() && (latest.isEmpty() || lastModified(candidate.get()) > lastModified(latest.get()))) {
                    latest = candidate;
                }
            } catch (IOException exception) {
                WorldBinder.LOGGER.warn(Lang.string("worldbinder.log.resource_pack.folder_inspect_failed", root), exception);
            }
        }
        return latest;
    }

    private static Set<String> selectedPackIds() {
        Set<String> ids = new LinkedHashSet<>();
        try {
            Minecraft minecraft = Minecraft.getInstance();
            Object repository = invokeAny(minecraft, "getResourcePackRepository", "getDownloadedPackSource", "getResourceManager");
            if (repository != null) {
                collectPackIds(repository, ids, 0);
            }
        } catch (RuntimeException exception) {
            WorldBinder.LOGGER.debug(Lang.string("worldbinder.log.resource_pack.selected_inspect_failed"), exception);
        }
        return ids;
    }

    private static void collectPackIds(Object value, Set<String> ids, int depth) {
        if (value == null || depth > 4) {
            return;
        }
        if (value instanceof CharSequence text) {
            String id = text.toString();
            if (id.contains("server") || id.contains("download") || UUID_PATTERN.matcher(id).find()) {
                ids.add(id);
            }
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                collectPackIds(item, ids, depth + 1);
            }
            return;
        }

        Object selected = invokeAny(value, "getSelectedPacks", "getSelected", "selected", "listSelectedIds");
        if (selected != null && selected != value) {
            collectPackIds(selected, ids, depth + 1);
        }

        Object id = invokeAny(value, "getId", "id", "packId", "getPackId", "location", "getLocation");
        if (id != null && id != value) {
            collectPackIds(id, ids, depth + 1);
        }

        if (depth < 2) {
            for (Field field : value.getClass().getDeclaredFields()) {
                if (!String.class.equals(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    collectPackIds(field.get(value), ids, depth + 1);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                }
            }
        }
    }

    private static Object invokeAny(Object target, String... names) {
        if (target == null) {
            return null;
        }
        for (String name : names) {
            try {
                Method method = target.getClass().getMethod(name);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return null;
    }

    private static boolean isLikelyPack(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".json") || name.endsWith(".txt") || name.endsWith(".log") || name.endsWith(".png") || name.endsWith(".corrupt")) {
            return false;
        }
        return name.endsWith(".zip") || !name.contains(".");
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            return 0L;
        }
    }
}
