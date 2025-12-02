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
        boolean loggedIn = authManager.isLoggedIn(player.getUniqueId());
        boolean forceChange = authManager.isForceChange(player.getUniqueId());

        if (!loggedIn || forceChange) {
            // Optimize: Only check if block position changed
            if (event.getFrom().getBlockX() != event.getTo().getBlockX() ||
                    event.getFrom().getBlockZ() != event.getTo().getBlockZ() ||
                    event.getFrom().getBlockY() != event.getTo().getBlockY()) {
                event.setTo(event.getFrom());
            }
        } else {
            // Remove blindness if logged in AND not force change
            if (player.hasPotionEffect(PotionEffectType.BLINDNESS)) {
                player.removePotionEffect(PotionEffectType.BLINDNESS);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        boolean loggedIn = authManager.isLoggedIn(player.getUniqueId());
        boolean forceChange = authManager.isForceChange(player.getUniqueId());

        if (!loggedIn) {
            event.setCancelled(true);
            player.sendMessage(plugin.getAuthMessage("not-logged-in"));
        } else if (forceChange) {
            event.setCancelled(true);
            player.sendMessage(plugin.getAuthMessage("force-change-required"));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        String[] args = message.split(" ");
        String command = args[0].toLowerCase();

        // Handle console logging security
        if (command.equals("/login") || command.equals("/register") || command.equals("/changepassword")) {
            event.setCancelled(true);
            plugin.getLogger().info(event.getPlayer().getName() + " issued server command: " + command + " *****");

            String cmdName = command.substring(1);
            org.bukkit.command.PluginCommand pluginCommand = plugin.getCommand(cmdName);

            if (pluginCommand != null) {
                String[] cmdArgs = new String[args.length - 1];
                System.arraycopy(args, 1, cmdArgs, 0, args.length - 1);
                pluginCommand.execute(event.getPlayer(), cmdName, cmdArgs);
            }
            return; // Don't process further logic for these commands here
        }

        // Handle auth restrictions
        Player player = event.getPlayer();
        boolean loggedIn = authManager.isLoggedIn(player.getUniqueId());
        boolean forceChange = authManager.isForceChange(player.getUniqueId());

        if (!loggedIn) {
            event.setCancelled(true);
            player.sendMessage(plugin.getAuthMessage("not-logged-in"));
        } else if (forceChange) {
            if (!command.equals("/changepassword")) {
                event.setCancelled(true);
                player.sendMessage(plugin.getAuthMessage("force-change-required"));
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!authManager.isLoggedIn(event.getPlayer().getUniqueId())
                || authManager.isForceChange(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!authManager.isLoggedIn(event.getPlayer().getUniqueId())
                || authManager.isForceChange(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!authManager.isLoggedIn(event.getPlayer().getUniqueId())
                || authManager.isForceChange(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
