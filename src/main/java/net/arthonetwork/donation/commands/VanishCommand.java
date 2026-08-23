package net.arthonetwork.donation.commands;

import net.arthonetwork.donation.ArthoPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Admin vanish: the player is hidden from everyone else and a regular leave
 * message is broadcast, so from the outside the session looks exactly like a
 * disconnection. Hiding through {@link Player#hidePlayer} also drops the entry
 * from the tab list, which is what makes the illusion hold.
 *
 * <p>State is intentionally in-memory only: a vanish must never outlive the
 * session that started it, otherwise an admin reconnects invisible without
 * realising it.
 */
public class VanishCommand implements CommandExecutor, Listener {

    /** Mirrors the vanilla wording so the broadcast is indistinguishable. */
    private static final String LEFT = " a quitté la partie";
    private static final String JOINED = " a rejoint la partie";

    private final ArthoPlugin plugin;
    private final Set<UUID> vanished = new HashSet<>();

    public VanishCommand(ArthoPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isVanished(Player player) {
        return vanished.contains(player.getUniqueId());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Seuls les joueurs peuvent utiliser cette commande.");
            return true;
        }
        Player player = (Player) sender;
        if (isVanished(player)) {
            reveal(player);
        } else {
            conceal(player);
        }
        return true;
    }

    private void conceal(Player player) {
        vanished.add(player.getUniqueId());
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) {
                other.hidePlayer(plugin, player);
            }
        }
        Bukkit.broadcastMessage(ChatColor.YELLOW + player.getName() + LEFT);
        player.sendMessage(ChatColor.GRAY + "Tu es maintenant invisible et absent de la liste des joueurs. "
                + ChatColor.YELLOW + "/vanish" + ChatColor.GRAY + " pour réapparaître.");
    }

    private void reveal(Player player) {
        vanished.remove(player.getUniqueId());
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) {
                other.showPlayer(plugin, player);
            }
        }
        Bukkit.broadcastMessage(ChatColor.YELLOW + player.getName() + JOINED);
        player.sendMessage(ChatColor.GRAY + "Tu es de nouveau visible.");
    }

    /**
     * A player who connects afterwards knows nothing about the vanish, so the
     * hidden players have to be re-hidden for that new client specifically.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        for (UUID id : vanished) {
            Player hidden = Bukkit.getPlayer(id);
            if (hidden != null && !hidden.equals(event.getPlayer())) {
                event.getPlayer().hidePlayer(plugin, hidden);
            }
        }
    }

    /**
     * A vanished player already had a leave message broadcast when they hid;
     * letting the real one through would announce the same departure twice.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        if (vanished.remove(event.getPlayer().getUniqueId())) {
            event.setQuitMessage(null);
        }
    }
}
