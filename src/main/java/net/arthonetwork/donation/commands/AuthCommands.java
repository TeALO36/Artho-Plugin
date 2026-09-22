package net.arthonetwork.donation.commands;

import net.arthonetwork.donation.ArthoPlugin;
import net.arthonetwork.donation.utils.AuthManager;
import net.arthonetwork.donation.utils.InviteCodeManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.UUID;

/**
 * /register, /login, /changepassword et l'admin /auth.
 *
 * <p>Depuis l'incident du 22/09/2026 (compte look-alike cree librement via
 * /register), l'inscription exige un code d'invitation distribue par un
 * administrateur : /auth invite add|generate|remove|list. Le mot de passe seul
 * ne peut pas suffire en mode crack : le pseudo est la seule identite presente
 * par le client, n'importe qui peut le taper, donc CREER un compte sur un
 * pseudo doit etre verrouille.
 */
public class AuthCommands implements CommandExecutor {

    private final ArthoPlugin plugin;
    private final AuthManager authManager;
    private final InviteCodeManager inviteCodes;

    public AuthCommands(ArthoPlugin plugin, AuthManager authManager, InviteCodeManager inviteCodes) {
        this.plugin = plugin;
        this.authManager = authManager;
        this.inviteCodes = inviteCodes;
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
            handleRegister(player, uuid, args);
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
            if (authManager.isIpBanned(ip)) {
                player.kickPlayer(ChatColor.RED + "Votre IP est bannie suite à trop de tentatives suspectes.");
                return true;
            }
            if (authManager.isIpBlocked(ip)) {
                long remaining = authManager.getRemainingTime(ip);
                player.kickPlayer(ChatColor.RED + "Trop de tentatives. Bloqué pour encore " + remaining + " secondes.");
                return true;
            }

            if (authManager.login(uuid, args[0], ip)) {
                authManager.resetAttempts(ip);
                player.sendMessage(plugin.getAuthMessage("success-login"));
            } else {
                authManager.incrementAttempts(ip);
                player.sendMessage(plugin.getAuthMessage("wrong-password"));
            }
            return true;
        }

        if (command.getName().equalsIgnoreCase("changepassword")) {
            if (!authManager.isLoggedIn(uuid)) {
                player.sendMessage(plugin.getAuthMessage("not-logged-in"));
                return true;
            }
            if (args.length != 2) {
                player.sendMessage(plugin.getAuthMessage("usage-changepassword"));
                return true;
            }
            if (!args[0].equals(args[1])) {
                player.sendMessage(plugin.getAuthMessage("passwords-do-not-match"));
                return true;
            }
            authManager.changePassword(uuid, args[0]);
            player.sendMessage(plugin.getAuthMessage("password-changed"));
            return true;
        }

        if (command.getName().equalsIgnoreCase("auth")) {
            handleAdminAuth(sender, args);
        }

