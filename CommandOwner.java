package com.nexuscraft.nexuspath;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;

import java.util.Locale;

/** Which plugin a typed command belongs to. */
final class CommandOwner {

    private CommandOwner() {
    }

    /** The first word of what was typed, without the slash and in lower case. */
    static String label(String typed) {
        if (typed == null) {
            return "";
        }
        String s = typed.trim();
        if (s.startsWith("/")) {
            s = s.substring(1);
        }
        int space = s.indexOf(' ');
        return (space < 0 ? s : s.substring(0, space)).toLowerCase(Locale.ROOT);
    }

    /** The name of the plugin that registered this command label (aliases and "plugin:command" included), or null. */
    static String pluginOf(String label) {
        CommandMap map = Bukkit.getCommandMap();
        if (map == null || label == null || label.isEmpty()) {
            return null;
        }
        Command c = map.getCommand(label);
        if (c instanceof PluginCommand pc && pc.getPlugin() != null) {
            return pc.getPlugin().getName();
        }
        return null;
    }
}
