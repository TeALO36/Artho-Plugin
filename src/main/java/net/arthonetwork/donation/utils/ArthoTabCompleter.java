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

        if (command.getName().equalsIgnoreCase("don")) {
            if (args.length == 1) {
                if (sender.hasPermission("arthodonation.admin") || sender.isOp()) {
                    commands.addAll(Arrays.asList("add", "link", "reset", "reload", "help"));
                } else {
                    commands.add("help");
                }
            }
        } else if (command.getName().equalsIgnoreCase("lag")) {
            if (args.length == 1) {
                commands.addAll(Arrays.asList("joueur", "serveur", "help"));
            }
        } else if (command.getName().equalsIgnoreCase("server")) {
            if (args.length == 1) {
                commands.addAll(Arrays.asList("add", "list", "help"));
                if (sender.hasPermission("arthodonation.admin") || sender.isOp()) {
                    commands.add("remove");
                }
            }
        }

        StringUtil.copyPartialMatches(args[0], commands, completions);
        Collections.sort(completions);
        return completions;
    }
}
