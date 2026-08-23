package net.arthonetwork.donation.variants;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Random;

/**
 * Plays a variant's looping ambient sound (e.g. an insect buzz) to each nearby
 * player individually, so the audio is never shared between players.
 */
public class AmbientSoundTask extends BukkitRunnable {

    private final VariantManager variantManager;
    private final Random random = new Random();
    private long tick = 0;

    public AmbientSoundTask(VariantManager variantManager) {
        this.variantManager = variantManager;
    }

    @Override
    public void run() {
        tick += 20;
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (Entity nearby : player.getNearbyEntities(24, 24, 24)) {
                Variant variant = variantManager.getVariantOf(nearby);
                if (variant == null || !variant.isAmbientEnabled()) {
                    continue;
                }
                int interval = Math.max(20, variant.getAmbientIntervalTicks());
                // Offset per entity so several mobs of the same variant don't
                // all fire on the same tick (avoids a "chorus" effect).
                long offset = Math.floorMod(nearby.getUniqueId().getLeastSignificantBits(), interval / 20) * 20L;
                if ((tick + offset) % interval != 0) {
                    continue;
                }
                if (player.getLocation().distanceSquared(nearby.getLocation()) >
                        variant.getAmbientRange() * variant.getAmbientRange()) {
                    continue;
                }
                Variant.SoundDef s = variant.getAmbient();
                player.playSound(nearby.getLocation(), s.key, s.category, s.volume, s.rollPitch(random));
            }
        }
    }
}
