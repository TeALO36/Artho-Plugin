package net.arthonetwork.donation.utils;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Persistence for one of the plugin's YAML data files.
 *
 * <p>The managers used to save by reloading the file from disk, copying every key
 * of their in-memory config onto that fresh copy, and writing it on an async
 * task. That can never delete anything - a key removed in memory is still in the
 * reloaded copy, so /delhome, unlinking an account, removing a suggestion or
 * unregistering a player were all lost at the next restart - and two overlapping
 * async writes could interleave on the same file.
 *
 * <p>Here the in-memory configuration is the single source of truth. It is
 * rendered to a string on the calling thread, so the snapshot is consistent, then
 * written by ONE worker thread, in order, to a temp file that atomically replaces
 * the real one: a crash can never leave a half-written data file behind.
 */
public final class YamlStore {

    private final File file;
    private final Logger log;
    private final ExecutorService writer;

    public YamlStore(File file, Logger log) {
        this.file = file;
        this.log = log;
        this.writer = Executors.newSingleThreadExecutor(r -> new Thread(r, "artho-yaml-" + file.getName()));
    }

    /** Loads the file, creating it (and its folder) when missing. */
    public YamlConfiguration load() {
        try {
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            if (!file.exists()) {
                file.createNewFile();
            }
        } catch (IOException e) {
            log.severe("Impossible de creer " + file.getName() + " : " + e.getMessage());
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    /** Snapshots {@code config} now and queues the write; returns immediately. */
    public void save(YamlConfiguration config) {
        final String snapshot = config.saveToString();
        try {
            writer.execute(() -> write(snapshot));
        } catch (RejectedExecutionException closed) {
            // Saving after flush() (e.g. an event racing the plugin shutdown):
            // losing the change would be worse than blocking for a moment.
            write(snapshot);
        }
    }

    private synchronized void write(String content) {
        Path target = file.toPath();
        Path tmp = target.resolveSibling(file.getName() + ".tmp");
        try {
            Files.write(tmp, content.getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.severe("Impossible d'ecrire " + file.getName() + " : " + e.getMessage());
        }
    }

    /** Blocks until every queued write is on disk. Call from onDisable(). */
    public void flush() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(15, TimeUnit.SECONDS)) {
                log.warning("Ecritures de " + file.getName() + " encore en cours apres 15s.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
