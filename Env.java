package com.nexuscraft.nexuspath;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** A NexusPath wired to a stub server: a small catalogue, installed plugins, a clock and a recording exporter. */
final class Env {

    final Kit.TestPlugin plugin = new Kit.TestPlugin();
    final PathConfig cfg = new PathConfig();
    final Catalog catalog = new Catalog();
    final PathStore store = new PathStore(null, w -> { });
    final Set<String> installed = new LinkedHashSet<>();
    final AtomicLong clock = new AtomicLong(1_000_000L);
    final List<String> exports = new ArrayList<>();
    final PathService service;
    final BlockOwners owners = new BlockOwners(clock::get);

    Env() {
        Kit.reset();
        catalog.load(sample(), "test");
        catalog.finish();
        installed.addAll(List.of("NexusRime", "NexusFarming", "NexusAngler", "NexusClaims", "NexusRedstone", "NexusTrials",
                "NexusMenu", "NexusUnlisted", "NexusPath", "NexusTutorial", "Vault"));
        service = new PathService(cfg, catalog, store, () -> installed, Env::everyone, clock::get, new Exporter() {
            @Override
            public void publish(Player player, Set<String> hidden) {
                exports.add(player.getName() + ":" + String.join(",", new java.util.TreeSet<>(hidden)));
            }

            @Override
            public void forget(Player player) {
                exports.add(player.getName() + ":forgotten");
            }
        });
    }

    static Collection<? extends Player> everyone() {
        return Bukkit.getOnlinePlayers();
    }

    static Kit.MapSection sample() {
        Kit.MapSection m = new Kit.MapSection();
        system(m, "NexusRime", "Rime", "BLUE_ICE", "Frozen hordes", false, false, true, false);
        system(m, "NexusFarming", "Farming", "WHEAT", "Crops", false, false, true, false);
        system(m, "NexusAngler", "Angler", "FISHING_ROD", "Fishing", false, false, true, false);
        system(m, "NexusClaims", "Claims", "SHIELD", "Land claims", true, false, true, false);
        system(m, "NexusRedstone", "Redstone", "REDSTONE", "Machines", false, true, true, false);
        system(m, "NexusTrials", "Trials", "TRIAL_KEY", "Trials", false, false, false, false);
        system(m, "NexusMenu", "Menu", "COMPASS", "The menu", false, false, true, true);
        m.put("styles.survivor.title", "Survivor").put("styles.survivor.icon", "IRON_SWORD").put("styles.survivor.blurb", "Fight")
                .put("styles.survivor.systems", List.of("NexusRime", "NexusTrials"));
        m.put("styles.farmer.title", "Farmer").put("styles.farmer.icon", "WHEAT").put("styles.farmer.blurb", "Grow")
                .put("styles.farmer.systems", List.of("NexusFarming", "NexusAngler"));
        m.put("styles.builder.title", "Builder").put("styles.builder.icon", "BRICKS").put("styles.builder.blurb", "Build")
                .put("styles.builder.systems", List.of("NexusRedstone", "NexusFarming"));
        return m;
    }

    static void system(Kit.MapSection m, String id, String name, String icon, String blurb, boolean locked, boolean world, boolean events, boolean def) {
        String b = "systems." + id + ".";
        m.put(b + "name", name).put(b + "icon", icon).put(b + "blurb", blurb).put(b + "locked", locked)
                .put(b + "world", world).put(b + "events", events).put(b + "default", def);
    }

    Kit.TestPlayer player(String name) {
        return Kit.online(new Kit.TestPlayer(name, 0, 0));
    }

    /** Picks the given styles for a player and applies it. */
    Choice choose(Player p, String... styles) {
        Choice c = service.draftOf(p.getUniqueId());
        c.all = false;
        c.styles.clear();
        c.shown.clear();
        c.hidden.clear();
        for (String s : styles) {
            c.styles.add(s);
        }
        service.commit(p, c);
        return store.peek(p.getUniqueId());
    }

    /** Registers a command with aliases the way a plugin.yml would, owned by a plugin. */
    static void command(Plugin owner, String name, String... aliases) {
        Command c = new PluginCommand(name, owner);
        Bukkit.COMMANDS.put(name, c);
        for (String a : aliases) {
            Bukkit.COMMANDS.put(a, c);
        }
    }

    static Set<String> set(String... s) {
        return new HashSet<>(List.of(s));
    }
}
