package net.arthonetwork.donation.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import org.bukkit.entity.Player;

public class LagCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            checkServerLag(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "joueur":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Cette commande n'est utilisable que par un joueur.");
                    return true;
                }
                checkPlayerLag((Player) sender);
                break;
            case "serveur":
                checkServerLag(sender);
                break;
            case "help":
            case "aide":
                sendHelp(sender);
                break;
            default:
                sender.sendMessage(ChatColor.RED + "Sous-commande inconnue.");
                sendHelp(sender);
                break;
        }
        return true;
    }

    private void checkPlayerLag(Player player) {
        player.sendMessage(ChatColor.GOLD + "--- Analyse Lag Joueur ---");
        int entityCount = 0;
        int tileEntityCount = 0;
        int chunkCount = 0;

        World world = player.getWorld();
        int viewDist = Bukkit.getViewDistance();
        int pX = player.getLocation().getChunk().getX();
        int pZ = player.getLocation().getChunk().getZ();

        for (int x = pX - viewDist; x <= pX + viewDist; x++) {
            for (int z = pZ - viewDist; z <= pZ + viewDist; z++) {
                if (world.isChunkLoaded(x, z)) {
                    Chunk chunk = world.getChunkAt(x, z);
                    entityCount += chunk.getEntities().length;
                    tileEntityCount += chunk.getTileEntities().length;
                    chunkCount++;
                }
            }
        }

        player.sendMessage(ChatColor.YELLOW + "Chunks chargés autour de vous: " + ChatColor.WHITE + chunkCount);
        player.sendMessage(ChatColor.YELLOW + "Entités proches: " + ChatColor.WHITE + entityCount);
        player.sendMessage(
                ChatColor.YELLOW + "Tile Entities (coffres, fours...): " + ChatColor.WHITE + tileEntityCount);

        if (entityCount > 100) {
            player.sendMessage(ChatColor.RED + "Attention: Beaucoup d'entités proches ! Cela peut causer du lag.");
        } else {
            player.sendMessage(ChatColor.GREEN + "Zone stable.");
        }
    }

    private void checkServerLag(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "--- Analyse Lag Serveur ---");

        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / 1024 / 1024;
        long totalMemory = runtime.totalMemory() / 1024 / 1024;
        long freeMemory = runtime.freeMemory() / 1024 / 1024;
        long usedMemory = totalMemory - freeMemory;

        sender.sendMessage(
                ChatColor.YELLOW + "RAM Utilisée: " + ChatColor.WHITE + usedMemory + "MB / " + maxMemory + "MB");

        int totalEntities = 0;
        int totalChunks = 0;
        for (World world : Bukkit.getWorlds()) {
            totalEntities += world.getEntities().size();
            totalChunks += world.getLoadedChunks().length;
        }

        sender.sendMessage(ChatColor.YELLOW + "Total Entités: " + ChatColor.WHITE + totalEntities);
        sender.sendMessage(ChatColor.YELLOW + "Total Chunks Chargés: " + ChatColor.WHITE + totalChunks);

        if (usedMemory > (maxMemory * 0.9)) {
            sender.sendMessage(ChatColor.RED + "Attention: Mémoire saturée !");
        } else {
            sender.sendMessage(ChatColor.GREEN + "Mémoire OK.");
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "--- Aide Lag ---");
        sender.sendMessage(
                ChatColor.YELLOW + "/lag " + ChatColor.WHITE + "- Stats serveur (défaut).");
        sender.sendMessage(
                ChatColor.YELLOW + "/lag joueur " + ChatColor.WHITE + "- Analyse les causes de lag autour de vous.");
        sender.sendMessage(
                ChatColor.YELLOW + "/lag serveur " + ChatColor.WHITE + "- Affiche les stats globales du serveur.");
    }
}
