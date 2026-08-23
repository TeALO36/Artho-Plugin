package net.arthonetwork.donation.variants;

import net.arthonetwork.donation.ArthoPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;

/**
 * Gives a variant a per-instance custom appearance, which vanilla cannot do for
 * entity types without native variants (an iron golem, for example).
 * <p>
 * The entity itself is made invisible and an {@code ItemDisplay} carrying a
 * custom item model is attached to it. Unlike an entity texture - locked to the
 * entity type and to the vanilla UV resolution - an item model is per-item and
 * free to use any texture size, so the model stays readable.
 * <p>
 * Trade-off: an item model does not animate, so the display does not reproduce
 * the walk cycle. Only entities carrying the variant are affected; every other
 * entity of the same type is untouched.
 */
public class DisplayModelManager {

    private final ArthoPlugin plugin;
    private final NamespacedKey ownerKey;

    /** Resolved once: ItemMeta#setItemModel only exists on recent server APIs. */
    private static Method setItemModel;
    private static boolean setItemModelResolved = false;

    public DisplayModelManager(ArthoPlugin plugin) {
        this.plugin = plugin;
        this.ownerKey = new NamespacedKey(plugin, "display_owner");
    }

    /** Attaches the model if the variant defines one. No-op otherwise. */
    public void apply(Entity entity, Variant variant) {
        String modelId = variant.getDisplayModel();
        if (modelId == null || modelId.isEmpty()) {
            return;
        }

        ItemStack item = new ItemStack(Material.PAPER);
        if (!applyItemModel(item, modelId)) {
            plugin.getLogger().warning("[Variants] display-model ignore: le serveur ne supporte pas "
                    + "ItemMeta#setItemModel (Paper 1.21.4+ requis).");
            return;
        }

        if (entity instanceof LivingEntity) {
            ((LivingEntity) entity).setInvisible(true);
        }

        // The entity's location carries its pitch (a golem tilts its head towards
        // its target); spawning with it would tip the whole model forward.
        org.bukkit.Location spawnAt = entity.getLocation().clone();
        spawnAt.setPitch(0f);

        entity.getWorld().spawn(spawnAt, org.bukkit.entity.ItemDisplay.class, display -> {
            display.setItemStack(item);
            // NONE: no inherited item transform, so the model stays upright.
            // HEAD would apply the rotation of an item worn on the head.
            display.setItemDisplayTransform(org.bukkit.entity.ItemDisplay.ItemDisplayTransform.NONE);
            display.setPersistent(true);
            // Riding puts the display at the mount point, well above the entity:
            // the offset brings it back down, and the scale sizes the model.
            float scale = variant.getDisplayScale();
            // Le modele occupe 0..16 en X/Z, soit un bloc dont le centre est a 0.5 :
            // on le recentre sur l'axe de l'entite, mise a l'echelle comprise.
            display.setTransformation(new org.bukkit.util.Transformation(
                    new org.joml.Vector3f(-0.5f * scale, variant.getDisplayOffsetY(), -0.5f * scale),
                    new org.joml.AxisAngle4f(0, 0, 0, 1),
                    new org.joml.Vector3f(scale, scale, scale),
                    new org.joml.AxisAngle4f(0, 0, 0, 1)));
            // Keep the model upright regardless of what the vehicle does.
            display.setRotation(spawnAt.getYaw(), 0f);
            // Tag the display with its owner so it can be cleaned up later.
            display.getPersistentDataContainer().set(ownerKey,
                    org.bukkit.persistence.PersistentDataType.STRING, entity.getUniqueId().toString());
            entity.addPassenger(display);
        });
    }

    /** Removes the attached display and makes the entity visible again. */
    public void clear(Entity entity) {
        for (Entity passenger : entity.getPassengers()) {
            if (passenger instanceof org.bukkit.entity.ItemDisplay) {
                passenger.remove();
            }
        }
        if (entity instanceof LivingEntity) {
            ((LivingEntity) entity).setInvisible(false);
        }
    }

    /**
     * Sets the item model component by reflection: the plugin targets an older
     * API for compatibility, but the method exists at runtime on this server.
     */
    private boolean applyItemModel(ItemStack item, String modelId) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (!setItemModelResolved) {
            setItemModelResolved = true;
            try {
                setItemModel = ItemMeta.class.getMethod("setItemModel", NamespacedKey.class);
            } catch (NoSuchMethodException e) {
                setItemModel = null;
            }
        }
        if (setItemModel == null) {
            return false;
        }
        try {
            String[] parts = modelId.split(":", 2);
            NamespacedKey key = parts.length == 2
                    ? new NamespacedKey(parts[0], parts[1])
                    : NamespacedKey.minecraft(parts[0]);
            setItemModel.invoke(meta, key);
            item.setItemMeta(meta);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("[Variants] display-model '" + modelId + "' inapplicable: " + e.getMessage());
            return false;
        }
    }
}
