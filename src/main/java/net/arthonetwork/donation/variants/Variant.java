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
        /** Random +/- range applied to pitch on each play, for natural variation. */
        public final float pitchVariation;
        public final SoundCategory category;

        public SoundDef(String key, float volume, float pitch, float pitchVariation, SoundCategory category) {
            this.key = key;
            this.volume = volume;
            this.pitch = pitch;
            this.pitchVariation = pitchVariation;
            this.category = category;
        }

        /** Pitch for this playback, randomised within the configured variation. */
        public float rollPitch(java.util.Random random) {
            if (pitchVariation <= 0) {
                return pitch;
            }
            float p = pitch + (random.nextFloat() * 2 - 1) * pitchVariation;
            return Math.max(0.5f, Math.min(2.0f, p));
        }
    }

    private final String id;
    private final EntityType entityType;
    private final String displayName;

    /** Probability (0..1) of being assigned at natural spawn. 0 disables auto-assignment. */
    private final double spawnProbability;
    /** Raw configured value, kept verbatim for display and for rewriting the file. */
    private final String spawnChanceRaw;

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

    /** Mutes every vanilla sound this entity would make (Entity#setSilent). */
    private final boolean silenceVanilla;

    /** Custom item model shown by an ItemDisplay attached to this entity. */
    private final String displayModel;
    /** Base id of a six-part animated rig (parts are suffixed _head, _torso...). */
    private final String rigModel;
    /** Uniform scale of that model. */
    private final float displayScale;
    /** Vertical offset, to bring the display down from the mount point. */
    private final float displayOffsetY;
    /** Peak limb angle when walking, in radians. */
    private final float swingAmplitude;

    /** Played to the attacker when a player damages this entity. */
    private final SoundDef onDamage;
    /** Played to the player when they right-click this entity. */
    private final SoundDef onInteract;
    /** Played to the killer when this entity dies. */
    private final SoundDef onDeath;

    public Variant(String id, EntityType entityType, String displayName, double spawnProbability,
            String spawnChanceRaw, String nativeVariant,
            SoundDef firstSight, SoundDef ambient, double ambientRange, int ambientIntervalTicks,
            SoundDef onDamage, SoundDef onInteract, SoundDef onDeath, boolean silenceVanilla,
            String displayModel, String rigModel, float displayScale, float displayOffsetY, float swingAmplitude) {
        this.swingAmplitude = swingAmplitude;
        this.rigModel = rigModel;
        this.silenceVanilla = silenceVanilla;
        this.displayModel = displayModel;
        this.displayScale = displayScale;
        this.displayOffsetY = displayOffsetY;
        this.onDamage = onDamage;
        this.onInteract = onInteract;
        this.onDeath = onDeath;
        this.id = id;
        this.entityType = entityType;
        this.displayName = displayName;
        this.spawnProbability = spawnProbability;
        this.spawnChanceRaw = spawnChanceRaw;
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

    public double getSpawnProbability() {
        return spawnProbability;
    }

    /** Human-readable form of the configured chance, e.g. "5/7" or "1/50". */
    public String getSpawnChanceDisplay() {
        return spawnChanceRaw != null ? spawnChanceRaw : "0";
    }

    public boolean hasSpawnChance() {
        return spawnProbability > 0;
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

    public boolean isSilenceVanilla() {
        return silenceVanilla;
    }

    public String getDisplayModel() {
        return displayModel;
    }

    public String getRigModel() {
        return rigModel;
    }

    public float getDisplayScale() {
        return displayScale;
    }

    public float getSwingAmplitude() {
        return swingAmplitude;
    }

    public float getDisplayOffsetY() {
        return displayOffsetY;
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
