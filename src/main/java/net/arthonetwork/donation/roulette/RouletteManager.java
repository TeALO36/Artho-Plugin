package net.arthonetwork.donation.roulette;

import net.arthonetwork.donation.ArthoPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Runs the roulette: a weighted draw over the configured table, then a draw
 * over the eligible players, both shown as a decelerating spin. Everything the
 * table contains, what it costs and who can be picked comes from config.yml.
 */
public class RouletteManager {

    /** One way of paying for a spin, e.g. 5 diamonds or 3 stacks of iron. */
    public static class PurchaseOption {
        public final String id;
        public final org.bukkit.Material material;
        public final int amount;
        public final String label;

        PurchaseOption(String id, org.bukkit.Material material, int amount, String label) {
            this.id = id;
            this.material = material;
            this.amount = amount;
            this.label = label;
        }
    }

    private final ArthoPlugin plugin;
    private final Random random = new Random();

    private boolean enabled;
    private List<String> excludedPlayers = Collections.emptyList();
    private boolean excludeCreative;
    private boolean purchaseEnabled;
    private final Map<String, PurchaseOption> purchaseOptions = new LinkedHashMap<>();
    private final List<RouletteOutcome> outcomes = new ArrayList<>();
    private int outcomeSteps;
    private int playerSteps;
    private int minDelay;
    private int maxDelay;
    private int tntCountdown;

    private boolean running = false;

    public RouletteManager(ArthoPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public boolean isEnabled() { return enabled; }
    public boolean isRunning() { return running; }
    public boolean isPurchaseEnabled() { return purchaseEnabled; }
    public Map<String, PurchaseOption> getPurchaseOptions() { return purchaseOptions; }
    public int getOutcomeCount() { return outcomes.size(); }

    public void load() {
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("features.roulette");
        outcomes.clear();
        purchaseOptions.clear();
        if (root == null) {
            enabled = false;
            return;
        }
        enabled = root.getBoolean("enabled", false);
        excludedPlayers = root.getStringList("excluded-players");
        excludeCreative = root.getBoolean("exclude-creative", true);

        ConfigurationSection anim = root.getConfigurationSection("animation");
        outcomeSteps = anim == null ? 46 : Math.max(5, anim.getInt("outcome-steps", 46));
        playerSteps = anim == null ? 42 : Math.max(5, anim.getInt("player-steps", 42));
        minDelay = anim == null ? 1 : Math.max(1, anim.getInt("min-delay-ticks", 1));
        maxDelay = anim == null ? 13 : Math.max(minDelay + 1, anim.getInt("max-delay-ticks", 13));

        ConfigurationSection tnt = root.getConfigurationSection("tnt");
        tntCountdown = tnt == null ? 30 : Math.max(1, tnt.getInt("countdown-seconds", 30));

        ConfigurationSection buy = root.getConfigurationSection("purchase");
        purchaseEnabled = buy != null && buy.getBoolean("enabled", false);
        ConfigurationSection opts = buy == null ? null : buy.getConfigurationSection("options");
        if (opts != null) {
            for (String key : opts.getKeys(false)) {
                ConfigurationSection o = opts.getConfigurationSection(key);
                if (o == null) continue;
                org.bukkit.Material mat = org.bukkit.Material.matchMaterial(o.getString("material", ""));
                if (mat == null) {
                    plugin.getLogger().warning("[Roulette] materiau de paiement inconnu pour '" + key + "'.");
                    continue;
                }
                int amount = Math.max(1, o.getInt("amount", 1));
                purchaseOptions.put(key.toLowerCase(Locale.ROOT),
                        new PurchaseOption(key.toLowerCase(Locale.ROOT), mat, amount,
                                o.getString("label", amount + " " + mat.name())));
            }
        }

        ConfigurationSection table = root.getConfigurationSection("outcomes");
        if (table != null) {
            for (String key : table.getKeys(false)) {
                RouletteOutcome o = RouletteOutcome.fromConfig(key, table.getConfigurationSection(key),
                        plugin.getLogger());
                if (o != null) {
                    outcomes.add(o);
                }
            }
        }
        plugin.getLogger().info("[Roulette] " + outcomes.size() + " sort(s) charge(s), achat "
                + (purchaseEnabled ? "actif" : "inactif") + ".");
    }

    /** Players who may be designated: online, not excluded, not in creative. */
    public List<Player> eligiblePlayers() {
        List<Player> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (excludedPlayers.contains(p.getName())) continue;
            if (excludeCreative && p.getGameMode() == GameMode.CREATIVE) continue;
            out.add(p);
        }
        return out;
    }

    private RouletteOutcome drawOutcome() {
        int total = 0;
        for (RouletteOutcome o : outcomes) total += o.getWeight();
        int r = random.nextInt(total);
        for (RouletteOutcome o : outcomes) {
            r -= o.getWeight();
            if (r < 0) return o;
        }
        return outcomes.get(outcomes.size() - 1);
    }

