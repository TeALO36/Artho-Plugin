package net.arthonetwork.donation.variants;

import net.arthonetwork.donation.ArthoPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Horse;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads the server-defined catalog of {@link Variant}s from external YAML files
 * (one file per variant, under plugins/Artho-Plugin/variants/) and handles
 * assigning/removing/reading a variant on a specific entity via its
 * PersistentDataContainer. Entirely generic - every value comes from the admin's
 * own YAML and resource pack.
 */
public class VariantManager {

    private final ArthoPlugin plugin;
    private final NamespacedKey variantIdKey;
    private final Map<String, Variant> variantsById = new LinkedHashMap<>();
    private File variantsFolder;
    private final java.util.Random random = new java.util.Random();

    public VariantManager(ArthoPlugin plugin) {
        this.plugin = plugin;
        this.variantIdKey = new NamespacedKey(plugin, "linked_variant_id");
    }

    public void reload() {
        variantsById.clear();
        variantsFolder = new File(plugin.getDataFolder(), "variants");
        if (!variantsFolder.exists()) {
            variantsFolder.mkdirs();
        }

        File[] existing = variantsFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
        if (existing == null || existing.length == 0) {
            plugin.saveResource("variants/example_variant.yml", false);
        }

        File[] files = variantsFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            try {
                Variant variant = parse(file);
                if (variant != null) {
                    variantsById.put(variant.getId(), variant);
                }
            } catch (Exception e) {
                plugin.getLogger().severe("[Variants] Impossible de charger " + file.getName() + ": " + e.getMessage());
            }
        }
        plugin.getLogger().info("[Variants] " + variantsById.size() + " variante(s) chargee(s).");
    }

    private Variant parse(File file) {
        String id = file.getName().substring(0, file.getName().length() - 4);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        String entityTypeRaw = config.getString("entity-type");
        if (entityTypeRaw == null) {
            plugin.getLogger().warning("[Variants] " + file.getName() + " ignore: 'entity-type' manquant.");
            return null;
        }
        EntityType entityType;
        try {
            entityType = EntityType.valueOf(entityTypeRaw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[Variants] " + file.getName() + " ignore: entity-type '" + entityTypeRaw
                    + "' invalide.");
            return null;
        }

        String displayName = config.getString("display-name");
        int spawnChance = config.getInt("spawn-chance", 0);
        String nativeVariant = config.getString("native-variant");

        Variant.SoundDef firstSight = config.getBoolean("first-sight.enabled", true)
                ? parseSound(config.getConfigurationSection("first-sight.sound"))
                : null;
        Variant.SoundDef ambient = config.getBoolean("ambient.enabled", false)
                ? parseSound(config.getConfigurationSection("ambient.sound"))
                : null;
        double ambientRange = config.getDouble("ambient.range", 16.0);
        int ambientInterval = config.getInt("ambient.interval-ticks", 100);

        Variant.SoundDef onDamage = config.getBoolean("on-damage.enabled", false)
                ? parseSound(config.getConfigurationSection("on-damage.sound")) : null;
        Variant.SoundDef onInteract = config.getBoolean("on-interact.enabled", false)
                ? parseSound(config.getConfigurationSection("on-interact.sound")) : null;
        Variant.SoundDef onDeath = config.getBoolean("on-death.enabled", false)
                ? parseSound(config.getConfigurationSection("on-death.sound")) : null;

        return new Variant(id, entityType, displayName, spawnChance, nativeVariant,
                firstSight, ambient, ambientRange, ambientInterval,
                onDamage, onInteract, onDeath);
    }

    private Variant.SoundDef parseSound(ConfigurationSection sec) {
        if (sec == null) {
            return null;
        }
        String key = sec.getString("key");
        if (key == null) {
            return null;
        }
        float volume = (float) sec.getDouble("volume", 1.0);
        float pitch = (float) sec.getDouble("pitch", 1.0);
        SoundCategory category;
        try {
            category = SoundCategory.valueOf(sec.getString("category", "MASTER").trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            category = SoundCategory.MASTER;
        }
        return new Variant.SoundDef(key, volume, pitch, category);
    }

    public Variant getVariant(String id) {
        return variantsById.get(id);
    }

    public Collection<Variant> getAllVariants() {
        return variantsById.values();
    }

    /** Variants that opt into automatic assignment at natural spawn. */
    public List<Variant> getSpawnableVariants(EntityType type) {
        List<Variant> out = new ArrayList<>();
        for (Variant v : variantsById.values()) {
            if (v.hasSpawnChance() && v.getEntityType() == type) {
                out.add(v);
            }
        }
        return out;
    }

    /** Tags the entity's PDC and applies the optional native game variant. */
    public void assign(Entity entity, Variant variant) {
        entity.getPersistentDataContainer().set(variantIdKey, PersistentDataType.STRING, variant.getId());
        applyNativeVariant(entity, variant.getNativeVariant());
    }

    /**
     * Applies a native game variant so a resource pack can retexture this one
     * entity. Only entity families that genuinely expose variants are handled;
     * anything else is a no-op (sound-only variants need nothing here).
     */
    public void applyNativeVariant(Entity entity, String nativeVariant) {
        if (nativeVariant == null || nativeVariant.isEmpty()) {
            return;
        }
        try {
            if (entity instanceof Horse) {
                ((Horse) entity).setColor(Horse.Color.valueOf(nativeVariant.trim().toUpperCase()));
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[Variants] native-variant '" + nativeVariant + "' invalide pour "
                    + entity.getType() + ".");
        }
    }

    /**
     * Steers an unmarked entity away from the colour reserved for a variant, so
     * naturally-spawned entities don't accidentally wear the custom texture.
     */
    public void avoidReservedVariant(Entity entity, String reserved) {
        if (reserved == null || !(entity instanceof Horse)) {
            return;
        }
        try {
            Horse horse = (Horse) entity;
            Horse.Color reservedColor = Horse.Color.valueOf(reserved.trim().toUpperCase());
            if (horse.getColor() == reservedColor) {
                // Pick a random other colour, so unmarked horses stay varied.
                Horse.Color[] all = Horse.Color.values();
                Horse.Color pick = reservedColor;
                while (pick == reservedColor) {
                    pick = all[random.nextInt(all.length)];
                }
                horse.setColor(pick);
            }
        } catch (IllegalArgumentException ignored) {
            // Unknown colour name: nothing sensible to steer away from.
        }
    }

    public void removeVariant(Entity entity) {
        entity.getPersistentDataContainer().remove(variantIdKey);
    }

    public String getVariantId(Entity entity) {
        return entity.getPersistentDataContainer().get(variantIdKey, PersistentDataType.STRING);
    }

    public Variant getVariantOf(Entity entity) {
        String id = getVariantId(entity);
        return id != null ? variantsById.get(id) : null;
    }
}
