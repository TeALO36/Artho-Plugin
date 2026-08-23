package net.arthonetwork.donation.variants;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;

/**
 * Periodically raycasts every online player's view to detect the "First
 * Sight" moment: the first time they lay eyes on a specific variant-tagged
 * entity, within range and with a clear line of sight (no blocks in the way).
 */
public class FirstSightTask extends BukkitRunnable {

    private final java.util.Random random = new java.util.Random();

    private final VariantManager variantManager;
    private final SeenTracker seenTracker;
    private final double maxDistance;
    /**
     * Thickness added around the ray. A zero-width ray almost never connects with
     * small, fast entities such as bees, so "looking at" one would rarely trigger.
     */
    private final double raySize;

    public FirstSightTask(VariantManager variantManager, SeenTracker seenTracker, double maxDistance,
            double raySize) {
        this.variantManager = variantManager;
        this.seenTracker = seenTracker;
        this.maxDistance = maxDistance;
        this.raySize = raySize;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            checkPlayer(player);
        }
    }

    private void checkPlayer(Player player) {
        Location eye = player.getEyeLocation();

        RayTraceResult entityHit = player.getWorld().rayTraceEntities(eye, eye.getDirection(), maxDistance,
                raySize, entity -> !entity.equals(player) && variantManager.getVariantId(entity) != null);

        if (entityHit == null || entityHit.getHitEntity() == null) {
            return;
        }

        Entity hit = entityHit.getHitEntity();
        double distanceToEntity = eye.toVector().distance(entityHit.getHitPosition());

        // Block line-of-sight: don't trigger through walls.
        RayTraceResult blockHit = player.getWorld().rayTraceBlocks(eye, eye.getDirection(), distanceToEntity,
                FluidCollisionMode.NEVER, true);
        if (blockHit != null) {
            return;
        }

        Variant variant = variantManager.getVariantOf(hit);
        if (variant == null || !variant.isFirstSightEnabled()) {
            return;
        }

        if (seenTracker.hasSeen(player, hit.getUniqueId())) {
            return;
        }

        seenTracker.markSeen(player, hit.getUniqueId());
        Variant.SoundDef sound = variant.getFirstSight();
        // Played to this player only: each player gets their own audio, never shared.
        player.playSound(hit.getLocation(), sound.key, sound.category, sound.volume, sound.rollPitch(random));
    }
}
