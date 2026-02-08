package net.arthonetwork.donation.utils;

import net.arthonetwork.donation.ArthoPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class AutoUpdater {

    private final ArthoPlugin plugin;
    private final String repo = "TeALO36/Artho-Plugin";
    private final String currentVersion;

    public AutoUpdater(ArthoPlugin plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getDescription().getVersion();
    }

    public void checkForUpdates() {
        if (!plugin.getConfig().getBoolean("update.check", true)) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String latestTag = fetchLatestTag();
                if (latestTag == null) {
                    plugin.getLogger().warning("Impossible de récupérer le tag latest depuis GitHub.");
                    return;
                }

                String latestVersion = latestTag.replace("v", "");
                plugin.getLogger()
                        .info("[AutoUpdate] Version actuelle: " + currentVersion + " | Dernière: " + latestVersion);

                if (isNewer(latestVersion, currentVersion)) {
                    plugin.getLogger().info("Une nouvelle version est disponible : " + latestTag);
                    if (plugin.getConfig().getBoolean("update.auto-download", true)) {
                        downloadUpdate(latestTag, null);
                    } else {
                        plugin.getLogger()
                                .info("Téléchargez-la ici : https://github.com/" + repo + "/releases/tag/" + latestTag);
                    }
                } else {
                    plugin.getLogger().info("Le plugin est à jour (" + currentVersion + ").");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Impossible de vérifier les mises à jour : " + e.getMessage());
            }
        });
    }

    private String fetchLatestTag() {
        try {
            URL url = new URL("https://github.com/" + repo + "/releases/latest");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false); // Don't follow, just get the Location header
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Artho-Plugin-Updater");

            int responseCode = connection.getResponseCode();
            if (responseCode == 302 || responseCode == 301) {
                String location = connection.getHeaderField("Location");
                // Location format: https://github.com/TeALO36/Artho-Plugin/releases/tag/v0.10.2
                return location.substring(location.lastIndexOf("/") + 1);
            } else {
                plugin.getLogger().warning("Impossible de récupérer la dernière version (Code: " + responseCode + ")");
                return null;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors de la récupération du tag : " + e.getMessage());
            return null;
        }
    }

    private boolean isNewer(String latest, String current) {
        String[] latestParts = latest.split("\\.");
        String[] currentParts = current.split("\\.");
        int length = Math.max(latestParts.length, currentParts.length);

        for (int i = 0; i < length; i++) {
            int v1 = i < latestParts.length ? Integer.parseInt(latestParts[i]) : 0;
            int v2 = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;
            if (v1 > v2)
                return true;
            if (v1 < v2)
                return false;
        }
        return false;
    }

    public void downloadLatest(org.bukkit.command.CommandSender sender) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String latestTag = fetchLatestTag();
                if (latestTag != null) {
                    String latestVersion = latestTag.replace("v", "");
                    if (isNewer(latestVersion, currentVersion)) {
                        downloadUpdate(latestTag, sender);
                    } else {
                        if (sender != null) {
                            sender.sendMessage(ChatColor.GREEN + "Le plugin est déjà à jour.");
                            sender.sendMessage(ChatColor.GRAY + "Version actuelle: " + currentVersion + " | GitHub: "
                                    + latestVersion);
                        }
                    }
                } else {
                    if (sender != null)
                        sender.sendMessage(ChatColor.RED + "Impossible de trouver la dernière version.");
                }
            } catch (Exception e) {
                if (sender != null)
                    sender.sendMessage(ChatColor.RED + "Erreur: " + e.getMessage());
            }
        });
    }

    public void downloadVersion(String tagName, org.bukkit.command.CommandSender sender) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            downloadUpdate(tagName, sender);
        });
    }

    private void downloadUpdate(String tagName, org.bukkit.command.CommandSender sender) {
        try {
            // Construct URL manually:
            // https://github.com/TeALO36/Artho-Plugin/releases/download/v0.10.2/Artho-Plugin.jar
            // Note: This assumes the jar name is always Artho-Plugin.jar. If it changes,
            // this breaks.
            String downloadUrl = "https://github.com/" + repo + "/releases/download/" + tagName + "/Artho-Plugin.jar";
            String fileName = "Artho-Plugin.jar"; // Or "Artho-Plugin-" + tagName + ".jar" if you prefer versioned names

            File updateFolder = new File(plugin.getDataFolder().getParentFile(), "update"); // plugins/update/
            if (!updateFolder.exists()) {
                updateFolder.mkdirs();
            }

            File outputFile = new File(updateFolder, fileName);

            if (sender != null)
                sender.sendMessage(ChatColor.YELLOW + "Téléchargement de " + tagName + "...");
            plugin.getLogger().info("Téléchargement de la mise à jour (" + tagName + ")...");

            URL url = new URL(downloadUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "Artho-Plugin-Updater");
            // Follow redirects for the download itself (GitHub releases redirect to
            // AWS/objects)
            connection.setInstanceFollowRedirects(true);

            try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
                    FileOutputStream fileOutputStream = new FileOutputStream(outputFile)) {
                byte dataBuffer[] = new byte[1024];
                int bytesRead;
                while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                    fileOutputStream.write(dataBuffer, 0, bytesRead);
                }
            }

            String msg = ChatColor.GREEN + "[Artho-Plugin] Version " + tagName
                    + " téléchargée ! Redémarrez pour appliquer.";
            plugin.getLogger().info(msg);
            if (sender != null)
                sender.sendMessage(msg);
            Bukkit.broadcast(msg, "arthoplugin.admin");

        } catch (Exception e) {
            String error = "Échec du téléchargement (" + tagName + ") : " + e.getMessage();
            plugin.getLogger().warning(error);
            if (sender != null)
                sender.sendMessage(ChatColor.RED + error);
            // e.printStackTrace(); // Optional: keep logs clean
        }
    }
}
