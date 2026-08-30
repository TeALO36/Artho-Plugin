package net.arthonetwork.donation.commands;

import net.arthonetwork.donation.roulette.RouletteManager;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * /roulette - free for staff, paid for everyone else. The price is taken
 * straight from config.yml, so the payment buttons always match what the
 * server actually charges.
 */
public class RouletteCommand implements CommandExecutor {

    public static final String PERM_USE = "arthoplugin.roulette";
    public static final String PERM_ADMIN = "arthoplugin.roulette.admin";

    private final RouletteManager manager;

    public RouletteCommand(RouletteManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!manager.isEnabled()) {
            sender.sendMessage(ChatColor.RED + "Le module roulette est desactive.");
            return true;
        }
        boolean admin = sender.hasPermission(PERM_ADMIN) || sender.isOp();

        if (args.length == 0) {
            showMenu(sender, admin);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "start":
                if (!admin) {
                    sender.sendMessage(ChatColor.RED + "Reserve aux operateurs. Utilise /roulette pour l'acheter.");
                    return true;
                }
                fire(sender, sender.getName());
                return true;

            case "reload":
                if (!admin) {
                    sender.sendMessage(ChatColor.RED + "Reserve aux operateurs.");
                    return true;
                }
                manager.load();
                sender.sendMessage(ChatColor.GREEN + "Roulette rechargee : " + manager.getOutcomeCount()
                        + " sort(s), achat " + (manager.isPurchaseEnabled() ? "actif" : "inactif") + ".");
                return true;

            case "pay":
                handlePay(sender, args);
                return true;

            default:
                showMenu(sender, admin);
                return true;
        }
    }

    private void handlePay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Seuls les joueurs peuvent payer.");
            return;
        }
        if (!manager.isPurchaseEnabled()) {
            sender.sendMessage(ChatColor.RED + "L'achat de tours est desactive.");
            return;
        }
        if (!sender.hasPermission(PERM_USE)) {
            sender.sendMessage(ChatColor.RED + "Tu n'as pas la permission de lancer la roulette.");
            return;
        }
        if (args.length != 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /roulette pay <" + String.join("|",
                    manager.getPurchaseOptions().keySet()) + ">");
            return;
        }
        RouletteManager.PurchaseOption opt = manager.getPurchaseOptions()
                .get(args[1].toLowerCase(Locale.ROOT));
        if (opt == null) {
            sender.sendMessage(ChatColor.RED + "Moyen de paiement inconnu.");
            return;
        }
        if (manager.isRunning()) {
            sender.sendMessage(ChatColor.RED + "Une roulette est deja en cours, reessaie apres.");
            return;
        }
        Player player = (Player) sender;
        if (!manager.charge(player, opt)) {
            player.sendMessage(ChatColor.RED + "Il te faut " + ChatColor.WHITE + opt.label
                    + ChatColor.RED + " dans ton inventaire.");
            return;
        }
        player.sendMessage(ChatColor.GREEN + "Paiement accepte : " + ChatColor.WHITE + opt.label);
        // Le paiement est deja preleve : en cas d'echec du lancement, on rembourse.
        String err = manager.start(player.getName());
        if (err != null) {
            player.getInventory().addItem(new org.bukkit.inventory.ItemStack(opt.material, opt.amount));
            player.sendMessage(ChatColor.RED + err + " Tu as ete rembourse.");
        }
    }

    private void fire(CommandSender sender, String initiator) {
        String err = manager.start(initiator);
        if (err != null) {
            sender.sendMessage(ChatColor.RED + err);
        }
    }

    private void showMenu(CommandSender sender, boolean admin) {
        sender.sendMessage("");
        sender.sendMessage(ChatColor.DARK_RED + "  ══ " + ChatColor.RED + ChatColor.BOLD + "ROULETTE"
                + ChatColor.RESET + ChatColor.DARK_RED + " ══");
        sender.sendMessage(ChatColor.GRAY + "  Un sort est tire au hasard (" + manager.getOutcomeCount()
                + " possibles), puis un joueur.");
        sender.sendMessage(ChatColor.GRAY + "  Ca peut etre un " + ChatColor.GREEN + "bonus"
                + ChatColor.GRAY + " comme un " + ChatColor.RED + "malus" + ChatColor.GRAY + ".");

        if (admin) {
            sender.sendMessage(ChatColor.GOLD + "  /roulette start " + ChatColor.WHITE + "- Lancer gratuitement.");
            sender.sendMessage(ChatColor.GOLD + "  /roulette reload " + ChatColor.WHITE + "- Recharger la config.");
        }

        if (!manager.isPurchaseEnabled()) {
            if (!admin) {
                sender.sendMessage(ChatColor.GRAY + "  L'achat de tours est actuellement desactive.");
            }
            sender.sendMessage("");
            return;
        }

        sender.sendMessage(ChatColor.GRAY + "  Paye ton tour en cliquant :");
        if (!(sender instanceof Player)) {
            for (RouletteManager.PurchaseOption o : manager.getPurchaseOptions().values()) {
                sender.sendMessage(ChatColor.YELLOW + "   /roulette pay " + o.id + ChatColor.WHITE + " - " + o.label);
            }
            sender.sendMessage("");
            return;
        }

        Player player = (Player) sender;
        TextComponent line = new TextComponent("  ");
        for (RouletteManager.PurchaseOption o : manager.getPurchaseOptions().values()) {
            TextComponent button = new TextComponent(" [ " + o.label + " ] ");
            button.setColor(net.md_5.bungee.api.ChatColor.GOLD);
            button.setBold(true);
            button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/roulette pay " + o.id));
            button.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new Text(ChatColor.GRAY + "Cliquer pour payer " + ChatColor.WHITE + o.label
                            + ChatColor.GRAY + "\net lancer la roulette.")));
            line.addExtra(button);
        }
        player.spigot().sendMessage(line);
        player.sendMessage(ChatColor.DARK_GRAY + "  Le paiement est preleve dans ton inventaire.");
        player.sendMessage("");
    }
}
