package com.nexuscraft.nexuspath;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.Cancellable;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;

import java.util.ArrayList;
import java.util.List;

import static com.nexuscraft.nexuspath.Kit.expect;

/** Wraps real handlers in a real handler list and fires events through them, as the server would. */
public final class GateTest {

    /** One shared handler list per event kind, standing in for each event class's own static list. */
    static final class Lists {
        final HandlerList move = new HandlerList();
        final HandlerList damage = new HandlerList();
        final HandlerList join = new HandlerList();
        final HandlerList quit = new HandlerList();
        final HandlerList respawn = new HandlerList();
        final HandlerList close = new HandlerList();
        final HandlerList click = new HandlerList();
        final HandlerList place = new HandlerList();
        final HandlerList dispense = new HandlerList();
        final HandlerList redstone = new HandlerList();
        final HandlerList target = new HandlerList();
        final HandlerList vehicle = new HandlerList();
        final HandlerList load = new HandlerList();
        final HandlerList command = new HandlerList();
    }

    /** What each fake plugin saw. */
    static final List<String> seen = new ArrayList<>();

    static RegisteredListener handler(HandlerList list, Plugin plugin, String tag, EventPriority prio, boolean ignoreCancelled) {
        RegisteredListener rl = new RegisteredListener(new Listener() { }, (l, ev) -> seen.add(tag), prio, plugin, ignoreCancelled);
        list.register(rl);
        return rl;
    }

    static RegisteredListener handler(HandlerList list, Plugin plugin, String tag) {
        return handler(list, plugin, tag, EventPriority.NORMAL, false);
    }

    static ListenerGate gateFor(Env e) {
        return new ListenerGate(e.service, e.owners, name -> {
            String k = Catalog.key(name);
            if (PathService.infrastructure(k)) {
                return false;
            }
            SystemInfo s = e.catalog.system(name);
            return s == null ? k.startsWith("nexus") : !s.locked() && s.events();
        });
    }

    static void fire(HandlerList list, Event ev) {
        list.call(ev);
    }

