package net.arthonetwork.donation.listeners;

import net.arthonetwork.donation.ArthoPlugin;
import net.arthonetwork.donation.utils.AuthManager;
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

    private final ArthoPlugin plugin;
    private final AuthManager authManager;

    public AuthListener(ArthoPlugin plugin, AuthManager authManager) {
        this.plugin = plugin;
        this.authManager = authManager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLogin(PlayerLoginEvent event) {
        if (authManager.isWhitelistEnabled()) {
            if (!authManager.isWhitelisted(event.getPlayer().getName())) {
                event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, plugin.getWhitelistMessage());
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        authManager.logout(player.getUniqueId()); // Ensure fresh login

        if (!authManager.isRegistered(player.getUniqueId())) {
            player.sendMessage(plugin.getAuthMessage("not-registered"));
            player.sendMessage(plugin.getAuthMessage("register-instruction"));
            player.sendTitle(plugin.getAuthMessage("title"), plugin.getAuthMessage("subtitle-register"), 10, 100, 20);
        } else {
            player.sendMessage(plugin.getAuthMessage("not-logged-in"));
            player.sendMessage(plugin.getAuthMessage("login-instruction"));
            player.sendTitle(plugin.getAuthMessage("title"), plugin.getAuthMessage("subtitle-login"), 10, 100, 20);
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
            // Optimize: Only check if block position changed
            if (event.getFrom().getBlockX() != event.getTo().getBlockX() ||
                    event.getFrom().getBlockZ() != event.getTo().getBlockZ() ||
                    event.getFrom().getBlockY() != event.getTo().getBlockY()) {
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
            event.getPlayer().sendMessage(plugin.getAuthMessage("not-logged-in"));
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
            player.sendMessage(plugin.getAuthMessage("not-logged-in"));
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
