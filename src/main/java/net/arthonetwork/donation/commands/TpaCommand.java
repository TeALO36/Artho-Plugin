package net.arthonetwork.donation.commands;

import net.arthonetwork.donation.utils.TeleportManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TpaCommand implements CommandExecutor {

    private final TeleportManager teleportManager;

    public TpaCommand(TeleportManager teleportManager) {
        this.teleportManager = teleportManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cCette commande est réservée aux joueurs.");
            return true;
        }

        Player player = (Player) sender;
        String cmd = command.getName().toLowerCase();

        switch (cmd) {
            case "tpa":
                return handleTpa(player, args);
            case "tpaccept":
                return handleTpaccept(player, args);
            case "tpdeny":
                return handleTpdeny(player, args);
            case "tpcancel":
                return handleTpcancel(player);
            default:
                return false;
        }
    }

    private boolean handleTpa(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage("§cUsage: /tpa <joueur>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            player.sendMessage("§cJoueur introuvable.");
            return true;
        }

        if (target.equals(player)) {
            player.sendMessage("§cVous ne pouvez pas vous téléporter à vous-même !");
            return true;
        }

        teleportManager.sendTpaRequest(player, target);
        return true;
    }

    private boolean handleTpaccept(Player player, String[] args) {
        String requesterName = args.length > 0 ? args[0] : null;
        teleportManager.acceptTpa(player, requesterName);
        return true;
    }

    private boolean handleTpdeny(Player player, String[] args) {
        String requesterName = args.length > 0 ? args[0] : null;
        teleportManager.denyTpa(player, requesterName);
        return true;
    }

    private boolean handleTpcancel(Player player) {
        teleportManager.cancelTpa(player);
        return true;
    }
}
