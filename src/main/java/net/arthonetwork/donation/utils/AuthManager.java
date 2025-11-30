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
        if (attempts >= getMaxAttempts()) {
            ipTimeouts.put(ip, System.currentTimeMillis() + (getLoginTimeout() * 1000L));
            return false;
        }

        ipAttempts.put(ip, attempts + 1);
        return true;
    }

    // Whitelist & Config Methods

    public boolean isWhitelistEnabled() {
        return userdataConfig.getBoolean("whitelist.enabled", false);
    }

    public void setWhitelistEnabled(boolean enabled) {
        userdataConfig.set("whitelist.enabled", enabled);
        saveUserdata();
    }

    public boolean isWhitelisted(String name) {
        List<String> list = userdataConfig.getStringList("whitelist.list");
        return list.contains(name.toLowerCase());
    }

    public void addWhitelist(String name) {
        List<String> list = userdataConfig.getStringList("whitelist.list");
        if (!list.contains(name.toLowerCase())) {
            list.add(name.toLowerCase());
            userdataConfig.set("whitelist.list", list);
            saveUserdata();
        }
    }

    public void removeWhitelist(String name) {
        List<String> list = userdataConfig.getStringList("whitelist.list");
        if (list.remove(name.toLowerCase())) {
            userdataConfig.set("whitelist.list", list);
            saveUserdata();
        }
    }

    public List<String> getWhitelist() {
        return userdataConfig.getStringList("whitelist.list");
    }

    public void setMaxAttempts(int max) {
        userdataConfig.set("config.max-attempts", max);
        saveUserdata();
    }

    public int getMaxAttempts() {
        return userdataConfig.getInt("config.max-attempts", 5);
    }

    public void setLoginTimeout(int seconds) {
        userdataConfig.set("config.login-timeout", seconds);
        saveUserdata();
    }

    public int getLoginTimeout() {
        return userdataConfig.getInt("config.login-timeout", 300);
    }

    // Password Reset & Force Change

    public boolean isForceChange(UUID uuid) {
        return userdataConfig.getBoolean(uuid.toString() + ".forceChange", false);
    }

    public void setForceChange(UUID uuid, boolean force) {
        userdataConfig.set(uuid.toString() + ".forceChange", force);
        saveUserdata();
    }

    public void changePassword(UUID uuid, String newPassword) {
        String hash = hashPassword(newPassword);
        userdataConfig.set(uuid.toString() + ".password", hash);
        setForceChange(uuid, false);
        saveUserdata();
    }

    public String resetPassword(UUID uuid) {
        String newPassword = generateRandomPassword();
        changePassword(uuid, newPassword);
        setForceChange(uuid, true);
        return newPassword;
    }

    private String generateRandomPassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 10; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
