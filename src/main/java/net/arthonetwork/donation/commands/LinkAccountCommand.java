package net.arthonetwork.donation.commands;

import net.arthonetwork.donation.ArthoPlugin;
import net.arthonetwork.donation.utils.AuthManager;
import net.arthonetwork.donation.utils.LinkManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.UUID;

/**
 * /linkaccount <bedrock|java> <Pseudo> <MotDePasse>
 *
 * Usable from either platform: the first argument names the platform of the
 * ACCOUNT YOU WANT TO LINK TO (the destination), which must be the opposite
 * of the platform you're currently connected with. The password verifies
 * ownership of that destination account through Artho-Plugin's own auth
 * system (AuthManager). On success the Bedrock side of the pair gets
 * auto-logged in on future joins (see BedrockAutoLoginListener).
 */
public class LinkAccountCommand implements CommandExecutor {

    private final ArthoPlugin plugin;
    private final AuthManager authManager;
    private final LinkManager linkManager;

    public LinkAccountCommand(ArthoPlugin plugin, AuthManager authManager, LinkManager linkManager) {
        this.plugin = plugin;
        this.authManager = authManager;
        this.linkManager = linkManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Seuls les joueurs peuvent utiliser cette commande.");
            return true;
        }

        if (!isFloodgateAvailable()) {
            sender.sendMessage(ChatColor.RED + "La liaison de compte n'est pas disponible (Floodgate non chargé).");
            return true;
        }

        Player player = (Player) sender;

        if (args.length != 3 || !(args[0].equalsIgnoreCase("bedrock") || args[0].equalsIgnoreCase("java"))) {
            player.sendMessage(ChatColor.RED + "Usage: /linkaccount <bedrock|java> <Pseudo> <MotDePasse>");
            player.sendMessage(
                    ChatColor.GRAY + "Précisez le type du compte DESTINATION (celui auquel vous voulez vous lier).");
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
            long remaining = authManager.getRemainingTime(ip);
            player.sendMessage(ChatColor.RED + "Trop de tentatives. Réessayez dans " + remaining + " secondes.");
            return true;
        }

        boolean callerIsBedrock = FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());

        if (callerIsBedrock && targetType.equals("bedrock")) {
            player.sendMessage(
                    ChatColor.RED + "Vous jouez déjà en Bedrock. Utilisez /linkaccount java <PseudoJava> <MotDePasse>.");
            return true;
        }
        if (!callerIsBedrock && targetType.equals("java")) {
            player.sendMessage(
                    ChatColor.RED + "Vous jouez déjà en Java. Utilisez /linkaccount bedrock <PseudoBedrock> <MotDePasse>.");
            return true;
        }

        if (callerIsBedrock) {
            linkBedrockCallerToJavaTarget(player, ip, pseudo, password);
        } else {
            linkJavaCallerToBedrockTarget(player, ip, pseudo, password);
        }
        return true;
    }

    /** Caller is on Bedrock, wants to link to an existing Java account. */
    private void linkBedrockCallerToJavaTarget(Player bedrockPlayer, String ip, String javaPseudo, String password) {
        UUID bedrockUuid = bedrockPlayer.getUniqueId();

        if (linkManager.isBedrockLinked(bedrockUuid)) {
            bedrockPlayer.sendMessage(ChatColor.RED + "Votre compte est déjà lié à "
                    + linkManager.getLinkedJavaUsername(bedrockUuid) + ".");
            return;
        }

        UUID javaUuid = Bukkit.getOfflinePlayer(javaPseudo).getUniqueId();

        if (FloodgateApi.getInstance().isFloodgateId(javaUuid)) {
            bedrockPlayer.sendMessage(ChatColor.RED + "Ce pseudo correspond à un compte Bedrock, pas Java.");
            return;
        }

        if (!authManager.isRegistered(javaUuid) || linkManager.isJavaLinked(javaUuid)) {
            if (linkManager.isJavaLinked(javaUuid)) {
                bedrockPlayer.sendMessage(ChatColor.RED + "Ce compte Java est déjà lié à un autre joueur Bedrock.");
            } else {
                authManager.incrementAttempts(ip);
                bedrockPlayer.sendMessage(ChatColor.RED + "Compte introuvable ou mot de passe incorrect.");
            }
            return;
        }

        if (!authManager.checkPassword(javaUuid, password)) {
            authManager.incrementAttempts(ip);
            bedrockPlayer.sendMessage(ChatColor.RED + "Compte introuvable ou mot de passe incorrect.");
            return;
        }

        authManager.resetAttempts(ip);
        linkManager.link(bedrockUuid, javaUuid, bedrockPlayer.getName(), javaPseudo);
        authManager.login(bedrockUuid);
        bedrockPlayer.removePotionEffect(PotionEffectType.BLINDNESS);
        bedrockPlayer.sendMessage(ChatColor.GREEN + "✔ Compte lié avec succès à " + javaPseudo
                + " ! Vous serez automatiquement connecté à vos prochaines connexions.");
    }

    /** Caller is on Java, wants to link to an existing Bedrock account. */
    private void linkJavaCallerToBedrockTarget(Player javaPlayer, String ip, String bedrockPseudo, String password) {
        UUID javaUuid = javaPlayer.getUniqueId();

        if (linkManager.isJavaLinked(javaUuid)) {
            javaPlayer.sendMessage(ChatColor.RED + "Votre compte est déjà lié à "
                    + linkManager.getLinkedBedrockName(javaUuid) + ".");
            return;
        }

        javaPlayer.sendMessage(ChatColor.GRAY + "Recherche du compte Bedrock " + bedrockPseudo + "...");

        FloodgateApi.getInstance().getUuidFor(bedrockPseudo).whenComplete((bedrockUuid, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!javaPlayer.isOnline()) {
                        return;
                    }
                    if (error != null || bedrockUuid == null) {
                        javaPlayer.sendMessage(ChatColor.RED + "Compte Bedrock introuvable (vérifiez le pseudo).");
                        return;
                    }

                    if (linkManager.isBedrockLinked(bedrockUuid)) {
                        javaPlayer.sendMessage(ChatColor.RED + "Ce compte Bedrock est déjà lié à un autre joueur.");
                        return;
                    }

                    if (!authManager.isRegistered(bedrockUuid)) {
                        authManager.incrementAttempts(ip);
                        javaPlayer.sendMessage(ChatColor.RED + "Compte introuvable ou mot de passe incorrect.");
                        return;
                    }

                    if (!authManager.checkPassword(bedrockUuid, password)) {
                        authManager.incrementAttempts(ip);
                        javaPlayer.sendMessage(ChatColor.RED + "Compte introuvable ou mot de passe incorrect.");
                        return;
                    }

                    authManager.resetAttempts(ip);
                    linkManager.link(bedrockUuid, javaUuid, bedrockPseudo, javaPlayer.getName());
                    javaPlayer.sendMessage(ChatColor.GREEN + "✔ Votre compte Java a été lié avec succès au compte Bedrock "
                            + bedrockPseudo + " !");
                }));
    }

    private boolean isFloodgateAvailable() {
        try {
            return plugin.getServer().getPluginManager().isPluginEnabled("floodgate")
                    && FloodgateApi.getInstance() != null;
        } catch (Throwable t) {
            return false;
        }
    }
}
