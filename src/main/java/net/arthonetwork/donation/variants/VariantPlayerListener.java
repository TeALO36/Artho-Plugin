package net.arthonetwork.donation.variants;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Keeps the SeenTracker's in-memory cache warm while a player is online, and releases it on quit. */
public class VariantPlayerListener implements Listener {

    private final SeenTracker seenTracker;

    public VariantPlayerListener(SeenTracker seenTracker) {
        this.seenTracker = seenTracker;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        seenTracker.preload(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        seenTracker.unload(event.getPlayer());
    }
}
