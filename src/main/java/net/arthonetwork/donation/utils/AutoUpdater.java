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

    private void downloadUpdate(JsonObject json) {
        try {
            String downloadUrl = json.get("assets").getAsJsonArray().get(0).getAsJsonObject()
                    .get("browser_download_url").getAsString();
            String fileName = json.get("assets").getAsJsonArray().get(0).getAsJsonObject().get("name").getAsString();

            File updateFolder = new File(plugin.getDataFolder().getParentFile(), "update"); // plugins/update/
            if (!updateFolder.exists()) {
                updateFolder.mkdirs();
            }

            File outputFile = new File(updateFolder, fileName);

            plugin.getLogger().info("Téléchargement de la mise à jour...");

            URL url = new URL(downloadUrl);
            try (BufferedInputStream in = new BufferedInputStream(url.openStream());
                    FileOutputStream fileOutputStream = new FileOutputStream(outputFile)) {
                byte dataBuffer[] = new byte[1024];
                int bytesRead;
                while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                    fileOutputStream.write(dataBuffer, 0, bytesRead);
                }
            }

            plugin.getLogger().info(
                    "Mise à jour téléchargée dans le dossier 'update'. Elle sera appliquée au prochain redémarrage.");
            Bukkit.getScheduler().runTask(plugin,
                    () -> Bukkit.broadcast(ChatColor.GREEN
                            + "[Artho-Plugin] Une mise à jour a été téléchargée et sera installée au redémarrage.",
                            "arthoplugin.admin"));

        } catch (Exception e) {
            plugin.getLogger().warning("Échec du téléchargement de la mise à jour : " + e.getMessage());
            e.printStackTrace();
        }
    }
}
