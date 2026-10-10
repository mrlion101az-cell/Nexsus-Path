package com.nexuscraft.nexuspath;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Every player's choices, in memory and in players.yml. */
final class PathStore {

    private final File file;
    private final Consumer<String> warn;
    private final Map<UUID, Choice> all = new HashMap<>();
    private boolean dirty;

    PathStore(File file, Consumer<String> warn) {
        this.file = file;
        this.warn = warn;
    }

    void load() {
        all.clear();
        dirty = false;
        if (file == null || !file.exists()) {
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yml.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String key : players.getKeys(false)) {
            ConfigurationSection s = players.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            try {
                Choice c = new Choice();
                c.chosen = s.getBoolean("chosen", false);
                c.all = s.getBoolean("all", false);
                c.reminders = s.getInt("reminders", 0);
                for (String x : s.getStringList("styles")) {
                    c.styles.add(Catalog.key(x));
                }
                for (String x : s.getStringList("shown")) {
                    c.shown.add(Catalog.key(x));
                }
                for (String x : s.getStringList("hidden")) {
                    c.hidden.add(Catalog.key(x));
                }
                all.put(UUID.fromString(key), c);
            } catch (IllegalArgumentException bad) {
                warn.accept("players.yml: '" + key + "' is not a player id -- skipped.");
            }
        }
    }

    /** The player's record, or null if they have never made a choice. */
    Choice peek(UUID id) {
        return all.get(id);
    }

    Choice of(UUID id) {
        return all.computeIfAbsent(id, k -> new Choice());
    }

    void put(UUID id, Choice c) {
        all.put(id, c);
        dirty = true;
    }

    void forget(UUID id) {
        if (all.remove(id) != null) {
            dirty = true;
        }
    }

    void markDirty() {
        dirty = true;
    }

    boolean dirty() {
        return dirty;
    }

    int size() {
        return all.size();
    }

    void flush() {
        if (!dirty || file == null) {
            return;
        }
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<UUID, Choice> e : all.entrySet()) {
            Choice c = e.getValue();
            if (c.isBlank()) {
                continue;
            }
            String base = "players." + e.getKey() + ".";
            yml.set(base + "chosen", c.chosen);
            yml.set(base + "all", c.all);
            yml.set(base + "reminders", c.reminders);
            yml.set(base + "styles", List.copyOf(c.styles));
            yml.set(base + "shown", List.copyOf(c.shown));
            yml.set(base + "hidden", List.copyOf(c.hidden));
        }
        try {
            File dir = file.getParentFile();
            if (dir != null) {
                dir.mkdirs();
            }
            yml.save(file);
            dirty = false;
        } catch (IOException ex) {
            warn.accept("Could not save players.yml: " + ex.getMessage());
        }
    }
}
