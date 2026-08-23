package net.arthonetwork.donation.variants;

import net.arthonetwork.donation.ArthoPlugin;
import org.bukkit.event.HandlerList;
import org.bukkit.scheduler.BukkitTask;

/**
 * Orchestrates the whole "Linked Variants" module as a single toggleable
 * feature. Disabled by default (features.linked-variants.enabled: false in
 * config.yml): when disabled, no listener is registered and no raycast task
 * runs at all - zero overhead, not just hidden behind a flag check.
 */
public class LinkedVariantsFeature {

    private final ArthoPlugin plugin;
    private final VariantManager variantManager;
    private final SeenTracker seenTracker;
    private final RigManager rigManager;
    private VariantPlayerListener playerListener;
    private VariantSpawnListener spawnListener;
    private PlayerSoundListener playerSoundListener;
    private VariantInteractionListener interactionListener;
    private BukkitTask firstSightTask;
    private BukkitTask ambientTask;
    private BukkitTask displaySyncTask;
    private BukkitTask rigTask;
    private boolean running = false;

    public LinkedVariantsFeature(ArthoPlugin plugin) {
        this.plugin = plugin;
        this.variantManager = new VariantManager(plugin);
        this.seenTracker = new SeenTracker(plugin);
        this.rigManager = new RigManager(plugin);
    }

    /** Starts the module if enabled in config.yml. Safe to call once at plugin startup. */
    public void init() {
        if (isConfigEnabled()) {
            start();
        }
    }

    public void shutdown() {
        stop();
    }

    public boolean isRunning() {
        return running;
    }

    public boolean enable() {
        plugin.getConfig().set("features.linked-variants.enabled", true);
        plugin.saveConfig();
        if (!running) {
            start();
        }
        return true;
    }

    public boolean disable() {
        plugin.getConfig().set("features.linked-variants.enabled", false);
        plugin.saveConfig();
        if (running) {
            stop();
        }
        return true;
    }

    public VariantManager getVariantManager() {
        return variantManager;
    }

    public RigManager getRigManager() {
        return rigManager;
    }

    public PlayerSoundListener getPlayerSoundListener() {
        return playerSoundListener;
    }

    /**
     * Re-reads config.yml and the variants/ catalogue with no restart. The
     * player sound triggers snapshot their configuration at construction (the
     * move event fires far too often to re-read it), so the listener has to be
     * rebuilt rather than mutated in place.
     */
    public void reloadConfiguration() {
        plugin.reloadConfig();
        variantManager.reload();
        if (!running) {
            return;
        }
        if (playerSoundListener != null) {
            HandlerList.unregisterAll(playerSoundListener);
        }
        playerSoundListener = new PlayerSoundListener(plugin);
        plugin.getServer().getPluginManager().registerEvents(playerSoundListener, plugin);
    }

    public SeenTracker getSeenTracker() {
        return seenTracker;
    }

    private boolean isConfigEnabled() {
        return plugin.getConfig().getBoolean("features.linked-variants.enabled", false);
    }

    private void start() {
        variantManager.reload();
        variantManager.setRigManager(rigManager);

        playerListener = new VariantPlayerListener(seenTracker);
        plugin.getServer().getPluginManager().registerEvents(playerListener, plugin);

        spawnListener = new VariantSpawnListener(plugin, variantManager, new HiveRegistry(plugin));
        plugin.getServer().getPluginManager().registerEvents(spawnListener, plugin);

        playerSoundListener = new PlayerSoundListener(plugin);
        plugin.getServer().getPluginManager().registerEvents(playerSoundListener, plugin);

        interactionListener = new VariantInteractionListener(variantManager, rigManager);
        plugin.getServer().getPluginManager().registerEvents(interactionListener, plugin);

        double maxDistance = plugin.getConfig().getDouble("features.linked-variants.first-sight.max-distance", 10);
        long intervalTicks = plugin.getConfig().getLong("features.linked-variants.first-sight.check-interval-ticks",
                10);

        double raySize = plugin.getConfig().getDouble("features.linked-variants.first-sight.ray-size", 0.75);
        FirstSightTask task = new FirstSightTask(variantManager, seenTracker, maxDistance, raySize);
        firstSightTask = task.runTaskTimer(plugin, intervalTicks, intervalTicks);

        ambientTask = new AmbientSoundTask(variantManager).runTaskTimer(plugin, 20L, 20L);
        displaySyncTask = new DisplaySyncTask().runTaskTimer(plugin, 5L, 2L);
        // Le rig doit etre recalcule a chaque tick pour que la marche soit fluide.
        rigTask = plugin.getServer().getScheduler().runTaskTimer(plugin, rigManager::tickAll, 5L, 1L);

        running = true;
        plugin.getLogger().info("[Variants] Module Variantes Liées activé.");
    }

    private void stop() {
        if (firstSightTask != null) {
            firstSightTask.cancel();
            firstSightTask = null;
        }
        if (ambientTask != null) {
            ambientTask.cancel();
            ambientTask = null;
        }
        if (displaySyncTask != null) {
            displaySyncTask.cancel();
            displaySyncTask = null;
        }
        if (rigTask != null) {
            rigTask.cancel();
            rigTask = null;
        }
        rigManager.removeAll();
        if (playerListener != null) {
            HandlerList.unregisterAll(playerListener);
            playerListener = null;
        }
        if (spawnListener != null) {
            HandlerList.unregisterAll(spawnListener);
            spawnListener = null;
        }
        if (playerSoundListener != null) {
            HandlerList.unregisterAll(playerSoundListener);
            playerSoundListener = null;
        }
        if (interactionListener != null) {
            HandlerList.unregisterAll(interactionListener);
            interactionListener = null;
        }
        running = false;
        plugin.getLogger().info("[Variants] Module Variantes Liées désactivé.");
    }
}
