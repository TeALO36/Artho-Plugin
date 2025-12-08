package net.arthonetwork.donation.commands;

import net.arthonetwork.donation.utils.SuggestionManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.Map;

public class SuggestionCommand implements CommandExecutor {

    private final SuggestionManager suggestionManager;

    public SuggestionCommand(SuggestionManager suggestionManager) {
        this.suggestionManager = suggestionManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("ajouter") || sub.equals("add")) {
            handleAdd(sender, args);
        } else if (sub.equals("voir") || sub.equals("list")) {
            handleList(sender);
        } else if (sub.equals("supprimer") || sub.equals("remove")) {
            // Permission check moved to handleRemove
            handleRemove(sender, args);
        } else {
            sendHelp(sender);
        }
        return true;
    }

    private void handleAdd(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /suggestion ajouter <votre idée>");
            return;
        }
        String text = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        suggestionManager.addSuggestion(sender.getName(), text);
        sender.sendMessage(ChatColor.GREEN + "Suggestion envoyée avec succès !");
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /suggestion supprimer <id>");
            return;
        }
        String id = args[1];
        String author = suggestionManager.getSuggestionAuthor(id);

        if (author == null) {
            sender.sendMessage(ChatColor.RED + "Suggestion introuvable.");
            return;
        }

        boolean isAdmin = sender.hasPermission("arthoplugin.admin") || sender.isOp();
        boolean isAuthor = author.equals(sender.getName());

        if (!isAdmin && !isAuthor) {
            sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission de supprimer cette suggestion.");
            return;
        }

        if (suggestionManager.removeSuggestion(id, sender.getName())) {
            sender.sendMessage(ChatColor.GREEN + "Suggestion #" + id + " supprimée.");
        } else {
            sender.sendMessage(ChatColor.RED + "Erreur lors de la suppression.");
        }
    }

    private void handleList(CommandSender sender) {
        Map<String, String> suggestions = suggestionManager.getSuggestions();
        if (suggestions.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Aucune suggestion active pour le moment.");
            return;
        }
        sender.sendMessage(ChatColor.GOLD + "--- Liste des Suggestions (ID - Contenu) ---");
        // Update display to show ID for easier deletion
        for (Map.Entry<String, String> entry : suggestions.entrySet()) {
            String id = entry.getKey();
            // We need to parse the value slightly or just prepend ID
            // Value format from manager: "&e" + date + " &7- &b" + author + "&7: &f" +
            // content
            sender.sendMessage(
                    ChatColor.YELLOW + "#" + id + " " + ChatColor.translateAlternateColorCodes('&', entry.getValue()));
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "--- Aide Suggestions ---");
        sender.sendMessage(ChatColor.YELLOW + "/suggestion ajouter <idée> " + ChatColor.WHITE + "- Proposer une idée");
        sender.sendMessage(ChatColor.YELLOW + "/suggestion voir " + ChatColor.WHITE + "- Voir les suggestions");
        sender.sendMessage(ChatColor.YELLOW + "/suggestion supprimer <id> " + ChatColor.WHITE
                + "- Supprimer une suggestion (Vos suggestions ou Admin).");
    }
}
