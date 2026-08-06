package net.arthonetwork.donation.commands;

import net.arthonetwork.donation.variants.LinkedVariantsFeature;
import net.arthonetwork.donation.variants.Variant;
import net.arthonetwork.donation.variants.VariantManager;
import org.bukkit.ChatColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import java.util.Locale;

/**
 * Admin command for the Linked Variants feature. enable/disable/status
 * always work (so the module can be turned on in the first place); every
 * other subcommand requires it to be running.
 */
public class VariantCommand implements CommandExecutor {

    private static final double TARGET_MAX_DISTANCE = 20.0;

    private final LinkedVariantsFeature feature;

    public VariantCommand(LinkedVariantsFeature feature) {
        this.feature = feature;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "enable":
                feature.enable();
                sender.sendMessage(ChatColor.GREEN + "Module Variantes Liées activé.");
                return true;
            case "disable":
                feature.disable();
                sender.sendMessage(ChatColor.RED + "Module Variantes Liées désactivé.");
                return true;
            case "status":
                sender.sendMessage(ChatColor.GOLD + "Variantes Liées: "
                        + (feature.isRunning() ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
                return true;
            default:
                break;
        }

        if (!feature.isRunning()) {
            sender.sendMessage(ChatColor.RED
                    + "Le module Variantes Liées est désactivé. Active-le avec /variant enable.");
            return true;
        }

        switch (sub) {
            case "reload":
                feature.getVariantManager().reload();
                sender.sendMessage(ChatColor.GREEN + "Catalogue de variantes rechargé ("
                        + feature.getVariantManager().getAllVariants().size() + " variante(s)).");
                return true;
            case "list":
                handleList(sender);
                return true;
            case "set":
                handleSet(sender, args);
                return true;
            case "remove":
                handleRemove(sender);
                return true;
            case "info":
                handleInfo(sender);
                return true;
            default:
                sendHelp(sender);
                return true;
        }
    }

    private void handleList(CommandSender sender) {
        VariantManager manager = feature.getVariantManager();
        if (manager.getAllVariants().isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Aucune variante définie dans plugins/Artho-Plugin/variants/.");
            return;
        }
        sender.sendMessage(ChatColor.GOLD + "Variantes chargées (" + manager.getAllVariants().size() + "):");
        for (Variant variant : manager.getAllVariants()) {
            sender.sendMessage(ChatColor.YELLOW + "  - " + variant.getId() + ChatColor.GRAY + " (" + variant.getEntityType()
                    + ")");
        }
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Seuls les joueurs peuvent utiliser cette commande.");
            return;
        }
        if (args.length != 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /variant set <id>");
            return;
        }

        Player player = (Player) sender;
        Variant variant = feature.getVariantManager().getVariant(args[1]);
        if (variant == null) {
            player.sendMessage(ChatColor.RED + "Variante '" + args[1] + "' introuvable.");
            return;
        }

        Entity target = findTarget(player);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Aucune entité visée (regardez une entité, à moins de "
                    + (int) TARGET_MAX_DISTANCE + " blocs).");
            return;
        }

        if (target.getType() != variant.getEntityType()) {
            player.sendMessage(ChatColor.RED + "Cette variante s'applique à " + variant.getEntityType()
                    + ", pas à " + target.getType() + ".");
            return;
        }

        feature.getVariantManager().assign(target, variant);
        player.sendMessage(ChatColor.GREEN + "Variante '" + variant.getId() + "' appliquée à l'entité visée ("
                + target.getUniqueId() + ").");
    }

    private void handleRemove(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Seuls les joueurs peuvent utiliser cette commande.");
            return;
        }
        Player player = (Player) sender;
        Entity target = findTarget(player);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Aucune entité visée.");
            return;
        }
        feature.getVariantManager().removeVariant(target);
        player.sendMessage(ChatColor.GREEN + "Variante retirée de l'entité visée. "
                + ChatColor.GRAY + "(l'équipement visuel appliqué n'est pas retiré automatiquement)");
    }

    private void handleInfo(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Seuls les joueurs peuvent utiliser cette commande.");
            return;
        }
        Player player = (Player) sender;
        Entity target = findTarget(player);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Aucune entité visée.");
            return;
        }
        String variantId = feature.getVariantManager().getVariantId(target);
        if (variantId == null) {
            player.sendMessage(ChatColor.YELLOW + "Cette entité n'a aucune variante assignée.");
        } else {
            player.sendMessage(ChatColor.GOLD + "Entité " + target.getUniqueId() + ChatColor.YELLOW
                    + " -> variante: " + ChatColor.WHITE + variantId);
        }
    }

    private Entity findTarget(Player player) {
        RayTraceResult result = player.getWorld().rayTraceEntities(player.getEyeLocation(),
                player.getEyeLocation().getDirection(), TARGET_MAX_DISTANCE, e -> !e.equals(player));
        if (result == null) {
            return null;
        }
        double distance = player.getEyeLocation().toVector().distance(result.getHitPosition());
        RayTraceResult blockHit = player.getWorld().rayTraceBlocks(player.getEyeLocation(),
                player.getEyeLocation().getDirection(), distance, FluidCollisionMode.NEVER, true);
        if (blockHit != null) {
            return null;
        }
        return result.getHitEntity();
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "--- Variantes Liées ---");
        sender.sendMessage(ChatColor.YELLOW + "/variant enable|disable|status " + ChatColor.WHITE
                + "- Activer/désactiver le module.");
        sender.sendMessage(ChatColor.YELLOW + "/variant list " + ChatColor.WHITE + "- Lister les variantes.");
        sender.sendMessage(ChatColor.YELLOW + "/variant set <id> " + ChatColor.WHITE
                + "- Assigner une variante à l'entité visée.");
        sender.sendMessage(ChatColor.YELLOW + "/variant remove " + ChatColor.WHITE
                + "- Retirer la variante de l'entité visée.");
        sender.sendMessage(ChatColor.YELLOW + "/variant info " + ChatColor.WHITE
                + "- Voir la variante de l'entité visée.");
        sender.sendMessage(ChatColor.YELLOW + "/variant reload " + ChatColor.WHITE
                + "- Recharger le catalogue de variantes depuis le disque.");
    }
}
