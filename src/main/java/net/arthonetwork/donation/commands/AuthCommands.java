package net.arthonetwork.donation.commands;

import net.arthonetwork.donation.utils.AuthManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class AuthCommands implements CommandExecutor {

    private final AuthManager authManager;

    public AuthCommands(AuthManager authManager) {
        this.authManager = authManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            if (command.getName().equalsIgnoreCase("auth")) {
                handleAdminAuth(sender, args);
            } else {
                sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            }
            return true;
        }

        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();

        if (command.getName().equalsIgnoreCase("register")) {
            if (authManager.isRegistered(uuid)) {
                player.sendMessage(ChatColor.RED + "Vous êtes déjà enregistré ! Utilisez /login <motdepasse>.");
                return true;
            }
            if (args.length != 2) {
                player.sendMessage(ChatColor.RED + "Usage: /register <motdepasse> <confirmation>");
                return true;
            }
            if (!args[0].equals(args[1])) {
                player.sendMessage(ChatColor.RED + "Les mots de passe ne correspondent pas.");
                return true;
            }
            authManager.register(uuid, args[0], player.getAddress().getAddress().getHostAddress());
            player.sendMessage(ChatColor.GREEN + "Enregistrement réussi ! Vous êtes maintenant connecté.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("login")) {
            if (!authManager.isRegistered(uuid)) {
                player.sendMessage(
                        ChatColor.RED + "Vous n'êtes pas enregistré ! Utilisez /register <motdepasse> <confirmation>.");
                return true;
            }
            if (authManager.isLoggedIn(uuid)) {
                player.sendMessage(ChatColor.RED + "Vous êtes déjà connecté.");
                return true;
            }
            if (args.length != 1) {
                player.sendMessage(ChatColor.RED + "Usage: /login <motdepasse>");
                return true;
            }

            String ip = player.getAddress().getAddress().getHostAddress();
            if (!authManager.checkIpLimit(ip)) {
                player.kickPlayer(ChatColor.RED + "Trop de tentatives de connexion. Réessayez plus tard.");
                return true;
            }

            if (authManager.login(uuid, args[0])) {
                player.sendMessage(ChatColor.GREEN + "Connexion réussie !");
            } else {
                player.sendMessage(ChatColor.RED + "Mot de passe incorrect.");
            }
            return true;
        }

        if (command.getName().equalsIgnoreCase("auth")) {
            handleAdminAuth(sender, args);
        }

        return true;
    }

    private void handleAdminAuth(CommandSender sender, String[] args) {
        if (!sender.hasPermission("arthodonation.admin") && !sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission.");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /auth unregister <player>");
            return;
        }

        if (args[0].equalsIgnoreCase("unregister")) {
            String targetName = args[1];
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
            if (target != null) {
                authManager.unregister(target.getUniqueId());
                sender.sendMessage(ChatColor.GREEN + "Joueur " + targetName + " désenregistré.");
            } else {
                sender.sendMessage(ChatColor.RED + "Joueur introuvable.");
            }
        }
    }
}
