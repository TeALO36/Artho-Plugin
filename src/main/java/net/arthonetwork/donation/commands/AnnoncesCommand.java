package net.arthonetwork.donation.commands;

import net.arthonetwork.donation.ArthoPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Arrays;

public class AnnoncesCommand implements CommandExecutor {

    private final ArthoPlugin plugin;

    public AnnoncesCommand(ArthoPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("arthoplugin.admin")) {
            sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission d'utiliser cette commande.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "fix": // Keep english or rename? Let's use french equivalents or keep them if config
                        // driven. User said "optimiser toute les commande, qu'elles aient plus de
                        // sens".
            case "interval":
                // Let's support both
                if (args.length < 2 || !isInteger(args[1])) {
                    sender.sendMessage(ChatColor.RED + "Usage: /annonces interval <minutes>");
                    return true;
                }
                int fixMinutes = Integer.parseInt(args[1]);
                if (fixMinutes <= 0) {
                    sender.sendMessage(ChatColor.RED + "L'intervalle doit être positif !");
                    return true;
                }
                plugin.setIntervals(fixMinutes * 60, fixMinutes * 60);
                sender.sendMessage(ChatColor.GREEN + "Intervalle de diffusion fixé à " + fixMinutes + " minutes.");
                break;
            case "variable":
            case "range":
                if (args.length < 2 || !args[1].contains("-")) {
                    sender.sendMessage(ChatColor.RED + "Usage: /annonces range <min>-<max>");
                    return true;
                }
                String[] parts = args[1].split("-");
                if (parts.length != 2 || !isInteger(parts[0]) || !isInteger(parts[1])) {
                    sender.sendMessage(ChatColor.RED + "Format invalide ! Utilisez: 5-10");
                    return true;
                }
                int min = Integer.parseInt(parts[0]);
                int max = Integer.parseInt(parts[1]);

                if (min <= 0 || max <= 0) {
                    sender.sendMessage(ChatColor.RED + "Les intervalles doivent être positifs !");
                    return true;
                }
                if (min > max) {
                    int temp = min;
                    min = max;
                    max = temp;
                }
                plugin.setIntervals(min * 60, max * 60);
                sender.sendMessage(ChatColor.GREEN + "Intervalle de diffusion aléatoire entre " + min + " et " + max
                        + " minutes.");
                break;
            case "ajouter":
            case "add":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /annonces ajouter <message>");
                    return true;
                }
                String newMessage = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                plugin.addMessage(newMessage);
                sender.sendMessage(ChatColor.GREEN + "Message ajouté !");
                break;
            case "lien":
            case "link":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /annonces lien <url>");
                    return true;
                }
                plugin.setDonationLink(args[1]);
                sender.sendMessage(ChatColor.GREEN + "Lien de donation mis à jour !");
                break;
            case "reset":
                plugin.resetConfig();
                sender.sendMessage(ChatColor.GREEN + "Configuration remise à zéro !");
                break;
            case "reload":
                plugin.reloadConfig();
                plugin.loadConfiguration();
                sender.sendMessage(ChatColor.GREEN + "Configuration rechargée !");
                break;
            case "config":
                sender.sendMessage(ChatColor.YELLOW + "Utilisez /annonces interval ou /annonces range.");
                break;
            case "aide":
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
        sender.sendMessage(ChatColor.GOLD + "--- Aide Annonces ---");
        sender.sendMessage(
                ChatColor.YELLOW + "/annonces interval <min> " + ChatColor.WHITE + "- Intervalle fixe.");
        sender.sendMessage(
                ChatColor.YELLOW + "/annonces range <min>-<max> " + ChatColor.WHITE + "- Intervalle aléatoire.");
        sender.sendMessage(ChatColor.YELLOW + "/annonces ajouter <msg> " + ChatColor.WHITE + "- Ajouter un message.");
        sender.sendMessage(ChatColor.YELLOW + "/annonces lien <url> " + ChatColor.WHITE + "- Changer le lien.");
        sender.sendMessage(ChatColor.YELLOW + "/annonces reload " + ChatColor.WHITE + "- Recharger config.");
        sender.sendMessage(ChatColor.YELLOW + "/annonces aire " + ChatColor.WHITE + "- Afficher l'aide.");
    }
}
