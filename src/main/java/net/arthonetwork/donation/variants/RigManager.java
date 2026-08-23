package net.arthonetwork.donation.variants;

import net.arthonetwork.donation.ArthoPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds and owns the {@link AnimatedRig}s: one six-part rig per entity that
 * declares an animated model. The entity is made invisible and the rig is what
 * players actually see.
 * <p>
 * Rigs are rebuilt on demand rather than persisted: displays are spawned
 * non-persistent so a crash or an unclean shutdown can never leave orphaned
 * body parts lying around the world.
 */
public class RigManager {

    private final ArthoPlugin plugin;
    private final NamespacedKey rigOwnerKey;
    private final Map<UUID, AnimatedRig> rigs = new ConcurrentHashMap<>();

    private static Method setItemModel;
    private static boolean setItemModelResolved = false;

    public RigManager(ArthoPlugin plugin) {
        this.plugin = plugin;
        this.rigOwnerKey = new NamespacedKey(plugin, "rig_owner");
    }

    /** Creates the rig for this entity if the variant declares an animated model. */
    public void create(Entity entity, Variant variant) {
        String base = variant.getRigModel();
        if (base == null || base.isEmpty() || rigs.containsKey(entity.getUniqueId())) {
            return;
        }

        AnimatedRig rig = new AnimatedRig(entity, variant.getDisplayScale(), variant.getDisplayOffsetY(),
                variant.getSwingAmplitude());
        Location at = entity.getLocation().clone();
        at.setPitch(0f);

        for (AnimatedRig.Part part : AnimatedRig.Part.values()) {
            ItemStack item = new ItemStack(Material.PAPER);
            if (!applyItemModel(item, base + "_" + part.model)) {
                plugin.getLogger().warning("[Variants] rig-model ignore: ItemMeta#setItemModel indisponible "
                        + "(Paper 1.21.4+ requis).");
                rig.remove();
                return;
            }
            ItemDisplay display = entity.getWorld().spawn(at, ItemDisplay.class, d -> {
                d.setItemStack(item);
                // Yaw stays 0: the rig positions and orients every part itself.
                d.setRotation(0f, 0f);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                // Not persistent: a rig is always rebuilt from the entity, so an
                // unclean shutdown cannot leave body parts behind.
                d.setPersistent(false);
                d.getPersistentDataContainer().set(rigOwnerKey, PersistentDataType.STRING,
                        entity.getUniqueId().toString());
            });
            entity.addPassenger(display);
            rig.addPart(part, display);
        }

        if (entity instanceof LivingEntity) {
            ((LivingEntity) entity).setInvisible(true);
        }
        rigs.put(entity.getUniqueId(), rig);
    }

    public void destroy(Entity entity) {
        AnimatedRig rig = rigs.remove(entity.getUniqueId());
        if (rig != null) {
            rig.remove();
        }
        if (entity instanceof LivingEntity) {
            ((LivingEntity) entity).setInvisible(false);
        }
    }

    public void onAttack(Entity attacker) {
        AnimatedRig rig = rigs.get(attacker.getUniqueId());
        if (rig != null) {
            rig.triggerAttack();
        }
    }

    /** Advances every live rig by one tick and reaps the dead ones. */
    public void tickAll() {
        for (Iterator<Map.Entry<UUID, AnimatedRig>> it = rigs.entrySet().iterator(); it.hasNext(); ) {
            AnimatedRig rig = it.next().getValue();
            if (!rig.isValid()) {
                rig.remove();
                it.remove();
                continue;
            }
            rig.tick();
        }
    }

    public void removeAll() {
        for (AnimatedRig rig : rigs.values()) {
            rig.remove();
        }
        rigs.clear();
    }

    public boolean hasRig(Entity entity) {
        return rigs.containsKey(entity.getUniqueId());
    }

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
            plugin.getLogger().warning("[Variants] rig-model '" + modelId + "' inapplicable: " + e.getMessage());
            return false;
        }
    }
}
