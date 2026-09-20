package net.arthonetwork.donation.listeners;

import net.arthonetwork.donation.ArthoPlugin;
import net.arthonetwork.donation.utils.AuthManager;
import net.arthonetwork.donation.utils.FloodgateLinkBridge;
import net.arthonetwork.donation.utils.LinkManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.potion.PotionEffectType;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import java.util.UUID;

/**
 * Logs in linked Bedrock players automatically, bypassing Artho-Plugin's
 * register/login gate (AuthListener) for them.
 *
 * <p>That is safe because a Bedrock connection is authenticated by Xbox Live and
 * relayed through Floodgate: its identity cannot be claimed by typing a name, the
 * way a cracked Java account's can. Java players keep using their password.
 *
 * <p>Registered after AuthListener at the same NORMAL priority, so its
 * PlayerJoinEvent handler runs second: AuthListener logs the player out and
 * applies the blindness/prompt first, and this then undoes both.
 */
public class BedrockAutoLoginListener implements Listener {

    private final ArthoPlugin plugin;
    private final AuthManager authManager;
    private final LinkManager linkManager;
    private final FloodgateLinkBridge bridge;

    public BedrockAutoLoginListener(ArthoPlugin plugin, AuthManager authManager, LinkManager linkManager,
            FloodgateLinkBridge bridge) {
        this.plugin = plugin;
        this.authManager = authManager;
        this.linkManager = linkManager;
        this.bridge = bridge;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        FloodgatePlayer session = bedrockSession(player);
        if (session == null) {
            return; // Java player: password as usual
        }

        // Once linked, the session's own Bukkit UUID is the Java account's, so
        // isFloodgatePlayer(uuid) would say "not Bedrock": the session is the
        // reliable way to tell, and it also carries the Bedrock account's real id.
        UUID bedrockUuid = session.getJavaUniqueId();
        boolean linkedInFloodgate = session.isLinked();
        if (!linkedInFloodgate && !linkManager.isBedrockLinked(bedrockUuid)) {
            return; // ordinary Bedrock account, never linked
        }

        if (!linkedInFloodgate) {
            // Our record says linked but Floodgate's database has no entry (wiped,
            // or the link predates it). Keep the player logged in as before so
            // they are not locked out, and repair the database for next time.
            repair(bedrockUuid);
        } else if (!authManager.isRegistered(player.getUniqueId())) {
            return; // no password to bypass: let the normal registration flow run
        }

        authManager.login(player.getUniqueId());
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.sendMessage(ChatColor.GREEN + (linkedInFloodgate
                ? "✔ Compte lié : vous jouez " + player.getName() + ". Connexion automatique effectuée."
                : "✔ Compte Bedrock reconnu, connexion automatique effectuée."));
    }

    private void repair(UUID bedrockUuid) {
        UUID javaUuid = linkManager.getLinkedJavaUuid(bedrockUuid);
        String javaName = linkManager.getLinkedJavaUsername(bedrockUuid);
        if (javaUuid == null || javaName == null || !bridge.isReady()) {
            return;
        }
        bridge.link(bedrockUuid, javaUuid, javaName).whenComplete((v, error) -> {
            if (error != null) {
                plugin.getLogger().warning("[Link] Reparation de Floodgate pour " + bedrockUuid + " impossible : " + error);
            } else {
                plugin.getLogger().info("[Link] Lien de " + bedrockUuid + " retabli dans Floodgate.");
            }
        });
    }

    private FloodgatePlayer bedrockSession(Player player) {
        try {
            return plugin.getServer().getPluginManager().isPluginEnabled("floodgate")
                    ? FloodgateApi.getInstance().getPlayer(player.getUniqueId())
                    : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
