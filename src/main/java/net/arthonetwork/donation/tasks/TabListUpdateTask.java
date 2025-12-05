package net.arthonetwork.donation.tasks;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class TabListUpdateTask extends BukkitRunnable {

    public TabListUpdateTask() {
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateTabList(player);
        }
    }

    private void updateTabList(Player player) {
        int ping = getPing(player);
        ChatColor pingColor = getPingColor(ping);

        String footer = "\n" + ChatColor.GRAY + "Ping : " + pingColor + ping + "ms" + "\n" +
                ChatColor.YELLOW + "Serveur hébergé par ArthoNetwork";

        player.setPlayerListFooter(footer);
    }

    private int getPing(Player player) {
        try {
            return player.getPing(); // Available in 1.13+ Spigot
        } catch (NoSuchMethodError e) {
            return -1;
        }
    }

    private ChatColor getPingColor(int ping) {
        if (ping == -1)
            return ChatColor.RED; // Unsupported
        if (ping < 100)
            return ChatColor.GREEN;
        if (ping < 200)
            return ChatColor.YELLOW;
        return ChatColor.RED;
    }
}
