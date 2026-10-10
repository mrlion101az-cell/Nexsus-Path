package com.nexuscraft.nexuspath;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The whole idea in one place: who has picked what, which systems that hides from each player right
 * now, and the quick questions the gates ask a hundred times a second. Everything that touches the
 * server goes through the small suppliers handed in, so it can all be tested without one.
 */
final class PathService {

    /** The answer to "can I borrow this system for a while?" */
    record TryResult(boolean ok, String why) {
    }

    private final PathConfig cfg;
    private final Catalog catalog;
    private final PathStore store;
    private final Supplier<Collection<String>> installed;
    private final Supplier<Collection<? extends Player>> online;
    private final LongSupplier clock;
    private final Exporter exporter;

    private final Map<UUID, Set<String>> hidden = new ConcurrentHashMap<>();
    private final Map<String, Integer> hiders = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> tries = new HashMap<>();
    private volatile boolean anyHidden;

    PathService(PathConfig cfg, Catalog catalog, PathStore store, Supplier<Collection<String>> installed,
                Supplier<Collection<? extends Player>> online, LongSupplier clock, Exporter exporter) {
        this.cfg = cfg;
        this.catalog = catalog;
        this.store = store;
        this.installed = installed;
        this.online = online;
        this.clock = clock;
        this.exporter = exporter;
    }

    Catalog catalog() {
        return catalog;
    }

    PathConfig cfg() {
        return cfg;
    }

    PathStore store() {
        return store;
    }

    // ------------------------------------------------------------------ the quick questions

    /** Has this player hidden this plugin from their game right now? */
    boolean isHidden(UUID player, String plugin) {
        if (!anyHidden || plugin == null) {
            return false;
        }
        Set<String> set = hidden.get(player);
        return set != null && set.contains(Catalog.key(plugin));
    }

    /** Is any player at all hiding this plugin? (Lets a gate skip the work for everyone else.) */
    boolean anyoneHides(String plugin) {
        return anyHidden && hiders.containsKey(Catalog.key(plugin));
    }

    Set<String> hiddenOf(UUID player) {
        Set<String> set = hidden.get(player);
        return set == null ? Set.of() : Set.copyOf(set);
    }

    /** The plugins that run the choosing itself: never hidden, never listed. */
    static boolean infrastructure(String key) {
        return "nexuspath".equals(key) || "nexustutorial".equals(key);
    }

    static boolean bypasses(Player p) {
        return p.hasPermission("nexuspath.bypass");
    }

    // ------------------------------------------------------------------ working it out

    private Set<String> installedKeys() {
        Set<String> keys = new HashSet<>();
        for (String name : installed.get()) {
            keys.add(Catalog.key(name));
        }
        return keys;
    }

    /** Which plugins this player has hidden: everything not in their game, that is installed, minus borrowed ones. */
    Set<String> compute(UUID id, boolean bypass) {
        Choice c = store.peek(id);
        if (bypass || c == null || !c.chosen || c.all) {
            return Set.of();
        }
        long now = clock.getAsLong();
        Map<String, Long> borrowed = tries.get(id);
        Set<String> out = new LinkedHashSet<>();
        Set<String> have = installedKeys();
        List<SystemInfo> relevant = new ArrayList<>(catalog.systems());
        for (String k : have) {
            if (catalog.system(k) == null && k.startsWith("nexus") && !infrastructure(k)) {
                relevant.add(catalog.systemFor(k));
            }
        }
        for (SystemInfo s : relevant) {
            String k = Catalog.key(s.id());
            if (!have.contains(k) || s.locked() || c.visible(catalog, cfg.unassignedVisible, s)) {
                continue;
            }
            Long until = borrowed == null ? null : borrowed.get(k);
            if (until != null && until > now) {
                continue;
            }
            out.add(k);
        }
        return out;
    }

    /** Works out and publishes what one player has hidden. Call after anything that could change it. */
    void refresh(Player p) {
        UUID id = p.getUniqueId();
        Set<String> now = compute(id, bypasses(p));
        Set<String> before = hidden.put(id, now);
        if (before != null) {
            for (String k : before) {
                hiders.computeIfPresent(k, (key, n) -> n <= 1 ? null : n - 1);
            }
        }
        for (String k : now) {
            hiders.merge(k, 1, Integer::sum);
        }
        anyHidden = !hiders.isEmpty();
        if (before == null || !before.equals(now)) {
            exporter.publish(p, now);
        }
    }

