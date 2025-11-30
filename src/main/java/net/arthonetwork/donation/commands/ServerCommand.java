package net.arthonetwork.donation.commands;

import net.arthonetwork.donation.utils.SuggestionManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Map;

public class ServerCommand implements CommandExecutor {

    private final SuggestionManager suggestionManager;

    public ServerCommand(SuggestionManager suggestionManager) {
        this.suggestionManager = suggestionManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "help":
                sendHelp(sender);
                break;
            case "add":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /server add <votre suggestion>");
                    return true;
                }
                StringBuilder contentBuilder = new StringBuilder();
                for (int i = 1; i < args.length; i++) {
                    contentBuilder.append(args[i]).append(" ");
                }
                String content = contentBuilder.toString().trim();
                suggestionManager.addSuggestion(sender.getName(), content);
                sender.sendMessage(ChatColor.GREEN + "Merci ! Votre suggestion a été ajoutée.");
                break;
            case "list":
                sender.sendMessage(ChatColor.GOLD + "--- Liste des Suggestions ---");
                Map<String, String> suggestions = suggestionManager.getSuggestions();
                if (suggestions.isEmpty()) {
                    sender.sendMessage(ChatColor.GRAY + "Aucune suggestion pour le moment.");
                } else {
                    for (Map.Entry<String, String> entry : suggestions.entrySet()) {
                        sender.sendMessage(ChatColor.YELLOW + "ID: " + entry.getKey() + " "
                                + ChatColor.translateAlternateColorCodes('&', entry.getValue()));
                    }
                }
                break;
            case "remove":
                if (!sender.hasPermission("arthodonation.admin")) {
                    sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission de supprimer des suggestions.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /server remove <id>");
                    return true;
                }
                String id = args[1];
                if (suggestionManager.removeSuggestion(id, sender.getName())) {
                    sender.sendMessage(ChatColor.GREEN + "Suggestion supprimée avec succès.");
                } else {
                    sender.sendMessage(ChatColor.RED + "ID introuvable.");
                }
                break;
            default:
                sender.sendMessage(ChatColor.RED + "Commande inconnue.");
                sendHelp(sender);
                break;
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "--- Aide /server ---");
        sender.sendMessage(
                ChatColor.YELLOW + "/server add <texte> " + ChatColor.WHITE + "- Proposer une fonctionnalité.");
        sender.sendMessage(ChatColor.YELLOW + "/server list " + ChatColor.WHITE + "- Voir les suggestions actuelles.");
        sender.sendMessage(ChatColor.YELLOW + "/server help " + ChatColor.WHITE + "- Afficher cette aide.");
        if (sender.hasPermission("arthodonation.admin")) {
            sender.sendMessage(
                    ChatColor.RED + "/server remove <id> " + ChatColor.WHITE + "- Supprimer une suggestion (Admin).");
        }
    }
}
