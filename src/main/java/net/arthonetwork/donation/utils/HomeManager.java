package net.arthonetwork.donation.utils;

import net.arthonetwork.donation.ArthoPlugin;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class HomeManager {

    private final ArthoPlugin plugin;
    private File homesFile;
    private FileConfiguration homesConfig;
    private final TeleportManager teleportManager;

    public HomeManager(ArthoPlugin plugin, TeleportManager teleportManager) {
        this.plugin = plugin;
        this.teleportManager = teleportManager;
        loadHomesFile();
    }

    private void loadHomesFile() {
        homesFile = new File(plugin.getDataFolder(), "homes.yml");
        if (!homesFile.exists()) {
            try {
                homesFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Impossible de créer homes.yml: " + e.getMessage());
            }
        }
        homesConfig = YamlConfiguration.loadConfiguration(homesFile);
    }

    private void saveHomesFile() {
        // Async save to prevent lag
        final org.bukkit.configuration.file.YamlConfiguration configCopy = YamlConfiguration
                .loadConfiguration(homesFile);
        for (String key : homesConfig.getKeys(true)) {
            configCopy.set(key, homesConfig.get(key));
        }
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                configCopy.save(homesFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Impossible de sauvegarder homes.yml: " + e.getMessage());
            }
        });
    }

    // ==================== HOME MANAGEMENT ====================

    public boolean setHome(Player player, String name) {
        String uuid = player.getUniqueId().toString();
        int maxHomes = plugin.getConfig().getInt("teleport.max-homes", 3);

        // Check limit
        Set<String> homes = getHomeNames(player);
        if (homes.size() >= maxHomes && !homes.contains(name.toLowerCase())) {
            player.sendMessage(getMessage("home-max-reached").replace("%max%", String.valueOf(maxHomes)));
            return false;
        }

        // Save home location
        String path = uuid + "." + name.toLowerCase();
        Location loc = player.getLocation();
        homesConfig.set(path + ".world", loc.getWorld().getName());
        homesConfig.set(path + ".x", loc.getX());
        homesConfig.set(path + ".y", loc.getY());
        homesConfig.set(path + ".z", loc.getZ());
        homesConfig.set(path + ".yaw", loc.getYaw());
        homesConfig.set(path + ".pitch", loc.getPitch());
        saveHomesFile();

        player.sendMessage(getMessage("home-set").replace("%name%", name));
        sendHomeCount(player);
        return true;
    }

    public boolean deleteHome(Player player, String name) {
        String uuid = player.getUniqueId().toString();
        String path = uuid + "." + name.toLowerCase();

        if (!homesConfig.contains(path)) {
            player.sendMessage(getMessage("home-not-found").replace("%name%", name));
            return false;
        }

        homesConfig.set(path, null);
        saveHomesFile();

        player.sendMessage(getMessage("home-deleted").replace("%name%", name));
        sendHomeCount(player);
        return true;
    }

    public boolean teleportToHome(Player player, String name) {
        // Check cooldown
        long cooldown = teleportManager.getRemainingCooldown(player, "home");
        if (cooldown > 0) {
            player.sendMessage(getMessage("cooldown").replace("%time%", String.valueOf(cooldown)));
            return false;
        }

        // Check if already teleporting
        if (teleportManager.hasPendingTeleport(player)) {
            player.sendMessage(getMessage("teleport-in-progress"));
            return false;
        }

        String uuid = player.getUniqueId().toString();
        String path = uuid + "." + name.toLowerCase();

        if (!homesConfig.contains(path)) {
            player.sendMessage(getMessage("home-not-found").replace("%name%", name));
            return false;
        }

        // Get location
        String worldName = homesConfig.getString(path + ".world");
        if (worldName == null || org.bukkit.Bukkit.getWorld(worldName) == null) {
            player.sendMessage(getMessage("home-not-found").replace("%name%", name));
            return false;
        }

        Location loc = new Location(
                org.bukkit.Bukkit.getWorld(worldName),
                homesConfig.getDouble(path + ".x"),
                homesConfig.getDouble(path + ".y"),
                homesConfig.getDouble(path + ".z"),
                (float) homesConfig.getDouble(path + ".yaw"),
                (float) homesConfig.getDouble(path + ".pitch"));

        // Start teleport with warmup
        teleportManager.startTeleport(player, loc, "home");
        return true;
    }

    public Set<String> getHomeNames(Player player) {
        String uuid = player.getUniqueId().toString();
        ConfigurationSection section = homesConfig.getConfigurationSection(uuid);
        if (section == null) {
            return new HashSet<>();
        }
        return section.getKeys(false);
    }

    public void sendHomeList(Player player) {
        Set<String> homes = getHomeNames(player);

        if (homes.isEmpty()) {
            player.sendMessage(getMessage("home-no-homes"));
            return;
        }

        player.sendMessage(getMessage("home-list-header"));
        player.sendMessage("");

        for (String homeName : homes) {
            // Create clickable home entry
            TextComponent homeText = new TextComponent(" §f• §d" + homeName + " ");

            // TP button
            TextComponent tpBtn = new TextComponent("[🏠 TP]");
            tpBtn.setColor(ChatColor.GREEN);
            tpBtn.setBold(true);
            tpBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/home " + homeName));
            tpBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new Text("§aCliquez pour vous téléporter")));

            // Delete button
            TextComponent delBtn = new TextComponent(" [🗑]");
            delBtn.setColor(ChatColor.RED);
            delBtn.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/delhome " + homeName));
            delBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new Text("§cCliquez pour supprimer ce home")));

            homeText.addExtra(tpBtn);
            homeText.addExtra(delBtn);
            player.spigot().sendMessage(homeText);
        }

        int maxHomes = plugin.getConfig().getInt("teleport.max-homes", 3);
        player.sendMessage("");
        player.sendMessage(getMessage("home-count")
                .replace("%count%", String.valueOf(homes.size()))
                .replace("%max%", String.valueOf(maxHomes)));
    }

    // ==================== UTILITIES ====================

    private void sendHomeCount(Player player) {
        Set<String> homes = getHomeNames(player);
        int maxHomes = plugin.getConfig().getInt("teleport.max-homes", 3);
        player.sendMessage(getMessage("home-count")
                .replace("%count%", String.valueOf(homes.size()))
                .replace("%max%", String.valueOf(maxHomes)));
    }

    private String getMessage(String key) {
        String msg = plugin.getConfig().getString("teleport.messages." + key, "&cMessage manquant: " + key);
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', msg);
    }
}
