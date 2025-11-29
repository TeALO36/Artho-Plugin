package net.arthonetwork.donation;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Random;

public class ArthoPlugin extends JavaPlugin {

    private List<String> messages;
    private String donationLink;
    private int interval;
    private BukkitRunnable task;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();
        getCommand("arthoreload").setExecutor(this);
        startBroadcasting();
        getLogger().info("ArthoDonation enabled!");
    }

    @Override
    public void onDisable() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        getLogger().info("ArthoDonation disabled!");
    }

    private void loadConfig() {
        reloadConfig();
        FileConfiguration config = getConfig();
        messages = config.getStringList("messages");
        donationLink = config.getString("donation-link");
        interval = config.getInt("interval");
    }

    private void startBroadcasting() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }

        task = new BukkitRunnable() {
            @Override
            public void run() {
                if (messages.isEmpty())
                    return;
                String randomMsg = messages.get(new Random().nextInt(messages.size()));
                String fullMsg = ChatColor.translateAlternateColorCodes('&', randomMsg.replace("%link%", donationLink));
                Bukkit.broadcastMessage(fullMsg);
            }
        };
        // Interval is in seconds, convert to ticks (20 ticks = 1 second)
        task.runTaskTimer(this, 0L, interval * 20L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("arthoreload")) {
            loadConfig();
            startBroadcasting();
            sender.sendMessage(ChatColor.GREEN + "ArthoDonation configuration reloaded!");
            return true;
        }
        return false;
    }
}
