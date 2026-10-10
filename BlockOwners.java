package com.nexuscraft.nexuspath;

import org.bukkit.Location;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Remembers who last placed, opened or used a block, for a while, so the events that block makes on
 * its own (a furnace finishing, a brewing stand, a hopper, a lever's circuit) can be attributed to a
 * player. Bounded: the oldest entries go first.
 */
final class BlockOwners {

    private static final int LIMIT = 4096;
    private static final long KEEP_MILLIS = 30L * 60L * 1000L;

    private record Entry(UUID player, long at) {
    }

    private final Map<String, Entry> owners = new LinkedHashMap<>(256, 0.75f, true) {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
            return size() > LIMIT;
        }
    };
    private final LongSupplier clock;

    BlockOwners(LongSupplier clock) {
        this.clock = clock;
    }

    static String key(Location at) {
        if (at == null || at.getWorld() == null) {
            return null;
        }
        return at.getWorld().getName() + ":" + at.getBlockX() + ":" + at.getBlockY() + ":" + at.getBlockZ();
    }

    synchronized void note(Location at, UUID player) {
        String k = key(at);
        if (k != null && player != null) {
            owners.put(k, new Entry(player, clock.getAsLong()));
        }
    }

    synchronized UUID ownerOf(Location at) {
        String k = key(at);
        Entry e = k == null ? null : owners.get(k);
        if (e == null) {
            return null;
        }
        if (clock.getAsLong() - e.at() > KEEP_MILLIS) {
            owners.remove(k);
            return null;
        }
        return e.player();
    }

    synchronized int size() {
        return owners.size();
    }
}
