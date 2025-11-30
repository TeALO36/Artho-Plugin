package net.arthonetwork.donation.commands;

import net.arthonetwork.donation.ArthoPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.List;

public class DonCommand implements CommandExecutor {

    private final ArthoPlugin plugin;

    public DonCommand(ArthoPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("arthodonation.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "help":
                sendHelp(sender);
                break;
            case "reload":
                plugin.reloadConfig();
                plugin.loadConfig(); // Ensure local variables are updated
                plugin.startBroadcasting();
                plugin.getSuggestionManager().reload();
                sender.sendMessage(ChatColor.GREEN + "ArthoDonation configuration reloaded!");
                break;
            case "link":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /don link <url>");
                    return true;
                }
                String newLink = args[1];
                plugin.getConfig().set("donation-link", newLink);
                plugin.saveConfig();
                plugin.loadConfig();
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
                List<String> currentMessages = plugin.getConfig().getStringList("messages");
                currentMessages.add(newMessage);
                plugin.getConfig().set("messages", currentMessages);
                plugin.saveConfig();
                plugin.loadConfig();
                sender.sendMessage(ChatColor.GREEN + "Message added!");
                break;
            case "reset":
                plugin.saveResource("config.yml", true);
                plugin.loadConfig();
                plugin.startBroadcasting();
                sender.sendMessage(ChatColor.GREEN + "Configuration reset to default!");
                break;
            case "enable":
                plugin.setDonationEnabled(true);
                sender.sendMessage(ChatColor.GREEN + "Donation broadcasting enabled!");
                break;
            case "disable":
                plugin.setDonationEnabled(false);
                sender.sendMessage(ChatColor.RED + "Donation broadcasting disabled!");
                break;
            default:
                sender.sendMessage(ChatColor.RED + "Unknown subcommand.");
                sendHelp(sender);
                break;
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "--- ArthoDonation Help ---");
        sender.sendMessage(ChatColor.YELLOW + "/don add <message> " + ChatColor.WHITE + "- Add a donation message.");
        sender.sendMessage(ChatColor.YELLOW + "/don link <url> " + ChatColor.WHITE + "- Set the donation link.");
        sender.sendMessage(ChatColor.YELLOW + "/don enable " + ChatColor.WHITE + "- Enable broadcasting.");
        sender.sendMessage(ChatColor.YELLOW + "/don disable " + ChatColor.WHITE + "- Disable broadcasting.");
        sender.sendMessage(ChatColor.YELLOW + "/don reset " + ChatColor.WHITE + "- Reset config to defaults.");
        sender.sendMessage(ChatColor.YELLOW + "/don reload " + ChatColor.WHITE + "- Reload configuration.");
        sender.sendMessage(ChatColor.YELLOW + "/don help " + ChatColor.WHITE + "- Show this help message.");
    }
}