    void refreshAll() {
        for (Player p : online.get()) {
            refresh(p);
        }
    }

    void quit(Player p) {
        UUID id = p.getUniqueId();
        Set<String> before = hidden.remove(id);
        if (before != null) {
            for (String k : before) {
                hiders.computeIfPresent(k, (key, n) -> n <= 1 ? null : n - 1);
            }
        }
        anyHidden = !hiders.isEmpty();
        tries.remove(id);
        exporter.forget(p);
    }

    // ------------------------------------------------------------------ choosing

    /** The player's current choice as a draft they can change without affecting anything until they save. */
    Choice draftOf(UUID id) {
        Choice c = store.peek(id);
        return c == null ? new Choice() : c.copy();
    }

    /** Saves a draft as the player's choice and applies it at once. */
    void commit(Player p, Choice draft) {
        draft.chosen = true;
        draft.tidy(catalog, cfg.unassignedVisible);
        store.put(p.getUniqueId(), draft);
        tries.remove(p.getUniqueId());
        refresh(p);
        say(p, draft.all ? cfg.msgEverything : fill(cfg.msgSaved, null, summaryFor(p, draft)));
    }

    /** Back to the start: sees everything, and will be invited again. */
    void reset(UUID id, Player online) {
        Choice c = store.of(id);
        int reminders = c.reminders;
        Choice fresh = new Choice();
        fresh.reminders = reminders;
        store.put(id, fresh);
        tries.remove(id);
        if (online != null) {
            refresh(online);
        }
    }

