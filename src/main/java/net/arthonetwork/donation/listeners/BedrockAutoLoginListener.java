package net.arthonetwork.donation.listeners;

import net.arthonetwork.donation.ArthoPlugin;
import net.arthonetwork.donation.utils.AuthManager;
import net.arthonetwork.donation.utils.LinkManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.potion.PotionEffectType;
import org.geysermc.floodgate.api.FloodgateApi;

/**
 * Auto-logs in Bedrock players who already linked their account via /linkaccount,
 * bypassing Artho-Plugin's normal register/login gate (AuthListener) for them.
 * Registered at default (NORMAL) priority so it runs after AuthListener's
 * LOWEST-priority PlayerJoinEvent handler, which applies the blindness/prompt first.
 */
public class BedrockAutoLoginListener implements Listener {

    private final ArthoPlugin plugin;
    private final AuthManager authManager;
    private final LinkManager linkManager;

    public BedrockAutoLoginListener(ArthoPlugin plugin, AuthManager authManager, LinkManager linkManager) {
        this.plugin = plugin;
        this.authManager = authManager;
        this.linkManager = linkManager;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        if (!isFloodgateAvailable()) {
            return;
        }

        Player player = event.getPlayer();
        if (!FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId())) {
            return;
        }

        if (!linkManager.isBedrockLinked(player.getUniqueId())) {
            return;
        }

        authManager.login(player.getUniqueId());
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.sendMessage(ChatColor.GREEN + "✔ Compte Bedrock reconnu, connexion automatique effectuée.");
    }

    private boolean isFloodgateAvailable() {
        try {
            return plugin.getServer().getPluginManager().isPluginEnabled("floodgate")
                    && FloodgateApi.getInstance() != null;
        } catch (Throwable t) {
            return false;
        }
    }
}
