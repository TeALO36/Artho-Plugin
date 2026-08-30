package net.arthonetwork.donation.roulette;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;
import java.util.logging.Logger;

/**
 * One entry of the roulette table, built entirely from config.yml: nothing
 * about the prizes or the penalties is hardcoded, so the table can be
 * reshaped without touching the plugin.
 */
public class RouletteOutcome {

    public enum Kind { BONUS, MALUS, TNT }

    private final String id;
    private final String label;
    private final ChatColor color;
    private final Kind kind;
    private final int weight;

    // give
    private final Material material;
    private final int amount;
    // effect
    private final PotionEffectType effectType;
    private final int duration;
    private final int amplifier;

    private RouletteOutcome(String id, String label, ChatColor color, Kind kind, int weight,
                            Material material, int amount,
                            PotionEffectType effectType, int duration, int amplifier) {
        this.id = id;
        this.label = label;
        this.color = color;
        this.kind = kind;
        this.weight = weight;
        this.material = material;
        this.amount = amount;
        this.effectType = effectType;
        this.duration = duration;
        this.amplifier = amplifier;
    }

    public String getId() { return id; }
    public String getLabel() { return label; }
    public ChatColor getColor() { return color; }
    public Kind getKind() { return kind; }
    public int getWeight() { return weight; }
    public boolean isTnt() { return kind == Kind.TNT; }

    /** Label already wrapped in its configured colour, ready for a title. */
    public String coloredLabel() {
        return color + label;
    }

    /**
     * @return null when the section is unusable; the reason is logged rather
     *         than thrown, so one broken entry never takes the whole table down
     */
    public static RouletteOutcome fromConfig(String id, ConfigurationSection sec, Logger log) {
        if (sec == null) {
            return null;
        }
        String label = sec.getString("label", id);
        int weight = Math.max(1, sec.getInt("weight", 1));

        ChatColor color;
        try {
            color = ChatColor.valueOf(sec.getString("color", "WHITE").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warning("[Roulette] couleur inconnue pour '" + id + "', WHITE utilise.");
            color = ChatColor.WHITE;
        }

        Kind kind;
        try {
            kind = Kind.valueOf(sec.getString("kind", "BONUS").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warning("[Roulette] kind inconnu pour '" + id + "' (bonus/malus/tnt attendu), entree ignoree.");
            return null;
        }

        Material material = null;
        int amount = 0;
        ConfigurationSection give = sec.getConfigurationSection("give");
        if (give != null) {
            material = Material.matchMaterial(give.getString("material", ""));
            amount = Math.max(1, give.getInt("amount", 1));
            if (material == null) {
                log.warning("[Roulette] materiau inconnu pour '" + id + "', entree ignoree.");
                return null;
            }
        }

        PotionEffectType effectType = null;
        int duration = 0;
        int amplifier = 0;
        ConfigurationSection eff = sec.getConfigurationSection("effect");
        if (eff != null) {
            effectType = PotionEffectType.getByName(eff.getString("type", "").toUpperCase(Locale.ROOT));
            duration = Math.max(1, eff.getInt("duration", 30));
            amplifier = Math.max(0, eff.getInt("amplifier", 0));
            if (effectType == null) {
                log.warning("[Roulette] effet inconnu pour '" + id + "', entree ignoree.");
                return null;
            }
        }

        if (kind != Kind.TNT && material == null && effectType == null) {
            log.warning("[Roulette] '" + id + "' ne fait rien (ni give, ni effect, ni tnt), entree ignoree.");
            return null;
        }
        return new RouletteOutcome(id, label, color, kind, weight,
                material, amount, effectType, duration, amplifier);
    }

    /** Applies everything except the TNT, which needs its own countdown. */
    public void apply(Player target) {
        if (material != null) {
            for (ItemStack leftover : target.getInventory().addItem(new ItemStack(material, amount)).values()) {
                // Inventaire plein : au sol plutot que perdu.
                target.getWorld().dropItemNaturally(target.getLocation(), leftover);
            }
        }
        if (effectType != null) {
            target.addPotionEffect(new PotionEffect(effectType, duration * 20, amplifier, false, true, true));
        }
    }

    /**
     * Detonates on the spot. The countdown already gave the player their time
     * to get clear of anything they cared about; the blast itself is not meant
     * to be dodgeable, so the primed entity is spawned with a one tick fuse.
     */
    public static void detonate(Player target) {
        Location at = target.getLocation();
        TNTPrimed tnt = target.getWorld().spawn(at, TNTPrimed.class);
        tnt.setFuseTicks(1);
    }
}
