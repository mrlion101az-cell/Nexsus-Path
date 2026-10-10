package com.nexuscraft.nexuspath;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Every system and every play style. Loaded from the catalog inside the jar, then the server's own
 * custom-catalog.yml on top: a system or style with an id that already exists is changed field by
 * field; a new id adds one; {@code hidden-systems} and {@code hidden-styles} take them away.
 */
final class Catalog {

    private final Map<String, SystemInfo> systems = new LinkedHashMap<>();
    private final Map<String, StyleInfo> styles = new LinkedHashMap<>();
    private final Set<String> assigned = new HashSet<>();
    private final List<String> warnings = new ArrayList<>();

    /** Ids compare without case. */
    static String key(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    void clear() {
        systems.clear();
        styles.clear();
        assigned.clear();
        warnings.clear();
    }

    Collection<SystemInfo> systems() {
        return systems.values();
    }

    Collection<StyleInfo> styles() {
        return styles.values();
    }

    List<String> warnings() {
        return warnings;
    }

    SystemInfo system(String id) {
        return systems.get(key(id));
    }

    StyleInfo style(String id) {
        return styles.get(key(id));
    }

    /** The system for a plugin, or a plain unlisted one for a plugin the catalog has never heard of. */
    SystemInfo systemFor(String plugin) {
        SystemInfo s = system(plugin);
        return s != null ? s : new SystemInfo(plugin, plugin, "PAPER", "", false, false, true, false);
    }

    /** Is this system named by any style at all? */
    boolean isAssigned(String id) {
        return assigned.contains(key(id));
    }

    /** The styles that include a system. */
    List<StyleInfo> stylesOf(String id) {
        List<StyleInfo> out = new ArrayList<>();
        String k = key(id);
        for (StyleInfo st : styles.values()) {
            for (String s : st.systems()) {
                if (key(s).equals(k)) {
                    out.add(st);
                    break;
                }
            }
        }
        return out;
    }

    void load(ConfigurationSection root, String source) {
        for (String id : root.getStringList("hidden-systems")) {
            systems.remove(key(id));
        }
        for (String id : root.getStringList("hidden-styles")) {
            styles.remove(key(id));
        }
        ConfigurationSection sys = root.getConfigurationSection("systems");
        if (sys != null) {
            for (String id : sys.getKeys(false)) {
                ConfigurationSection s = sys.getConfigurationSection(id);
                if (s == null) {
                    warnings.add(source + ": system '" + id + "' is not a section -- skipped.");
                    continue;
                }
                SystemInfo old = systems.get(key(id));
                String name = s.getString("name", old == null ? id : old.name());
                systems.put(key(id), new SystemInfo(old == null ? id : old.id(), name,
                        s.getString("icon", old == null ? "PAPER" : old.icon()),
                        s.getString("blurb", old == null ? "" : old.blurb()),
                        s.getBoolean("locked", old != null && old.locked()),
                        s.getBoolean("world", old != null && old.world()),
                        s.getBoolean("events", old == null || old.events()),
                        s.getBoolean("default", old != null && old.byDefault())));
            }
        }
        ConfigurationSection sty = root.getConfigurationSection("styles");
        if (sty != null) {
            for (String id : sty.getKeys(false)) {
                ConfigurationSection s = sty.getConfigurationSection(id);
                if (s == null) {
                    warnings.add(source + ": style '" + id + "' is not a section -- skipped.");
                    continue;
                }
                StyleInfo old = styles.get(key(id));
                List<String> list = new ArrayList<>(s.contains("systems") ? s.getStringList("systems") : old == null ? List.of() : old.systems());
                for (String add : s.getStringList("add-systems")) {
                    if (!containsKey(list, add)) {
                        list.add(add);
                    }
                }
                for (String drop : s.getStringList("remove-systems")) {
                    list.removeIf(x -> key(x).equals(key(drop)));
                }
                styles.put(key(id), new StyleInfo(old == null ? id : old.id(), s.getString("title", old == null ? id : old.title()),
                        s.getString("icon", old == null ? "BOOK" : old.icon()), s.getString("blurb", old == null ? "" : old.blurb()),
                        List.copyOf(list)));
            }
        }
    }

    /** Call after every file is loaded: works out which systems any style names, and what is wrong. */
    void finish() {
        assigned.clear();
        for (StyleInfo st : styles.values()) {
            for (String s : st.systems()) {
                assigned.add(key(s));
                SystemInfo known = system(s);
                if (known == null) {
                    warnings.add("style '" + st.id() + "' names '" + s + "', which is not in the catalog (it still works if that plugin is installed).");
                } else if (known.locked()) {
                    warnings.add("style '" + st.id() + "' names '" + s + "', which is locked (always on), so naming it does nothing.");
                }
            }
        }
    }

    private static boolean containsKey(List<String> list, String id) {
        for (String s : list) {
            if (key(s).equals(key(id))) {
                return true;
            }
        }
        return false;
    }
}
