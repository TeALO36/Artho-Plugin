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

    /** True when {@code version} (e.g. "v0.17.4") is older than major.minor.patch; unparsable counts as not older. */
    private static boolean isBefore(String version, int major, int minor, int patch) {
        try {
            String[] p = version.replace("v", "").split("\\.");
            int[] have = { Integer.parseInt(p[0]), Integer.parseInt(p[1]), p.length > 2 ? Integer.parseInt(p[2]) : 0 };
            int[] ref = { major, minor, patch };
            for (int i = 0; i < 3; i++) {
                if (have[i] != ref[i]) {
                    return have[i] < ref[i];
                }
            }
            return false;
        } catch (RuntimeException unparsable) {
            return false;
        }
    }

    /**
     * /artho link list | unlink <pseudo>. It lives under /artho because Floodgate registers
     * its own /linkaccount and /unlinkaccount, which the console cannot use and which win
     * over ours in the dispatcher; /artho has no such rival.
     */
    private void handleLink(CommandSender sender, String[] args) {
        if (args.length >= 3 && args[1].equalsIgnoreCase("unlink")) {
            // Same code path as /unlinkaccount <pseudo>, dispatched by hand for the same reason.
            plugin.getCommand("unlinkaccount").execute(sender, "unlinkaccount", new String[] { args[2] });
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("list")) {
            if (!plugin.getLinkBridge().isReady()) {
                sender.sendMessage(ChatColor.RED + "Floodgate ou sa base de liaison ne sont pas chargés.");
                return;
            }
            java.util.List<net.arthonetwork.donation.utils.LinkManager.Entry> entries = plugin.getLinkManager().entries();
            sender.sendMessage(ChatColor.GOLD + "Comptes liés (" + entries.size() + ") :");
            for (net.arthonetwork.donation.utils.LinkManager.Entry entry : entries) {
                String bedrock = entry.bedrockName.startsWith(".") ? entry.bedrockName.substring(1) : entry.bedrockName;
                String base = ChatColor.YELLOW + "  " + entry.javaUsername + ChatColor.GRAY + " <-> " + ChatColor.WHITE
                        + bedrock + ChatColor.GRAY + " (Bedrock)";
                // The plugin's record and Floodgate's database must agree: say so when they do not.
                plugin.getLinkBridge().isLinked(entry.bedrockUuid).whenComplete((known, error) ->
                        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(base
                                + (error != null ? ChatColor.RED + "  ? etat Floodgate inconnu"
                                        : known ? ChatColor.GREEN + "  OK Floodgate"
                                                : ChatColor.RED + "  ABSENT de Floodgate (rétabli à la prochaine connexion)"))));
            }
            return;
        }
        sender.sendMessage(ChatColor.RED + "Usage: /artho link <list | unlink <pseudo>>");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0) {
            String sub = args[0].toLowerCase();

            if (sub.equals("update")) {
                if (!sender.hasPermission("arthoplugin.admin")) {
                    sender.sendMessage(ChatColor.RED + "Permission refusée.");
                    return true;
                }
                if (args.length == 1 || (args.length == 2 && args[1].equalsIgnoreCase("auto"))) {
                    new net.arthonetwork.donation.utils.AutoUpdater(plugin).downloadLatest(sender);
                    return true;
                }
                if (args.length == 3 && args[1].equalsIgnoreCase("rollback")) {
                    if (isBefore(args[2], 0, 18, 1)) {
                        sender.sendMessage(ChatColor.YELLOW + "Attention : avant la 0.18.1, les mots de passe étaient lus "
                                + "dans un ancien format. Les comptes déjà migrés ne pourraient plus se connecter "
                                + "(/auth reset <joueur> les débloque).");
                    }
                    new net.arthonetwork.donation.utils.AutoUpdater(plugin).downloadVersion(args[2], sender);
                    return true;
                }
                sender.sendMessage(ChatColor.RED + "Usage: /artho update [auto | rollback <version>]");
                return true;
            }

            if (sub.equals("link")) {
                if (!sender.hasPermission("arthoplugin.admin")) {
                    sender.sendMessage(ChatColor.RED + "Permission refusée.");
                    return true;
                }
                handleLink(sender, args);
                return true;
            }

            if (sub.equals("tips")) {
                if (!sender.hasPermission("arthoplugin.admin")) {
                    sender.sendMessage(ChatColor.RED + "Permission refusée.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /artho tips <on|off>");
                    return true;
                }
                String state = args[1].toLowerCase();
                if (state.equals("on") || state.equals("enable")) {
                    plugin.setTipsEnabled(true);
                    sender.sendMessage(ChatColor.GREEN + "Astuces automatiques activées.");
                } else if (state.equals("off") || state.equals("disable")) {
                    plugin.setTipsEnabled(false);
                    sender.sendMessage(ChatColor.RED + "Astuces automatiques désactivées.");
                } else {
                    sender.sendMessage(ChatColor.RED + "Usage: /artho tips <on|off>");
                }
                return true;
            }

            if (sub.equals("anticheat")) {
                if (!sender.hasPermission("arthoplugin.admin")) {
                    sender.sendMessage(ChatColor.RED + "Permission refusée.");
                    return true;
                }

                if (args.length < 2) {
                    boolean enabled = plugin.getConfig().getBoolean("anticheat.movement.enabled", true);
                    sender.sendMessage(ChatColor.GOLD + "Anticheat mouvement (speed/fly) Status: "
                            + (enabled ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
                    sender.sendMessage(ChatColor.YELLOW + "Usage: /artho anticheat <on|off|status>");
                    return true;
                }

                String state = args[1].toLowerCase();
                if (state.equals("on") || state.equals("enable")) {
                    plugin.getConfig().set("anticheat.movement.enabled", true);
                    plugin.saveConfig();
                    sender.sendMessage(ChatColor.GREEN + "Anticheat mouvement activé.");
                } else if (state.equals("off") || state.equals("disable")) {
                    plugin.getConfig().set("anticheat.movement.enabled", false);
                    plugin.saveConfig();
                    sender.sendMessage(ChatColor.RED + "Anticheat mouvement désactivé.");
                } else if (state.equals("status")) {
                    boolean enabled = plugin.getConfig().getBoolean("anticheat.movement.enabled", true);
                    sender.sendMessage(ChatColor.GOLD + "Anticheat mouvement Status: "
                            + (enabled ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
                } else {
                    sender.sendMessage(ChatColor.RED + "Usage: /artho anticheat <on|off|status>");
                }
                return true;
            }

            if (sub.equals("antixray")) {
                if (!sender.hasPermission("arthoplugin.admin")) {
                    sender.sendMessage(ChatColor.RED + "Permission refusée.");
                    return true;
                }
                net.arthonetwork.donation.listeners.AntiXrayListener listener = plugin.getAntiXrayListener();

                if (args.length < 2) {
                    sender.sendMessage(ChatColor.GOLD + "AntiXray Status: "
                            + (listener.isEnabled() ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
                    sender.sendMessage(ChatColor.YELLOW + "Usage: /artho antixray <on|off|status>");
                    return true;
                }

                String state = args[1].toLowerCase();
                if (state.equals("on") || state.equals("enable")) {
                    listener.setEnabled(true);
                    sender.sendMessage(ChatColor.GREEN + "AntiXray activé.");
                } else if (state.equals("off") || state.equals("disable")) {
                    listener.setEnabled(false);
                    sender.sendMessage(ChatColor.RED + "AntiXray désactivé.");
                } else if (state.equals("status")) {
                    sender.sendMessage(ChatColor.GOLD + "AntiXray Status: "
                            + (listener.isEnabled() ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
                } else {
                    sender.sendMessage(ChatColor.RED + "Usage: /artho antixray <on|off|status>");
                }
                return true;
            }
        }

        sender.sendMessage(ChatColor.DARK_PURPLE + "========================================");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "          " + ChatColor.BOLD + "Aide ArthoNetwork"
                + ChatColor.RESET + ChatColor.GRAY + " (v"
                + plugin.getDescription().getVersion() + ")");
        sender.sendMessage(ChatColor.DARK_PURPLE + "========================================");

        sender.sendMessage(ChatColor.GOLD + "➤ Annonces / Donations:");
        sender.sendMessage(ChatColor.YELLOW + "  /annonces interval <min> " + ChatColor.WHITE + "- Intervalle fixe.");
        sender.sendMessage(
                ChatColor.YELLOW + "  /annonces range <min>-<max> " + ChatColor.WHITE + "- Intervalle variable.");
        sender.sendMessage(ChatColor.YELLOW + "  /annonces ajouter <msg> " + ChatColor.WHITE + "- Ajouter un message.");
        sender.sendMessage(ChatColor.YELLOW + "  /annonces lien <url> " + ChatColor.WHITE + "- Changer le lien.");
        sender.sendMessage(ChatColor.YELLOW + "  /annonces reload " + ChatColor.WHITE + "- Recharger config.");

        sender.sendMessage(ChatColor.GOLD + "➤ Authentification:");
        sender.sendMessage(ChatColor.YELLOW + "  /register <mdp> <confirm> " + ChatColor.WHITE + "- S'inscrire.");
        sender.sendMessage(ChatColor.YELLOW + "  /login <mdp> " + ChatColor.WHITE + "- Se connecter.");
        sender.sendMessage(
                ChatColor.YELLOW + "  /changepassword <new> <confirm> " + ChatColor.WHITE + "- Changer mdp.");
        sender.sendMessage(ChatColor.YELLOW + "  /linkaccount <bedrock|java> <pseudo> <mdp> " + ChatColor.WHITE
                + "- Fusionner ses comptes Bedrock et Java (un seul personnage).");
        sender.sendMessage(ChatColor.YELLOW + "  /linkaccount status " + ChatColor.WHITE + "- Voir l'état de sa liaison.");
        sender.sendMessage(ChatColor.YELLOW + "  /unlinkaccount " + ChatColor.WHITE + "- Séparer ses comptes.");
        if (sender.hasPermission("arthoplugin.admin")) {
            sender.sendMessage(ChatColor.RED + "  /auth reset <joueur> " + ChatColor.WHITE + "- Reset mdp joueur.");
            sender.sendMessage(ChatColor.RED + "  /unlinkaccount <pseudo> " + ChatColor.WHITE
                    + "- Délier les comptes d'un joueur.");
            sender.sendMessage(ChatColor.RED + "  /artho link <list|unlink <pseudo>> " + ChatColor.WHITE
                    + "- Voir/délier les comptes liés (utilisable depuis la console).");
            sender.sendMessage(ChatColor.RED + "  /auth whitelist <add|remove|list|on|off> " + ChatColor.WHITE
                    + "- Gérer whitelist.");
            sender.sendMessage(ChatColor.RED + "  /auth set <max-attempts|timeout> <valeur> " + ChatColor.WHITE
                    + "- Config auth.");
        }

        sender.sendMessage(ChatColor.GOLD + "➤ Suggestions:");
        sender.sendMessage(
                ChatColor.YELLOW + "  /suggestion ajouter <idée> " + ChatColor.WHITE + "- Proposer une idée.");
        sender.sendMessage(ChatColor.YELLOW + "  /suggestion voir " + ChatColor.WHITE + "- Voir les idées.");
        if (sender.hasPermission("arthoplugin.admin")) {
            sender.sendMessage(
                    ChatColor.RED + "  /suggestion supprimer <id> " + ChatColor.WHITE + "- Supprimer une idée.");
        }

        sender.sendMessage(ChatColor.GOLD + "➤ Utilitaires:");
        sender.sendMessage(ChatColor.YELLOW + "  /ping " + ChatColor.WHITE + "- Voir sa latence.");
        sender.sendMessage(ChatColor.YELLOW + "  /lag " + ChatColor.WHITE + "- Voir les infos de lag.");
        sender.sendMessage(ChatColor.YELLOW + "  /roulette " + ChatColor.WHITE
                + "- Tenter la roulette : bonus ou malus tire au sort.");

        sender.sendMessage(ChatColor.GOLD + "\u27a4 T\u00e9l\u00e9portation:");
        sender.sendMessage(ChatColor.YELLOW + "  /tpa <joueur> " + ChatColor.WHITE + "- Demander \u00e0 se t\u00e9l\u00e9porter.");
        sender.sendMessage(ChatColor.YELLOW + "  /tpaccept " + ChatColor.WHITE + "- Accepter une demande.");
        sender.sendMessage(ChatColor.YELLOW + "  /tpdeny " + ChatColor.WHITE + "- Refuser une demande.");
        sender.sendMessage(ChatColor.YELLOW + "  /tpcancel " + ChatColor.WHITE + "- Annuler sa demande.");
        sender.sendMessage(ChatColor.YELLOW + "  /sethome <nom> " + ChatColor.WHITE + "- Cr\u00e9er un point de retour.");
        sender.sendMessage(ChatColor.YELLOW + "  /home <nom> " + ChatColor.WHITE + "- S'y t\u00e9l\u00e9porter.");
        sender.sendMessage(ChatColor.YELLOW + "  /homes " + ChatColor.WHITE + "- Lister ses homes.");
        sender.sendMessage(ChatColor.YELLOW + "  /delhome <nom> " + ChatColor.WHITE + "- Supprimer un home.");

        if (sender.hasPermission("arthoplugin.admin")) {
            sender.sendMessage(ChatColor.GOLD + "➤ Administration:");
            sender.sendMessage(
                    ChatColor.RED + "  /artho update [auto] " + ChatColor.WHITE + "- Forcer la mise à jour.");
            sender.sendMessage(ChatColor.RED + "  /artho tips <on|off> " + ChatColor.WHITE + "- Activer astuces.");
            sender.sendMessage(
                    ChatColor.RED + "  /artho antixray <on|off> " + ChatColor.WHITE + "- Gérer l'Anti-Xray.");
            sender.sendMessage(ChatColor.RED + "  /artho anticheat <on|off> " + ChatColor.WHITE
                    + "- Gérer l'anticheat speed/fly.");
            sender.sendMessage(ChatColor.RED + "  /variant enable " + ChatColor.WHITE
                    + "- Activer le module Variantes Liées (désactivé par défaut).");
            if (sender.hasPermission("arthoplugin.vanish")) {
                sender.sendMessage(ChatColor.RED + "  /vanish " + ChatColor.WHITE
                        + "- Se rendre invisible (simule une déconnexion).");
            }
            if (sender.hasPermission("arthoplugin.roulette.admin")) {
                sender.sendMessage(ChatColor.RED + "  /roulette start " + ChatColor.WHITE
                        + "- Lancer la roulette gratuitement.");
                sender.sendMessage(ChatColor.RED + "  /roulette reload " + ChatColor.WHITE
                        + "- Recharger la table de la roulette.");
            }
        }

        sender.sendMessage(ChatColor.DARK_PURPLE + "========================================");
        return true;
    }
}
