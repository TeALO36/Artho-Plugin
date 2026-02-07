package net.arthonetwork.donation.listeners;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class DeathListener implements Listener {

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Location loc = player.getLocation();

        String worldName = loc.getWorld() != null ? loc.getWorld().getName() : "unknown";
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        // Envoyer les coordonnées au joueur après un léger délai (pour qu'il les voie
        // après respawn)
        player.getServer().getScheduler().runTaskLater(
                player.getServer().getPluginManager().getPlugin("Artho-Plugin"),
                () -> {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            "&c☠ &7Vous êtes mort en &f" + worldName + " &7aux coordonnées:"));
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            "&c☠ &eX: &f" + x + " &eY: &f" + y + " &eZ: &f" + z));
                },
                20L // 1 seconde après respawn
        );
    }
}
