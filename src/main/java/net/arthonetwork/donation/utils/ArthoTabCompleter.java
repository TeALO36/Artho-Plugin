package net.arthonetwork.donation.utils;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ArthoTabCompleter implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        List<String> commands = new ArrayList<>();

        if (args.length == 0)
            return completions;
        String currentArg = args[args.length - 1];

        String cmdName = command.getName().toLowerCase();

        if (cmdName.equals("annonces") || cmdName.equals("don")) {
            if (args.length == 1) {
                if (sender.hasPermission("arthoplugin.admin") || sender.isOp()) {
                    commands.addAll(Arrays.asList("ajouter", "lien", "reset", "reload", "aide", "interval", "range",
                            "fix", "variable", "add", "link", "help"));
                } else {
                    commands.add("aide");
                }
            } else if (args.length == 2) {
                if (args[0].equalsIgnoreCase("fix") || args[0].equalsIgnoreCase("interval")) {
                    commands.addAll(Arrays.asList("5", "10", "15", "30", "60"));
                } else if (args[0].equalsIgnoreCase("variable") || args[0].equalsIgnoreCase("range")) {
                    commands.addAll(Arrays.asList("5-10", "10-20", "15-30"));
                }
            }
        } else if (cmdName.equals("lag")) {
            if (args.length == 1) {
                commands.addAll(Arrays.asList("joueur", "serveur", "aide"));
            } else if (args.length == 2 && args[0].equalsIgnoreCase("joueur")) {
                return null;
            }
        } else if (cmdName.equals("suggestion") || cmdName.equals("server")) {
            if (args.length == 1) {
                commands.addAll(Arrays.asList("ajouter", "voir", "aide", "supprimer"));
            }
        } else if (cmdName.equals("auth")) {
            if (sender.hasPermission("arthoplugin.admin") || sender.isOp()) {
                if (args.length == 1) {
                    commands.addAll(Arrays.asList("unregister", "whitelist", "set", "reset", "enable", "disable"));
                } else if (args.length == 2) {
                    if (args[0].equalsIgnoreCase("whitelist")) {
                        commands.addAll(Arrays.asList("add", "remove", "list", "on", "off"));
                    } else if (args[0].equalsIgnoreCase("set")) {
                        commands.addAll(Arrays.asList("max-attempts", "timeout"));
                    } else if (args[0].equalsIgnoreCase("unregister") || args[0].equalsIgnoreCase("reset")) {
                        return null;
                    }
                } else if (args.length == 3) {
                    if (args[0].equalsIgnoreCase("whitelist")
                            && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
                        return null;
                    }
                }
            }
        } else if (cmdName.equals("ping")) {
            if (args.length == 1) {
                if (sender.hasPermission("arthoplugin.ping.others") || sender.isOp()) {
                    return null;
                }
            }
        } else if (cmdName.equals("artho") || cmdName.equals("arthonetwork")) {
            if (sender.hasPermission("arthoplugin.admin")) {
                if (args.length == 1) {
                    commands.addAll(Arrays.asList("update", "tips"));
                } else if (args.length == 2) {
                    if (args[0].equalsIgnoreCase("update")) {
                        commands.add("auto");
                        commands.add("rollback");
                    } else if (args[0].equalsIgnoreCase("tips")) {
                        commands.addAll(Arrays.asList("on", "off"));
                    }
                }
            }
        }

        StringUtil.copyPartialMatches(currentArg, commands, completions);
        Collections.sort(completions);
        return completions;
    }
}
