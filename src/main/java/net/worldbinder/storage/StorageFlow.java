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
        submit(library, scene, target, sceneArchive, false, onSuccess, onError);
    }

    /** Capture already announced this job before freezing. Do not reset its timer/progress or reopen its UI. */
    public static void submitPrepared(SceneLibrary library, WorldScene scene, Path target, boolean sceneArchive, Consumer<Path> onSuccess, Consumer<Throwable> onError) {
        submit(library, scene, target, sceneArchive, true, onSuccess, onError);
    }

    private static void submit(SceneLibrary library, WorldScene scene, Path target, boolean sceneArchive, boolean started, Consumer<Path> onSuccess, Consumer<Throwable> onError) {
        if (!started) PROGRESS.start(target, scene, false);
        EXECUTOR.execute(() -> {
            try {
                Path saved;
                PROGRESS.update(StorageStage.VALIDATE, net.worldbinder.util.Lang.stored("worldbinder.storage.detail.checking_target"), 0.08D);
                if (sceneArchive) {
                    PROGRESS.update(StorageStage.METADATA, net.worldbinder.util.Lang.stored("worldbinder.storage.detail.writing_archive"), 0.32D);
                    library.save(scene, target);
                    saved = target;
                } else {
                    saved = library.saveWorldFolder(scene, target, PROGRESS);
                }
                PROGRESS.finish(saved);
                if (onSuccess != null) onSuccess.accept(saved);
            } catch (IOException exception) {
                WorldBinder.LOGGER.error("WorldBinder StorageFlow failed", exception);
                PROGRESS.fail(net.worldbinder.util.Lang.stored("worldbinder.status.message", net.worldbinder.validation.Finding.detail(exception)));
                if (onError != null) onError.accept(exception);
            } catch (Throwable throwable) {
                WorldBinder.LOGGER.error("Unhandled WorldBinder StorageFlow error", throwable);
                PROGRESS.fail(net.worldbinder.util.Lang.stored("worldbinder.status.message", net.worldbinder.validation.Finding.detail(throwable)));
                if (onError != null) onError.accept(throwable);
            }
        });
    }
}