    /** A player with something hidden may borrow it for a few minutes. */
    TryResult tryIt(Player p, String systemId) {
        SystemInfo s = catalog.system(systemId);
        Set<String> have = installedKeys();
        if (s == null && have.contains(Catalog.key(systemId))) {
            s = catalog.systemFor(systemId);
        }
        if (s == null || !have.contains(Catalog.key(s.id()))) {
            return new TryResult(false, "no such system on this server");
        }
        if (s.locked()) {
            return new TryResult(false, s.name() + " is always on for everybody");
        }
        if (!isHidden(p.getUniqueId(), s.id())) {
            return new TryResult(false, s.name() + " is already part of your game");
        }
        tries.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>()).put(Catalog.key(s.id()), clock.getAsLong() + cfg.tryMinutes * 60_000L);
        refresh(p);
        say(p, fill(cfg.msgTryStart, s, null));
        return new TryResult(true, "");
    }

    /** Once a second: borrowed systems whose time is up go back out of the game. */
    void tick() {
        long now = clock.getAsLong();
        Iterator<Map.Entry<UUID, Map<String, Long>>> it = tries.entrySet().iterator();
        List<Player> changed = new ArrayList<>();
        List<String> ended = new ArrayList<>();
        while (it.hasNext()) {
            Map.Entry<UUID, Map<String, Long>> e = it.next();
            Player p = null;
            for (Player each : online.get()) {
                if (each.getUniqueId().equals(e.getKey())) {
                    p = each;
                }
            }
            Iterator<Map.Entry<String, Long>> inner = e.getValue().entrySet().iterator();
            while (inner.hasNext()) {
                Map.Entry<String, Long> t = inner.next();
                if (t.getValue() <= now) {
                    inner.remove();
                    if (p != null) {
                        changed.add(p);
                        ended.add(t.getKey());
                    }
                }
            }
            if (e.getValue().isEmpty()) {
                it.remove();
            }
        }
        for (int i = 0; i < changed.size(); i++) {
            refresh(changed.get(i));
            say(changed.get(i), fill(cfg.msgTryEnd, catalog.systemFor(ended.get(i)), null));
        }
    }

    /** The systems this player is borrowing right now (lower-case id to minutes left), soonest to run out first. */
    java.util.LinkedHashMap<String, Integer> borrowedOf(UUID id) {
        java.util.LinkedHashMap<String, Integer> out = new java.util.LinkedHashMap<>();
        Map<String, Long> m = tries.get(id);
        if (m == null) {
            return out;
        }
        List<Map.Entry<String, Long>> entries = new ArrayList<>(m.entrySet());
        entries.sort(Map.Entry.comparingByValue());
        for (Map.Entry<String, Long> e : entries) {
            int left = borrowedMinutes(id, e.getKey());
            if (left > 0) {
                out.put(e.getKey(), left);
            }
        }
        return out;
    }

    /** Minutes left on a borrowed system, or 0. */
    int borrowedMinutes(UUID id, String system) {
        Map<String, Long> m = tries.get(id);
        Long until = m == null ? null : m.get(Catalog.key(system));
        if (until == null) {
            return 0;
        }
        long left = until - clock.getAsLong();
        return left <= 0 ? 0 : (int) ((left + 59_999L) / 60_000L);
    }

    // ------------------------------------------------------------------ inviting

    boolean shouldInvite(Player p) {
        if ("off".equals(cfg.inviteMode) || bypasses(p)) {
            return false;
        }
        Choice c = store.peek(p.getUniqueId());
        return (c == null || !c.chosen) && (c == null ? 0 : c.reminders) < cfg.maxReminders;
    }

    /** May the picker be pushed open for this player (they have not chosen, and are not staff on bypass)? */
    boolean canOffer(Player p) {
        if (bypasses(p)) {
            return false;
        }
        Choice c = store.peek(p.getUniqueId());
        return c == null || !c.chosen;
    }

    void invite(Player p) {
        Choice c = store.of(p.getUniqueId());
        c.reminders++;
        store.markDirty();
        say(p, cfg.msgInvite);
    }

    // ------------------------------------------------------------------ words

    void say(Player p, String text) {
        p.sendMessage(ColorText.translate(cfg.prefix + text));
    }

    /** Counts for the "saved" message: styles picked, systems on, systems hidden. */
    Map<String, String> summaryFor(Player p, Choice c) {
        List<String> titles = new ArrayList<>();
        for (String s : c.styles) {
            StyleInfo st = catalog.style(s);
            titles.add(st == null ? s : st.title());
        }
        int on = 0;
        int off = 0;
        for (SystemInfo s : pickable()) {
            if (c.visible(catalog, cfg.unassignedVisible, s)) {
                on++;
            } else {
                off++;
            }
        }
        Map<String, String> m = new HashMap<>();
        m.put("styles", titles.isEmpty() ? "your own mix" : String.join(" + ", titles));
        m.put("shown", String.valueOf(on));
        m.put("hidden", String.valueOf(off));
        return m;
    }

    String fill(String template, SystemInfo system, Map<String, String> extra) {
        String s = template.replace("{minutes}", String.valueOf(cfg.tryMinutes));
        if (system != null) {
            s = s.replace("{system}", system.name()).replace("{id}", system.id().toLowerCase(Locale.ROOT));
        }
        if (extra != null) {
            for (Map.Entry<String, String> e : extra.entrySet()) {
                s = s.replace("{" + e.getKey() + "}", e.getValue());
            }
        }
        return s;
    }

    /** Which installed systems (not locked) the picker lists, in catalog order, then unlisted Nexus plugins. */
    List<SystemInfo> pickable() {
        Set<String> have = installedKeys();
        List<SystemInfo> out = new ArrayList<>();
        for (SystemInfo s : catalog.systems()) {
            if (!s.locked() && have.contains(Catalog.key(s.id()))) {
                out.add(s);
            }
        }
        for (String k : have) {
            if (catalog.system(k) == null && k.startsWith("nexus") && !infrastructure(k)) {
                out.add(catalog.systemFor(originalName(k)));
            }
        }
        return out;
    }

    private String originalName(String key) {
        for (String name : installed.get()) {
            if (Catalog.key(name).equals(key)) {
                return name;
            }
        }
        return key;
    }

    /** The always-on systems that are installed, for the "always on" note. */
    List<SystemInfo> alwaysOn() {
        Set<String> have = installedKeys();
        List<SystemInfo> out = new ArrayList<>();
        for (SystemInfo s : catalog.systems()) {
            if (s.locked() && have.contains(Catalog.key(s.id()))) {
                out.add(s);
            }
        }
        return out;
    }
}
