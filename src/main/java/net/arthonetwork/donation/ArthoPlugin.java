package net.arthonetwork.donation;

import net.arthonetwork.donation.commands.DonCommand;
import net.arthonetwork.donation.commands.LagCommand;
import net.arthonetwork.donation.commands.PingCommand;
import net.arthonetwork.donation.commands.ServerCommand;
import net.arthonetwork.donation.utils.SuggestionManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
    private SuggestionManager suggestionManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();

        suggestionManager = new SuggestionManager(this);

        // Register commands
        getCommand("don").setExecutor(new DonCommand(this));
        getCommand("ping").setExecutor(new PingCommand());
        getCommand("lag").setExecutor(new LagCommand());
        getCommand("server").setExecutor(new ServerCommand(suggestionManager));

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

    public void loadConfig() {
        reloadConfig();
        FileConfiguration config = getConfig();
        messages = config.getStringList("messages");
        donationLink = config.getString("donation-link");
        interval = config.getInt("interval");
    }

    public void startBroadcasting() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }

        task = new BukkitRunnable() {
            @Override
            public void run() {
                if (messages.isEmpty())
                    return;
                String randomMsg = messages.get(new Random().nextInt(messages.size()));
                String fullMsg = ChatColor.translateAlternateColorCodes('&', randomMsg.replace("$link", donationLink));
                Bukkit.broadcastMessage(fullMsg);
            }
        };
        // Interval is in seconds, convert to ticks (20 ticks = 1 second)
        task.runTaskTimer(this, 0L, interval * 20L);
    }
}
