package com.nexuscraft.nexuspath;

import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * Hides a plugin's behaviour from the players who switched it off, from outside the plugin.
 *
 * <p>Every plugin's event handlers sit in the server's handler lists. For each plugin that players
 * can hide, its handlers are swapped for a wrapper that does exactly what the original did, except
 * that it does nothing when the event is about a player who hid that plugin. The plugin itself is
 * never touched and never knows. Events that are about no player (a chunk loading, a block that
 * nobody has touched) pass straight through, and so do the events a plugin needs to keep its own
 * books straight (a player joining, leaving, respawning, closing an inventory).
 */
final class ListenerGate {

    /** A wrapped handler: knows what it wraps, so it can be put back. */
    static final class Gated extends RegisteredListener {

        private static final EventExecutor NOTHING = (listener, event) -> { };

        private final RegisteredListener inner;
        private final ListenerGate gate;

        Gated(RegisteredListener inner, ListenerGate gate) {
            super(inner.getListener(), NOTHING, inner.getPriority(), inner.getPlugin(), inner.isIgnoringCancelled());
            this.inner = inner;
            this.gate = gate;
        }

        RegisteredListener inner() {
            return inner;
        }

        @Override
        public void callEvent(Event event) throws EventException {
            if (gate.skips(event, getPlugin().getName())) {
                gate.skipped++;
                return;
            }
            inner.callEvent(event);
        }
    }

    private final PathService service;
    private final BlockOwners owners;
    private final Predicate<String> gated;
    private boolean enabled = true;
    long skipped;
    int wrapped;

    /**
     * @param gated says whether a plugin's handlers should be wrapped at all (it is installed, not locked, not opted out)
     */
    ListenerGate(PathService service, BlockOwners owners, Predicate<String> gated) {
        this.service = service;
        this.owners = owners;
        this.gated = gated;
    }

    boolean enabled() {
        return enabled;
    }

    void setEnabled(boolean on) {
        this.enabled = on;
    }

    /** Events a plugin must always see, so its own records of who is where stay true. */
    static boolean alwaysDelivered(Event e) {
        return e instanceof PlayerJoinEvent || e instanceof PlayerQuitEvent || e instanceof PlayerRespawnEvent
                || e instanceof InventoryCloseEvent;
    }

    /** Should this plugin's handler be skipped for this event? */
    boolean skips(Event event, String plugin) {
        if (!enabled || !service.anyoneHides(plugin) || alwaysDelivered(event)) {
            return false;
        }
        for (UUID id : EventPlayers.of(event, owners)) {
            if (service.isHidden(id, plugin)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ installing

    /** Wraps the handlers of every plugin that should be gated. Returns how many were wrapped. */
    int installAll() {
        int n = 0;
        for (HandlerList list : HandlerList.getHandlerLists()) {
            n += wrapIn(list, null);
        }
        wrapped += n;
        return n;
    }

    /** Wraps the handlers of one plugin that has just been enabled. */
    int install(Plugin plugin) {
        int n = 0;
        for (HandlerList list : HandlerList.getHandlerLists()) {
            n += wrapIn(list, plugin);
        }
        wrapped += n;
        return n;
    }

    private int wrapIn(HandlerList list, Plugin only) {
        return rebuild(list, rl -> {
            Plugin owner = rl.getPlugin();
            if (rl instanceof Gated || owner == null || (only != null && owner != only) || !gated.test(owner.getName())) {
                return rl;
            }
            return new Gated(rl, this);
        });
    }

    /**
     * Swaps handlers in one list without changing who runs before whom: the whole list is taken out and
     * put back in its old order with the swaps made. (Registering just the wrapper would put it after
     * every other plugin's handler of the same priority.) Returns how many were swapped.
     */
    private static int rebuild(HandlerList list, java.util.function.UnaryOperator<RegisteredListener> swap) {
        RegisteredListener[] before = list.getRegisteredListeners();
        RegisteredListener[] after = new RegisteredListener[before.length];
        int changed = 0;
        for (int i = 0; i < before.length; i++) {
            after[i] = swap.apply(before[i]);
            if (after[i] != before[i]) {
                changed++;
            }
        }
        if (changed == 0) {
            return 0;
        }
        for (RegisteredListener rl : before) {
            list.unregister(rl);
        }
        for (RegisteredListener rl : after) {
            list.register(rl);
        }
        return changed;
    }

    /** Puts every original handler back (when NexusPath is switched off). */
    int uninstall() {
        int n = 0;
        for (HandlerList list : HandlerList.getHandlerLists()) {
            n += rebuild(list, rl -> rl instanceof Gated g ? g.inner() : rl);
        }
        wrapped = 0;
        return n;
    }
}
