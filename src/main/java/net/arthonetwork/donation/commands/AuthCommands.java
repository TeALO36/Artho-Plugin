package net.arthonetwork.donation.commands;

import net.arthonetwork.donation.ArthoPlugin;
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

    private final ArthoPlugin plugin;
    private final AuthManager authManager;

    public AuthCommands(ArthoPlugin plugin, AuthManager authManager) {
        this.plugin = plugin;
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
                player.sendMessage(plugin.getAuthMessage("already-registered"));
                return true;
            }
            if (args.length != 2) {
                player.sendMessage(plugin.getAuthMessage("usage-register"));
                return true;
            }
            if (!args[0].equals(args[1])) {
                player.sendMessage(plugin.getAuthMessage("passwords-do-not-match"));
                return true;
            }
            authManager.register(uuid, args[0], player.getAddress().getAddress().getHostAddress());
            player.sendMessage(plugin.getAuthMessage("success-register"));
            return true;
        }

        if (command.getName().equalsIgnoreCase("login")) {
            if (!authManager.isRegistered(uuid)) {
                player.sendMessage(plugin.getAuthMessage("not-registered-error"));
                return true;
            }
            if (authManager.isLoggedIn(uuid)) {
                player.sendMessage(plugin.getAuthMessage("already-logged-in"));
                return true;
            }
            if (args.length != 1) {
                player.sendMessage(plugin.getAuthMessage("usage-login"));
                return true;
            }

            String ip = player.getAddress().getAddress().getHostAddress();
            if (!authManager.checkIpLimit(ip)) {
                player.kickPlayer(ChatColor.RED + "Trop de tentatives de connexion. Réessayez plus tard.");
                return true;
            }

            if (authManager.login(uuid, args[0])) {
                player.sendMessage(plugin.getAuthMessage("success-login"));
            } else {
                player.sendMessage(plugin.getAuthMessage("wrong-password"));
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
            sendAdminHelp(sender);
            return;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "unregister":
                String targetName = args[1];
                OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                if (target != null) {
                    authManager.unregister(target.getUniqueId());
                    sender.sendMessage(ChatColor.GREEN + "Joueur " + targetName + " désenregistré.");
                } else {
                    sender.sendMessage(ChatColor.RED + "Joueur introuvable.");
                }
                break;
            case "whitelist":
                handleWhitelist(sender, args);
                break;
            case "set":
                handleSet(sender, args);
                break;
            default:
                sendAdminHelp(sender);
                break;
        }
    }

    private void handleWhitelist(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /auth whitelist <add|remove|list|on|off>");
            return;
        }
        String action = args[1].toLowerCase();
        switch (action) {
            case "add":
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /auth whitelist add <player>");
                    return;
                }
                authManager.addWhitelist(args[2]);
                sender.sendMessage(ChatColor.GREEN + args[2] + " ajouté à la whitelist.");
                break;
            case "remove":
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /auth whitelist remove <player>");
                    return;
                }
                authManager.removeWhitelist(args[2]);
                sender.sendMessage(ChatColor.GREEN + args[2] + " retiré de la whitelist.");
                break;
            case "list":
                sender.sendMessage(ChatColor.GOLD + "Whitelist: " + ChatColor.WHITE
                        + String.join(", ", authManager.getWhitelist()));
                break;
            case "on":
                authManager.setWhitelistEnabled(true);
                sender.sendMessage(ChatColor.GREEN + "Whitelist activée.");
                break;
            case "off":
                authManager.setWhitelistEnabled(false);
                sender.sendMessage(ChatColor.RED + "Whitelist désactivée.");
                break;
            default:
                sender.sendMessage(ChatColor.RED + "Action inconnue.");
                break;
        }
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /auth set <max-attempts|timeout> <value>");
            return;
        }
        String setting = args[1].toLowerCase();
        try {
            int value = Integer.parseInt(args[2]);
            if (setting.equals("max-attempts")) {
                authManager.setMaxAttempts(value);
                sender.sendMessage(ChatColor.GREEN + "Max tentatives défini à " + value);
            } else if (setting.equals("timeout")) {
                authManager.setLoginTimeout(value);
                sender.sendMessage(ChatColor.GREEN + "Timeout défini à " + value + " secondes");
            } else {
                sender.sendMessage(ChatColor.RED + "Paramètre inconnu.");
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "La valeur doit être un nombre entier.");
        }
    }

    private void sendAdminHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "--- ArthoAuth Admin ---");
        sender.sendMessage(ChatColor.YELLOW + "/auth unregister <player>");
        sender.sendMessage(ChatColor.YELLOW + "/auth whitelist <add|remove|list|on|off>");
        sender.sendMessage(ChatColor.YELLOW + "/auth set <max-attempts|timeout> <value>");
    }
}
