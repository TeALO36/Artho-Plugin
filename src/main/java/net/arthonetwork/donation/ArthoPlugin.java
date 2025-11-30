package net.arthonetwork.donation;

import net.arthonetwork.donation.commands.AuthCommands;
import net.arthonetwork.donation.commands.DonCommand;
import net.arthonetwork.donation.commands.LagCommand;
import net.arthonetwork.donation.commands.PingCommand;
import net.arthonetwork.donation.commands.ServerCommand;
import net.arthonetwork.donation.listeners.AuthListener;
import net.arthonetwork.donation.listeners.PlayerJoinListener;
import net.arthonetwork.donation.tasks.OpCheckTask;
import net.arthonetwork.donation.tasks.AuthReminderTask;
import net.arthonetwork.donation.utils.ArthoTabCompleter;
import net.arthonetwork.donation.utils.AuthManager;
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
    private AuthManager authManager;
    private OpCheckTask opCheckTask;
    private boolean donationEnabled;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();

        suggestionManager = new SuggestionManager(this);
        authManager = new AuthManager(this);

        // Register commands
        getCommand("don").setExecutor(new DonCommand(this));
        getCommand("ping").setExecutor(new PingCommand());
        getCommand("lag").setExecutor(new LagCommand());
        getCommand("server").setExecutor(new ServerCommand(suggestionManager));

        AuthCommands authCmd = new AuthCommands(this, authManager);
        getCommand("register").setExecutor(authCmd);
        getCommand("login").setExecutor(authCmd);
        getCommand("auth").setExecutor(authCmd);
        getCommand("changepassword").setExecutor(authCmd);

        // Register TabCompleter
        ArthoTabCompleter tabCompleter = new ArthoTabCompleter();
        getCommand("don").setTabCompleter(tabCompleter);
        getCommand("lag").setTabCompleter(tabCompleter);
        getCommand("server").setTabCompleter(tabCompleter);
        getCommand("auth").setTabCompleter(tabCompleter);

        // Register events
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(), this);
        getServer().getPluginManager().registerEvents(new AuthListener(this, authManager), this);

        // Start tasks
        startBroadcasting();
        opCheckTask = new OpCheckTask();
        opCheckTask.runTaskTimer(this, 20L, 100L); // Check every 5 seconds (100 ticks)

        // Start Auth Reminder Task (every 2 seconds = 40 ticks)
        new AuthReminderTask(this, authManager).runTaskTimer(this, 20L, 40L);

        getLogger().info("Artho-Plugin enabled!");
    }

    @Override
    public void onDisable() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        if (opCheckTask != null && !opCheckTask.isCancelled()) {
            opCheckTask.cancel();
        }
        getLogger().info("Artho-Plugin disabled!");
    }

    public void loadConfig() {
        reloadConfig();
        FileConfiguration config = getConfig();
        messages = config.getStringList("messages");
        donationLink = config.getString("donation-link");
        interval = config.getInt("interval");
        donationEnabled = config.getBoolean("donation-enabled", true);
    }

    public void startBroadcasting() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }

        if (!donationEnabled)
            return;

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

    public void setDonationEnabled(boolean enabled) {
        this.donationEnabled = enabled;
        getConfig().set("donation-enabled", enabled);
        saveConfig();
        startBroadcasting();
    }

    public String getAuthMessage(String path) {
        return ChatColor.translateAlternateColorCodes('&',
                getConfig().getString("auth.messages." + path, "&cMessage missing: " + path));
    }

    public String getWhitelistMessage() {
        return ChatColor.translateAlternateColorCodes('&',
                getConfig().getString("auth.whitelist.kick-message", "&cNot whitelisted"));
    }

    public SuggestionManager getSuggestionManager() {
        return suggestionManager;
    }
}
