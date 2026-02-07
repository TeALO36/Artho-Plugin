package net.arthonetwork.donation.commands;

import net.arthonetwork.donation.utils.HomeManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class HomeCommand implements CommandExecutor {

    private final HomeManager homeManager;

    public HomeCommand(HomeManager homeManager) {
        this.homeManager = homeManager;
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
            case "sethome":
                return handleSetHome(player, args);
            case "home":
                return handleHome(player, args);
            case "delhome":
                return handleDelHome(player, args);
            case "homes":
                return handleHomesList(player);
            default:
                return false;
        }
    }

    private boolean handleSetHome(Player player, String[] args) {
        String name = args.length > 0 ? args[0] : "home";

        // Validate name
        if (!name.matches("^[a-zA-Z0-9_-]+$")) {
            player.sendMessage("§cNom invalide ! Utilisez uniquement des lettres, chiffres, _ et -");
            return true;
        }

        if (name.length() > 16) {
            player.sendMessage("§cNom trop long ! Maximum 16 caractères.");
            return true;
        }

        homeManager.setHome(player, name);
        return true;
    }

    private boolean handleHome(Player player, String[] args) {
        String name = args.length > 0 ? args[0] : "home";
        homeManager.teleportToHome(player, name);
        return true;
    }

    private boolean handleDelHome(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage("§cUsage: /delhome <nom>");
            return true;
        }

        homeManager.deleteHome(player, args[0]);
        return true;
    }

    private boolean handleHomesList(Player player) {
        homeManager.sendHomeList(player);
        return true;
    }
}
