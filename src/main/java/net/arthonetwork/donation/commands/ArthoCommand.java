package net.arthonetwork.donation.commands;

import net.arthonetwork.donation.ArthoPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ArthoCommand implements CommandExecutor {

    private final ArthoPlugin plugin;

    public ArthoCommand(ArthoPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage(ChatColor.DARK_PURPLE + "========================================");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "           Artho-Plugin " + ChatColor.GRAY + "v"
                + plugin.getDescription().getVersion());
        sender.sendMessage(ChatColor.DARK_PURPLE + "========================================");

        sender.sendMessage(ChatColor.GOLD + "➤ Donation / Broadcast:");
        sender.sendMessage(ChatColor.YELLOW + "  /don fix <min> " + ChatColor.WHITE + "- Intervalle fixe.");
        sender.sendMessage(
                ChatColor.YELLOW + "  /don variable <min>-<max> " + ChatColor.WHITE + "- Intervalle variable.");
        sender.sendMessage(ChatColor.YELLOW + "  /don add <msg> " + ChatColor.WHITE + "- Ajouter un message.");

        sender.sendMessage(ChatColor.GOLD + "➤ Authentification:");
        sender.sendMessage(ChatColor.YELLOW + "  /register <mdp> <confirm> " + ChatColor.WHITE + "- S'inscrire.");
        sender.sendMessage(ChatColor.YELLOW + "  /login <mdp> " + ChatColor.WHITE + "- Se connecter.");
        sender.sendMessage(
                ChatColor.YELLOW + "  /changepassword <new> <confirm> " + ChatColor.WHITE + "- Changer mdp.");
        if (sender.hasPermission("arthoplugin.admin")) {
            sender.sendMessage(ChatColor.RED + "  /auth reset <joueur> " + ChatColor.WHITE + "- Reset mdp joueur.");
            sender.sendMessage(ChatColor.RED + "  /auth whitelist ... " + ChatColor.WHITE + "- Gérer whitelist.");
        }

        sender.sendMessage(ChatColor.GOLD + "➤ Suggestions:");
        sender.sendMessage(ChatColor.YELLOW + "  /server add <idée> " + ChatColor.WHITE + "- Proposer une idée.");
        sender.sendMessage(ChatColor.YELLOW + "  /server list " + ChatColor.WHITE + "- Voir les idées.");

        sender.sendMessage(ChatColor.GOLD + "➤ Utilitaires:");
        sender.sendMessage(ChatColor.YELLOW + "  /ping " + ChatColor.WHITE + "- Voir sa latence.");
        sender.sendMessage(ChatColor.YELLOW + "  /lag " + ChatColor.WHITE + "- Voir les infos de lag.");

        sender.sendMessage(ChatColor.DARK_PURPLE + "========================================");
        return true;
    }
}
