package net.arthonetwork.donation.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PingCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            // Note: getPing() is available in newer Spigot versions (1.12+ approx).
            // If strictly 1.8, we'd need NMS, but assuming modern version based on previous
            // context.
            int ping = 0;
            try {
                // Reflection or direct method if available.
                // For broad compatibility without NMS, we can try the method directly.
                // If it fails, we catch it.
                ping = player.getPing();
            } catch (NoSuchMethodError e) {
                sender.sendMessage(ChatColor.RED + "Ping feature not supported on this server version.");
                return true;
            }

            ChatColor color = ChatColor.GREEN;
            if (ping > 100)
                color = ChatColor.YELLOW;
            if (ping > 250)
                color = ChatColor.RED;

            sender.sendMessage(ChatColor.GRAY + "Pong! Your ping is " + color + ping + "ms");
        } else {
            sender.sendMessage(ChatColor.RED + "Console has 0ms ping!");
        }
        return true;
    }
}
