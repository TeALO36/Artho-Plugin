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
        if (args.length > 0 && args[0].equalsIgnoreCase("update")) {
            if (!sender.hasPermission("arthoplugin.admin")) {
                sender.sendMessage(ChatColor.RED + "Permission refusée.");
                return true;
            }
            if (args.length == 2 && args[1].equalsIgnoreCase("auto")) {
                new net.arthonetwork.donation.utils.AutoUpdater(plugin).downloadLatest(sender);
                return true;
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("rollback")) {
                new net.arthonetwork.donation.utils.AutoUpdater(plugin).downloadVersion(args[2], sender);
                return true;
            }
            sender.sendMessage(ChatColor.RED + "Usage: /artho update <auto | rollback <version>>");
            return true;
        }

        sender.sendMessage(ChatColor.DARK_PURPLE + "========================================");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "           Artho-Plugin " + ChatColor.GRAY + "v"
                + plugin.getDescription().getVersion());
        sender.sendMessage(ChatColor.DARK_PURPLE + "========================================");

        sender.sendMessage(ChatColor.GOLD + "➤ Donation / Broadcast:");
        sender.sendMessage(ChatColor.YELLOW + "  /don fix <min> " + ChatColor.WHITE + "- Intervalle fixe.");
        sender.sendMessage(
                ChatColor.YELLOW + "  /don variable <min>-<max> " + ChatColor.WHITE + "- Intervalle variable.");
        sender.sendMessage(ChatColor.YELLOW + "  /don add <msg> " + ChatColor.WHITE + "- Ajouter un message.");
        sender.sendMessage(ChatColor.YELLOW + "  /don link <url> " + ChatColor.WHITE + "- Changer le lien.");
        sender.sendMessage(ChatColor.YELLOW + "  /don enable/disable " + ChatColor.WHITE + "- Activer/Désactiver.");
        sender.sendMessage(ChatColor.YELLOW + "  /don reload " + ChatColor.WHITE + "- Recharger config.");

        sender.sendMessage(ChatColor.GOLD + "➤ Authentification:");
        sender.sendMessage(ChatColor.YELLOW + "  /register <mdp> <confirm> " + ChatColor.WHITE + "- S'inscrire.");
        sender.sendMessage(ChatColor.YELLOW + "  /login <mdp> " + ChatColor.WHITE + "- Se connecter.");
        sender.sendMessage(
                ChatColor.YELLOW + "  /changepassword <new> <confirm> " + ChatColor.WHITE + "- Changer mdp.");
        if (sender.hasPermission("arthoplugin.admin")) {
            sender.sendMessage(ChatColor.RED + "  /auth reset <joueur> " + ChatColor.WHITE + "- Reset mdp joueur.");
            sender.sendMessage(ChatColor.RED + "  /auth whitelist <add|remove|list|on|off> " + ChatColor.WHITE
                    + "- Gérer whitelist.");
            sender.sendMessage(ChatColor.RED + "  /auth set <max-attempts|timeout> <valeur> " + ChatColor.WHITE
                    + "- Config auth.");
        }

        sender.sendMessage(ChatColor.GOLD + "➤ Suggestions:");
        sender.sendMessage(ChatColor.YELLOW + "  /server add <idée> " + ChatColor.WHITE + "- Proposer une idée.");
        sender.sendMessage(ChatColor.YELLOW + "  /server list " + ChatColor.WHITE + "- Voir les idées.");
        if (sender.hasPermission("arthoplugin.admin")) {
            sender.sendMessage(ChatColor.RED + "  /server remove <id> " + ChatColor.WHITE + "- Supprimer une idée.");
        }

        sender.sendMessage(ChatColor.GOLD + "➤ Utilitaires:");
        sender.sendMessage(ChatColor.YELLOW + "  /ping " + ChatColor.WHITE + "- Voir sa latence.");
        sender.sendMessage(ChatColor.YELLOW + "  /lag " + ChatColor.WHITE + "- Voir les infos de lag.");

        if (sender.hasPermission("arthoplugin.admin")) {
            sender.sendMessage(ChatColor.GOLD + "➤ Mises à jour:");
            sender.sendMessage(ChatColor.RED + "  /artho update auto " + ChatColor.WHITE + "- Forcer la mise à jour.");
            sender.sendMessage(ChatColor.RED + "  /artho update rollback <version> " + ChatColor.WHITE
                    + "- Revenir à une version.");
        }

        sender.sendMessage(ChatColor.DARK_PURPLE + "========================================");
        return true;
    }
}
