package net.arthonetwork.donation.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.arthonetwork.donation.ArthoPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
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
                URL url = new URL("https://api.github.com/repos/" + repo + "/releases/latest");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "Artho-Plugin-Updater");

                if (connection.getResponseCode() == 200) {
                    InputStreamReader reader = new InputStreamReader(connection.getInputStream());
                    JsonObject json = new JsonParser().parse(reader).getAsJsonObject();
                    String latestTag = json.get("tag_name").getAsString();
                    String latestVersion = latestTag.replace("v", "");

                    if (isNewer(latestVersion, currentVersion)) {
                        plugin.getLogger().info("Une nouvelle version est disponible : " + latestTag);
                        if (plugin.getConfig().getBoolean("update.auto-download", true)) {
                            downloadUpdate(json);
                        } else {
                            plugin.getLogger().info("Téléchargez-la ici : " + json.get("html_url").getAsString());
                        }
                    } else {
                        plugin.getLogger().info("Le plugin est à jour.");
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Impossible de vérifier les mises à jour : " + e.getMessage());
            }
        });
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
                URL url = new URL("https://api.github.com/repos/" + repo + "/releases/latest");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "Artho-Plugin-Updater");

                if (connection.getResponseCode() == 200) {
                    InputStreamReader reader = new InputStreamReader(connection.getInputStream());
                    JsonObject json = new JsonParser().parse(reader).getAsJsonObject();
                    downloadUpdate(json, sender);
                } else {
                    sender.sendMessage(ChatColor.RED + "Impossible de récupérer la dernière version.");
                }
            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + "Erreur: " + e.getMessage());
            }
        });
    }

    public void downloadVersion(String tagName, org.bukkit.command.CommandSender sender) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                URL url = new URL("https://api.github.com/repos/" + repo + "/releases/tags/" + tagName);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "Artho-Plugin-Updater");

                if (connection.getResponseCode() == 200) {
                    InputStreamReader reader = new InputStreamReader(connection.getInputStream());
                    JsonObject json = new JsonParser().parse(reader).getAsJsonObject();
                    downloadUpdate(json, sender);
                } else {
                    sender.sendMessage(ChatColor.RED + "Version introuvable: " + tagName);
                }
            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + "Erreur: " + e.getMessage());
            }
        });
    }

    private void downloadUpdate(JsonObject json, org.bukkit.command.CommandSender sender) {
        try {
            String downloadUrl = json.get("assets").getAsJsonArray().get(0).getAsJsonObject()
                    .get("browser_download_url").getAsString();
            String fileName = json.get("assets").getAsJsonArray().get(0).getAsJsonObject().get("name").getAsString();
            String tagName = json.get("tag_name").getAsString();

            File updateFolder = new File(plugin.getDataFolder().getParentFile(), "update"); // plugins/update/
            if (!updateFolder.exists()) {
                updateFolder.mkdirs();
            }

            File outputFile = new File(updateFolder, fileName);

            if (sender != null)
                sender.sendMessage(ChatColor.YELLOW + "Téléchargement de " + tagName + "...");
            plugin.getLogger().info("Téléchargement de la mise à jour (" + tagName + ")...");

            URL url = new URL(downloadUrl);
            try (BufferedInputStream in = new BufferedInputStream(url.openStream());
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
            String error = "Échec du téléchargement : " + e.getMessage();
            plugin.getLogger().warning(error);
            if (sender != null)
                sender.sendMessage(ChatColor.RED + error);
            e.printStackTrace();
        }
    }

    // Overload for auto-update
    private void downloadUpdate(JsonObject json) {
        downloadUpdate(json, null);
    }
}
