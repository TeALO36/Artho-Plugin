package net.arthonetwork.donation.tasks;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class TipBroadcastTask extends BukkitRunnable {

    private final List<String> playerTips = Arrays.asList(
            "&aSaviez-vous que vous pouvez faire &e/suggestion ajouter <idée> &apour nous aider ?",
            "&aBesoin de voir votre ping ? Faites &e/ping &a!",
            "&aVous pouvez voir les lags avec &e/lag &a!");

    private final List<String> adminTips = Arrays.asList(
            "&c[Admin] &aPensez à utiliser &e/annonces &apour gérer les broadcasts.",
            "&c[Admin] &aVous pouvez voir le ping d'un joueur avec &e/ping <joueur> &a!",
            "&c[Admin] &aGérez l'authentification avec &e/auth &a!");

    private final Random random = new Random();

    @Override
    public void run() {
        if (Bukkit.getOnlinePlayers().isEmpty())
            return;

        // Choose a random tip type for this run or send to everyone?
        // Requirement: "user... plugin will send message... then same for operator..."
        // Let's send a tip to everyone, adapted to their permissions.

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("arthoplugin.admin") || player.isOp()) {
                // OP gets random op tip OR random player tip
                if (random.nextBoolean()) {
                    sendTip(player, adminTips);
                } else {
                    sendTip(player, playerTips);
                }
            } else {
                // Non-OP gets player tip
                sendTip(player, playerTips);
            }
        }
    }

    private void sendTip(Player player, List<String> tips) {
        String tip = tips.get(random.nextInt(tips.size()));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7[Astuce] " + tip));
    }
}
