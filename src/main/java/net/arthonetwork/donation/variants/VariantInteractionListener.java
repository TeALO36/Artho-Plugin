package net.arthonetwork.donation.variants;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

/**
 * Player-versus-entity triggers for variants: hitting, right-clicking or
 * killing a variant-tagged entity. Each sound is played to the acting player
 * only, so audio is never shared between players.
 */
public class VariantInteractionListener implements Listener {

    private final java.util.Random random = new java.util.Random();

    private final VariantManager variantManager;
    private final RigManager rigManager;

    public VariantInteractionListener(VariantManager variantManager, RigManager rigManager) {
        this.variantManager = variantManager;
        this.rigManager = rigManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        // A rigged entity striking something plays its attack animation, whoever
        // the target is - that is the golem swinging, not the golem being hit.
        rigManager.onAttack(event.getDamager());

        if (!(event.getDamager() instanceof Player)) {
            return;
        }
        play((Player) event.getDamager(), event.getEntity(), Trigger.DAMAGE);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        play(event.getPlayer(), event.getRightClicked(), Trigger.INTERACT);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            play(killer, event.getEntity(), Trigger.DEATH);
        }
    }

    private enum Trigger { DAMAGE, INTERACT, DEATH }

    private void play(Player player, Entity target, Trigger trigger) {
        Variant variant = variantManager.getVariantOf(target);
        if (variant == null) {
            return;
        }
        Variant.SoundDef sound;
        switch (trigger) {
            case DAMAGE:   sound = variant.getOnDamage();   break;
            case INTERACT: sound = variant.getOnInteract();  break;
            case DEATH:    sound = variant.getOnDeath();     break;
            default:       return;
        }
        if (sound == null || sound.key == null) {
            return;
        }
        player.playSound(target.getLocation(), sound.key, sound.category, sound.volume, sound.rollPitch(random));
    }
}