    public static void main(String[] args) {
        Env e = new Env();
        Lists L = new Lists();
        Kit.NexusRime rime = new Kit.NexusRime();
        Kit.NexusFarming farming = new Kit.NexusFarming();
        Kit.NexusClaims claims = new Kit.NexusClaims();
        Kit.NexusTrials trials = new Kit.NexusTrials();
        Kit.NexusUnlisted unlisted = new Kit.NexusUnlisted();
        Kit.NexusPath path = new Kit.NexusPath();
        Kit.Vault vault = new Kit.Vault();

        // everybody listens to movement and damage
        handler(L.move, rime, "rime:move");
        handler(L.move, farming, "farming:move");
        handler(L.move, claims, "claims:move");
        handler(L.move, trials, "trials:move");
        handler(L.move, unlisted, "unlisted:move");
        handler(L.move, path, "path:move");
        handler(L.move, vault, "vault:move");
        handler(L.damage, farming, "farming:damage");
        handler(L.damage, rime, "rime:damage");
        handler(L.join, farming, "farming:join");
        handler(L.quit, farming, "farming:quit");
        handler(L.respawn, farming, "farming:respawn");
        handler(L.close, farming, "farming:close");

        ListenerGate gate = gateFor(e);
        Kit.TestPlayer ann = e.player("Ann");
        Kit.TestPlayer bob = e.player("Bob");

        // before anything is installed nothing changes
        seen.clear();
        fire(L.move, new PlayerMoveEvent(ann, new Location(Kit.WORLD, 0, 0, 0), new Location(Kit.WORLD, 1, 0, 0)));
        expect(seen.size() == 7, "before install every handler runs: " + seen);

        int n = gate.installAll();
        expect(n == 9, "wrapped: move(rime, farming, unlisted) + damage(2) + four lifecycle = 9; got " + n);
        expect(gate.wrapped == n, "wrapped count tracked");
        expect(L.move.getRegisteredListeners().length == 7, "no handler lost or added");
        int wrappedMoves = 0;
        for (RegisteredListener rl : L.move.getRegisteredListeners()) {
            if (rl instanceof ListenerGate.Gated) {
                wrappedMoves++;
            }
        }
        expect(wrappedMoves == 3, "move: rime, farming and unlisted are wrapped; claims (locked), trials (events off), path and vault are not: " + wrappedMoves);
        expect(gate.installAll() == 0, "installing twice does not wrap twice");

        // nobody has hidden anything: everything runs, for every event
        seen.clear();
        PlayerMoveEvent mv = new PlayerMoveEvent(ann, new Location(Kit.WORLD, 0, 0, 0), new Location(Kit.WORLD, 1, 0, 0));
        fire(L.move, mv);
        expect(seen.size() == 7, "nothing hidden: all seven run: " + seen);
        expect(gate.skipped == 0, "nothing skipped");

        // Ann picks Survivor: farming hidden, rime and trials visible, unlisted visible
        e.choose(ann, "survivor");
        seen.clear();
        fire(L.move, mv);
        expect(!seen.contains("farming:move"), "farming skipped for Ann");
        expect(seen.contains("rime:move") && seen.contains("trials:move") && seen.contains("unlisted:move"), "her own systems run: " + seen);
        expect(seen.contains("claims:move") && seen.contains("path:move") && seen.contains("vault:move"), "locked, infrastructure and other plugins always run");
        expect(seen.size() == 6 && gate.skipped == 1, "exactly one skipped: " + seen + " skipped=" + gate.skipped);

        // the same event about Bob (who chose nothing) runs everything
        seen.clear();
        fire(L.move, new PlayerMoveEvent(bob, new Location(Kit.WORLD, 0, 0, 0), new Location(Kit.WORLD, 1, 0, 0)));
        expect(seen.size() == 7, "Bob sees all: " + seen);

        // order is kept: handlers run in priority then registration order
        seen.clear();
        fire(L.move, mv);
        expect(seen.equals(List.of("rime:move", "claims:move", "trials:move", "unlisted:move", "path:move", "vault:move")), "order kept: " + seen);

        // events about two players: skipped if EITHER hid it
        e.choose(bob, "farmer");
        Kit.TestPlayer cy = e.player("Cy");
        seen.clear();
        fire(L.damage, new EntityDamageByEntityEvent(ann, bob, EntityDamageEvent.DamageCause.ENTITY_ATTACK));
        expect(seen.isEmpty(), "ann hid farming, bob hid rime: both skipped when they fight: " + seen);
        seen.clear();
        fire(L.damage, new EntityDamageByEntityEvent(cy, bob, EntityDamageEvent.DamageCause.ENTITY_ATTACK));
        expect(seen.equals(List.of("farming:damage")), "cy fights bob: rime skipped (bob hid it), farming runs: " + seen);
        seen.clear();
        fire(L.damage, new EntityDamageByEntityEvent(cy, cy, EntityDamageEvent.DamageCause.ENTITY_ATTACK));
        expect(seen.size() == 2, "two players who hid nothing: both run");

        // a mob damaging a player: that player's choice decides
        seen.clear();
        Entity zombie = new Entity();
        fire(L.damage, new EntityDamageByEntityEvent(zombie, ann, EntityDamageEvent.DamageCause.ENTITY_ATTACK));
        expect(seen.equals(List.of("rime:damage")), "zombie hits ann: farming skipped: " + seen);
        // a mob hurting a mob: no player
        seen.clear();
        fire(L.damage, new EntityDamageByEntityEvent(zombie, new Entity(), EntityDamageEvent.DamageCause.ENTITY_ATTACK));
        expect(seen.size() == 2, "mob versus mob: nobody's choice applies");
        // a projectile shot by ann
        seen.clear();
        Projectile arrow = new Projectile(ann);
        fire(L.damage, new EntityDamageByEntityEvent(arrow, new Entity(), EntityDamageEvent.DamageCause.PROJECTILE));
        expect(seen.equals(List.of("rime:damage")), "ann's arrow: farming skipped: " + seen);
        // a projectile shot by a mob
        seen.clear();
        fire(L.damage, new EntityDamageByEntityEvent(new Projectile(new org.bukkit.entity.LivingEntity()), new Entity(), EntityDamageEvent.DamageCause.PROJECTILE));
        expect(seen.size() == 2, "a mob's arrow involves no player");

        // lifecycle events always run
        seen.clear();
        fire(L.join, new PlayerJoinEvent(ann, ""));
        fire(L.quit, new PlayerQuitEvent(ann, ""));
        fire(L.respawn, new PlayerRespawnEvent(ann, null));
        fire(L.close, new InventoryCloseEvent());
        expect(seen.equals(List.of("farming:join", "farming:quit", "farming:respawn", "farming:close")), "join, quit, respawn and close always delivered: " + seen);
        expect(ListenerGate.alwaysDelivered(new PlayerJoinEvent(ann, "")) && ListenerGate.alwaysDelivered(new InventoryCloseEvent())
                && !ListenerGate.alwaysDelivered(mv), "alwaysDelivered classifies");

        // events with no player run
        seen.clear();
        HandlerList loads = L.load;
        handler(loads, farming, "farming:load");
        gate.install(farming);
        fire(loads, new EntitiesLoadEvent(List.of()));
        expect(seen.equals(List.of("farming:load")), "an event with no player runs: " + seen);

        // inventory clicks go by the viewer
        HandlerList clicks = L.click;
        handler(clicks, farming, "farming:click");
        gate.install(farming);
        InventoryView view = new InventoryView();
        view.setPlayer(ann);
        seen.clear();
        fire(clicks, new InventoryClickEvent(view, org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER, 0, org.bukkit.event.inventory.ClickType.LEFT, org.bukkit.event.inventory.InventoryAction.PICKUP_ALL));
        expect(seen.isEmpty(), "ann's inventory click is skipped for farming");
        InventoryView view2 = new InventoryView();
        view2.setPlayer(cy);
        fire(clicks, new InventoryClickEvent(view2, org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER, 0, org.bukkit.event.inventory.ClickType.LEFT, org.bukkit.event.inventory.InventoryAction.PICKUP_ALL));
        expect(seen.equals(List.of("farming:click")), "cy's click runs");

        // block place by a player
        HandlerList places = L.place;
        handler(places, farming, "farming:place");
        gate.install(farming);
        Block block = Kit.WORLD.getBlockAt(5, 64, 5);
        seen.clear();
        fire(places, new BlockPlaceEvent(block, ann));
        expect(seen.isEmpty(), "ann's block place skipped");
        fire(places, new BlockPlaceEvent(block, cy));
        expect(seen.equals(List.of("farming:place")), "cy's block place runs");

        // block break by a player
        HandlerList breaks = new HandlerList();
        handler(breaks, farming, "farming:break");
        gate.install(farming);
        seen.clear();
        fire(breaks, new BlockBreakEvent(block, ann));
        expect(seen.isEmpty(), "ann's block break is skipped");
        fire(breaks, new BlockBreakEvent(block, cy));
        expect(seen.equals(List.of("farming:break")), "cy's block break runs");

        // EventPlayers directly
        BlockOwners none = new BlockOwners(e.clock::get);
        expect(EventPlayers.of(new BlockBreakEvent(block, ann), none).equals(List.of(ann.getUniqueId())), "a break names its player");
        expect(EventPlayers.of(new BlockPlaceEvent(block, cy), none).equals(List.of(cy.getUniqueId())), "a place names its player");
        expect(EventPlayers.of(new BlockPlaceEvent(block, null), none).isEmpty(), "a place with no player names nobody (and does not crash)");
        expect(EventPlayers.of(new EntityDamageByEntityEvent(ann, ann, EntityDamageEvent.DamageCause.ENTITY_ATTACK), none).size() == 1, "a player hitting themselves is listed once");
        expect(EventPlayers.of(new EntityDamageByEntityEvent(ann, cy, EntityDamageEvent.DamageCause.ENTITY_ATTACK), none).equals(List.of(cy.getUniqueId(), ann.getUniqueId())), "victim then attacker");
        expect(EventPlayers.of(new PlayerMoveEvent(null, null, null), none).isEmpty(), "a player event with no player names nobody");

        // a block event with no player is attributed to whoever last touched the block
        HandlerList dispenses = L.dispense;
        handler(dispenses, farming, "farming:dispense");
        gate.install(farming);
        Block dispenser = Kit.WORLD.getBlockAt(9, 64, 9);
        seen.clear();
        fire(dispenses, new BlockDispenseEvent(dispenser, null, null));
        expect(seen.equals(List.of("farming:dispense")), "no owner: runs");
        e.owners.note(dispenser.getLocation(), ann.getUniqueId());
        seen.clear();
        fire(dispenses, new BlockDispenseEvent(dispenser, null, null));
        expect(seen.isEmpty(), "block last touched by ann: skipped for farming");
        e.owners.note(dispenser.getLocation(), cy.getUniqueId());
        fire(dispenses, new BlockDispenseEvent(dispenser, null, null));
        expect(seen.equals(List.of("farming:dispense")), "block last touched by cy: runs");
        HandlerList reds = L.redstone;
        handler(reds, farming, "farming:redstone");
        gate.install(farming);
        e.owners.note(Kit.WORLD.getBlockAt(1, 1, 1).getLocation(), ann.getUniqueId());
        seen.clear();
        fire(reds, new BlockRedstoneEvent(Kit.WORLD.getBlockAt(1, 1, 1), 0, 15));
        expect(seen.isEmpty(), "redstone at a block ann touched: skipped");

        // a mob targeting a player
        HandlerList targets = L.target;
        handler(targets, farming, "farming:target");
        gate.install(farming);
        seen.clear();
        fire(targets, new EntityTargetLivingEntityEvent(zombie, ann));
        expect(seen.isEmpty(), "a mob targeting ann: skipped");
        fire(targets, new EntityTargetLivingEntityEvent(zombie, cy));
        expect(seen.equals(List.of("farming:target")), "a mob targeting cy: runs");

        // a vehicle with ann riding
        HandlerList vehicles = L.vehicle;
        handler(vehicles, farming, "farming:vehicle");
        gate.install(farming);
        Vehicle cart = new Vehicle();
        seen.clear();
        fire(vehicles, new VehicleMoveEvent(cart, new Location(Kit.WORLD, 0, 0, 0), new Location(Kit.WORLD, 1, 0, 0)));
        expect(seen.equals(List.of("farming:vehicle")), "an empty cart runs");
        cart.getPassengers().add(ann);
        seen.clear();
        fire(vehicles, new VehicleMoveEvent(cart, new Location(Kit.WORLD, 0, 0, 0), new Location(Kit.WORLD, 1, 0, 0)));
        expect(seen.isEmpty(), "a cart ann rides: skipped");
        cart.getPassengers().clear();
        cart.getPassengers().add(cy);
        fire(vehicles, new VehicleMoveEvent(cart, new Location(Kit.WORLD, 0, 0, 0), new Location(Kit.WORLD, 1, 0, 0)));
        expect(seen.equals(List.of("farming:vehicle")), "a cart cy rides: runs");

        // cancellation: ignoreCancelled and cancelled events are kept honest
        HandlerList cmds = L.command;
        handler(cmds, farming, "farming:strict", EventPriority.NORMAL, true);
        handler(cmds, farming, "farming:lenient", EventPriority.NORMAL, false);
        handler(cmds, rime, "rime:strict", EventPriority.NORMAL, true);
        gate.install(farming);
        gate.install(rime);
        PlayerCommandPreprocessEvent pre = new PlayerCommandPreprocessEvent(cy, "/x");
        pre.setCancelled(true);
        seen.clear();
        fire(cmds, pre);
        expect(seen.equals(List.of("farming:lenient")), "ignoreCancelled is still honoured by wrapped handlers: " + seen);
        for (RegisteredListener rl : cmds.getRegisteredListeners()) {
            if (rl instanceof ListenerGate.Gated g) {
                expect(g.isIgnoringCancelled() == g.inner().isIgnoringCancelled(), "the wrapper copies ignoreCancelled");
                expect(g.getPriority() == g.inner().getPriority() && g.getPlugin() == g.inner().getPlugin() && g.getListener() == g.inner().getListener(), "the wrapper copies priority, plugin, listener");
            }
        }

        // priorities are kept
        HandlerList pr = new HandlerList();
        handler(pr, farming, "late", EventPriority.MONITOR, false);
        handler(pr, rime, "early", EventPriority.LOWEST, false);
        handler(pr, farming, "mid", EventPriority.NORMAL, false);
        gate.installAll();
        seen.clear();
        fire(pr, new PlayerMoveEvent(cy, null, null));
        expect(seen.equals(List.of("early", "mid", "late")), "wrapping keeps priority order: " + seen);

        // the kill switch
        gate.setEnabled(false);
        seen.clear();
        fire(L.move, mv);
        expect(seen.contains("farming:move"), "gate off: nothing is skipped");
        expect(!gate.enabled(), "enabled reflects the switch");
        gate.setEnabled(true);
        seen.clear();
        fire(L.move, mv);
        expect(!seen.contains("farming:move"), "gate back on: skipped again");

        // a handler that throws still throws through the wrapper (the server logs it)
        HandlerList boom = new HandlerList();
        RegisteredListener bad = new RegisteredListener(new Listener() { }, (l, ev) -> { throw new org.bukkit.event.EventException(new RuntimeException("boom")); }, EventPriority.NORMAL, rime, false);
        boom.register(bad);
        gate.installAll();
        boolean thrown = false;
        try {
            for (RegisteredListener rl : boom.getRegisteredListeners()) {
                rl.callEvent(mv);
            }
        } catch (org.bukkit.event.EventException ex) {
            thrown = true;
        }
        expect(thrown, "an exception from the original handler is not swallowed");

        // uninstall puts the originals back
        int back = gate.uninstall();
        expect(back > 0 && gate.wrapped == 0, "uninstall restored handlers");
        for (HandlerList list : HandlerList.getHandlerLists()) {
            for (RegisteredListener rl : list.getRegisteredListeners()) {
                expect(!(rl instanceof ListenerGate.Gated), "no wrapper left");
            }
        }
        seen.clear();
        fire(L.move, mv);
        expect(seen.equals(List.of("rime:move", "farming:move", "claims:move", "trials:move", "unlisted:move", "path:move", "vault:move")),
                "after uninstall the original handlers run again in the original order: " + seen);
        expect(gate.uninstall() == 0, "uninstalling twice is harmless");

        // a plugin that enables late is wrapped on its own
        Env e2 = new Env();
        HandlerList late = new HandlerList();
        Kit.NexusAngler angler = new Kit.NexusAngler();
        Kit.NexusFarming farming2 = new Kit.NexusFarming();
        handler(late, angler, "angler:late");
        handler(late, farming2, "farming:late");
        ListenerGate g2 = gateFor(e2);
        expect(g2.install(angler) == 1, "install(plugin) wraps only that plugin");
        int wrappedNow = 0;
        for (RegisteredListener rl : late.getRegisteredListeners()) {
            if (rl instanceof ListenerGate.Gated) {
                wrappedNow++;
                expect(rl.getPlugin() == angler, "it is the angler's handler that got wrapped");
            }
        }
        expect(wrappedNow == 1, "one wrapped, the other untouched");
        // skip logic needs no wrapper: skips() alone
        Kit.TestPlayer dee = e2.player("Dee");
        e2.choose(dee, "survivor");
        expect(g2.skips(new PlayerMoveEvent(dee, null, null), "NexusAngler"), "skips: angler hidden for dee");
        expect(!g2.skips(new PlayerMoveEvent(dee, null, null), "NexusRime"), "skips: rime is dee's");
        expect(!g2.skips(new PlayerMoveEvent(dee, null, null), "Vault"), "skips: other plugins never");
        expect(!g2.skips(new PlayerJoinEvent(dee, ""), "NexusAngler"), "skips: join never");
        g2.setEnabled(false);
        expect(!g2.skips(new PlayerMoveEvent(dee, null, null), "NexusAngler"), "skips: not when disabled");
        g2.setEnabled(true);
        // events whose handler plugin is null are left alone
        HandlerList odd = new HandlerList();
        odd.register(new RegisteredListener(new Listener() { }, (l, ev) -> seen.add("orphan"), EventPriority.NORMAL, null, false));
        expect(g2.installAll() >= 0 && odd.getRegisteredListeners()[0].getClass() == RegisteredListener.class, "a handler with no plugin is left alone");

        // spawn and the unattributable
        seen.clear();
        HandlerList spawns = new HandlerList();
        handler(spawns, angler, "angler:spawn");
        g2.install(angler);
        fire(spawns, new EntitySpawnEvent(new Entity()));
        expect(seen.equals(List.of("angler:spawn")), "a spawn of a non-player entity runs");
        Bukkit.PLAYERS.clear();
        Kit.finish();
    }
}
