package net.arthonetwork.donation.listeners;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPlayedBefore()) {
            sendAvailableCommands(player);
        }
    }

    public static void sendAvailableCommands(Player player) {
        player.sendMessage(ChatColor.GOLD + "--- Bienvenue sur ArthoNetwork ! ---");
        player.sendMessage(ChatColor.YELLOW + "Voici les commandes disponibles pour vous :");
        player.sendMessage(ChatColor.AQUA + "/ping " + ChatColor.WHITE + "- Voir votre latence.");
        player.sendMessage(ChatColor.AQUA + "/lag <joueur|serveur> " + ChatColor.WHITE + "- Vérifier les lags.");
        player.sendMessage(
                ChatColor.AQUA + "/server <add|list> " + ChatColor.WHITE + "- Suggérer des fonctionnalités.");

        if (player.isOp() || player.hasPermission("arthodonation.admin")) {
            sendOpCommands(player);
        }
    }

    public static void sendOpCommands(Player player) {
        player.sendMessage(ChatColor.RED + "--- Commandes Admin ---");
        player.sendMessage(
                ChatColor.LIGHT_PURPLE + "/don <add|link|reset|reload> " + ChatColor.WHITE + "- Gérer les dons.");
        player.sendMessage(
                ChatColor.LIGHT_PURPLE + "/server remove <id> " + ChatColor.WHITE + "- Supprimer une suggestion.");
    }
}
