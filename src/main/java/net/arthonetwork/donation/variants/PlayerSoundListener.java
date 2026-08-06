package net.arthonetwork.donation.variants;

import net.arthonetwork.donation.ArthoPlugin;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Player-centric sound triggers that aren't tied to a specific entity:
 * <ul>
 *   <li>depth: crossing a configured Y threshold downwards (cave ambience)</li>
 *   <li>eat: consuming an item while above a health ratio</li>
 * </ul>
 * All thresholds, probabilities and sound keys come from config.yml and are
 * cached at construction - {@link PlayerMoveEvent} fires many times per second
 * per player, so it must not re-read the configuration.
 */
public class PlayerSoundListener implements Listener {

    /** Immutable snapshot of one trigger's configuration. */
    private static final class SoundConfig {
        boolean enabled;
        int chance;
        List<String> keys = Collections.emptyList();
        float volume = 1f;
        float pitch = 1f;
        SoundCategory category = SoundCategory.AMBIENT;
    }

    private final Random random = new Random();
    private final Map<UUID, Integer> lastY = new HashMap<>();

    private final SoundConfig depth = new SoundConfig();
    private final SoundConfig eat = new SoundConfig();
    private List<Integer> depthLevels = Collections.emptyList();
    private double eatMinHealthRatio = 0.75;
    private boolean eatFoodOnly = true;

    public PlayerSoundListener(ArthoPlugin plugin) {
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("features.linked-variants");
        if (root == null) {
            return;
        }
        load(root.getConfigurationSection("depth"), depth, "sound.key");
        load(root.getConfigurationSection("eat"), eat, "sound.keys");

        ConfigurationSection d = root.getConfigurationSection("depth");
        if (d != null) {
            depthLevels = new ArrayList<>(d.getIntegerList("levels"));
        }
        ConfigurationSection e = root.getConfigurationSection("eat");
        if (e != null) {
            eatMinHealthRatio = e.getDouble("min-health-ratio", 0.75);
            eatFoodOnly = e.getBoolean("food-only", true);
        }
    }

    private void load(ConfigurationSection sec, SoundConfig target, String keyPath) {
        if (sec == null) {
            return;
        }
        target.enabled = sec.getBoolean("enabled", false);
        target.chance = Math.max(1, sec.getInt("chance", 5));
        if (keyPath.endsWith("keys")) {
            target.keys = new ArrayList<>(sec.getStringList(keyPath));
        } else {
            String k = sec.getString(keyPath);
            target.keys = k == null ? Collections.emptyList() : Collections.singletonList(k);
        }
        target.volume = (float) sec.getDouble("sound.volume", 1.0);
        target.pitch = (float) sec.getDouble("sound.pitch", 1.0);
        try {
            target.category = SoundCategory.valueOf(
                    sec.getString("sound.category", "AMBIENT").trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            target.category = SoundCategory.AMBIENT;
        }
    }

    // ------------------------------------------------------------ depth / cave

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!depth.enabled || depth.keys.isEmpty() || event.getTo() == null) {
            return;
        }
        int toY = event.getTo().getBlockY();
        Integer prev = lastY.put(event.getPlayer().getUniqueId(), toY);
        if (prev == null || toY >= prev) {
            return; // only descending crossings count
        }
        for (int threshold : depthLevels) {
            // Fired exactly on the tick the player crosses the threshold downwards.
            if (prev > threshold && toY <= threshold) {
                if (random.nextInt(depth.chance) == 0) {
                    play(event.getPlayer(), depth);
                }
                return;
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastY.remove(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------- eating

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        if (!eat.enabled || eat.keys.isEmpty()) {
            return;
        }
        // PlayerItemConsumeEvent also covers potions and milk; food-only by default.
        if (eatFoodOnly && !event.getItem().getType().isEdible()) {
            return;
        }

        Player player = event.getPlayer();
        double max = maxHealth(player);
        if (max <= 0 || player.getHealth() / max <= eatMinHealthRatio) {
            return;
        }
        if (random.nextInt(eat.chance) != 0) {
            return;
        }
        play(player, eat);
    }

    /**
     * Max health via the legacy accessor: the Attribute enum constant was renamed
     * across versions, and this method stays valid on all of them.
     */
    @SuppressWarnings("deprecation")
    private double maxHealth(Player player) {
        return player.getMaxHealth();
    }

    // -------------------------------------------------------------------- utils

    private void play(Player player, SoundConfig cfg) {
        String key = cfg.keys.size() == 1 ? cfg.keys.get(0) : cfg.keys.get(random.nextInt(cfg.keys.size()));
        // Played to this player only, at their own location: never shared.
        player.playSound(player.getLocation(), key, cfg.category, cfg.volume, cfg.pitch);
    }
}
