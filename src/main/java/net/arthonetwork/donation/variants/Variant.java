package net.arthonetwork.donation.variants;

import org.bukkit.SoundCategory;
import org.bukkit.entity.EntityType;

/**
 * A generic, server-defined "Linked Variant": a unique identity (sound and/or
 * native visual variant) an admin can attach to a specific entity instance.
 * <p>
 * Everything is admin-supplied via YAML - no resource pack, sound key or model
 * value is hardcoded here.
 */
public class Variant {

    public static class SoundDef {
        public final String key;
        public final float volume;
        public final float pitch;
        public final SoundCategory category;

        public SoundDef(String key, float volume, float pitch, SoundCategory category) {
            this.key = key;
            this.volume = volume;
            this.pitch = pitch;
            this.category = category;
        }
    }

    private final String id;
    private final EntityType entityType;
    private final String displayName;

    /** 1-in-N chance to be assigned at natural spawn. 0 disables auto-assignment. */
    private final int spawnChance;

    /**
     * Optional native game variant to force on assignment (e.g. a horse colour).
     * Resource packs can retexture that variant, which is the only vanilla way
     * to make one entity instance look different from another.
     */
    private final String nativeVariant;

    /** Played once per player per entity, on first clear line of sight. */
    private final SoundDef firstSight;

    /** Played repeatedly while a player is nearby (e.g. an insect buzz). */
    private final SoundDef ambient;
    private final double ambientRange;
    private final int ambientIntervalTicks;

    /** Played to the attacker when a player damages this entity. */
    private final SoundDef onDamage;
    /** Played to the player when they right-click this entity. */
    private final SoundDef onInteract;
    /** Played to the killer when this entity dies. */
    private final SoundDef onDeath;

    public Variant(String id, EntityType entityType, String displayName, int spawnChance, String nativeVariant,
            SoundDef firstSight, SoundDef ambient, double ambientRange, int ambientIntervalTicks,
            SoundDef onDamage, SoundDef onInteract, SoundDef onDeath) {
        this.onDamage = onDamage;
        this.onInteract = onInteract;
        this.onDeath = onDeath;
        this.id = id;
        this.entityType = entityType;
        this.displayName = displayName;
        this.spawnChance = spawnChance;
        this.nativeVariant = nativeVariant;
        this.firstSight = firstSight;
        this.ambient = ambient;
        this.ambientRange = ambientRange;
        this.ambientIntervalTicks = ambientIntervalTicks;
    }

    public String getId() {
        return id;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public String getDisplayName() {
        return displayName != null ? displayName : id;
    }

    public int getSpawnChance() {
        return spawnChance;
    }

    public boolean hasSpawnChance() {
        return spawnChance > 0;
    }

    public String getNativeVariant() {
        return nativeVariant;
    }

    public SoundDef getFirstSight() {
        return firstSight;
    }

    public boolean isFirstSightEnabled() {
        return firstSight != null && firstSight.key != null;
    }

    public SoundDef getAmbient() {
        return ambient;
    }

    public boolean isAmbientEnabled() {
        return ambient != null && ambient.key != null;
    }

    public double getAmbientRange() {
        return ambientRange;
    }

    public int getAmbientIntervalTicks() {
        return ambientIntervalTicks;
    }

    public SoundDef getOnDamage() {
        return onDamage;
    }

    public SoundDef getOnInteract() {
        return onInteract;
    }

    public SoundDef getOnDeath() {
        return onDeath;
    }
}
