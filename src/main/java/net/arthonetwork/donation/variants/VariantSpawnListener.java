package net.arthonetwork.donation.variants;

import net.arthonetwork.donation.ArthoPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.List;
import java.util.Random;

/**
 * Rolls each variant's configured spawn chance when a creature spawns naturally.
 * <p>
 * Bees are special-cased: the roll is made once per hive rather than per bee, so
 * an entire hive is marked or none of it is - matching "the whole hive, not a
 * single bee".
 */
public class VariantSpawnListener implements Listener {

    private final ArthoPlugin plugin;
    private final VariantManager variantManager;
    private final HiveRegistry hiveRegistry;
    private final Random random = new Random();

    public VariantSpawnListener(ArthoPlugin plugin, VariantManager variantManager, HiveRegistry hiveRegistry) {
        this.plugin = plugin;
        this.variantManager = variantManager;
        this.hiveRegistry = hiveRegistry;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        Entity entity = event.getEntity();
        List<Variant> candidates = variantManager.getSpawnableVariants(entity.getType());
        if (candidates.isEmpty()) {
            return;
        }

        for (Variant variant : candidates) {
            if (roll(entity, variant)) {
                variantManager.assign(entity, variant);
                return;
            }
            // Not chosen: make sure this entity doesn't wear the reserved look anyway.
            variantManager.avoidReservedVariant(entity, variant.getNativeVariant());
        }
    }

    private boolean roll(Entity entity, Variant variant) {
        if (entity instanceof Bee) {
            // Ask the bee which hive it belongs to rather than scanning blocks.
            Location hive = ((Bee) entity).getHive();
            if (hive != null) {
                return hiveRegistry.isHiveMarked(hive, variant.getId(), variant.getSpawnProbability());
            }
            // Hive-less bee (wandering): fall back to an individual roll.
        }
        return random.nextDouble() < variant.getSpawnProbability();
    }
}
