package net.arthonetwork.donation.utils;

import net.arthonetwork.donation.ArthoPlugin;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.link.PlayerLink;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Talks to Floodgate's own linking database.
 *
 * <p>Once a Bedrock account is registered there, Floodgate hands that player the
 * Java account's UUID and name at every connection. To the server they simply
 * are the Java player, so their homes, inventory, ender chest, experience,
 * advancements and position are the Java account's own: there is nothing to
 * synchronise, and so nothing that can drift out of sync.
 */
public class FloodgateLinkBridge {

    private final ArthoPlugin plugin;

    public FloodgateLinkBridge(ArthoPlugin plugin) {
        this.plugin = plugin;
    }

    /** True when Floodgate is running with its linking database loaded. */
    public boolean isReady() {
        try {
            if (!plugin.getServer().getPluginManager().isPluginEnabled("floodgate")) {
                return false;
            }
            PlayerLink link = link();
            return link != null && link.isEnabled();
        } catch (Throwable t) {
            return false;
        }
    }

    private PlayerLink link() {
        FloodgateApi api = FloodgateApi.getInstance();
        return api != null ? api.getPlayerLink() : null;
    }

    /** Idempotent: leaves an identical link alone and replaces a stale one. */
    public CompletableFuture<Void> link(UUID bedrockUuid, UUID javaUuid, String javaName) {
        PlayerLink link = link();
        return link.getLinkedPlayer(bedrockUuid).thenCompose(existing -> {
            if (existing == null) {
                return link.linkPlayer(bedrockUuid, javaUuid, javaName);
            }
            if (javaUuid.equals(existing.getJavaUniqueId())) {
                return CompletableFuture.<Void>completedFuture(null);
            }
            return link.unlinkPlayer(bedrockUuid).thenCompose(v -> link.linkPlayer(bedrockUuid, javaUuid, javaName));
        });
    }

    public CompletableFuture<Void> unlink(UUID bedrockUuid) {
        return link().unlinkPlayer(bedrockUuid);
    }

    public CompletableFuture<Boolean> isLinked(UUID bedrockUuid) {
        return link().isLinkedPlayer(bedrockUuid);
    }
}
