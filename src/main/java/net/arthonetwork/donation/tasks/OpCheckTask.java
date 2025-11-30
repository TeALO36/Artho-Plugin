package net.arthonetwork.donation.tasks;

import net.arthonetwork.donation.listeners.PlayerJoinListener;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class OpCheckTask extends BukkitRunnable {

    private final Map<UUID, Boolean> opStatus = new HashMap<>();

    @Override
    public void run() {
        // Clean up offline players to prevent memory leak
        opStatus.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            boolean isOp = player.isOp();

            if (opStatus.containsKey(uuid)) {
                boolean wasOp = opStatus.get(uuid);
                if (!wasOp && isOp) {
                    // Player became OP
                    player.sendMessage(
                            ChatColor.GREEN + "Vous êtes maintenant Opérateur ! Voici vos nouvelles commandes :");
                    PlayerJoinListener.sendOpCommands(player);
                }
            }
            opStatus.put(uuid, isOp);
        }
    }
}
