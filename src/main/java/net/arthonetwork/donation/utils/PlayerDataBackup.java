package net.arthonetwork.donation.utils;

import net.arthonetwork.donation.ArthoPlugin;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps a copy of a Bedrock account's own character before linking replaces it
 * with the Java account's: nothing is lost, an admin can restore it.
 *
 * <p>Files are copied out of the world folder into
 * plugins/Artho-Plugin/link-backups/. Both the current layout
 * (world/players/...) and the legacy one (world/playerdata, world/stats,
 * world/advancements) are looked for.
 */
public final class PlayerDataBackup {

    private static final String[][] SOURCES = {
            // {sous-dossier de sauvegarde, dossier dans le monde, extension}
            {"data", "players/data", ".dat"},
            {"data", "playerdata", ".dat"},
            {"advancements", "players/advancements", ".json"},
            {"advancements", "advancements", ".json"},
            {"stats", "players/stats", ".json"},
            {"stats", "stats", ".json"},
    };

    private PlayerDataBackup() {
    }

    /**
     * @return the backup folder, or null when the account had no saved data at all
     */
    public static File backup(ArthoPlugin plugin, UUID uuid) throws IOException {
        File dest = new File(plugin.getDataFolder(), "link-backups/" + uuid + "-"
                + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()));
        int copied = 0;
        for (File root : worldRoots()) {
            for (String[] src : SOURCES) {
                File from = new File(root, src[1] + "/" + uuid + src[2]);
                if (!from.isFile()) {
                    continue;
                }
                File toDir = new File(dest, src[0]);
                toDir.mkdirs();
                Files.copy(from.toPath(), new File(toDir, from.getName()).toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
                copied++;
            }
        }
        return copied > 0 ? dest : null;
    }

    /**
     * Where per-player files can live. getWorldFolder() is the world root on older
     * servers, but on recent ones it is the DIMENSION's folder
     * (world/dimensions/minecraft/overworld) while players/ stays at the world
     * root, so the ancestors are searched too. The level folder itself is also
     * derived from the world container, which does not depend on that layout.
     */
    private static Set<File> worldRoots() {
        Set<File> roots = new LinkedHashSet<>();
        File main = Bukkit.getWorlds().get(0).getWorldFolder();
        roots.add(new File(Bukkit.getWorldContainer(), Bukkit.getWorlds().get(0).getName()));
        File folder = main;
        for (int i = 0; i < 4 && folder != null; i++) {
            roots.add(folder);
            folder = folder.getParentFile();
        }
        return roots;
    }
}
