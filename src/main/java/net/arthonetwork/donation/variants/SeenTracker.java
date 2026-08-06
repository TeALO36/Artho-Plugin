package net.arthonetwork.donation.variants;

import net.arthonetwork.donation.ArthoPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks, per player, which unique entity UUIDs they have already had a
 * "First Sight" trigger for - so it only ever fires once per player per entity.
 * <p>
 * The player's own PersistentDataContainer is the source of truth (durable
 * across restarts); an in-memory cache avoids re-parsing it on every raycast.
 * The set is capped and stored as dash-less hex so it can't grow without bound:
 * once the cap is reached the oldest sightings are dropped, which at worst lets
 * a very old entity trigger once more.
 */
public class SeenTracker {

    /** Maximum remembered sightings per player (~32 bytes each once encoded). */
    private static final int MAX_ENTRIES = 500;

    private final ArthoPlugin plugin;
    private final NamespacedKey seenKey;
    private final Map<UUID, Set<UUID>> cache = new HashMap<>();

    public SeenTracker(ArthoPlugin plugin) {
        this.plugin = plugin;
        this.seenKey = new NamespacedKey(plugin, "linked_variant_seen");
    }

    public boolean hasSeen(Player player, UUID entityUuid) {
        return getOrLoad(player).contains(entityUuid);
    }

    public void markSeen(Player player, UUID entityUuid) {
        Set<UUID> seen = getOrLoad(player);
        if (!seen.add(entityUuid)) {
            return;
        }
        // Insertion-ordered: evict the oldest entries once over the cap.
        while (seen.size() > MAX_ENTRIES) {
            Iterator<UUID> it = seen.iterator();
            it.next();
            it.remove();
        }
        persist(player, seen);
    }

    /** Warm the cache on join; release it on quit to bound memory use. */
    public void preload(Player player) {
        getOrLoad(player);
    }

    public void unload(Player player) {
        cache.remove(player.getUniqueId());
    }

    private Set<UUID> getOrLoad(Player player) {
        return cache.computeIfAbsent(player.getUniqueId(), uuid -> loadFromPdc(player));
    }

    private Set<UUID> loadFromPdc(Player player) {
        Set<UUID> result = new LinkedHashSet<>();
        String raw = player.getPersistentDataContainer().get(seenKey, PersistentDataType.STRING);
        if (raw == null || raw.isEmpty()) {
            return result;
        }
        for (String part : raw.split(",")) {
            if (part.length() != 32) {
                continue; // legacy or corrupted entry: skip rather than fail the load
            }
            try {
                result.add(new UUID(Long.parseUnsignedLong(part.substring(0, 16), 16),
                        Long.parseUnsignedLong(part.substring(16), 16)));
            } catch (NumberFormatException ignored) {
                // Not a valid encoded UUID.
            }
        }
        return result;
    }

    private void persist(Player player, Set<UUID> seen) {
        StringBuilder sb = new StringBuilder(seen.size() * 33);
        for (UUID uuid : seen) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(String.format("%016x%016x", uuid.getMostSignificantBits(), uuid.getLeastSignificantBits()));
        }
        player.getPersistentDataContainer().set(seenKey, PersistentDataType.STRING, sb.toString());
    }
}
