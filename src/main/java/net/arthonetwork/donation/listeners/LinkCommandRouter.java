package net.arthonetwork.donation.listeners;

import net.arthonetwork.donation.ArthoPlugin;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.Arrays;
import java.util.Locale;

/**
 * Routes /linkaccount and /unlinkaccount to Artho-Plugin before the server's
 * command dispatcher sees them.
 *
 * <p>Floodgate registers commands of the same names as soon as its own linking is
 * enabled - and its linking database is what makes linked accounts work, so it has
 * to be. In the dispatcher its versions win over the ones declared in plugin.yml:
 * typing the command runs Floodgate's, or nothing at all ("Unknown command") for a
 * sender that one rejects, such as the console. Floodgate's database is needed,
 * its commands are not, so ours are dispatched by hand.
 *
 * <p>Registered after AuthListener at the same LOWEST priority, and ignoring
 * cancelled events: a player who is not logged in has already been stopped by
 * AuthListener, so they cannot reach /unlinkaccount through here.
 */
public class LinkCommandRouter implements Listener {

    private static final String NAMESPACE = "artho-plugin:";

    private final ArthoPlugin plugin;

    public LinkCommandRouter(ArthoPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (route(event.getPlayer(), event.getMessage().substring(1))) {
            event.setCancelled(true);
        }
    }

    /** Console and RCON (RemoteServerCommandEvent extends ServerCommandEvent). */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        if (route(event.getSender(), event.getCommand())) {
            event.setCancelled(true);
        }
    }

    private boolean route(CommandSender sender, String line) {
        String[] parts = line.trim().split("\\s+");
        String label = parts[0].toLowerCase(Locale.ROOT);
        if (label.startsWith(NAMESPACE)) {
            label = label.substring(NAMESPACE.length());
        }
        if (!label.equals("linkaccount") && !label.equals("unlinkaccount")) {
            return false;
        }
        PluginCommand command = plugin.getCommand(label);
        if (command == null) {
            return false;
        }
        command.execute(sender, label, Arrays.copyOfRange(parts, 1, parts.length));
        return true;
    }
}
