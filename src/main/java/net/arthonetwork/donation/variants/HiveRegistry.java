package net.arthonetwork.donation.variants;

import net.arthonetwork.donation.ArthoPlugin;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Remembers, per beehive, whether that hive won its variant roll - so every bee
 * belonging to the hive shares the same outcome.
 * <p>
 * Rolls are kept in the plugin's own data file, keyed by world and coordinates.
 * They are deliberately NOT stored in the hive block's PersistentDataContainer:
 * writing a block state back with {@code BlockState#update()} re-applies a
 * snapshot taken earlier, which for a beehive also carries the bees stored
 * inside it - and this code runs exactly when a bee is leaving the hive, so it
 * could revert the hive's occupancy.
 */
public class HiveRegistry {

    private final ArthoPlugin plugin;
    private final File file;
    private final YamlConfiguration config;
    private final Map<String, String> cache = new HashMap<>();
    private final Random random = new Random();
    private boolean saveScheduled = false;

    public HiveRegistry(ArthoPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "hive-rolls.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
        for (String key : config.getKeys(false)) {
            cache.put(key, config.getString(key, ""));
        }
    }

    private static String keyOf(Location loc) {
        return loc.getWorld().getName() + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ();
    }

    /**
     * Whether this hive is marked for the given variant, rolling once
     * (1-in-chance) the first time the hive is seen and caching the result.
     */
    public boolean isHiveMarked(Location hive, String variantId, int chance) {
        String key = keyOf(hive);
        String stored = cache.get(key);
        if (stored != null) {
            return stored.equals(variantId);
        }

        boolean win = random.nextInt(Math.max(1, chance)) == 0;
        String value = win ? variantId : "";
        cache.put(key, value);
        config.set(key, value);
        scheduleSave();
        return win;
    }

    /** Batches writes: many bees can spawn in the same tick from one hive. */
    private void scheduleSave() {
        if (saveScheduled) {
            return;
        }
        saveScheduled = true;
        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            saveScheduled = false;
            try {
                config.save(file);
            } catch (IOException e) {
                plugin.getLogger().severe("[Variants] Impossible d'enregistrer hive-rolls.yml: " + e.getMessage());
            }
        }, 100L);
    }
}
