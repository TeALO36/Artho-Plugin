package net.arthonetwork.donation;

import net.arthonetwork.donation.commands.AuthCommands;
import net.arthonetwork.donation.commands.AnnoncesCommand;
import net.arthonetwork.donation.commands.HomeCommand;
import net.arthonetwork.donation.commands.LagCommand;
import net.arthonetwork.donation.commands.PingCommand;
import net.arthonetwork.donation.commands.SuggestionCommand;
import net.arthonetwork.donation.commands.TpaCommand;
import net.arthonetwork.donation.listeners.AuthListener;
import net.arthonetwork.donation.listeners.PlayerJoinListener;
import net.arthonetwork.donation.listeners.TeleportListener;
import net.arthonetwork.donation.tasks.OpCheckTask;
import net.arthonetwork.donation.tasks.AuthReminderTask;
import net.arthonetwork.donation.tasks.TabListUpdateTask;
import net.arthonetwork.donation.utils.ArthoTabCompleter;
import net.arthonetwork.donation.utils.AuthManager;
import net.arthonetwork.donation.utils.HomeManager;
import net.arthonetwork.donation.utils.SuggestionManager;
import net.arthonetwork.donation.utils.TeleportManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

import net.arthonetwork.donation.utils.ConsoleFilter;

import java.util.List;
import java.util.Random;

public class ArthoPlugin extends JavaPlugin {

    private List<String> messages;
    private String donationLink;
    private int minInterval;
    private int maxInterval;
    private BukkitRunnable task;
    private SuggestionManager suggestionManager;
    private AuthManager authManager;
    private TeleportManager teleportManager;
    private HomeManager homeManager;
    private OpCheckTask opCheckTask;
    private boolean donationEnabled;
    private boolean tipsEnabled;
    private ConsoleFilter consoleFilter;
    private BukkitRunnable tipsTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();

        suggestionManager = new SuggestionManager(this);
        authManager = new AuthManager(this);
        teleportManager = new TeleportManager(this);
        homeManager = new HomeManager(this, teleportManager);

        // Register Console Filter
        consoleFilter = new ConsoleFilter();
        try {
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            Configuration config = ctx.getConfiguration();
            LoggerConfig loggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
            loggerConfig.addFilter(consoleFilter);
            ctx.updateLoggers();
        } catch (Exception e) {
            getLogger().warning("Unable to register ConsoleFilter: " + e.getMessage());
        }

        // Register commands
        getCommand("annonces").setExecutor(new AnnoncesCommand(this));
        getCommand("ping").setExecutor(new PingCommand());
        getCommand("lag").setExecutor(new LagCommand());
        getCommand("suggestion").setExecutor(new SuggestionCommand(suggestionManager));

        net.arthonetwork.donation.commands.ArthoCommand arthoCmd = new net.arthonetwork.donation.commands.ArthoCommand(
                this);
        getCommand("artho").setExecutor(arthoCmd);
        getCommand("arthonetwork").setExecutor(arthoCmd);

        AuthCommands authCmd = new AuthCommands(this, authManager);
        getCommand("register").setExecutor(authCmd);
        getCommand("login").setExecutor(authCmd);
        getCommand("auth").setExecutor(authCmd);
        getCommand("changepassword").setExecutor(authCmd);

        // Teleportation commands
        TpaCommand tpaCmd = new TpaCommand(teleportManager);
        getCommand("tpa").setExecutor(tpaCmd);
        getCommand("tpaccept").setExecutor(tpaCmd);
        getCommand("tpdeny").setExecutor(tpaCmd);
        getCommand("tpcancel").setExecutor(tpaCmd);

        HomeCommand homeCmd = new HomeCommand(homeManager);
        getCommand("sethome").setExecutor(homeCmd);
        getCommand("home").setExecutor(homeCmd);
        getCommand("delhome").setExecutor(homeCmd);
        getCommand("homes").setExecutor(homeCmd);

        // Register TabCompleter
        ArthoTabCompleter tabCompleter = new ArthoTabCompleter();
        getCommand("annonces").setTabCompleter(tabCompleter);
        getCommand("lag").setTabCompleter(tabCompleter);
        getCommand("suggestion").setTabCompleter(tabCompleter);
        getCommand("auth").setTabCompleter(tabCompleter);
        getCommand("ping").setTabCompleter(tabCompleter);
        getCommand("artho").setTabCompleter(tabCompleter);

