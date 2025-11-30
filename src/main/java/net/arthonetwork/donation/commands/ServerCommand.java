package net.arthonetwork.donation.commands;

import net.arthonetwork.donation.utils.SuggestionManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;
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
        if (sub.equals("help")) {
            sendHelp(sender);
        } else if (sub.equals("add")) {
            handleAdd(sender, args);
        } else if (sub.equals("list")) {
            handleList(sender);
        } else if (sub.equals("remove")) {
            if (!sender.hasPermission("arthoplugin.admin")) {
                sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission.");
                return true;
            }
            handleRemove(sender, args);
        } else {
            sendHelp(sender);
        }
        return true;
    }

    private void handleAdd(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /server add <texte>");
            return;
        }
        String text = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        suggestionManager.addSuggestion(sender.getName(), text);
        sender.sendMessage(ChatColor.GREEN + "Suggestion envoyée !");
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /server remove <id>");
            return;
        }
        try {
            int id = Integer.parseInt(args[1]);
            if (suggestionManager.removeSuggestion(id)) {
                sender.sendMessage(ChatColor.GREEN + "Suggestion #" + id + " supprimée.");
            } else {
                sender.sendMessage(ChatColor.RED + "Suggestion introuvable.");
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "L'ID doit être un nombre.");
        }
    }

    private void handleList(CommandSender sender) {
        List<String> suggestions = suggestionManager.getSuggestions();
        if (suggestions.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Aucune suggestion active.");
            return;
        }
        sender.sendMessage(ChatColor.GOLD + "--- Suggestions ---");
        for (String s : suggestions) {
            sender.sendMessage(ChatColor.YELLOW + s);
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "--- ArthoNetwork Suggestions ---");
        sender.sendMessage(ChatColor.YELLOW + "/server add <texte> : Proposer une idée");
        sender.sendMessage(ChatColor.YELLOW + "/server list : Voir les suggestions");
        if (sender.hasPermission("arthoplugin.admin")) {
            sender.sendMessage(
                    ChatColor.RED + "/server remove <id> " + ChatColor.WHITE + "- Supprimer une suggestion (Admin).");
        }
    }
}
