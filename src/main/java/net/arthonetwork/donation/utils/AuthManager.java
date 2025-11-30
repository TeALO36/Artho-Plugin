package net.arthonetwork.donation.utils;

import net.arthonetwork.donation.ArthoPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class AuthManager {

    private final ArthoPlugin plugin;
    private File userdataFile;
    private FileConfiguration userdataConfig;
    private final Set<UUID> loggedInPlayers = new HashSet<>();
    private final Map<String, Integer> ipAttempts = new HashMap<>();
    private final Map<String, Long> ipTimeouts = new HashMap<>();

    public AuthManager(ArthoPlugin plugin) {
        this.plugin = plugin;
        initFile();
    }

    private void initFile() {
        userdataFile = new File(plugin.getDataFolder(), "userdata.yml");
        if (!userdataFile.exists()) {
            try {
                userdataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create userdata.yml!");
                e.printStackTrace();
            }
        }
        userdataConfig = YamlConfiguration.loadConfiguration(userdataFile);
    }

    public boolean isRegistered(UUID uuid) {
        return userdataConfig.contains(uuid.toString() + ".password");
    }

    public boolean isLoggedIn(UUID uuid) {
        return loggedInPlayers.contains(uuid);
    }

    public void register(UUID uuid, String password, String ip) {
        String hash = hashPassword(password);
        userdataConfig.set(uuid.toString() + ".password", hash);
        userdataConfig.set(uuid.toString() + ".ip", ip);
        saveUserdata();
        login(uuid);
    }

    public boolean login(UUID uuid, String password) {
        String storedHash = userdataConfig.getString(uuid.toString() + ".password");
        if (storedHash != null && storedHash.equals(hashPassword(password))) {
            login(uuid);
            return true;
        }
        return false;
    }

    public void login(UUID uuid) {
        loggedInPlayers.add(uuid);
    }

    public void logout(UUID uuid) {
        loggedInPlayers.remove(uuid);
    }

    public void unregister(UUID uuid) {
        userdataConfig.set(uuid.toString(), null);
        saveUserdata();
        logout(uuid);
    }

    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void saveUserdata() {
        try {
            userdataConfig.save(userdataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save userdata.yml!");
            e.printStackTrace();
        }
    }

    public boolean checkIpLimit(String ip) {
        if (ipTimeouts.containsKey(ip)) {
            if (System.currentTimeMillis() < ipTimeouts.get(ip)) {
                return false;
            } else {
                ipTimeouts.remove(ip);
                ipAttempts.remove(ip);
            }
        }

        int attempts = ipAttempts.getOrDefault(ip, 0);
        if (attempts >= 5) {
            ipTimeouts.put(ip, System.currentTimeMillis() + 300000); // 5 minutes timeout
            return false;
        }

        ipAttempts.put(ip, attempts + 1);
        return true;
    }
}
