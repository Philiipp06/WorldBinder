package net.worldbinder.storage;

import net.worldbinder.WorldBinder;
import net.worldbinder.scene.SceneLibrary;
import net.worldbinder.scene.WorldScene;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class StorageFlow {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "WorldBinder StorageFlow");
        thread.setDaemon(true);
        return thread;
    });

    private static final StorageProgress PROGRESS = new StorageProgress();

    private StorageFlow() {}

    public static StorageProgress progress() {
        return PROGRESS;
    }

    public static void submit(SceneLibrary library, WorldScene scene, Path target, boolean sceneArchive, Consumer<Path> onSuccess, Consumer<Throwable> onError) {
        PROGRESS.start(target);
        EXECUTOR.execute(() -> {
            try {
                Path saved;
                PROGRESS.update(StorageStage.VALIDATE, "Checking target folder", 0.08D);
                if (sceneArchive) {
                    PROGRESS.update(StorageStage.METADATA, "Writing scene JSON", 0.32D);
                    library.save(scene, target);
                    saved = target;
                } else {
                    saved = library.saveWorldFolder(scene, target, PROGRESS);
                }
                PROGRESS.finish(saved);
                if (onSuccess != null) onSuccess.accept(saved);
            } catch (IOException exception) {
                WorldBinder.LOGGER.error("WorldBinder StorageFlow failed", exception);
                PROGRESS.fail(exception.getMessage());
                if (onError != null) onError.accept(exception);
            } catch (Throwable throwable) {
                WorldBinder.LOGGER.error("Unhandled WorldBinder StorageFlow error", throwable);
                PROGRESS.fail(throwable.getMessage());
                if (onError != null) onError.accept(throwable);
            }
        });
    }
}