    /** Charges the option, returning false when the player cannot afford it. */
    public boolean charge(Player player, PurchaseOption option) {
        if (!player.getInventory().containsAtLeast(new ItemStack(option.material), option.amount)) {
            return false;
        }
        player.getInventory().removeItem(new ItemStack(option.material, option.amount));
        player.updateInventory();
        return true;
    }

    /**
     * @return an error message, or null when the spin started
     */
    public String start(String initiatorName) {
        if (!enabled) return "Le module roulette est desactive.";
        if (running) return "Une roulette est deja en cours.";
        if (outcomes.isEmpty()) return "Aucun sort configure dans features.roulette.outcomes.";
        List<Player> pool = eligiblePlayers();
        if (pool.isEmpty()) return "Aucun joueur eligible (creatif et exclus ne comptent pas).";

        running = true;
        RouletteOutcome outcome = drawOutcome();
        Player victim = pool.get(random.nextInt(pool.size()));
        new SpinTask(outcome, victim, pool, initiatorName).runTaskLater(plugin, 1L);
        return null;
    }

    /**
     * Runs the exact same draw logic as start() - drawOutcome() then a uniform
     * index pick over the eligible pool - n times with no animation, no sound,
     * no side effect. Exists purely to let an admin verify empirically that
     * the live, deployed code is unbiased, instead of trusting a code review
     * or a separate simulation that might not reflect what actually runs.
     *
     * @return null when there is nothing to draw over (disabled, empty table,
     *         no eligible player); otherwise a two-line tally of outcomes and
     *         victims
     */
    public String debugDraw(int n) {
        if (!enabled) return null;
        if (outcomes.isEmpty()) return null;
        List<Player> pool = eligiblePlayers();
        if (pool.isEmpty()) return null;

        Map<String, Integer> byOutcome = new LinkedHashMap<>();
        Map<String, Integer> byPlayer = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            RouletteOutcome o = drawOutcome();
            Player v = pool.get(random.nextInt(pool.size()));
            byOutcome.merge(o.getId(), 1, Integer::sum);
            byPlayer.merge(v.getName(), 1, Integer::sum);
        }
        StringBuilder sb = new StringBuilder();
        sb.append(n).append(" tirages (sort) : ");
        byOutcome.forEach((k, v) -> sb.append(k).append("=").append(v).append(" (")
                .append(Math.round(v * 100.0 / n)).append("%) "));
        sb.append("\n").append(n).append(" tirages (joueur) : ");
        byPlayer.forEach((k, v) -> sb.append(k).append("=").append(v).append(" (")
                .append(Math.round(v * 100.0 / n)).append("%) "));
        return sb.toString();
    }

    // ------------------------------------------------------------ animation

    /** Cubic ease-out: a long fast blur, then a sharp, readable braking. */
    private long delayFor(int step, int steps) {
        double t = steps <= 1 ? 1 : (double) step / (steps - 1);
        return Math.round(minDelay + (maxDelay - minDelay) * Math.pow(t, 3));
    }

    private void broadcastTitle(String title, String subtitle, int stay) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(title, subtitle, 0, stay, 0);
        }
    }

    /** Spigot n'expose pas Player#sendActionBar : on passe par le canal ACTION_BAR. */
    private void actionBarAll(String text) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    net.md_5.bungee.api.chat.TextComponent.fromLegacyText(text));
        }
    }

    private void broadcastSound(Sound sound, float volume, float pitch) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), sound, volume, pitch);
        }
    }

    private class SpinTask extends BukkitRunnable {
        private final RouletteOutcome outcome;
        private final Player victim;
        private final List<Player> pool;
        private final String initiator;

        private int phase = 0; // 0 annonce, 1 sorts, 2 pause, 3 joueurs, 4 final
        private int step = 0;

        SpinTask(RouletteOutcome outcome, Player victim, List<Player> pool, String initiator) {
            this.outcome = outcome;
            this.victim = victim;
            this.pool = pool;
            this.initiator = initiator;
        }

        private void next(long delay) {
            new SpinTask2(this).runTaskLater(plugin, delay);
        }

        @Override
        public void run() {
            switch (phase) {
                case 0:
                    Bukkit.broadcastMessage(ChatColor.DARK_RED + "  ╔══════════════════════════════════╗");
                    Bukkit.broadcastMessage(ChatColor.DARK_RED + "  ║   " + ChatColor.RED + ChatColor.BOLD
                            + "ROULETTE ARTHONETWORK" + ChatColor.RESET + ChatColor.DARK_RED + "       ║");
                    Bukkit.broadcastMessage(ChatColor.DARK_RED + "  ╚══════════════════════════════════╝");
                    Bukkit.broadcastMessage(ChatColor.GRAY + "  Lancee par " + ChatColor.WHITE + initiator);
                    Bukkit.broadcastMessage(ChatColor.GRAY + "  Candidats : " + ChatColor.WHITE + names());
                    broadcastTitle(ChatColor.RED + "" + ChatColor.BOLD + "ROULETTE",
                            ChatColor.GRAY + "Quel sort va tomber ?", 40);
                    broadcastSound(Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 0.6f);
                    phase = 1;
                    next(50L);
                    return;

                case 1: {
                    RouletteOutcome shown = step == outcomeSteps - 1
                            ? outcome
                            : outcomes.get((step + outcomeStart()) % outcomes.size());
                    broadcastTitle(shown.coloredLabel(), ChatColor.YELLOW + "" + ChatColor.BOLD + "LE SORT", 40);
                    broadcastSound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.7f, 1.6f);
                    long d = delayFor(step, outcomeSteps);
                    step++;
                    if (step >= outcomeSteps) {
                        phase = 2;
                        step = 0;
                    }
                    next(d);
                    return;
                }

                case 2:
                    broadcastTitle(outcome.coloredLabel(),
                            ChatColor.WHITE + "Reste a savoir POUR QUI.", 70);
                    broadcastSound(Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.8f);
                    Bukkit.broadcastMessage(ChatColor.GRAY + "  ► Sort tire : " + outcome.coloredLabel());
                    phase = 3;
                    next(70L);
                    return;

                case 3: {
                    Player shown = step == playerSteps - 1
                            ? victim
                            : pool.get((step + playerStart()) % pool.size());
                    ChatColor c = step % 3 == 0 ? ChatColor.WHITE
                            : step % 3 == 1 ? ChatColor.RED : ChatColor.GRAY;
                    broadcastTitle(c + "" + ChatColor.BOLD + shown.getName(),
                            ChatColor.RED + "" + ChatColor.BOLD + "LA VICTIME", 40);
                    broadcastSound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.7f, 1.9f);
                    long d = delayFor(step, playerSteps);
                    step++;
                    if (step >= playerSteps) {
                        phase = 4;
                    }
                    next(d);
                    return;
                }

                default:
                    finish();
            }
        }

        private int outcomeStart() {
            return Math.floorMod(outcomes.indexOf(outcome) - (outcomeSteps - 1), outcomes.size());
        }

        private int playerStart() {
            return Math.floorMod(pool.indexOf(victim) - (playerSteps - 1), pool.size());
        }

        private String names() {
            StringBuilder sb = new StringBuilder();
            for (Player p : pool) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(p.getName());
            }
            return sb.toString();
        }

        private void finish() {
            broadcastTitle(ChatColor.RED + "" + ChatColor.BOLD + victim.getName(),
                    outcome.coloredLabel(), 80);
            broadcastSound(Sound.ENTITY_WITHER_SPAWN, 0.4f, 1.4f);
            Bukkit.broadcastMessage(ChatColor.GOLD + "  ★ " + ChatColor.RED + ChatColor.BOLD
                    + victim.getName() + ChatColor.RESET + ChatColor.WHITE + " est designe : "
                    + outcome.coloredLabel());

            if (outcome.isTnt()) {
                new TntCountdown(victim).runTaskTimer(plugin, 60L, 20L);
            } else {
                outcome.apply(victim);
                broadcastSound(outcome.getKind() == RouletteOutcome.Kind.BONUS
                        ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ENTITY_WITCH_CELEBRATE, 1f, 1.2f);
                running = false;
            }
        }
    }

    /** Trampoline so a SpinTask can schedule its own next step. */
    private class SpinTask2 extends BukkitRunnable {
        private final SpinTask inner;
        SpinTask2(SpinTask inner) { this.inner = inner; }
        @Override public void run() { inner.run(); }
    }

    /**
     * Gives the player time to get away from anything they care about, then
     * detonates on them. The blast is not meant to be escapable - only its
     * location is, which is the point of the delay.
     */
    private class TntCountdown extends BukkitRunnable {
        private final Player target;
        private int left = tntCountdown;

        TntCountdown(Player target) {
            this.target = target;
            Bukkit.broadcastMessage(ChatColor.GRAY + "  " + ChatColor.RED + ChatColor.BOLD + target.getName()
                    + ChatColor.RESET + ChatColor.GRAY + " explose dans " + tntCountdown
                    + "s. Eloignez-vous de vos constructions.");
        }

        @Override
        public void run() {
            if (!target.isOnline()) {
                cancel();
                running = false;
                return;
            }
            if (left <= 0) {
                target.sendTitle(ChatColor.DARK_RED + "" + ChatColor.BOLD + "BOOM", "", 0, 30, 10);
                RouletteOutcome.detonate(target);
                actionBarAll("");
                cancel();
                running = false;
                return;
            }
            ChatColor c = left <= 5 ? ChatColor.DARK_RED : left <= 10 ? ChatColor.RED : ChatColor.GOLD;
            float pitch = left <= 5 ? 2.0f : left <= 10 ? 1.6f : 1.2f;
            target.sendTitle(c + "" + ChatColor.BOLD + left,
                    ChatColor.RED + "" + ChatColor.BOLD + "COURS !", 0, 25, 0);
            target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, pitch);
            actionBarAll(c + "TNT sur " + target.getName() + " — " + left + "s");
            left--;
        }
    }
}
