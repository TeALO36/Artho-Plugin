package net.arthonetwork.donation.listeners;

import net.arthonetwork.donation.utils.TeleportManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class TeleportListener implements Listener {

    private final TeleportManager teleportManager;

    public TeleportListener(TeleportManager teleportManager) {
        this.teleportManager = teleportManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (!teleportManager.hasPendingTeleport(player)) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();

        if (to == null)
            return;

        // Only cancel if player actually moved (not just head rotation)
        double distance = Math.sqrt(
                Math.pow(to.getX() - from.getX(), 2) +
                        Math.pow(to.getY() - from.getY(), 2) +
                        Math.pow(to.getZ() - from.getZ(), 2));

        if (distance > 0.1) { // Threshold for movement
            teleportManager.cancelTeleport(player, "cancelled-moved");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();

        if (teleportManager.hasPendingTeleport(player)) {
            teleportManager.cancelTeleport(player, "cancelled-damage");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Cancel any pending teleport when player disconnects
        if (teleportManager.hasPendingTeleport(player)) {
            TeleportManager.PendingTeleport pending = teleportManager.getPendingTeleport(player);
            if (pending != null && pending.getTask() != null) {
                pending.getTask().cancel();
            }
        }
    }
}
