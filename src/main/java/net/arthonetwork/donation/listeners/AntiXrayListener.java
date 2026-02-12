package net.arthonetwork.donation.listeners;

import net.arthonetwork.donation.ArthoPlugin;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AntiXrayListener implements Listener {

    private final ArthoPlugin plugin;
    private boolean enabled;

    // Tracking per player
    private final Map<UUID, PlayerMineData> mineData = new ConcurrentHashMap<>();

    // Ores to track
    private static final Set<Material> VALUABLE_ORES = new HashSet<>(Arrays.asList(
            Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
            Material.ANCIENT_DEBRIS,
            Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
            Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
            Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
            Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE));

    // High-value ores (trigger stricter checks)
    private static final Set<Material> HIGH_VALUE_ORES = new HashSet<>(Arrays.asList(
            Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
            Material.ANCIENT_DEBRIS));

    // Stone-like blocks (what miners break to get to ores)
    private static final Set<Material> STONE_BLOCKS = new HashSet<>(Arrays.asList(
            Material.STONE, Material.DEEPSLATE, Material.GRANITE, Material.DIORITE,
            Material.ANDESITE, Material.TUFF, Material.NETHERRACK,
            Material.COBBLESTONE, Material.BASALT, Material.BLACKSTONE));

    public AntiXrayListener(ArthoPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        this.enabled = plugin.getConfig().getBoolean("anticheat.antixray.enabled", false);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        plugin.getConfig().set("anticheat.antixray.enabled", enabled);
        plugin.saveConfig();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!enabled)
            return;

        Player player = event.getPlayer();

        // Skip admins/ops if configured
        if (plugin.getConfig().getBoolean("anticheat.antixray.ignore-ops", true) && player.isOp()) {
            return;
        }
        if (player.hasPermission("arthoplugin.antixray.bypass")) {
            return;
        }

        Block block = event.getBlock();
        Material type = block.getType();

        // Only track underground mining (below y=64 for overworld)
        if (block.getY() > plugin.getConfig().getInt("anticheat.antixray.max-y", 64)) {
            return;
        }

        // Track stone mining
        if (STONE_BLOCKS.contains(type)) {
            getOrCreate(player).addStone();
            return;
        }

        // Track ore mining
        if (VALUABLE_ORES.contains(type)) {
            PlayerMineData data = getOrCreate(player);
            data.addOre(type);

            // Check thresholds
            checkSuspicious(player, data, type);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Keep data for a while (don't clear immediately)
        // Data will be cleared when analyzed or on plugin disable
    }

    private PlayerMineData getOrCreate(Player player) {
        return mineData.computeIfAbsent(player.getUniqueId(), k -> new PlayerMineData());
    }

    private void checkSuspicious(Player player, PlayerMineData data, Material ore) {
        int windowMinutes = plugin.getConfig().getInt("anticheat.antixray.window-minutes", 10);
        long windowMs = windowMinutes * 60_000L;

        // Clean old data
        data.cleanOlderThan(windowMs);

        int totalStone = data.getStoneCount();
        int totalDiamonds = data.getHighValueCount();
        int totalOres = data.getOreCount();

        // Thresholds from config
        int diamondThreshold = plugin.getConfig().getInt("anticheat.antixray.thresholds.diamonds", 12);
        int oreRatioMinStone = plugin.getConfig().getInt("anticheat.antixray.thresholds.min-stone", 20);
        double maxOreRatio = plugin.getConfig().getDouble("anticheat.antixray.thresholds.max-ore-ratio", 0.4);

        boolean suspicious = false;
        String reason = "";

        // Check 1: Too many diamonds/emeralds in time window
        if (HIGH_VALUE_ORES.contains(ore) && totalDiamonds >= diamondThreshold) {
            suspicious = true;
            reason = totalDiamonds + " minerais précieux en " + windowMinutes + " min";
        }

        // Check 2: Ore-to-stone ratio is abnormally high
        if (!suspicious && totalStone >= oreRatioMinStone && totalOres > 0) {
            double ratio = (double) totalOres / totalStone;
            if (ratio > maxOreRatio) {
                suspicious = true;
                reason = "ratio minerais/pierre: " + String.format("%.1f%%", ratio * 100)
                        + " (" + totalOres + " minerais / " + totalStone + " pierres)";
            }
        }

        if (suspicious) {
            // Prevent spam: only alert every 30 seconds per player
            if (data.canAlert()) {
                alertAdmins(player, reason, ore, data);
                data.markAlerted();
            }
        }
    }

    private void alertAdmins(Player player, String reason, Material ore, PlayerMineData data) {
        String prefix = org.bukkit.ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("anticheat.antixray.messages.prefix",
                        "&c&l⚠ AntiXray &8»"));

        String alertMsg = prefix + " &e" + player.getName() + " &7est suspect: &f" + reason;
        String formatted = org.bukkit.ChatColor.translateAlternateColorCodes('&', alertMsg);

        plugin.getLogger().warning("[AntiXray] " + player.getName() + " suspect: " + reason
                + " | Dernier minerai: " + ore.name()
                + " @ " + player.getLocation().getBlockX()
                + "," + player.getLocation().getBlockY()
                + "," + player.getLocation().getBlockZ());

        // Send clickable message to admins
        for (Player admin : Bukkit.getOnlinePlayers()) {
            if (admin.hasPermission("arthoplugin.admin")) {
                TextComponent msg = new TextComponent(formatted);

                TextComponent tp = new TextComponent(" [📍 TP]");
                tp.setColor(ChatColor.AQUA);
                tp.setBold(true);
                tp.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                        "/tp " + admin.getName() + " " + player.getName()));
                tp.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new Text("§aSe téléporter vers " + player.getName())));

                TextComponent spectate = new TextComponent(" [👁 Spec]");
                spectate.setColor(ChatColor.GOLD);
                spectate.setBold(true);
                spectate.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                        "/gamemode spectator " + admin.getName()));
                spectate.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new Text("§ePasser en spectateur pour observer")));

                msg.addExtra(tp);
                msg.addExtra(spectate);
                admin.spigot().sendMessage(msg);
            }
        }
    }

    public void clearData() {
        mineData.clear();
    }

    public void clearData(UUID uuid) {
        mineData.remove(uuid);
    }

    // ==================== INNER CLASS ====================

    private static class PlayerMineData {
        private final List<Long> stoneTimes = new ArrayList<>();
        private final List<Long> oreTimes = new ArrayList<>();
        private final List<Long> highValueTimes = new ArrayList<>();
        private final Map<Material, Integer> oreCounts = new HashMap<>();
        private long lastAlert = 0;

        void addStone() {
            stoneTimes.add(System.currentTimeMillis());
        }

        void addOre(Material type) {
            oreTimes.add(System.currentTimeMillis());
            oreCounts.merge(type, 1, (a, b) -> a + b);

            if (HIGH_VALUE_ORES.contains(type)) {
                highValueTimes.add(System.currentTimeMillis());
            }
        }

        void cleanOlderThan(long windowMs) {
            long cutoff = System.currentTimeMillis() - windowMs;
            stoneTimes.removeIf(t -> t < cutoff);
            oreTimes.removeIf(t -> t < cutoff);
            highValueTimes.removeIf(t -> t < cutoff);
        }

        int getStoneCount() {
            return stoneTimes.size();
        }

        int getOreCount() {
            return oreTimes.size();
        }

        int getHighValueCount() {
            return highValueTimes.size();
        }

        boolean canAlert() {
            return System.currentTimeMillis() - lastAlert > 30_000;
        }

        void markAlerted() {
            lastAlert = System.currentTimeMillis();
        }
    }
}
