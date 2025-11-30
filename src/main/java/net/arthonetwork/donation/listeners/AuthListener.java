package net.arthonetwork.donation.listeners;

import net.arthonetwork.donation.utils.AuthManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class AuthListener implements Listener {

    private final AuthManager authManager;

    public AuthListener(AuthManager authManager) {
        this.authManager = authManager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLogin(PlayerLoginEvent event) {
        if (authManager.isWhitelistEnabled()) {
            if (!authManager.isWhitelisted(event.getPlayer().getName())) {
                event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST,
                        ChatColor.RED + "Vous n'êtes pas sur la whitelist !");
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        authManager.logout(player.getUniqueId()); // Ensure fresh login

        if (!authManager.isRegistered(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "⚠ Vous devez vous enregistrer !");
            player.sendMessage(ChatColor.YELLOW + "/register <motdepasse> <confirmation>");
        } else {
            player.sendMessage(ChatColor.RED + "⚠ Vous devez vous connecter !");
            player.sendMessage(ChatColor.YELLOW + "/login <motdepasse>");
        }

        // Apply blindness
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 1));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        authManager.logout(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!authManager.isLoggedIn(player.getUniqueId())) {
            // Prevent movement but allow looking around
            if (event.getFrom().getX() != event.getTo().getX() ||
                    event.getFrom().getZ() != event.getTo().getZ() ||
                    event.getFrom().getY() != event.getTo().getY()) {
                event.setTo(event.getFrom());
            }
        } else {
            // Remove blindness if logged in
            if (player.hasPotionEffect(PotionEffectType.BLINDNESS)) {
                player.removePotionEffect(PotionEffectType.BLINDNESS);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!authManager.isLoggedIn(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "Connectez-vous d'abord !");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (authManager.isLoggedIn(player.getUniqueId()))
            return;

        String msg = event.getMessage().toLowerCase();
        if (!msg.startsWith("/login") && !msg.startsWith("/register")) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Connectez-vous d'abord !");
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!authManager.isLoggedIn(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!authManager.isLoggedIn(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!authManager.isLoggedIn(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
