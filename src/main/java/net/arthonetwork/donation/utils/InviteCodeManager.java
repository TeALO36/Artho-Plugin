package net.arthonetwork.donation.utils;

import net.arthonetwork.donation.ArthoPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Codes d'invitation pour /register.
 *
 * <p>En mode crack, le pseudo est la seule identite qu'un client Java presente :
 * quiconque connait le pseudo d'un joueur peut le taper. Le mot de passe ne
 * protege donc que les comptes DEJA enregistres ; sans garde supplementaire,
 * quiconque peut aussi CREER un compte sur un pseudo existant ou voisin
 * (look-alike), ce qui a permis l'incident du 22/09/2026 (bananee imitant
 * banane pour approcher ses amis).
 *
 * <p>Desormais un compte ne peut etre cree qu'avec un code distribue par un
 * administrateur : /auth invite add|generate|remove|list. Les codes sont
 * conserves dans invite-codes.yml (nombre d'utilisations par code, trace de
 * l'utilisation).
 */
public class InviteCodeManager {

    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"; // sans I, L, O, 0, 1
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ArthoPlugin plugin;
    private final YamlStore store;
    private final YamlConfiguration codes;

    public InviteCodeManager(ArthoPlugin plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(new File(plugin.getDataFolder(), "invite-codes.yml"), plugin.getLogger());
        this.codes = store.load();
    }

    /** Vrai quand /register exige un code d'invitation (defaut : oui). */
    public boolean isRequired() {
        return plugin.getConfig().getBoolean("auth.invite-codes.enabled", true);
    }

    /**
     * Consomme un code pour un joueur.
     *
     * @return null si le code est accepte, sinon le message d'erreur a afficher.
     */
    public String redeem(String rawCode, String playerName, String ip) {
        if (rawCode == null || rawCode.trim().isEmpty()) {
            return plugin.getAuthMessage("invalid-invite-code");
        }
        String code = normalize(rawCode);
        if (!codes.contains(code + ".max-uses")) {
            plugin.getLogger().warning("[Auth] Code d'invitation invalide presente par " + playerName + " (" + ip + ")");
            return plugin.getAuthMessage("invalid-invite-code");
        }
        int maxUses = codes.getInt(code + ".max-uses", 1);
        int uses = codes.getInt(code + ".uses", 0);
        if (uses >= maxUses) {
            plugin.getLogger().warning("[Auth] Code d'invitation epuise presente par " + playerName + " (" + ip + ")");
            return plugin.getAuthMessage("invalid-invite-code");
        }
        codes.set(code + ".uses", uses + 1);
        List<String> usedBy = codes.getStringList(code + ".used-by");
        usedBy.add(playerName + "@" + ip + " le " + LocalDateTime.now().format(DATE));
        codes.set(code + ".used-by", usedBy);
        save();
        plugin.getLogger().info("[Auth] Code d'invitation consomme par " + playerName + " (" + ip + ")");
        return null;
    }

    /** Genere un code aleatoire (format affichable ARTH-XXXX-XXXX). */
    public String generate(int maxUses, String note) {
        String code;
        do {
            StringBuilder sb = new StringBuilder("ARTH");
            for (int i = 0; i < 8; i++) {
                sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
            code = sb.toString();
        } while (codes.contains(code + ".max-uses"));
        setCode(code, maxUses, note);
        return display(code);
    }

    /** Ajoute un code choisi par l'admin ; retourne faux s'il existe deja. */
    public boolean add(String rawCode, int maxUses, String note) {
        String code = normalize(rawCode);
        if (code.length() < 4 || codes.contains(code + ".max-uses")) {
            return false;
        }
        setCode(code, maxUses, note);
        return true;
    }

    private void setCode(String code, int maxUses, String note) {
        codes.set(code + ".max-uses", Math.max(1, maxUses));
        codes.set(code + ".uses", 0);
        codes.set(code + ".created", LocalDateTime.now().format(DATE));
        if (note != null && !note.isEmpty()) {
            codes.set(code + ".note", note);
        }
        save();
    }

    public boolean remove(String rawCode) {
        String code = normalize(rawCode);
        if (!codes.contains(code + ".max-uses")) {
            return false;
        }
        codes.set(code, null);
        save();
        return true;
    }

    public List<String> listCodes() {
        List<String> out = new ArrayList<>();
        for (String code : codes.getKeys(false)) {
            int maxUses = codes.getInt(code + ".max-uses", 1);
            int uses = codes.getInt(code + ".uses", 0);
            String note = codes.getString(code + ".note", "");
            StringBuilder line = new StringBuilder(display(code));
            line.append(" : ").append(uses).append('/').append(maxUses).append(" utilise(s)");
            if (!note.isEmpty()) {
                line.append(" [").append(note).append(']');
            }
            for (String u : codes.getStringList(code + ".used-by")) {
                line.append("\n    - consomme par ").append(u);
            }
            out.add(line.toString());
        }
        return out;
    }

    /** ARTH-XXXX-XXXX tel qu'affiche, independamment des separateurs tapes. */
    private String display(String normalized) {
        if (normalized.length() >= 8) {
            return normalized.substring(0, 4) + "-" + normalized.substring(4, 8) + "-" + normalized.substring(8);
        }
        return normalized;
    }

    private String normalize(String raw) {
        return raw.trim().toUpperCase().replaceAll("[^A-Z0-9]", "");
    }

    private void save() {
        store.save(codes);
    }
}
