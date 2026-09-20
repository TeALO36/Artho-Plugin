package net.arthonetwork.donation.utils;

import net.arthonetwork.donation.ArthoPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Business logic of account linking, shared by the commands and by the startup
 * reconciliation so both take exactly the same steps.
 *
 * <p>Linking is recorded in two places that must never disagree: the plugin's own
 * {@link LinkManager} file, and Floodgate's link database, which is what makes a
 * linked Bedrock player log in AS the Java account. Every mutation goes through
 * here so the two stay in step, and a failure on one side is rolled back on the
 * other.
 */
public class AccountLinkService {

    /** What handing the Bedrock character over to the Java account changed. */
    public static final class Result {
        public final List<String> movedHomes = new ArrayList<>();
        public final List<String> skippedHomes = new ArrayList<>();
    }

    private final ArthoPlugin plugin;
    private final LinkManager linkManager;
    private final FloodgateLinkBridge bridge;
    private final HomeManager homeManager;

    public AccountLinkService(ArthoPlugin plugin, LinkManager linkManager, FloodgateLinkBridge bridge,
            HomeManager homeManager) {
        this.plugin = plugin;
        this.linkManager = linkManager;
        this.bridge = bridge;
        this.homeManager = homeManager;
    }

    /** Seconds a linked Bedrock player is given to read the result before being reconnected. */
    public int reconnectDelaySeconds() {
        return Math.max(1, plugin.getConfig().getInt("features.account-link.reconnect-delay", 4));
    }

    /**
     * Registers the link locally and in Floodgate, then hands the Bedrock account's
     * homes over and backs up its character. Completes on the main thread; on
     * failure nothing is left half-linked.
     */
    public CompletableFuture<Result> link(UUID bedrockUuid, UUID javaUuid, String bedrockName, String javaName) {
        CompletableFuture<Result> done = new CompletableFuture<>();
        linkManager.link(bedrockUuid, javaUuid, bedrockName, javaName);

        CompletableFuture<Void> registered;
        try {
            registered = bridge.link(bedrockUuid, javaUuid, javaName);
        } catch (Throwable t) {
            registered = new CompletableFuture<>();
            registered.completeExceptionally(t);
        }
        registered.whenComplete((v, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (error != null) {
                linkManager.unlink(bedrockUuid);
                done.completeExceptionally(error);
                return;
            }
            done.complete(activate(bedrockUuid, javaUuid));
        }));
        return done;
    }

    /** Removes the link in Floodgate first (that is what changes identities), then locally. */
    public CompletableFuture<Void> unlink(UUID bedrockUuid) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        CompletableFuture<Void> removed;
        try {
            removed = bridge.unlink(bedrockUuid);
        } catch (Throwable t) {
            removed = new CompletableFuture<>();
            removed.completeExceptionally(t);
        }
        removed.whenComplete((v, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (error != null) {
                // The link is still effective in Floodgate: keep our record consistent with it.
                done.completeExceptionally(error);
                return;
            }
            linkManager.unlink(bedrockUuid);
            done.complete(null);
        }));
        return done;
    }

    /**
     * Steps that go with a link becoming effective, run once per link: the old
     * Bedrock character is backed up (after the player has been reconnected, so
     * the save reflects everything they had) and its homes are handed over.
     */
    private Result activate(UUID bedrockUuid, UUID javaUuid) {
        Result result = new Result();

        if (plugin.getConfig().getBoolean("features.account-link.migrate-homes", true)) {
            HomeManager.Migration migration = homeManager.migrateHomes(bedrockUuid, javaUuid);
            result.movedHomes.addAll(migration.moved);
            result.skippedHomes.addAll(migration.skipped);
        }

        if (plugin.getConfig().getBoolean("features.account-link.backup-on-link", true)) {
            long delay = 20L * (reconnectDelaySeconds() + 3);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                try {
                    File saved = PlayerDataBackup.backup(plugin, bedrockUuid);
                    plugin.getLogger().info("[Link] Ancien personnage Bedrock " + bedrockUuid + " sauvegarde"
                            + (saved != null ? " dans " + saved.getPath() : " (aucune donnee a sauvegarder)"));
                } catch (IOException e) {
                    plugin.getLogger().warning("[Link] Sauvegarde de " + bedrockUuid + " impossible : " + e.getMessage());
                }
            }, delay);
        }

        linkManager.markActivated(bedrockUuid);
        return result;
    }

    /**
     * Replays every recorded link into Floodgate. A link created before Floodgate's
     * database existed (or after it was wiped) would otherwise be honoured by no
     * one, and the player would keep logging in as a separate Bedrock character.
     */
    public void reconcile() {
        if (!bridge.isReady()) {
            plugin.getLogger().warning("[Link] La liaison native de Floodgate est indisponible : les comptes lies "
                    + "ne changeront pas d'identite a la connexion. Verifiez player-link dans floodgate/config.yml.");
            return;
        }
        List<LinkManager.Entry> entries = linkManager.entries();
        for (LinkManager.Entry entry : entries) {
            CompletableFuture<Void> registered;
            try {
                registered = bridge.link(entry.bedrockUuid, entry.javaUuid, entry.javaUsername);
            } catch (Throwable t) {
                plugin.getLogger().warning("[Link] Rapprochement de " + entry.bedrockName + " impossible : " + t);
                continue;
            }
            registered.whenComplete((v, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (error != null) {
                    plugin.getLogger().warning("[Link] Rapprochement de " + entry.bedrockName + " -> "
                            + entry.javaUsername + " echoue : " + error);
                    return;
                }
                if (!entry.activated) {
                    Result r = activate(entry.bedrockUuid, entry.javaUuid);
                    plugin.getLogger().info("[Link] " + entry.bedrockName + " -> " + entry.javaUsername
                            + " pris en charge : " + r.movedHomes.size() + " home(s) transmis"
                            + (r.skippedHomes.isEmpty() ? "" : ", " + r.skippedHomes.size() + " ignore(s)"));
                }
            }));
        }
        plugin.getLogger().info("[Link] " + entries.size() + " lien(s) enregistre(s) verifie(s) aupres de Floodgate.");
    }
}
