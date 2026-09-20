package net.arthonetwork.donation.commands;

import net.arthonetwork.donation.ArthoPlugin;
import net.arthonetwork.donation.utils.AccountLinkService;
import net.arthonetwork.donation.utils.AuthManager;
import net.arthonetwork.donation.utils.FloodgateLinkBridge;
import net.arthonetwork.donation.utils.LinkManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import java.util.UUID;

/**
 * /linkaccount <bedrock|java> <Pseudo> <MotDePasse> | status, and /unlinkaccount [pseudo].
 *
 * <p>Linking makes two accounts ONE character: once linked, the Bedrock account
 * logs in as the Java account at every connection (same UUID, same name), so its
 * homes, inventory, ender chest, experience and progress are the Java account's.
 * That is done by Floodgate's own link database; this command is the friendly
 * front end that proves ownership first.
 *
 * <p>The first argument names the platform of the ACCOUNT YOU WANT TO LINK TO
 * (the destination), which must be the opposite of the one you are connected
 * with. The destination's password, checked through AuthManager, proves you own
 * it. A Bedrock player is already authenticated by Xbox Live, so once linked
 * they need no password at all on later connections (see BedrockAutoLoginListener).
 */
public class LinkAccountCommand implements CommandExecutor {

    private final ArthoPlugin plugin;
    private final AuthManager authManager;
    private final LinkManager linkManager;
    private final AccountLinkService service;
    private final FloodgateLinkBridge bridge;

    public LinkAccountCommand(ArthoPlugin plugin, AuthManager authManager, LinkManager linkManager,
            AccountLinkService service, FloodgateLinkBridge bridge) {
        this.plugin = plugin;
        this.authManager = authManager;
        this.linkManager = linkManager;
        this.service = service;
        this.bridge = bridge;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!bridge.isReady()) {
            sender.sendMessage(ChatColor.RED + "La liaison de compte n'est pas disponible "
                    + "(Floodgate ou sa base de liaison ne sont pas chargés).");
            return true;
        }
        if (command.getName().equalsIgnoreCase("unlinkaccount")) {
            handleUnlink(sender, args);
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Seuls les joueurs peuvent utiliser cette commande.");
            return true;
        }
        Player player = (Player) sender;

        if (args.length == 1 && args[0].equalsIgnoreCase("status")) {
            handleStatus(player);
            return true;
        }
        if (args.length != 3 || !(args[0].equalsIgnoreCase("bedrock") || args[0].equalsIgnoreCase("java"))) {
            player.sendMessage(ChatColor.RED + "Usage: /linkaccount <bedrock|java> <Pseudo> <MotDePasse>");
            player.sendMessage(ChatColor.GRAY + "Précisez le type du compte DESTINATION (celui auquel vous voulez vous lier).");
            player.sendMessage(ChatColor.GRAY + "/linkaccount status" + ChatColor.DARK_GRAY + " - voir l'état de votre liaison.");
            return true;
        }

        String targetType = args[0].toLowerCase();
        String pseudo = args[1];
        String password = args[2];
        String ip = player.getAddress().getAddress().getHostAddress();

        if (authManager.isIpBanned(ip)) {
            player.sendMessage(ChatColor.RED + "Votre IP est bannie suite à trop de tentatives suspectes.");
            return true;
        }
        if (authManager.isIpBlocked(ip)) {
            player.sendMessage(ChatColor.RED + "Trop de tentatives. Réessayez dans "
                    + authManager.getRemainingTime(ip) + " secondes.");
            return true;
        }

        FloodgatePlayer bedrockSession = bedrockSession(player);
        boolean callerIsBedrock = bedrockSession != null;

        if (callerIsBedrock && targetType.equals("bedrock")) {
            player.sendMessage(ChatColor.RED
                    + "Vous jouez déjà en Bedrock. Utilisez /linkaccount java <PseudoJava> <MotDePasse>.");
            return true;
        }
        if (!callerIsBedrock && targetType.equals("java")) {
            player.sendMessage(ChatColor.RED
                    + "Vous jouez déjà en Java. Utilisez /linkaccount bedrock <PseudoBedrock> <MotDePasse>.");
            return true;
        }

