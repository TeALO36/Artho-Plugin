package net.arthonetwork.donation.commands;

import net.arthonetwork.donation.variants.LinkedVariantsFeature;
import net.arthonetwork.donation.variants.PlayerSoundListener;
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

import java.util.List;
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
            case "chance":
                handleChance(sender, args);
                return true;
            case "summon":
                handleSummon(sender, args);
                return true;
            case "rig":
                handleRig(sender, args);
                return true;
            case "testbeds":
                handleTestBeds(sender, args);
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
        } else {
            sender.sendMessage(ChatColor.GOLD + "Variantes d'entités (" + manager.getAllVariants().size() + "):");
            for (Variant variant : manager.getAllVariants()) {
                String chance = variant.hasSpawnChance()
                        ? VariantManager.toPercent(variant.getSpawnProbability()) + "%"
                        : "manuel";
                sender.sendMessage(ChatColor.YELLOW + "  - " + variant.getId() + ChatColor.GRAY + " ("
                        + variant.getEntityType() + ", " + chance + ")");
            }
        }
        listPlayerSounds(sender);
    }

    /**
     * The depth and eat triggers are declared in config.yml instead of
     * variants/, so listing only the entity catalogue made them look absent.
     */
    private void listPlayerSounds(CommandSender sender) {
        PlayerSoundListener listener = feature.getPlayerSoundListener();
        if (listener == null) {
            return;
        }
        List<PlayerSoundListener.TriggerInfo> triggers = listener.getTriggers();
        sender.sendMessage(ChatColor.GOLD + "Sons joueur (" + triggers.size() + ", depuis config.yml):");
        for (PlayerSoundListener.TriggerInfo t : triggers) {
            sender.sendMessage(ChatColor.YELLOW + "  - " + t.getId()
                    + ChatColor.GRAY + " (" + t.getLabel() + ") "
                    + (t.isEnabled() ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
            sender.sendMessage(ChatColor.DARK_GRAY + "      1 chance sur " + t.getChance()
                    + " · " + t.getCondition());
            sender.sendMessage(ChatColor.DARK_GRAY + "      "
                    + (t.getKeys().isEmpty() ? "aucun son configuré" : String.join(", ", t.getKeys())));
        }
    }

    /**
     * Lays out a row of beds so the resource pack's 1-in-5 stained variant can be
     * observed. Beds are NOT a plugin variant: the model is picked client-side
     * from the block position, so the server can neither know nor force which
     * ones come out stained - only lay down enough of them to see the ratio.
     */
    private void handleTestBeds(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Seuls les joueurs peuvent utiliser cette commande.");
            return;
        }
        Player player = (Player) sender;
        int count = 20;
        if (args.length >= 2) {
            try {
                count = Math.max(1, Math.min(60, Integer.parseInt(args[1])));
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Nombre invalide.");
                return;
            }
        }
        org.bukkit.Material material = org.bukkit.Material.RED_BED;
        if (args.length >= 3) {
            org.bukkit.Material m = org.bukkit.Material.matchMaterial(args[2].toUpperCase());
            if (m == null || !m.name().endsWith("_BED")) {
                player.sendMessage(ChatColor.RED + "Couleur inconnue (ex: blue_bed, white_bed).");
                return;
            }
            material = m;
        }

        org.bukkit.Location base = safeSpotInFront(player);
        int placed = 0;
        for (int i = 0; i < count; i++) {
            org.bukkit.block.Block foot = base.clone().add(i * 2, 0, 0).getBlock();
            org.bukkit.block.Block head = foot.getRelative(org.bukkit.block.BlockFace.NORTH);
            foot.setType(material, false);
            head.setType(material, false);
            org.bukkit.block.data.type.Bed fd = (org.bukkit.block.data.type.Bed) foot.getBlockData();
            fd.setPart(org.bukkit.block.data.type.Bed.Part.FOOT);
            fd.setFacing(org.bukkit.block.BlockFace.NORTH);
            foot.setBlockData(fd, false);
            org.bukkit.block.data.type.Bed hd = (org.bukkit.block.data.type.Bed) head.getBlockData();
            hd.setPart(org.bukkit.block.data.type.Bed.Part.HEAD);
            hd.setFacing(org.bukkit.block.BlockFace.NORTH);
            head.setBlockData(hd, false);
            placed++;
        }
        player.sendMessage(ChatColor.GREEN + "\u2714 " + placed + " lits poses.");
        player.sendMessage(ChatColor.GRAY + "Environ " + Math.round(placed / 5.0)
                + " devraient porter la tache (1 sur 5), sur la partie PIED.");
        player.sendMessage(ChatColor.GRAY + "Si aucun n'est tache, le pack n'est pas applique chez toi.");
    }

    /** Spawns the variant's entity type at the player and marks it immediately. */
    private void handleSummon(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Seuls les joueurs peuvent utiliser cette commande.");
            return;
        }
        if (args.length != 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /variant summon <id>");
            return;
        }
        Player player = (Player) sender;
        Variant variant = feature.getVariantManager().getVariant(args[1]);
        if (variant == null) {
            player.sendMessage(ChatColor.RED + "Variante '" + args[1] + "' introuvable.");
            return;
        }

        // Place the entity on solid ground just in front of the player, so it is
        // always visible and never stuck inside a wall.
        org.bukkit.Location loc = safeSpotInFront(player);

        if (variant.getEntityType() == org.bukkit.entity.EntityType.BEE) {
            summonHive(player, variant, loc);
            return;
        }

        Entity spawned;
        try {
            spawned = player.getWorld().spawnEntity(loc, variant.getEntityType());
        } catch (IllegalArgumentException e) {
            player.sendMessage(ChatColor.RED + "Impossible de faire apparaitre " + variant.getEntityType()
                    + " (type non invocable).");
            return;
        }

        feature.getVariantManager().assign(spawned, variant);
        player.sendMessage(ChatColor.GREEN + "\u2714 " + variant.getEntityType() + " invoque et marque '"
                + variant.getId() + "'.");
        if (variant.getNativeVariant() != null && !variant.getNativeVariant().isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "Variante native : " + variant.getNativeVariant());
        }
        if (variant.isSilenceVanilla()) {
            player.sendMessage(ChatColor.GRAY + "Sons vanilla coupes sur cette entite.");
        }
    }

    /**
     * Bees belong to a hive, so summoning one bee proves nothing: place an actual
     * beehive and a batch of bees bound to it, all sharing the variant.
     */
    private void summonHive(Player player, Variant variant, org.bukkit.Location loc) {
        org.bukkit.block.Block block = loc.getBlock();
        block.setType(org.bukkit.Material.BEEHIVE);

        int count = 5;
        for (int i = 0; i < count; i++) {
            org.bukkit.Location spot = loc.clone().add(
                    (Math.random() - 0.5) * 3, 1, (Math.random() - 0.5) * 3);
            Entity bee = player.getWorld().spawnEntity(spot, org.bukkit.entity.EntityType.BEE);
            if (bee instanceof org.bukkit.entity.Bee) {
                ((org.bukkit.entity.Bee) bee).setHive(block.getLocation());
            }
            feature.getVariantManager().assign(bee, variant);
        }
        player.sendMessage(ChatColor.GREEN + "\u2714 Ruche posee avec " + count
                + " abeilles marquees '" + variant.getId() + "'.");
        player.sendMessage(ChatColor.GRAY + "Toute la ruche partage la variante, comme en jeu.");
    }

    /** Finds a free spot 2 blocks ahead, dropping to the first solid ground below. */
    private org.bukkit.Location safeSpotInFront(Player player) {
        org.bukkit.Location base = player.getLocation();
        org.bukkit.util.Vector dir = base.getDirection().setY(0);
        if (dir.lengthSquared() < 0.01) {
            dir = new org.bukkit.util.Vector(0, 0, 1);
        }
        org.bukkit.Location loc = base.clone().add(dir.normalize().multiply(2));
        loc.setY(base.getY());
        // Walk down to the ground, but never more than 5 blocks.
        for (int i = 0; i < 5 && loc.getBlock().getType().isAir()
                && loc.clone().subtract(0, 1, 0).getBlock().getType().isAir(); i++) {
            loc.subtract(0, 1, 0);
        }
        // If we ended up inside a block, climb back out.
        for (int i = 0; i < 3 && !loc.getBlock().getType().isAir(); i++) {
            loc.add(0, 1, 0);
        }
        return loc;
    }

    /** Live tuning of the animated rig: size, height and swing amplitude. */
    private void handleRig(CommandSender sender, String[] args) {
        if (args.length != 4) {
            sender.sendMessage(ChatColor.RED + "Usage: /variant rig <id> <scale|offset|swing> <valeur>");
            sender.sendMessage(ChatColor.GRAY + "scale = taille, offset = hauteur, swing = amplitude des membres");
            return;
        }
        Variant variant = feature.getVariantManager().getVariant(args[1]);
        if (variant == null) {
            sender.sendMessage(ChatColor.RED + "Variante '" + args[1] + "' introuvable.");
            return;
        }
        String key;
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "scale":  key = "display-scale";    break;
            case "offset": key = "display-offset-y"; break;
            case "swing":  key = "swing-amplitude";  break;
            default:
                sender.sendMessage(ChatColor.RED + "Reglage inconnu (scale, offset ou swing).");
                return;
        }
        try {
            Double.parseDouble(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Valeur numerique attendue.");
            return;
        }
        if (!feature.getVariantManager().writeKey(args[1], key, args[3])) {
            sender.sendMessage(ChatColor.RED + "Ecriture impossible.");
            return;
        }
        feature.getVariantManager().reload();
        sender.sendMessage(ChatColor.GREEN + key + " = " + args[3] + " pour '" + args[1] + "'.");
        sender.sendMessage(ChatColor.GRAY + "Re-invoque l'entite pour voir le resultat "
                + "(/variant summon " + args[1] + ").");
    }

    private void handleChance(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /variant chance <id> <valeur>");
            sender.sendMessage(ChatColor.GRAY + "Sans / c'est un pourcentage (50 = 50%), avec / une fraction (5/7).");
            return;
        }
        Variant variant = feature.getVariantManager().getVariant(args[1]);
        if (variant == null) {
            sender.sendMessage(ChatColor.RED + "Variante '" + args[1] + "' introuvable.");
            return;
        }

        String value = args[2];
        double prob = VariantManager.parseChance(value, args[1]);
        if (prob <= 0 && !value.trim().replace("%", "").equals("0")) {
            sender.sendMessage(ChatColor.RED + "Valeur '" + value + "' invalide.");
            sender.sendMessage(ChatColor.GRAY + "Exemples : 50 (=50%), 5/7, 2.5, ou 0 pour desactiver.");
            return;
        }

        // Toujours stocke en pourcentage, meme si l'admin a saisi une fraction.
        String stored = VariantManager.toPercent(prob);
        if (!feature.getVariantManager().writeSpawnChance(args[1], stored)) {
            sender.sendMessage(ChatColor.RED + "Impossible d'ecrire le fichier de la variante.");
            return;
        }
        feature.getVariantManager().reload();

        if (prob <= 0) {
            sender.sendMessage(ChatColor.GREEN + "Apparition automatique desactivee pour '" + args[1]
                    + "' (assignation manuelle uniquement).");
        } else {
            String extra = value.contains("/") ? ChatColor.GRAY + " (" + value + " converti)" : "";
            sender.sendMessage(ChatColor.GREEN + "Chance de '" + args[1] + "' reglee sur " + stored + "%" + extra);
        }
        sender.sendMessage(ChatColor.GRAY + "Ne s'applique qu'aux NOUVELLES apparitions ; "
                + "les entites deja presentes gardent leur etat.");
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
        sender.sendMessage(ChatColor.YELLOW + "/variant summon <id> " + ChatColor.WHITE
                + "- Invoquer l'entité déjà marquée (idéal pour tester).");
        sender.sendMessage(ChatColor.YELLOW + "/variant testbeds [nb] [couleur] " + ChatColor.WHITE
                + "- Poser une rangée de lits pour voir les taches.");
        sender.sendMessage(ChatColor.YELLOW + "/variant set <id> " + ChatColor.WHITE
                + "- Assigner une variante à l'entité visée.");
        sender.sendMessage(ChatColor.YELLOW + "/variant remove " + ChatColor.WHITE
                + "- Retirer la variante de l'entité visée.");
        sender.sendMessage(ChatColor.YELLOW + "/variant info " + ChatColor.WHITE
                + "- Voir la variante de l'entité visée.");
        sender.sendMessage(ChatColor.YELLOW + "/variant rig <id> <scale|offset|swing> <valeur> " + ChatColor.WHITE
                + "- Regler le modele anime en direct.");
        sender.sendMessage(ChatColor.YELLOW + "/variant chance <id> <valeur> " + ChatColor.WHITE
                + "- Regler la frequence (50 = 50%, ou 5/7).");
        sender.sendMessage(ChatColor.YELLOW + "/variant reload " + ChatColor.WHITE
                + "- Recharger le catalogue de variantes depuis le disque.");
    }
}
