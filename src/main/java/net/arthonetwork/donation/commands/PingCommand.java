package net.arthonetwork.donation.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PingCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0) {
            // Check for other player
            if (!sender.isOp() && !sender.hasPermission("arthoplugin.ping.others")) {
                sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission de voir le ping des autres joueurs.");
                return true;
            }

            Player target = org.bukkit.Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Joueur introuvable.");
                return true;
            }

            sendPingMessage(sender, target.getName(), getPlayerPing(target));
            return true;
        }

        if (sender instanceof Player) {
            Player player = (Player) sender;
            sendPingMessage(sender, "Votre", getPlayerPing(player));
        } else {
            sender.sendMessage(ChatColor.RED + "La console a 0ms de ping !");
        }
        return true;
    }

    private int getPlayerPing(Player player) {
        try {
            return player.getPing();
        } catch (NoSuchMethodError e) {
            return -1; // Not supported
        }
    }

    private void sendPingMessage(CommandSender sender, String targetName, int ping) {
        if (ping == -1) {
            sender.sendMessage(ChatColor.RED + "Fonction ping non supportée sur cette version.");
            return;
        }

        ChatColor color = ChatColor.GREEN;
        if (ping > 100)
            color = ChatColor.YELLOW;
        if (ping > 250)
            color = ChatColor.RED;

        String message = targetName.equals("Votre")
                ? ChatColor.GRAY + "Pong ! Votre ping est de " + color + ping + "ms"
                : ChatColor.GRAY + "Ping de " + ChatColor.AQUA + targetName + ChatColor.GRAY + " : " + color + ping
                        + "ms";

        sender.sendMessage(message);
    }
}
