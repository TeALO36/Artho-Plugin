package net.arthonetwork.donation.listeners;

import net.arthonetwork.donation.ArthoPlugin;
import net.arthonetwork.donation.utils.AuthManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lightweight speed/fly hack detector. Alert-only by default: notifies
 * online ops and logs to console, but never auto-kicks/bans. A homemade
 * movement check has too many legitimate false-positive triggers (elytra,
 * potions, vehicles, lag spikes) to be trusted with automatic punishment.
 */
public class SpeedFlyListener implements Listener {

    private static final double MAX_SPEED_BLOCKS_PER_SEC = 12.0; // generous margin above sprint speed (~5.6)
    private static final long FLY_SUSPICION_MS = 3000;
    private static final long ALERT_COOLDOWN_MS = 10000;

    private final ArthoPlugin plugin;
    private final AuthManager authManager;
    private final Map<UUID, Long> lastCheck = new HashMap<>();
    private final Map<UUID, Long> airborneSince = new HashMap<>();
    private final Map<UUID, Long> lastAlert = new HashMap<>();
    private final Map<UUID, Integer> violations = new HashMap<>();

    public SpeedFlyListener(ArthoPlugin plugin, AuthManager authManager) {
        this.plugin = plugin;
        this.authManager = authManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!isEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        if (shouldSkip(player)) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (to == null || from.getWorld() != to.getWorld()) {
            lastCheck.put(uuid, now);
            return;
        }

        Long last = lastCheck.put(uuid, now);
        checkSpeed(player, from, to, last, now);
        checkFly(player, now);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastCheck.remove(uuid);
        airborneSince.remove(uuid);
        lastAlert.remove(uuid);
        violations.remove(uuid);
    }

    private void checkSpeed(Player player, Location from, Location to, Long last, long now) {
        if (last == null) {
            return;
        }
        long deltaMs = now - last;
        if (deltaMs <= 0) {
            return;
        }

        double blocksPerSec = from.distance(to) / (deltaMs / 1000.0);
        if (blocksPerSec > MAX_SPEED_BLOCKS_PER_SEC
                && !player.isFlying()
                && !player.isGliding()
                && player.getVehicle() == null) {
            flag(player, "speed", String.format("~%.1f blocs/s", blocksPerSec));
        }
    }

    private void checkFly(Player player, long now) {
        UUID uuid = player.getUniqueId();
        boolean suspicious = !player.isOnGround()
                && !player.isFlying()
                && !player.isGliding()
                && !player.hasPotionEffect(PotionEffectType.LEVITATION)
                && !player.hasPotionEffect(PotionEffectType.SLOW_FALLING)
                && player.getVehicle() == null
                && player.getGameMode() != GameMode.CREATIVE
                && player.getGameMode() != GameMode.SPECTATOR;

        if (!suspicious) {
            airborneSince.remove(uuid);
            return;
        }

        long since = airborneSince.computeIfAbsent(uuid, k -> now);
        if (now - since > FLY_SUSPICION_MS) {
            flag(player, "fly", "en l'air depuis " + ((now - since) / 1000) + "s sans raison apparente");
            airborneSince.put(uuid, now); // avoid re-alerting every tick while still airborne
        }
    }

    private boolean shouldSkip(Player player) {
        return player.hasPermission("arthoplugin.anticheat.bypass") || !authManager.isLoggedIn(player.getUniqueId());
    }

    private void flag(Player player, String type, String detail) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = lastAlert.get(uuid);
        if (last != null && now - last < ALERT_COOLDOWN_MS) {
            return; // avoid spamming ops for the same ongoing violation
        }
        lastAlert.put(uuid, now);

        int count = violations.merge(uuid, 1, Integer::sum);
        String consoleMsg = "[Anticheat] " + player.getName() + " - suspicion " + type + ": " + detail
                + " (total: " + count + ")";
        plugin.getLogger().warning(consoleMsg);

        String opMsg = ChatColor.GOLD + "[AC] " + ChatColor.YELLOW + player.getName()
                + ChatColor.GRAY + " - suspicion " + type + " (" + detail + ") [x" + count + "]";
        for (Player op : Bukkit.getOnlinePlayers()) {
            if (op.isOp() || op.hasPermission("arthoplugin.admin")) {
                op.sendMessage(opMsg);
            }
        }
    }

    private boolean isEnabled() {
        return plugin.getConfig().getBoolean("anticheat.movement.enabled", true);
    }
}
