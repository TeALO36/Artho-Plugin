package net.arthonetwork.donation.utils;

import net.arthonetwork.donation.ArthoPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Records the pairs of accounts linked through /linkaccount, keyed by the
 * Bedrock UUID (the one Floodgate derives from the player's XUID). Each entry
 * points at the Java account that Bedrock account has become.
 *
 * <p>This file is the plugin's own record of who is linked to whom. The linking
 * that actually changes a player's identity at login lives in Floodgate's link
 * database; {@link FloodgateLinkBridge} keeps the two in step and replays this
 * file into Floodgate at startup, so a missing or wiped Floodgate database is
 * repaired rather than silently dropping every link.
 */
public class LinkManager {

    /** One linked pair, as a snapshot detached from the underlying config. */
    public static final class Entry {
        public final UUID bedrockUuid;
        public final UUID javaUuid;
        public final String javaUsername;
        public final String bedrockName;
        public final boolean activated;

        Entry(UUID bedrockUuid, UUID javaUuid, String javaUsername, String bedrockName, boolean activated) {
            this.bedrockUuid = bedrockUuid;
            this.javaUuid = javaUuid;
            this.javaUsername = javaUsername;
            this.bedrockName = bedrockName;
            this.activated = activated;
        }
    }

    private final YamlStore store;
    private final YamlConfiguration links;

    public LinkManager(ArthoPlugin plugin) {
        this.store = new YamlStore(new File(plugin.getDataFolder(), "linked-accounts.yml"), plugin.getLogger());
        this.links = store.load();
    }

    public synchronized boolean isBedrockLinked(UUID bedrockUuid) {
        return links.contains(bedrockUuid + ".javaUuid");
    }

    public synchronized boolean isJavaLinked(UUID javaUuid) {
        return getBedrockUuidForJava(javaUuid) != null;
    }

    public synchronized void link(UUID bedrockUuid, UUID javaUuid, String bedrockName, String javaUsername) {
        String key = bedrockUuid.toString();
        links.set(key + ".javaUuid", javaUuid.toString());
        links.set(key + ".javaUsername", javaUsername);
        links.set(key + ".bedrockName", bedrockName);
        links.set(key + ".linkedAt", System.currentTimeMillis());
        links.set(key + ".activated", false);
        store.save(links);
    }

    public synchronized void unlink(UUID bedrockUuid) {
        links.set(bedrockUuid.toString(), null);
        store.save(links);
    }

    public synchronized UUID getLinkedJavaUuid(UUID bedrockUuid) {
        String raw = links.getString(bedrockUuid + ".javaUuid");
        return raw != null ? UUID.fromString(raw) : null;
    }

    public synchronized String getLinkedJavaUsername(UUID bedrockUuid) {
        return links.getString(bedrockUuid + ".javaUsername");
    }

    /** The Bedrock UUID linked to this Java account, or null. */
    public synchronized UUID getBedrockUuidForJava(UUID javaUuid) {
        for (String key : links.getKeys(false)) {
            if (javaUuid.toString().equals(links.getString(key + ".javaUuid"))) {
                return UUID.fromString(key);
            }
        }
        return null;
    }

    public synchronized String getLinkedBedrockName(UUID javaUuid) {
        UUID bedrock = getBedrockUuidForJava(javaUuid);
        return bedrock != null ? links.getString(bedrock + ".bedrockName") : null;
    }

    /**
     * Whether the steps that go with a new link (homes handed over, old character
     * backed up) have already run for it, so a restart never repeats them.
     */
    public synchronized boolean isActivated(UUID bedrockUuid) {
        return links.getBoolean(bedrockUuid + ".activated", false);
    }

    public synchronized void markActivated(UUID bedrockUuid) {
        if (isBedrockLinked(bedrockUuid)) {
            links.set(bedrockUuid + ".activated", true);
            store.save(links);
        }
    }

    /** Finds a link by either side's name, ignoring case and Floodgate's leading '.'. */
    public synchronized Entry findByName(String name) {
        String wanted = stripPrefix(name).toLowerCase(Locale.ROOT);
        for (Entry e : entries()) {
            if (wanted.equals(stripPrefix(e.javaUsername).toLowerCase(Locale.ROOT))
                    || wanted.equals(stripPrefix(e.bedrockName).toLowerCase(Locale.ROOT))) {
                return e;
            }
        }
        return null;
    }

    /** Snapshot of every link. */
    public synchronized List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        for (String key : links.getKeys(false)) {
            ConfigurationSection sec = links.getConfigurationSection(key);
            if (sec == null || sec.getString("javaUuid") == null) {
                continue;
            }
            try {
                out.add(new Entry(UUID.fromString(key), UUID.fromString(sec.getString("javaUuid")),
                        sec.getString("javaUsername", ""), sec.getString("bedrockName", ""),
                        sec.getBoolean("activated", false)));
            } catch (IllegalArgumentException malformed) {
                // A hand-edited entry with a broken UUID must not take the others down.
            }
        }
        return out;
    }

    private static String stripPrefix(String name) {
        return name != null && name.startsWith(".") ? name.substring(1) : (name == null ? "" : name);
    }

    /** Waits for pending writes; call from onDisable(). */
    public void flush() {
        store.flush();
    }
}
