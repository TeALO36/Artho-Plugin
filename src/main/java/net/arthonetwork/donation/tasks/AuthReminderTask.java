package net.arthonetwork.donation.tasks;

import net.arthonetwork.donation.ArthoPlugin;
import net.arthonetwork.donation.utils.AuthManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class AuthReminderTask extends BukkitRunnable {

    private final ArthoPlugin plugin;
    private final AuthManager authManager;

    public AuthReminderTask(ArthoPlugin plugin, AuthManager authManager) {
        this.plugin = plugin;
        this.authManager = authManager;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!authManager.isLoggedIn(player.getUniqueId())) {
                boolean isRegistered = authManager.isRegistered(player.getUniqueId());
                String title = plugin.getAuthMessage("title");
                String subtitle;

                if (isRegistered) {
                    subtitle = plugin.getAuthMessage("subtitle-login");
                } else {
                    subtitle = plugin.getAuthMessage("subtitle-register");
                }

                // sendTitle(title, subtitle, fadeIn, stay, fadeOut)
                // 0 fadeIn, 40 stay (2s), 10 fadeOut
                player.sendTitle(title, subtitle, 0, 40, 10);
            } else if (authManager.isForceChange(player.getUniqueId())) {
                String title = ChatColor.RED + "Changement Requis";
                String subtitle = ChatColor.YELLOW + "/changepassword <nouveau> <confirm>";
                player.sendTitle(title, subtitle, 0, 40, 10);
            }
        }
    }
}
