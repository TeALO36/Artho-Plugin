package net.arthonetwork.donation.variants;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Keeps each attached {@link ItemDisplay} facing the same way as the entity it
 * rides. Riding synchronises position but not rotation, so without this the
 * model stays locked to the heading it had when it was created while the entity
 * turns underneath it.
 * <p>
 * Only displays near online players are touched, so the cost scales with what is
 * actually visible rather than with the number of entities in the world.
 */
public class DisplaySyncTask extends BukkitRunnable {

    private static final double RANGE = 48;

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (Entity nearby : player.getNearbyEntities(RANGE, RANGE, RANGE)) {
                if (!(nearby instanceof ItemDisplay)) {
                    continue;
                }
                Entity vehicle = nearby.getVehicle();
                if (vehicle == null) {
                    continue;
                }
                float yaw = vehicle.getLocation().getYaw();
                if (Math.abs(yaw - nearby.getLocation().getYaw()) < 1.0f) {
                    continue; // already aligned: skip the packet
                }
                // Interpolate so the model turns smoothly instead of snapping.
                ((ItemDisplay) nearby).setInterpolationDuration(3);
                ((ItemDisplay) nearby).setInterpolationDelay(0);
                nearby.setRotation(yaw, 0f);
            }
        }
    }
}