        if (callerIsBedrock) {
            linkBedrockCallerToJavaTarget(player, bedrockSession, ip, pseudo, password);
        } else {
            linkJavaCallerToBedrockTarget(player, ip, pseudo, password);
        }
        return true;
    }

    // ------------------------------------------------------------------ linking

    /** Caller is on Bedrock, wants to become an existing Java account. */
    private void linkBedrockCallerToJavaTarget(Player caller, FloodgatePlayer session, String ip,
            String javaPseudo, String password) {
        // Identity of the Bedrock ACCOUNT (derived from its XUID). Once linked, the
        // session's own Bukkit UUID is the Java account's instead, so it can't be used here.
        UUID bedrockUuid = session.getJavaUniqueId();

        if (session.isLinked() || linkManager.isBedrockLinked(bedrockUuid)) {
            caller.sendMessage(ChatColor.RED + "Votre compte est déjà lié à "
                    + linkManager.getLinkedJavaUsername(bedrockUuid) + ". /unlinkaccount pour le délier.");
            return;
        }

        UUID javaUuid = Bukkit.getOfflinePlayer(javaPseudo).getUniqueId();

        if (FloodgateApi.getInstance().isFloodgateId(javaUuid)) {
            caller.sendMessage(ChatColor.RED + "Ce pseudo correspond à un compte Bedrock, pas Java.");
            return;
        }
        if (linkManager.isJavaLinked(javaUuid)) {
            caller.sendMessage(ChatColor.RED + "Ce compte Java est déjà lié à un autre joueur Bedrock.");
            return;
        }
        if (!authManager.isRegistered(javaUuid) || !authManager.checkPassword(javaUuid, password)) {
            authManager.incrementAttempts(ip);
            caller.sendMessage(ChatColor.RED + "Compte introuvable ou mot de passe incorrect.");
            return;
        }

        authManager.resetAttempts(ip);
        caller.sendMessage(ChatColor.GRAY + "Liaison en cours...");
        service.link(bedrockUuid, javaUuid, session.getUsername(), javaPseudo).whenComplete((result, error) -> {
            if (error != null) {
                failed(caller, session.getUsername(), error);
                return;
            }
            caller.sendMessage(ChatColor.GREEN + "✔ Compte lié avec succès à " + javaPseudo + " !");
            caller.sendMessage(ChatColor.YELLOW + "Vous allez être reconnecté pour retrouver son personnage : "
                    + "inventaire, homes, expérience, progression.");
            describeHomes(caller, result);
            caller.sendMessage(ChatColor.GRAY + "Votre ancien personnage Bedrock est sauvegardé. "
                    + "Ensuite, plus rien à saisir : la connexion est automatique.");
            kickLater(caller, ChatColor.GREEN + "Compte lié à " + javaPseudo + " !\n"
                    + ChatColor.WHITE + "Reconnectez-vous pour retrouver votre personnage.");
        });
    }

    /** Caller is on Java, wants to link an existing Bedrock account to their own. */
    private void linkJavaCallerToBedrockTarget(Player caller, String ip, String bedrockPseudo, String password) {
        UUID javaUuid = caller.getUniqueId();

        if (linkManager.isJavaLinked(javaUuid)) {
            caller.sendMessage(ChatColor.RED + "Votre compte est déjà lié à "
                    + plain(linkManager.getLinkedBedrockName(javaUuid)) + ". /unlinkaccount pour le délier.");
            return;
        }

        caller.sendMessage(ChatColor.GRAY + "Recherche du compte Bedrock " + bedrockPseudo + "...");

        FloodgateApi.getInstance().getUuidFor(bedrockPseudo).whenComplete((bedrockUuid, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!caller.isOnline()) {
                        return;
                    }
                    if (error != null || bedrockUuid == null) {
                        caller.sendMessage(ChatColor.RED + "Compte Bedrock introuvable (vérifiez le pseudo).");
                        return;
                    }
                    if (linkManager.isBedrockLinked(bedrockUuid)) {
                        caller.sendMessage(ChatColor.RED + "Ce compte Bedrock est déjà lié à un autre joueur.");
                        return;
                    }
                    if (!authManager.isRegistered(bedrockUuid) || !authManager.checkPassword(bedrockUuid, password)) {
                        authManager.incrementAttempts(ip);
                        caller.sendMessage(ChatColor.RED + "Compte introuvable ou mot de passe incorrect.");
                        return;
                    }

                    authManager.resetAttempts(ip);
                    service.link(bedrockUuid, javaUuid, bedrockPseudo, caller.getName()).whenComplete((result, err) -> {
                        if (err != null) {
                            failed(caller, bedrockPseudo, err);
                            return;
                        }
                        caller.sendMessage(ChatColor.GREEN + "✔ Votre compte Java a été lié au compte Bedrock "
                                + bedrockPseudo + " !");
                        caller.sendMessage(ChatColor.YELLOW + "Ce compte Bedrock jouera désormais votre personnage "
                                + caller.getName() + " à sa prochaine connexion.");
                        describeHomes(caller, result);

                        // If that Bedrock account is online right now it still has its old identity.
                        Player online = Bukkit.getPlayer(bedrockUuid);
                        if (online != null) {
                            online.sendMessage(ChatColor.GREEN + "Ce compte vient d'être lié à " + caller.getName() + ".");
                            kickLater(online, ChatColor.GREEN + "Compte lié à " + caller.getName() + " !\n"
                                    + ChatColor.WHITE + "Reconnectez-vous pour retrouver son personnage.");
                        }
                    });
                }));
    }

    // ------------------------------------------------------------------ status / unlink

    private void handleStatus(Player player) {
        FloodgatePlayer session = bedrockSession(player);
        UUID bedrockUuid = session != null ? session.getJavaUniqueId() : linkManager.getBedrockUuidForJava(player.getUniqueId());

        if (bedrockUuid == null || !linkManager.isBedrockLinked(bedrockUuid)) {
            player.sendMessage(ChatColor.YELLOW + "Votre compte n'est lié à aucun autre compte.");
            player.sendMessage(ChatColor.GRAY + (session != null
                    ? "Pour jouer votre personnage Java depuis ici : /linkaccount java <PseudoJava> <MotDePasse>"
                    : "Pour jouer ce personnage sur Bedrock : connectez-vous côté Bedrock et faites "
                            + "/linkaccount java " + player.getName() + " <MotDePasse>"));
            return;
        }
        player.sendMessage(ChatColor.GOLD + "Compte lié :");
        player.sendMessage(ChatColor.YELLOW + "  Personnage (Java)  : " + ChatColor.WHITE
                + linkManager.getLinkedJavaUsername(bedrockUuid));
        player.sendMessage(ChatColor.YELLOW + "  Compte Bedrock     : " + ChatColor.WHITE
                + plain(linkManager.entries().stream().filter(e -> e.bedrockUuid.equals(bedrockUuid))
                        .map(e -> e.bedrockName).findFirst().orElse("?")));
        player.sendMessage(ChatColor.GRAY + "  /unlinkaccount pour les séparer.");
    }

    /** /unlinkaccount (your own link) or /unlinkaccount <pseudo> (operators). */
    private void handleUnlink(CommandSender sender, String[] args) {
        UUID bedrockUuid;
        if (args.length >= 1) {
            if (!(sender instanceof ConsoleCommandSender) && !sender.hasPermission("arthoplugin.admin")) {
                sender.sendMessage(ChatColor.RED + "Réservé aux opérateurs. Sans argument, "
                        + "/unlinkaccount délie votre propre compte.");
                return;
            }
            LinkManager.Entry entry = linkManager.findByName(args[0]);
            if (entry == null) {
                sender.sendMessage(ChatColor.RED + "Aucune liaison trouvée pour '" + args[0] + "'.");
                return;
            }
            bedrockUuid = entry.bedrockUuid;
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Usage depuis la console : /unlinkaccount <pseudo>");
                return;
            }
            Player player = (Player) sender;
            FloodgatePlayer session = bedrockSession(player);
            bedrockUuid = session != null ? session.getJavaUniqueId()
                    : linkManager.getBedrockUuidForJava(player.getUniqueId());
            if (bedrockUuid == null || !linkManager.isBedrockLinked(bedrockUuid)) {
                sender.sendMessage(ChatColor.YELLOW + "Votre compte n'est lié à aucun autre compte.");
                return;
            }
        }

        UUID javaUuid = linkManager.getLinkedJavaUuid(bedrockUuid);
        String javaName = linkManager.getLinkedJavaUsername(bedrockUuid);
        service.unlink(bedrockUuid).whenComplete((v, error) -> {
            if (error != null) {
                failed(sender, javaName, error);
                return;
            }
            sender.sendMessage(ChatColor.GREEN + "✔ Compte délié de " + javaName
                    + ". Le compte Bedrock retrouve son propre personnage à sa prochaine connexion.");

            // A Bedrock session of that character still holds the Java identity until it reconnects.
            Player online = javaUuid != null ? Bukkit.getPlayer(javaUuid) : null;
            if (online != null && bedrockSession(online) != null) {
                online.sendMessage(ChatColor.YELLOW + "Votre compte vient d'être délié.");
                kickLater(online, ChatColor.YELLOW + "Compte délié.\n"
                        + ChatColor.WHITE + "Reconnectez-vous pour retrouver votre propre personnage Bedrock.");
            }
        });
    }

    // ------------------------------------------------------------------ helpers

    private void failed(CommandSender who, String account, Throwable error) {
        plugin.getLogger().severe("[Link] Opération sur '" + account + "' échouée dans Floodgate : " + error);
        who.sendMessage(ChatColor.RED + "✘ L'opération a échoué (base de liaison Floodgate). "
                + "Rien n'a été modifié. Prévenez un administrateur.");
    }

    private void describeHomes(Player to, AccountLinkService.Result result) {
        if (!result.movedHomes.isEmpty()) {
            to.sendMessage(ChatColor.GRAY + "Homes transmis à votre personnage : " + String.join(", ", result.movedHomes));
        }
        for (String skipped : result.skippedHomes) {
            to.sendMessage(ChatColor.GRAY + "Home non transmis : " + skipped);
        }
    }

    /** Lets the player read the result, then reconnects them so the new identity applies. */
    private void kickLater(Player player, String reason) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.kickPlayer(reason);
            }
        }, service.reconnectDelaySeconds() * 20L);
    }

    /** The Floodgate session behind this player, linked or not; null for a Java player. */
    private FloodgatePlayer bedrockSession(Player player) {
        try {
            return FloodgateApi.getInstance().getPlayer(player.getUniqueId());
        } catch (Throwable t) {
            return null;
        }
    }

    private static String plain(String name) {
        return name != null && name.startsWith(".") ? name.substring(1) : name;
    }
}