        // Register events
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(), this);
        getServer().getPluginManager().registerEvents(new AuthListener(this, authManager), this);
        getServer().getPluginManager().registerEvents(new TeleportListener(teleportManager), this);

        // Start tasks
        startBroadcasting();
        opCheckTask = new OpCheckTask();
        opCheckTask.runTaskTimer(this, 20L, 100L); // Check every 5 seconds (100 ticks)

        // Start Auth Reminder Task (every 2 seconds = 40 ticks)
        new AuthReminderTask(this, authManager).runTaskTimer(this, 20L, 40L);

        // Start TabList Update Task (every 1 second = 20 ticks)
        new TabListUpdateTask().runTaskTimer(this, 20L, 20L);

        // Start Tip Broadcast Task
        startTipsTask();

        // Check for updates
        new net.arthonetwork.donation.utils.AutoUpdater(this).checkForUpdates();

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
        if (tipsTask != null && !tipsTask.isCancelled()) {
            tipsTask.cancel();
        }

        // Unregister Console Filter
        if (consoleFilter != null) {
            try {
                LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
                Configuration config = ctx.getConfiguration();
                LoggerConfig loggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
                loggerConfig.removeFilter(consoleFilter);
                ctx.updateLoggers();
            } catch (Exception e) {
                getLogger().warning("Unable to unregister ConsoleFilter: " + e.getMessage());
            }
        }

        getLogger().info("Artho-Plugin disabled!");
    }

    public void loadConfig() {
        reloadConfig();
        FileConfiguration config = getConfig();
        messages = config.getStringList("messages");
        donationLink = config.getString("donation-link");

        // Load min/max, fallback to old 'interval' if missing
        if (config.contains("interval-min") && config.contains("interval-max")) {
            minInterval = config.getInt("interval-min");
            maxInterval = config.getInt("interval-max");
        } else {
            int legacyInterval = config.getInt("interval", 300);
            minInterval = legacyInterval;
            maxInterval = legacyInterval;
        }

        donationEnabled = config.getBoolean("donation-enabled", true);
        tipsEnabled = config.getBoolean("tips-enabled", true);
    }

    // Alias for loadConfig to match DonCommand usage
    public void loadConfiguration() {
        loadConfig();
    }

    public void startBroadcasting() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }

        if (!donationEnabled)
            return;

        scheduleNextBroadcast();
    }

    private void scheduleNextBroadcast() {
        if (!donationEnabled)
            return;

        long delay;
        if (minInterval == maxInterval) {
            delay = minInterval * 20L;
        } else {
            delay = (minInterval + new Random().nextInt(maxInterval - minInterval + 1)) * 20L;
        }

        task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!messages.isEmpty()) {
                    String randomMsg = messages.get(new Random().nextInt(messages.size()));
                    String fullMsg = ChatColor.translateAlternateColorCodes('&',
                            randomMsg.replace("$link", donationLink));
                    Bukkit.broadcastMessage(fullMsg);
                }
                scheduleNextBroadcast(); // Schedule next recursively
            }
        };
        task.runTaskLater(this, delay);
    }

    public void setDonationEnabled(boolean enabled) {
        this.donationEnabled = enabled;
        getConfig().set("donation-enabled", enabled);
        saveConfig();
        startBroadcasting();
    }

    public void addMessage(String message) {
        messages.add(message);
        getConfig().set("messages", messages);
        saveConfig();
    }

    public void setDonationLink(String link) {
        this.donationLink = link;
        getConfig().set("donation-link", link);
        saveConfig();
    }

    public void setIntervals(int min, int max) {
        this.minInterval = min;
        this.maxInterval = max;
        getConfig().set("interval-min", min);
        getConfig().set("interval-max", max);
        saveConfig();
        startBroadcasting(); // Restart with new intervals
    }

    public void resetConfig() {
        saveResource("config.yml", true);
        loadConfig();
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

    private void startTipsTask() {
        if (tipsTask != null && !tipsTask.isCancelled()) {
            tipsTask.cancel();
        }

        if (tipsEnabled) {
            tipsTask = new net.arthonetwork.donation.tasks.TipBroadcastTask();
            tipsTask.runTaskTimer(this, 1200L, 6000L); // 5 minutes
        }
    }

    public void setTipsEnabled(boolean enabled) {
        this.tipsEnabled = enabled;
        getConfig().set("tips-enabled", enabled);
        saveConfig();
        startTipsTask();
    }
}
