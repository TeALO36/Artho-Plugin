package net.arthonetwork.donation;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Random;

public class ArthoPlugin extends JavaPlugin implements CommandExecutor {

    private List<String> messages;
    private String donationLink;
    private int interval;
    private BukkitRunnable task;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();
        getCommand("don").setExecutor(this);
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
                String fullMsg = ChatColor.translateAlternateColorCodes('&', randomMsg.replace("$link", donationLink));
                Bukkit.broadcastMessage(fullMsg);
            }
        };
        // Interval is in seconds, convert to ticks (20 ticks = 1 second)
        task.runTaskTimer(this, 0L, interval * 20L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("don")) {
            if (!sender.hasPermission("arthodonation.admin")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                return true;
            }

            if (args.length == 0) {
                sender.sendMessage(ChatColor.RED + "Usage: /don <add|link|reset|reload>");
                return true;
            }

            String sub = args[0].toLowerCase();
            switch (sub) {
                case "reload":
                    loadConfig();
                    startBroadcasting();
                    sender.sendMessage(ChatColor.GREEN + "ArthoDonation configuration reloaded!");
                    break;
                case "link":
                    if (args.length < 2) {
                        sender.sendMessage(ChatColor.RED + "Usage: /don link <url>");
                        return true;
                    }
                    String newLink = args[1];
                    getConfig().set("donation-link", newLink);
                    saveConfig();
                    loadConfig();
                    sender.sendMessage(ChatColor.GREEN + "Donation link updated to: " + newLink);
                    break;
                case "add":
                    if (args.length < 2) {
                        sender.sendMessage(ChatColor.RED + "Usage: /don add <message>");
                        return true;
                    }
                    StringBuilder msgBuilder = new StringBuilder();
                    for (int i = 1; i < args.length; i++) {
                        msgBuilder.append(args[i]).append(" ");
                    }
                    String newMessage = msgBuilder.toString().trim();
                    List<String> currentMessages = getConfig().getStringList("messages");
                    currentMessages.add(newMessage);
                    getConfig().set("messages", currentMessages);
                    saveConfig();
                    loadConfig();
                    sender.sendMessage(ChatColor.GREEN + "Message added!");
                    break;
                case "reset":
                    saveResource("config.yml", true);
                    loadConfig();
                    startBroadcasting();
                    sender.sendMessage(ChatColor.GREEN + "Configuration reset to default!");
                    break;
                default:
                    sender.sendMessage(ChatColor.RED + "Unknown subcommand. Usage: /don <add|link|reset|reload>");
                    break;
            }
            return true;
        }
        return false;
    }
}
