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
    private VariantPlayerListener playerListener;
    private VariantSpawnListener spawnListener;
    private PlayerSoundListener playerSoundListener;
    private VariantInteractionListener interactionListener;
    private BukkitTask firstSightTask;
    private BukkitTask ambientTask;
    private boolean running = false;

    public LinkedVariantsFeature(ArthoPlugin plugin) {
        this.plugin = plugin;
        this.variantManager = new VariantManager(plugin);
        this.seenTracker = new SeenTracker(plugin);
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

    public SeenTracker getSeenTracker() {
        return seenTracker;
    }

    private boolean isConfigEnabled() {
        return plugin.getConfig().getBoolean("features.linked-variants.enabled", false);
    }

    private void start() {
        variantManager.reload();

        playerListener = new VariantPlayerListener(seenTracker);
        plugin.getServer().getPluginManager().registerEvents(playerListener, plugin);

        spawnListener = new VariantSpawnListener(plugin, variantManager, new HiveRegistry(plugin));
        plugin.getServer().getPluginManager().registerEvents(spawnListener, plugin);

        playerSoundListener = new PlayerSoundListener(plugin);
        plugin.getServer().getPluginManager().registerEvents(playerSoundListener, plugin);

        interactionListener = new VariantInteractionListener(variantManager);
        plugin.getServer().getPluginManager().registerEvents(interactionListener, plugin);

        double maxDistance = plugin.getConfig().getDouble("features.linked-variants.first-sight.max-distance", 10);
        long intervalTicks = plugin.getConfig().getLong("features.linked-variants.first-sight.check-interval-ticks",
                10);

        FirstSightTask task = new FirstSightTask(variantManager, seenTracker, maxDistance);
        firstSightTask = task.runTaskTimer(plugin, intervalTicks, intervalTicks);

        ambientTask = new AmbientSoundTask(variantManager).runTaskTimer(plugin, 20L, 20L);

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
