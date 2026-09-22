package net.arthonetwork.donation.utils;

import net.arthonetwork.donation.ArthoPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AuthManager {

    private final ArthoPlugin plugin;
    private YamlStore store;
    private YamlConfiguration userdataConfig;
    // Read from AsyncPlayerChatEvent as well as from the main thread.
    private final Set<UUID> loggedInPlayers = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> ipAttempts = new HashMap<>();
    private final Map<String, Long> ipTimeouts = new HashMap<>();
    // Escalation tracking (in-memory, resets on restart): counts how many times
    // an IP has been through a full temporary lockout before earning a permanent ban.
    private final Map<String, Integer> ipBlockCount = new HashMap<>();
    // Permanent bans (persisted to userdata.yml, survive restarts).
    private final Set<String> bannedIps = new HashSet<>();
    // Permanent ACCOUNT bans (persisted, survive restarts) : an intruder who
    // lost an IP (VPN hop) must not get back in with the same stolen identity.
    private final Set<String> bannedUuids = new HashSet<>();
    // Last IP seen per account, for the login-from-new-IP alert (ops tripwire).
    private final Map<String, String> lastIpByUuid = new HashMap<>();
    // Comptes encore en SHA-256 nu sans sel : remplis au demarrage par l audit.
    private java.util.Set<String> legacyHashAccounts = null;

    public AuthManager(ArthoPlugin plugin) {
        this.plugin = plugin;
        initFile();
    }

    private void initFile() {
        store = new YamlStore(new File(plugin.getDataFolder(), "userdata.yml"), plugin.getLogger());
        userdataConfig = store.load();
        bannedIps.addAll(userdataConfig.getStringList("security.banned-ips"));
        bannedUuids.addAll(userdataConfig.getStringList("security.banned-uuids"));
        for (String key : userdataConfig.getKeys(false)) {
            String ip = userdataConfig.getString(key + ".ip");
            if (ip != null) {
                lastIpByUuid.put(key.toLowerCase(), ip);
            }
        }
    }

    public boolean isRegistered(UUID uuid) {
        return userdataConfig.contains(uuid.toString() + ".password");
    }

    public boolean isLoggedIn(UUID uuid) {
        return loggedInPlayers.contains(uuid);
    }

    public void register(UUID uuid, String password, String ip) {
        userdataConfig.set(uuid.toString() + ".password", PasswordHasher.hash(password));
        userdataConfig.set(uuid.toString() + ".ip", ip);
        saveUserdata();
        login(uuid);
    }

    public boolean login(UUID uuid, String password, String ip) {
        if (checkPassword(uuid, password)) {
            // Le hachage doit partir en PBKDF2 des que le mot de passe est en
            // main : en laissant la migration au hasard d un futur login, les
            // comptes inactifs restaient cassables indefiniment (SHA-256 nu).
            String key = uuid.toString() + ".password";
            String stored = userdataConfig.getString(key);
            if (PasswordHasher.needsUpgrade(stored)) {
                upgradeHash(key, password);
                plugin.getLogger().info("[Auth] Hachage PBKDF2 pose pour " + uuid + " (migration au login).");
            }
            if (ip != null) {
                alertOnNewIp(uuid, ip);
                userdataConfig.set(uuid.toString() + ".ip", ip);
                saveUserdata();
            }
            login(uuid);
            return true;
        }
        return false;
    }

    /** Previens les ops en ligne quand un compte se connecte d une nouvelle IP (usurpation possible). */
    private void alertOnNewIp(UUID uuid, String ip) {
        String previous = lastIpByUuid.get(uuid.toString().toLowerCase());
        lastIpByUuid.put(uuid.toString().toLowerCase(), ip);
        if (previous == null || previous.equals(ip)) {
            return;
        }
        String name = org.bukkit.Bukkit.getOfflinePlayer(uuid).getName();
        String msg = "[Auth] " + name + " se connecte depuis une nouvelle IP : " + ip
                + " (precedente : " + previous + "). Si ce n est pas lui : /auth unregister " + name;
        plugin.getLogger().warning(msg);
        for (org.bukkit.entity.Player op : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (op.isOp() || op.hasPermission("arthoplugin.admin")) {
                op.sendMessage(org.bukkit.ChatColor.YELLOW + msg);
            }
        }
    }

    /**
     * Verifies a password against the stored hash without mutating any
     * login state. Used by /linkaccount to check a Java account's password
     * on behalf of a Bedrock player without logging the Java UUID in.
     *
     * <p>A hash in the old format (bare SHA-256) is accepted and replaced by a
     * current one here, since this is the only moment the plaintext is in hand:
     * nobody has to change their password for the upgrade to happen.
     */
    public boolean checkPassword(UUID uuid, String password) {
        String key = uuid.toString() + ".password";
        String stored = userdataConfig.getString(key);
        if (stored == null || !PasswordHasher.verify(password, stored)) {
            return false;
        }
        if (PasswordHasher.needsUpgrade(stored)) {
            upgradeHash(key, password);
        }
        return true;
    }

    /** Only keeps the new hash if it verifies: a bug here must never be able to lock an account out. */
    private void upgradeHash(String key, String password) {
        String upgraded = PasswordHasher.hash(password);
        if (!PasswordHasher.verify(password, upgraded)) {
            plugin.getLogger().warning("[Auth] Mise a jour d'un hachage abandonnee : le nouveau hachage ne se verifie pas.");
            return;
        }
        userdataConfig.set(key, upgraded);
        saveUserdata();
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

    private void saveUserdata() {
        store.save(userdataConfig);
    }

    /** Waits for pending writes; call from onDisable(). */
    public void flush() {
        store.flush();
    }

    public boolean isIpBlocked(String ip) {
        if (isIpBanned(ip)) {
            return true;
        }
        if (ipTimeouts.containsKey(ip)) {
            if (System.currentTimeMillis() < ipTimeouts.get(ip)) {
                return true;
            } else {
                ipTimeouts.remove(ip);
                ipAttempts.remove(ip);
            }
        }
        return false;
    }

    public void incrementAttempts(String ip) {
        int attempts = ipAttempts.getOrDefault(ip, 0) + 1;
        ipAttempts.put(ip, attempts);
        if (attempts >= getMaxAttempts()) {
            ipTimeouts.put(ip, System.currentTimeMillis() + (getLoginTimeout() * 1000L));
            int blocks = ipBlockCount.getOrDefault(ip, 0) + 1;
            ipBlockCount.put(ip, blocks);
            plugin.getLogger().warning("[Sécurité] IP " + ip + " bloquée temporairement (" + getLoginTimeout()
                    + "s) après " + attempts + " échecs. Blocage n°" + blocks + "/" + getMaxBlocksBeforeBan()
                    + " avant ban définitif.");
            if (blocks >= getMaxBlocksBeforeBan()) {
                banIp(ip);
            }
        }
    }

    public void resetAttempts(String ip) {
        ipAttempts.remove(ip);
        ipTimeouts.remove(ip);
    }

    // Permanent IP bans (escalation from repeated temporary lockouts)

    public boolean isIpBanned(String ip) {
        return bannedIps.contains(ip);
    }

    public void banIp(String ip) {
        if (bannedIps.add(ip)) {
            List<String> list = userdataConfig.getStringList("security.banned-ips");
            list.add(ip);
            userdataConfig.set("security.banned-ips", list);
            saveUserdata();
            plugin.getLogger().warning(
                    "[Sécurité] IP " + ip + " BANNIE DÉFINITIVEMENT après " + getMaxBlocksBeforeBan()
                            + " blocages répétés (bruteforce suspecté). Débloquer avec /auth security unban " + ip);
        }
    }

    public void unbanIp(String ip) {
        bannedIps.remove(ip);
        ipBlockCount.remove(ip);
        ipAttempts.remove(ip);
        ipTimeouts.remove(ip);
        List<String> list = userdataConfig.getStringList("security.banned-ips");
        list.remove(ip);
        userdataConfig.set("security.banned-ips", list);
        saveUserdata();
    }

    public List<String> getBannedIps() {
        return new ArrayList<>(bannedIps);
    }

    // Permanent ACCOUNT bans (plugin side)

    public boolean isUuidBanned(UUID uuid) {
        return bannedUuids.contains(uuid.toString().toLowerCase());
    }

    public boolean banUuid(UUID uuid, String name) {
        String id = uuid.toString().toLowerCase();
        if (!bannedUuids.add(id)) {
            return false;
        }
        List<String> list = userdataConfig.getStringList("security.banned-uuids");
        list.add(id);
        userdataConfig.set("security.banned-uuids", list);
        if (name != null) {
            userdataConfig.set("security.banned-names." + id, name);
        }
        saveUserdata();
        plugin.getLogger().warning("[Sécurité] Compte BANNI (plugin) : " + name + " (" + id + "). Débloquer avec /auth security unban-uuid " + id);
        return true;
    }

    public boolean unbanUuid(String uuidOrName) {
        String id = normalizeUuid(uuidOrName);
        if (id == null) {
            return false;
        }
        if (!bannedUuids.remove(id)) {
            return false;
        }
        List<String> list = userdataConfig.getStringList("security.banned-uuids");
        list.remove(id);
        userdataConfig.set("security.banned-uuids", list);
        saveUserdata();
        return true;
    }

    public List<String> getBannedUuids() {
        return new ArrayList<>(bannedUuids);
    }

    private String normalizeUuid(String input) {
        // Accepte un UUID complet ou un pseudo connu (bananee, .FoggySteak85110...).
        try {
            return java.util.UUID.fromString(input).toString();
        } catch (IllegalArgumentException notAnUuid) {
            for (String id : bannedUuids) {
                String name = userdataConfig.getString("security.banned-names." + id, "");
                if (name.equalsIgnoreCase(input)) {
                    return id;
                }
            }
            org.bukkit.OfflinePlayer known = org.bukkit.Bukkit.getOfflinePlayer(input);
            if (known != null && known.getUniqueId() != null && userdataConfig.contains(known.getUniqueId().toString() + ".password")) {
                return known.getUniqueId().toString();
            }
            return null;
        }
    }

    // Audit des hachages legacy (SHA-256 nu sans sel)

    /** Liste les comptes encore en SHA-256 nu : a vider par les logins ou /auth security rehash. */
    public List<String> auditLegacyHashes() {
        List<String> out = new ArrayList<>();
        for (String key : userdataConfig.getKeys(false)) {
            if ("security".equals(key) || "config".equals(key) || "whitelist".equals(key)) {
                continue;
            }
            String stored = userdataConfig.getString(key + ".password");
            if (PasswordHasher.needsUpgrade(stored)) {
                String name = userdataConfig.getString("security.banned-names." + key.toLowerCase(), "");
                if (name.isEmpty()) {
                    name = org.bukkit.Bukkit.getOfflinePlayer(java.util.UUID.fromString(key)).getName();
                }
                out.add((name != null && !name.isEmpty() ? name : key) + " (" + key + ")");
            }
        }
        return out;
    }

    public int getMaxBlocksBeforeBan() {
        return userdataConfig.getInt("config.max-blocks-before-ban", 3);
    }

    public void setMaxBlocksBeforeBan(int max) {
        userdataConfig.set("config.max-blocks-before-ban", max);
        saveUserdata();
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

    public boolean isAuthEnabled() {
        return userdataConfig.getBoolean("config.auth-enabled", true);
    }

    public void setAuthEnabled(boolean enabled) {
        userdataConfig.set("config.auth-enabled", enabled);
        saveUserdata();
    }

    public long getRemainingTime(String ip) {
        if (ipTimeouts.containsKey(ip)) {
            long remaining = ipTimeouts.get(ip) - System.currentTimeMillis();
            return remaining > 0 ? remaining / 1000 : 0;
        }
        return 0;
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
        userdataConfig.set(uuid.toString() + ".password", PasswordHasher.hash(newPassword));
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
