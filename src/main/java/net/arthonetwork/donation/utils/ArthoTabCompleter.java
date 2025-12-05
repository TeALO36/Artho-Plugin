package net.arthonetwork.donation.utils;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
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

        if (command.getName().equalsIgnoreCase("don")) {
            if (args.length == 1) {
                if (sender.hasPermission("arthoplugin.admin") || sender.isOp()) {
                    commands.addAll(Arrays.asList("add", "link", "reset", "reload", "help", "enable", "disable", "fix",
                            "variable"));
                } else {
                    commands.add("help");
                }
            } else if (args.length == 2) {
                if (args[0].equalsIgnoreCase("fix")) {
                    commands.addAll(Arrays.asList("5", "10", "15", "30", "60"));
                } else if (args[0].equalsIgnoreCase("variable")) {
                    commands.addAll(Arrays.asList("5-10", "10-20", "15-30"));
                }
            }
        } else if (command.getName().equalsIgnoreCase("lag")) {
            if (args.length == 1) {
                commands.addAll(Arrays.asList("joueur", "serveur", "help"));
            } else if (args.length == 2 && args[0].equalsIgnoreCase("joueur")) {
                return null; // Return null to let Bukkit suggest online players
            }
        } else if (command.getName().equalsIgnoreCase("server")) {
            if (args.length == 1) {
                commands.addAll(Arrays.asList("add", "list", "help"));
                if (sender.hasPermission("arthoplugin.admin") || sender.isOp()) {
                    commands.add("remove");
                }
            }
        } else if (command.getName().equalsIgnoreCase("auth")) {
            if (sender.hasPermission("arthoplugin.admin") || sender.isOp()) {
                if (args.length == 1) {
                    commands.addAll(Arrays.asList("unregister", "whitelist", "set", "reset"));
                } else if (args.length == 2) {
                    if (args[0].equalsIgnoreCase("whitelist")) {
                        commands.addAll(Arrays.asList("add", "remove", "list", "on", "off"));
                    } else if (args[0].equalsIgnoreCase("set")) {
                        commands.addAll(Arrays.asList("max-attempts", "timeout"));
                    } else if (args[0].equalsIgnoreCase("unregister") || args[0].equalsIgnoreCase("reset")) {
                        return null; // Suggest players
                    }
                } else if (args.length == 3) {
                    if (args[0].equalsIgnoreCase("whitelist")
                            && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
                        return null; // Suggest players
                    }
                }
            }
        } else if (command.getName().equalsIgnoreCase("ping")) {
            if (args.length == 1) {
                if (sender.hasPermission("arthoplugin.ping.others") || sender.isOp()) {
                    return null; // Suggest players
                }
            }
        }

        StringUtil.copyPartialMatches(currentArg, commands, completions);
        Collections.sort(completions);
        return completions;
    }
}
