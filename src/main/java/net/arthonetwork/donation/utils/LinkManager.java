package net.arthonetwork.donation.utils;

import net.arthonetwork.donation.ArthoPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * Stores the link between a Bedrock player's Bukkit UUID (the UUID Floodgate
 * deterministically assigns from their XUID) and the offline UUID of the
 * Java account they linked to via /linkaccount, in either direction.
 */
public class LinkManager {

    private final ArthoPlugin plugin;
    private File linksFile;
    private FileConfiguration linksConfig;

    public LinkManager(ArthoPlugin plugin) {
        this.plugin = plugin;
        initFile();
    }

    private void initFile() {
        linksFile = new File(plugin.getDataFolder(), "linked-accounts.yml");
        if (!linksFile.exists()) {
            try {
                linksFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create linked-accounts.yml!");
                e.printStackTrace();
            }
        }
        linksConfig = YamlConfiguration.loadConfiguration(linksFile);
    }

    public boolean isBedrockLinked(UUID bedrockUuid) {
        return linksConfig.contains(bedrockUuid.toString() + ".javaUuid");
    }

    public boolean isJavaLinked(UUID javaUuid) {
        for (String key : linksConfig.getKeys(false)) {
            if (javaUuid.toString().equals(linksConfig.getString(key + ".javaUuid"))) {
                return true;
            }
        }
        return false;
    }

    public void link(UUID bedrockUuid, UUID javaUuid, String bedrockName, String javaUsername) {
        linksConfig.set(bedrockUuid.toString() + ".javaUuid", javaUuid.toString());
        linksConfig.set(bedrockUuid.toString() + ".javaUsername", javaUsername);
        linksConfig.set(bedrockUuid.toString() + ".bedrockName", bedrockName);
        linksConfig.set(bedrockUuid.toString() + ".linkedAt", System.currentTimeMillis());
        save();
    }

    public void unlink(UUID bedrockUuid) {
        linksConfig.set(bedrockUuid.toString(), null);
        save();
    }

    public UUID getLinkedJavaUuid(UUID bedrockUuid) {
        String raw = linksConfig.getString(bedrockUuid.toString() + ".javaUuid");
        return raw != null ? UUID.fromString(raw) : null;
    }

    public String getLinkedJavaUsername(UUID bedrockUuid) {
        return linksConfig.getString(bedrockUuid.toString() + ".javaUsername");
    }

    public String getLinkedBedrockName(UUID javaUuid) {
        for (String key : linksConfig.getKeys(false)) {
            if (javaUuid.toString().equals(linksConfig.getString(key + ".javaUuid"))) {
                return linksConfig.getString(key + ".bedrockName");
            }
        }
        return null;
    }

    private void save() {
        final YamlConfiguration configCopy = YamlConfiguration.loadConfiguration(linksFile);
        for (String key : linksConfig.getKeys(true)) {
            configCopy.set(key, linksConfig.get(key));
        }
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                configCopy.save(linksFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Could not save linked-accounts.yml!");
                e.printStackTrace();
            }
        });
    }
}
