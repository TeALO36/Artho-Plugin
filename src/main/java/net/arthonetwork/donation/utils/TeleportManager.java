package net.arthonetwork.donation.utils;

import net.arthonetwork.donation.ArthoPlugin;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TeleportManager {

    private final ArthoPlugin plugin;

    // Active TPA requests: target UUID -> requester UUID
    private final Map<UUID, UUID> tpaRequests = new HashMap<>();
    private final Map<UUID, Long> tpaRequestTimes = new HashMap<>();

    // Pending teleports with countdown
    private final Map<UUID, PendingTeleport> pendingTeleports = new HashMap<>();

    // Cooldowns: player UUID -> last teleport time
    private final Map<UUID, Long> tpaCooldowns = new HashMap<>();
    private final Map<UUID, Long> homeCooldowns = new HashMap<>();

    public TeleportManager(ArthoPlugin plugin) {
        this.plugin = plugin;
    }

    // ==================== TPA SYSTEM ====================

    public void sendTpaRequest(Player requester, Player target) {
        // Check cooldown
        long cooldown = getRemainingCooldown(requester, "tpa");
        if (cooldown > 0) {
            requester.sendMessage(getMessage("cooldown").replace("%time%", String.valueOf(cooldown)));
            return;
        }

        // Check if already has pending request
        if (tpaRequests.containsValue(requester.getUniqueId())) {
            requester.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&',
                    "&cVous avez déjà une demande en attente. Utilisez /tpcancel pour l'annuler."));
            return;
        }

        // Store request
        tpaRequests.put(target.getUniqueId(), requester.getUniqueId());
        tpaRequestTimes.put(target.getUniqueId(), System.currentTimeMillis());

        // Notify requester
        requester.sendMessage(getMessage("tpa-sent").replace("%player%", target.getName()));

        // Notify target with clickable buttons
        sendClickableRequest(target, requester);

        // Schedule expiration
        int expireTime = plugin.getConfig().getInt("teleport.tpa-expire", 60);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (tpaRequests.containsKey(target.getUniqueId()) &&
                        tpaRequests.get(target.getUniqueId()).equals(requester.getUniqueId())) {
                    tpaRequests.remove(target.getUniqueId());
                    tpaRequestTimes.remove(target.getUniqueId());
                    if (requester.isOnline()) {
                        requester.sendMessage(getMessage("tpa-expired"));
                    }
                    if (target.isOnline()) {
                        target.sendMessage(getMessage("tpa-expired"));
                    }
                }
            }
        }.runTaskLater(plugin, expireTime * 20L);
    }

    private void sendClickableRequest(Player target, Player requester) {
        // Main message
        target.sendMessage(getMessage("tpa-received").replace("%player%", requester.getName()));

        // Create clickable buttons
        TextComponent accept = new TextComponent(" [✔ Accepter] ");
        accept.setColor(ChatColor.GREEN);
        accept.setBold(true);
        accept.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpaccept " + requester.getName()));
        accept.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text("§aCliquez pour accepter la demande")));

        TextComponent deny = new TextComponent("[✘ Refuser]");
        deny.setColor(ChatColor.RED);
        deny.setBold(true);
        deny.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpdeny " + requester.getName()));
        deny.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text("§cCliquez pour refuser la demande")));

        TextComponent message = new TextComponent("");
        message.addExtra(accept);
        message.addExtra(deny);
        target.spigot().sendMessage(message);
    }

    public void acceptTpa(Player target, String requesterName) {
        UUID requesterId = null;

        if (requesterName != null) {
            Player requester = Bukkit.getPlayer(requesterName);
            if (requester != null && tpaRequests.get(target.getUniqueId()) != null &&
                    tpaRequests.get(target.getUniqueId()).equals(requester.getUniqueId())) {
                requesterId = requester.getUniqueId();
            }
        } else {
            requesterId = tpaRequests.get(target.getUniqueId());
        }

        if (requesterId == null) {
            target.sendMessage(getMessage("tpa-no-request"));
            return;
        }

        Player requester = Bukkit.getPlayer(requesterId);
        if (requester == null || !requester.isOnline()) {
            target.sendMessage(getMessage("tpa-no-request"));
            tpaRequests.remove(target.getUniqueId());
            return;
        }

        tpaRequests.remove(target.getUniqueId());
        tpaRequestTimes.remove(target.getUniqueId());

        target.sendMessage(getMessage("tpa-accepted"));
        requester.sendMessage(getMessage("tpa-accepted"));

        // Start teleport with warmup
        startTeleport(requester, target.getLocation(), "tpa");
    }

    public void denyTpa(Player target, String requesterName) {
        UUID requesterId = tpaRequests.get(target.getUniqueId());

        if (requesterId == null) {
            target.sendMessage(getMessage("tpa-no-request"));
            return;
        }

        Player requester = Bukkit.getPlayer(requesterId);
        tpaRequests.remove(target.getUniqueId());
        tpaRequestTimes.remove(target.getUniqueId());

        target.sendMessage(getMessage("tpa-denied"));
        if (requester != null && requester.isOnline()) {
            requester.sendMessage(getMessage("tpa-denied"));
        }
    }

    public void cancelTpa(Player requester) {
        UUID targetId = null;
        for (Map.Entry<UUID, UUID> entry : tpaRequests.entrySet()) {
            if (entry.getValue().equals(requester.getUniqueId())) {
                targetId = entry.getKey();
                break;
            }
        }

        if (targetId == null) {
            requester.sendMessage(getMessage("tpa-no-request"));
            return;
        }

        tpaRequests.remove(targetId);
        tpaRequestTimes.remove(targetId);
        requester.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&',
                "&7Demande annulée."));
    }

    // ==================== TELEPORT WITH WARMUP ====================

    public void startTeleport(Player player, Location destination, String type) {
        int warmup = plugin.getConfig().getInt("teleport.warmup", 5);
        Location startLocation = player.getLocation().clone();

        // Show title
        player.sendTitle(
                org.bukkit.ChatColor.translateAlternateColorCodes('&', getMessage("warmup-title")),
                org.bukkit.ChatColor.translateAlternateColorCodes('&', getMessage("warmup-subtitle")),
                10, 70, 20);

        PendingTeleport pending = new PendingTeleport(player, destination, startLocation, type, warmup);
        pendingTeleports.put(player.getUniqueId(), pending);

        // Countdown task
        BukkitTask task = new BukkitRunnable() {
            int remaining = warmup;

            @Override
            public void run() {
                if (!pendingTeleports.containsKey(player.getUniqueId())) {
                    cancel();
                    return;
                }

                if (remaining <= 0) {
                    // Teleport!
                    completeTeleport(player, destination, type);
                    cancel();
                    return;
                }

                // Show actionbar countdown
                String actionbar = getMessage("warmup-actionbar").replace("%time%", String.valueOf(remaining));
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent(org.bukkit.ChatColor.translateAlternateColorCodes('&', actionbar)));

                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);

        pending.setTask(task);
    }

    public void completeTeleport(Player player, Location destination, String type) {
        pendingTeleports.remove(player.getUniqueId());
        player.teleport(destination);
        player.sendMessage(getMessage("success"));

        // Set cooldown
        if (type.equals("tpa")) {
            tpaCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        } else if (type.equals("home")) {
            homeCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    public void cancelTeleport(Player player, String reason) {
        PendingTeleport pending = pendingTeleports.remove(player.getUniqueId());
        if (pending != null) {
            pending.getTask().cancel();
            player.sendMessage(getMessage(reason));
            // Clear title
            player.resetTitle();
        }
    }

    public boolean hasPendingTeleport(Player player) {
        return pendingTeleports.containsKey(player.getUniqueId());
    }

    public PendingTeleport getPendingTeleport(Player player) {
        return pendingTeleports.get(player.getUniqueId());
    }

    // ==================== COOLDOWN SYSTEM ====================

    public long getRemainingCooldown(Player player, String type) {
        Map<UUID, Long> cooldowns = type.equals("tpa") ? tpaCooldowns : homeCooldowns;
        int cooldownSeconds = plugin.getConfig().getInt("teleport.cooldowns." + type, 30);

        Long lastUse = cooldowns.get(player.getUniqueId());
        if (lastUse == null)
            return 0;

        long elapsed = (System.currentTimeMillis() - lastUse) / 1000;
        long remaining = cooldownSeconds - elapsed;
        return Math.max(0, remaining);
    }

    // ==================== UTILITIES ====================

    private String getMessage(String key) {
        String msg = plugin.getConfig().getString("teleport.messages." + key, "&cMessage manquant: " + key);
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', msg);
    }

    // Inner class for pending teleports
    public static class PendingTeleport {
        private final Player player;
        private final Location destination;
        private final Location startLocation;
        private final String type;
        private final int warmup;
        private BukkitTask task;

        public PendingTeleport(Player player, Location destination, Location startLocation, String type, int warmup) {
            this.player = player;
            this.destination = destination;
            this.startLocation = startLocation;
            this.type = type;
            this.warmup = warmup;
        }

        public Player getPlayer() {
            return player;
        }

        public Location getDestination() {
            return destination;
        }

        public Location getStartLocation() {
            return startLocation;
        }

        public String getType() {
            return type;
        }

        public int getWarmup() {
            return warmup;
        }

        public BukkitTask getTask() {
            return task;
        }

        public void setTask(BukkitTask task) {
            this.task = task;
        }
    }
}
