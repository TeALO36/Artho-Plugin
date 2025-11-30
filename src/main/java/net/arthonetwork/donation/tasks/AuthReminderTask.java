package net.arthonetwork.donation.tasks;

import net.arthonetwork.donation.utils.AuthManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class AuthReminderTask extends BukkitRunnable {

    private final AuthManager authManager;

    public AuthReminderTask(AuthManager authManager) {
        this.authManager = authManager;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!authManager.isLoggedIn(player.getUniqueId())) {
                boolean isRegistered = authManager.isRegistered(player.getUniqueId());
                String title = ChatColor.RED + "Authentification Requise";
                String subtitle;

                if (isRegistered) {
                    subtitle = ChatColor.YELLOW + "/login <mot de passe>";
                } else {
                    subtitle = ChatColor.YELLOW + "/register <mdp> <confirmation>";
                }

                // sendTitle(title, subtitle, fadeIn, stay, fadeOut)
                // 0 fadeIn, 40 stay (2s), 10 fadeOut
                player.sendTitle(title, subtitle, 0, 40, 10);
            }
        }
    }
}