        return true;
    }

    /**
     * /register <code> <mdp> <confirmation>. Le code d'invitation est obligatoire
     * par defaut (auth.invite-codes.enabled). L'ancienne forme a deux arguments
     * est refusee avec un message clair plutot que de creer un compte sans garde.
     */
    private void handleRegister(Player player, UUID uuid, String[] args) {
        if (authManager.isRegistered(uuid)) {
            player.sendMessage(plugin.getAuthMessage("already-registered"));
            return;
        }
        if (inviteCodes.isRequired()) {
            if (args.length != 3) {
                player.sendMessage(plugin.getAuthMessage("register-instruction"));
                return;
            }
        } else if (args.length != 2) {
            player.sendMessage(plugin.getAuthMessage("usage-register"));
            return;
        }

        String code;
        String password;
        String confirm;
        if (inviteCodes.isRequired()) {
            code = args[0];
            password = args[1];
            confirm = args[2];
        } else {
            code = null;
            password = args[0];
            confirm = args[1];
        }

        if (!password.equals(confirm)) {
            player.sendMessage(plugin.getAuthMessage("passwords-do-not-match"));
            return;
        }
        if (password.length() < 6) {
            player.sendMessage(plugin.getAuthMessage("password-too-short"));
            return;
        }

        String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "inconnue";
        if (code != null) {
            String error = inviteCodes.redeem(code, player.getName(), ip);
            if (error != null) {
                player.sendMessage(error);
                return;
            }
        }

        authManager.register(uuid, password, ip);
        player.sendMessage(plugin.getAuthMessage("success-register"));
    }

    private void handleAdminAuth(CommandSender sender, String[] args) {
        if (!sender.hasPermission("arthoplugin.admin") && !sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission.");
            return;
        }

        if (args.length < 1) {
            sendAdminHelp(sender);
            return;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "unregister":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /auth unregister <player>");
                    return;
                }
                String targetName = args[1];
                OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                if (target != null && (target.isOnline() || target.hasPlayedBefore()
                        || authManager.isRegistered(target.getUniqueId()))) {
                    authManager.unregister(target.getUniqueId());
                    sender.sendMessage(ChatColor.GREEN + "Joueur " + targetName + " désenregistré.");
                } else {
                    sender.sendMessage(ChatColor.RED + "Joueur introuvable.");
                }
                break;
            case "reset":
                handleReset(sender, args);
                break;
            case "whitelist":
                handleWhitelist(sender, args);
                break;
            case "enable":
                authManager.setAuthEnabled(true);
                sender.sendMessage(ChatColor.GREEN + "Système d'authentification activé.");
                break;
            case "disable":
                authManager.setAuthEnabled(false);
                sender.sendMessage(ChatColor.RED + "Système d'authentification désactivé.");
                break;
            case "set":
                handleSet(sender, args);
                break;
            case "security":
                handleSecurity(sender, args);
                break;
            case "invite":
                handleInvite(sender, args);
                break;
            default:
                sendAdminHelp(sender);
                break;
        }
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /auth reset <player>");
            return;
        }
        String targetName = args[1];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (target != null && authManager.isRegistered(target.getUniqueId())) {
            String newPass = authManager.resetPassword(target.getUniqueId());
            String msg = plugin.getAuthMessage("admin-reset-success").replace("$player", targetName);

            net.md_5.bungee.api.chat.TextComponent message = new net.md_5.bungee.api.chat.TextComponent(msg + newPass);
            message.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                    net.md_5.bungee.api.chat.ClickEvent.Action.COPY_TO_CLIPBOARD, newPass));
            message.setHoverEvent(
                    new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                            new net.md_5.bungee.api.chat.ComponentBuilder(plugin.getAuthMessage("admin-reset-hover"))
                                    .create()));

            sender.spigot().sendMessage(message);
        } else {
            sender.sendMessage(ChatColor.RED + "Joueur introuvable ou non enregistré.");
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
            } else if (setting.equals("max-blocks-before-ban")) {
                authManager.setMaxBlocksBeforeBan(value);
                sender.sendMessage(ChatColor.GREEN + "Ban définitif après " + value + " blocages répétés.");
            } else {
                sender.sendMessage(ChatColor.RED + "Paramètre inconnu.");
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "La valeur doit être un nombre entier.");
        }
    }

    /** Bans persistants du plugin : IPs et comptes (UUID), audit des hachages legacy. */
    private void handleSecurity(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /auth security <banned|ban-ip|unban-ip|ban-uuid|unban-uuid|audit>");
            return;
        }
        String action = args[1].toLowerCase();
        switch (action) {
            case "banned":
                List<String> banned = authManager.getBannedIps();
                sender.sendMessage(ChatColor.GOLD + "IPs bannies (" + banned.size() + "): "
                        + ChatColor.WHITE + (banned.isEmpty() ? "aucune" : String.join(", ", banned)));
                List<String> uuidBans = authManager.getBannedUuids();
                sender.sendMessage(ChatColor.GOLD + "Comptes bannis (" + uuidBans.size() + "): "
                        + ChatColor.WHITE + (uuidBans.isEmpty() ? "aucun" : String.join(", ", uuidBans)));
                break;
            case "ban-ip":
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /auth security ban-ip <ip>");
                    return;
                }
                if (authManager.isIpBanned(args[2])) {
                    sender.sendMessage(ChatColor.YELLOW + "IP déjà bannie.");
                } else {
                    authManager.banIp(args[2]);
                    sender.sendMessage(ChatColor.GREEN + "IP " + args[2] + " bannie (plugin, persistant).");
                }
                break;
            case "unban-ip":
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /auth security unban-ip <ip>");
                    return;
                }
                authManager.unbanIp(args[2]);
                sender.sendMessage(ChatColor.GREEN + "IP " + args[2] + " débannie.");
                break;
            case "ban-uuid":
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /auth security ban-uuid <joueur|uuid>");
                    return;
                }
                handleBanUuid(sender, args[2]);
                break;
            case "unban-uuid":
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /auth security unban-uuid <joueur|uuid>");
                    return;
                }
                if (authManager.unbanUuid(args[2])) {
                    sender.sendMessage(ChatColor.GREEN + "Compte " + args[2] + " débanni.");
                } else {
                    sender.sendMessage(ChatColor.RED + "Ban introuvable pour " + args[2] + ".");
                }
                break;
            case "audit":
                List<String> legacy = authManager.auditLegacyHashes();
                if (legacy.isEmpty()) {
                    sender.sendMessage(ChatColor.GREEN + "Tous les hachages sont en PBKDF2. Rien à faire.");
                } else {
                    sender.sendMessage(ChatColor.YELLOW + "Comptes encore en SHA-256 nu (" + legacy.size()
                            + ") - migrés automatiquement à leur prochain /login :");
                    for (String entry : legacy) {
                        sender.sendMessage(ChatColor.WHITE + "  - " + entry);
                    }
                }
                break;
            default:
                sender.sendMessage(ChatColor.RED + "Usage: /auth security <banned|ban-ip|unban-ip|ban-uuid|unban-uuid|audit>");
                break;
        }
    }

    /** Ban compte par pseudo ou UUID : accepte un pseudo en ligne, connu du serveur ou enregistré. */
    private void handleBanUuid(CommandSender sender, String input) {
        UUID uuid = null;
        String name = input;
        try {
            uuid = UUID.fromString(input);
        } catch (IllegalArgumentException notAnUuid) {
            OfflinePlayer p = Bukkit.getPlayerExact(input);
            if (p == null) {
                p = Bukkit.getOfflinePlayer(input);
            }
            if (p != null && (p.isOnline() || p.hasPlayedBefore() || authManager.isRegistered(p.getUniqueId()))) {
                uuid = p.getUniqueId();
                name = p.getName() != null ? p.getName() : input;
            }
        }
        if (uuid == null) {
            sender.sendMessage(ChatColor.RED + "Joueur introuvable : " + input);
            return;
        }
        if (authManager.banUuid(uuid, name)) {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                online.kickPlayer(ChatColor.RED + "Votre compte a été banni par un administrateur.");
            }
            sender.sendMessage(ChatColor.GREEN + "Compte " + name + " (" + uuid + ") banni (plugin, persistant).");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Ce compte est déjà banni.");
        }
    }

    /** Gestion des codes d'invitation. */
    private void handleInvite(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /auth invite <generate|add|remove|list> [args]");
            return;
        }
        String action = args[1].toLowerCase();
        switch (action) {
            case "generate":
                int uses = parseUses(sender, args, 2);
                String note = joinNote(args, 3);
                String code = inviteCodes.generate(uses, note);
                sender.sendMessage(ChatColor.GREEN + "Code généré (" + uses + " utilisation(s)) : "
                        + ChatColor.WHITE + code + ChatColor.GRAY + (note.isEmpty() ? "" : " - " + note));
                break;
            case "add":
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /auth invite add <code> [utilisations] [note]");
                    return;
                }
                int addUses = parseUses(sender, args, 3);
                if (inviteCodes.add(args[2], addUses, joinNote(args, 4))) {
                    sender.sendMessage(ChatColor.GREEN + "Code ajouté : " + ChatColor.WHITE + args[2]);
                } else {
                    sender.sendMessage(ChatColor.RED + "Code refusé (déjà existant ou trop court).");
                }
                break;
            case "remove":
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /auth invite remove <code>");
                    return;
                }
                if (inviteCodes.remove(args[2])) {
                    sender.sendMessage(ChatColor.GREEN + "Code supprimé.");
                } else {
                    sender.sendMessage(ChatColor.RED + "Code introuvable.");
                }
                break;
            case "list":
                java.util.List<String> codes = inviteCodes.listCodes();
                if (codes.isEmpty()) {
                    sender.sendMessage(ChatColor.YELLOW + "Aucun code actif : la registration est FERMÉE de fait.");
                } else {
                    sender.sendMessage(ChatColor.GOLD + "Codes d'invitation (" + codes.size() + ") :");
                    for (String line : codes) {
                        sender.sendMessage(ChatColor.WHITE + "  " + line);
                    }
                }
                break;
            default:
                sender.sendMessage(ChatColor.RED + "Usage: /auth invite <generate|add|remove|list> [args]");
                break;
        }
    }

    private int parseUses(CommandSender sender, String[] args, int index) {
        if (args.length > index) {
            try {
                return Math.max(1, Integer.parseInt(args[index]));
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.YELLOW + "Nombre d'utilisations invalide, 1 utilisé.");
            }
        }
        return 1;
    }

    private String joinNote(String[] args, int from) {
        if (args.length <= from) {
            return "";
        }
        return String.join(" ", java.util.Arrays.copyOfRange(args, from, args.length));
    }

    private void sendAdminHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "--- ArthoAuth Admin ---");
        sender.sendMessage(ChatColor.YELLOW + "/auth enable/disable");
        sender.sendMessage(ChatColor.YELLOW + "/auth unregister <player>");
        sender.sendMessage(ChatColor.YELLOW + "/auth reset <player>");
        sender.sendMessage(ChatColor.YELLOW + "/auth whitelist <add|remove|list|on|off>");
        sender.sendMessage(ChatColor.YELLOW + "/auth set <max-attempts|timeout|max-blocks-before-ban> <value>");
        sender.sendMessage(ChatColor.YELLOW + "/auth security <banned|ban-ip|unban-ip|ban-uuid|unban-uuid|audit>");
        sender.sendMessage(ChatColor.YELLOW + "/auth invite <generate|add|remove|list>");
    }
}
