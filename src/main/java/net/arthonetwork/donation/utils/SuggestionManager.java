package net.arthonetwork.donation.utils;

import net.arthonetwork.donation.ArthoPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public class SuggestionManager {

    private final ArthoPlugin plugin;
    private File suggestionsFile;
    private FileConfiguration suggestionsConfig;
    private File historyFile;

    public SuggestionManager(ArthoPlugin plugin) {
        this.plugin = plugin;
        initFiles();
    }

    private void initFiles() {
        suggestionsFile = new File(plugin.getDataFolder(), "suggestions.yml");
        if (!suggestionsFile.exists()) {
            try {
                suggestionsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create suggestions.yml!");
                e.printStackTrace();
            }
        }
        suggestionsConfig = YamlConfiguration.loadConfiguration(suggestionsFile);

        historyFile = new File(plugin.getDataFolder(), "suggestions_history.txt");
        if (!historyFile.exists()) {
            try {
                historyFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create suggestions_history.txt!");
                e.printStackTrace();
            }
        }
    }

    public void reload() {
        suggestionsConfig = YamlConfiguration.loadConfiguration(suggestionsFile);
    }

    public void addSuggestion(String playerName, String content) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

        // Save to YML (Active suggestions)
        suggestionsConfig.set("suggestions." + id + ".author", playerName);
        suggestionsConfig.set("suggestions." + id + ".date", date);
        suggestionsConfig.set("suggestions." + id + ".content", content);
        saveSuggestions();

        // Log to History TXT
        logHistory("[ADD] [" + date + "] ID: " + id + " | Author: " + playerName + " | Content: " + content);
    }

    public boolean removeSuggestion(String id, String removerName) {
        if (suggestionsConfig.contains("suggestions." + id)) {
            String author = suggestionsConfig.getString("suggestions." + id + ".author");
            String content = suggestionsConfig.getString("suggestions." + id + ".content");
            String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

            suggestionsConfig.set("suggestions." + id, null);
            saveSuggestions();

            // Log removal
            logHistory("[REMOVE] [" + date + "] ID: " + id + " | Removed by: " + removerName + " | Original Author: "
                    + author + " | Content: " + content);
            return true;
        }
        return false;
    }

    public Map<String, String> getSuggestions() {
        Map<String, String> list = new HashMap<>();
        ConfigurationSection section = suggestionsConfig.getConfigurationSection("suggestions");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                String author = section.getString(id + ".author");
                String date = section.getString(id + ".date");
                String content = section.getString(id + ".content");
                list.put(id, "&e" + date + " &7- &b" + author + "&7: &f" + content);
            }
        }
        return list;
    }

    public String getSuggestionAuthor(String id) {
        if (suggestionsConfig.contains("suggestions." + id)) {
            return suggestionsConfig.getString("suggestions." + id + ".author");
        }
        return null;
    }

    private void saveSuggestions() {
        // Async save to prevent lag
        final org.bukkit.configuration.file.YamlConfiguration configCopy = YamlConfiguration
                .loadConfiguration(suggestionsFile);
        for (String key : suggestionsConfig.getKeys(true)) {
            configCopy.set(key, suggestionsConfig.get(key));
        }
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                configCopy.save(suggestionsFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Could not save suggestions.yml!");
                e.printStackTrace();
            }
        });
    }

    private void logHistory(String line) {
        // Async write to prevent lag
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(historyFile, true))) {
                writer.write(line);
                writer.newLine();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not write to suggestions_history.txt!");
                e.printStackTrace();
            }
        });
    }
}
