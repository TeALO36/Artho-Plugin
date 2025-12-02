package net.arthonetwork.donation.commands;

import net.arthonetwork.donation.ArthoPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Arrays;

public class DonCommand implements CommandExecutor {

    private final ArthoPlugin plugin;

    public DonCommand(ArthoPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("arthoplugin.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "fix":
                if (args.length < 2 || !isInteger(args[1])) {
                    sender.sendMessage(ChatColor.RED + "Usage: /don fix <minutes>");
                    return true;
                }
                int fixMinutes = Integer.parseInt(args[1]);
                if (fixMinutes <= 0) {
                    sender.sendMessage(ChatColor.RED + "Interval must be positive!");
                    return true;
                }
                plugin.setIntervals(fixMinutes * 60, fixMinutes * 60);
                sender.sendMessage(ChatColor.GREEN + "Broadcast interval set to fixed " + fixMinutes + " minutes.");
                break;
            case "variable":
                if (args.length < 2 || !args[1].contains("-")) {
                    sender.sendMessage(ChatColor.RED + "Usage: /don variable <min>-<max>");
                    return true;
                }
                String[] parts = args[1].split("-");
                if (parts.length != 2 || !isInteger(parts[0]) || !isInteger(parts[1])) {
                    sender.sendMessage(ChatColor.RED + "Invalid format! Use: 5-10");
                    return true;
                }
                int min = Integer.parseInt(parts[0]);
                int max = Integer.parseInt(parts[1]);

                if (min <= 0 || max <= 0) {
                    sender.sendMessage(ChatColor.RED + "Intervals must be positive!");
                    return true;
                }
                if (min > max) {
                    int temp = min;
                    min = max;
                    max = temp;
                }
                plugin.setIntervals(min * 60, max * 60);
                sender.sendMessage(ChatColor.GREEN + "Broadcast interval set to random between " + min + " and " + max
                        + " minutes.");
                break;
            case "add":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /don add <message>");
                    return true;
                }
                String newMessage = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                plugin.addMessage(newMessage);
                sender.sendMessage(ChatColor.GREEN + "Message added!");
                break;
            case "link":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /don link <url>");
                    return true;
                }
                plugin.setDonationLink(args[1]);
                sender.sendMessage(ChatColor.GREEN + "Donation link updated!");
                break;
            case "reset":
                plugin.resetConfig();
                sender.sendMessage(ChatColor.GREEN + "Configuration reset to defaults!");
                break;
            case "reload":
                plugin.reloadConfig();
                plugin.loadConfiguration();
                sender.sendMessage(ChatColor.GREEN + "Artho-Plugin configuration reloaded!");
                break;
            case "enable":
                plugin.setDonationEnabled(true);
                sender.sendMessage(ChatColor.GREEN + "Donation broadcasting enabled!");
                break;
            case "disable":
                plugin.setDonationEnabled(false);
                sender.sendMessage(ChatColor.RED + "Donation broadcasting disabled!");
                break;
            case "help":
            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private boolean isInteger(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "--- Artho-Plugin Help ---");
        sender.sendMessage(
                ChatColor.YELLOW + "/don fix <minutes> " + ChatColor.WHITE + "- Set fixed broadcast interval.");
        sender.sendMessage(
                ChatColor.YELLOW + "/don variable <min>-<max> " + ChatColor.WHITE + "- Set random interval range.");
        sender.sendMessage(ChatColor.YELLOW + "/don add <message> " + ChatColor.WHITE + "- Add a donation message.");
        sender.sendMessage(ChatColor.YELLOW + "/don link <url> " + ChatColor.WHITE + "- Set the donation link.");
        sender.sendMessage(ChatColor.YELLOW + "/don enable " + ChatColor.WHITE + "- Enable broadcasting.");
        sender.sendMessage(ChatColor.YELLOW + "/don disable " + ChatColor.WHITE + "- Disable broadcasting.");
        sender.sendMessage(ChatColor.YELLOW + "/don reset " + ChatColor.WHITE + "- Reset config to defaults.");
        sender.sendMessage(ChatColor.YELLOW + "/don reload " + ChatColor.WHITE + "- Reload configuration.");
        sender.sendMessage(ChatColor.YELLOW + "/don help " + ChatColor.WHITE + "- Show this help message.");
    }
}
