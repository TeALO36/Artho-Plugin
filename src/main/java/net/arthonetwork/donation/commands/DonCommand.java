package net.arthonetwork.donation.commands;

import net.arthonetwork.donation.ArthoPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.List;

public class DonCommand implements CommandExecutor {

    private final ArthoPlugin plugin;

    case"enable":plugin.setDonationEnabled(true);sender.sendMessage(ChatColor.GREEN+"Donation broadcasting enabled!");break;case"disable":plugin.setDonationEnabled(false);sender.sendMessage(ChatColor.RED+"Donation broadcasting disabled!");break;

    default:
                sender.sendMessage(ChatColor.RED + "Unknown subcommand.");
                sendHelp(sender);
                break;
        }return true;

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
