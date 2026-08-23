package net.arthonetwork.donation.variants;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.EnumMap;
import java.util.Map;

/**
 * A six-part humanoid rig riding a single entity: head, torso, two arms and two
 * legs, each its own {@link ItemDisplay}.
 * <p>
 * Every part model is authored with its pivot at the model origin, because a
 * display rotates around its own origin: an arm hangs below that origin so the
 * rotation reads as a shoulder joint rather than a spin around the arm's middle.
 * <p>
 * Each tick the rig recomputes, for every part, a translation (where the pivot
 * sits relative to the entity, turned by the entity's yaw) and a rotation (the
 * limb angle for the current animation frame).
 */
public class AnimatedRig {

    public enum Part {
        // Hauteurs de pivot en blocs pour une echelle de 1 (figure de 2 blocs) :
        // hanche a 0.75, cou a 1.50, epaule juste en dessous.
        HEAD("head", 0f, 1.50f, 0f),
        TORSO("torso", 0f, 1.50f, 0f),
        ARM_RIGHT("arm_right", -0.30f, 1.45f, 0f),
        ARM_LEFT("arm_left", 0.30f, 1.45f, 0f),
        LEG_RIGHT("leg_right", -0.125f, 0.75f, 0f),
        LEG_LEFT("leg_left", 0.125f, 0.75f, 0f);

        public final String model;
        /** Pivot position relative to the entity's feet, before yaw is applied. */
        public final float ox, oy, oz;

        Part(String model, float ox, float oy, float oz) {
            this.model = model;
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
        }
    }

    private final Entity owner;
    private final Map<Part, ItemDisplay> parts = new EnumMap<>(Part.class);
    private final float scale;
    /** Global vertical correction: passengers sit at the vehicle's mount point. */
    private final float offsetY;
    /** Peak limb angle, in radians, at full walking speed. */
    private final float swingAmplitude;
    /** Last pose pushed per part, to avoid re-sending an identical transform. */
    private final Map<Part, float[]> lastPose = new EnumMap<>(Part.class);

    /** Walk cycle phase, advanced only while the entity actually moves. */
    private float walkPhase = 0f;
    /** Countdown of the attack animation, in ticks. 0 = not attacking. */
    private int attackTicks = 0;
    private Location lastLocation;

    public AnimatedRig(Entity owner, float scale, float offsetY, float swingAmplitude) {
        this.owner = owner;
        this.scale = scale;
        this.offsetY = offsetY;
        this.swingAmplitude = swingAmplitude;
        this.lastLocation = owner.getLocation().clone();
    }

    public void addPart(Part part, ItemDisplay display) {
        parts.put(part, display);
    }

    public boolean isComplete() {
        return parts.size() == Part.values().length;
    }

    public Entity getOwner() {
        return owner;
    }

    public void triggerAttack() {
        attackTicks = 12;
    }

    public void remove() {
        for (ItemDisplay d : parts.values()) {
            d.remove();
        }
        parts.clear();
    }

    public boolean isValid() {
        return owner.isValid() && !parts.isEmpty();
    }

    /** Advances the animation by one tick and pushes the new pose to every part. */
    public void tick() {
        Location now = owner.getLocation();
        double moved = (now.getWorld().equals(lastLocation.getWorld()))
                ? now.toVector().setY(0).distance(lastLocation.toVector().setY(0))
                : 0;
        lastLocation = now.clone();

        // Only advance the walk cycle when moving, so the model stands still
        // instead of marching in place.
        boolean walking = moved > 0.02;
        if (walking) {
            walkPhase += (float) Math.min(moved * 9.0, 1.2);
        } else {
            walkPhase *= 0.6f; // ease limbs back to rest
        }
        if (attackTicks > 0) {
            attackTicks--;
        }

        float swing = walking ? (float) Math.sin(walkPhase) * swingAmplitude : walkPhase * 0.1f;

        // Minecraft yaw 0 faces +Z; limbs swing around the body's "right" axis.
        double yawRad = Math.toRadians(now.getYaw());
        float forwardX = (float) -Math.sin(yawRad), forwardZ = (float) Math.cos(yawRad);
        float rightX = (float) Math.cos(yawRad),   rightZ = (float) Math.sin(yawRad);

        for (Map.Entry<Part, ItemDisplay> e : parts.entrySet()) {
            Part part = e.getKey();
            ItemDisplay display = e.getValue();
            if (!display.isValid()) {
                continue;
            }

            float limbAngle = limbAngle(part, swing);

            // Everything is computed explicitly in world axes and the display is
            // kept at yaw 0, so nothing depends on whether a display's rotation
            // also turns its transformation - a convention we cannot rely on.
            float rx = part.ox * scale * rightX + part.oz * scale * forwardX;
            float rz = part.ox * scale * rightZ + part.oz * scale * forwardZ;
            float py = part.oy * scale + offsetY;

            float[] prev = lastPose.get(part);
            boolean changed = prev == null
                    || Math.abs(prev[0] - rx) > 1e-3f
                    || Math.abs(prev[2] - rz) > 1e-3f
                    || Math.abs(prev[3] - limbAngle) > 1e-3f;

            // Re-sending an identical transform every tick restarts the
            // interpolation towards the same spot, which reads as jitter -
            // most visible on parts that never move, such as the head.
            if (changed) {
                // Duration 1 = the interpolation finishes exactly within the tick,
                // so the next update never restarts an unfinished one (the cause
                // of the jitter).
                display.setInterpolationDuration(1);
                display.setInterpolationDelay(0);
                display.setTransformation(new org.bukkit.util.Transformation(
                        new Vector3f(rx, py, rz),
                        // Swing around the body's right axis, expressed in world space.
                        new AxisAngle4f(limbAngle, rightX, 0f, rightZ),
                        new Vector3f(scale, scale, scale),
                        new AxisAngle4f(0f, 0f, 1f, 0f)));
                lastPose.put(part, new float[]{rx, py, rz, limbAngle});
            }
        }
    }

    /** Angle of one limb for the current frame, in radians. */
    private float limbAngle(Part part, float swing) {
        if (attackTicks > 0) {
            // Arms swing up and back down over the attack window.
            float progress = 1f - (attackTicks / 12f);
            float raise = (float) Math.sin(progress * Math.PI) * 2.2f;
            if (part == Part.ARM_RIGHT || part == Part.ARM_LEFT) {
                return -raise;
            }
        }
        switch (part) {
            case ARM_RIGHT:
            case LEG_LEFT:
                return swing;
            case ARM_LEFT:
            case LEG_RIGHT:
                return -swing;   // opposite phase, as in a natural gait
            default:
                return 0f;       // head and torso stay upright
        }
    }
}
