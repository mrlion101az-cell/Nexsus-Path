package com.nexuscraft.nexuspath;

import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.vehicle.VehicleEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Who an event is about. A plugin's handler for an event only needs to be skipped for a player who
 * switched that plugin off, so this answers "which players are involved?" for the event types that
 * have a player in them: the player doing it (or being hit, or shooting), the one being targeted,
 * and the one at the inventory. An event with no player in it (a hopper, a dispenser, redstone) is
 * attributed to whoever last touched that block, if anyone did.
 */
final class EventPlayers {

    private EventPlayers() {
    }

    static List<UUID> of(Event event, BlockOwners owners) {
        List<UUID> out = new ArrayList<>(2);
        if (event instanceof PlayerEvent pe) {
            add(out, pe.getPlayer());
        }
        if (event instanceof BlockBreakEvent b) {
            add(out, b.getPlayer());
        }
        if (event instanceof BlockPlaceEvent b) {
            add(out, b.getPlayer());
        }
        if (event instanceof EntityEvent ee) {
            addEntity(out, ee.getEntity());
        }
        if (event instanceof EntityDamageByEntityEvent d) {
            addEntity(out, d.getDamager());
        }
        if (event instanceof EntityTargetLivingEntityEvent t) {
            addEntity(out, t.getTarget());
        }
        if (event instanceof InventoryEvent ie && ie.getView() != null) {
            HumanEntity who = ie.getView().getPlayer();
            if (who instanceof Player p) {
                add(out, p);
            }
        }
        if (event instanceof VehicleEvent ve && ve.getVehicle() != null) {
            for (Entity rider : ve.getVehicle().getPassengers()) {
                addEntity(out, rider);
            }
        }
        if (out.isEmpty() && owners != null && event instanceof BlockEvent be && be.getBlock() != null) {
            UUID owner = owners.ownerOf(be.getBlock().getLocation());
            if (owner != null) {
                out.add(owner);
            }
        }
        return out;
    }

    private static void addEntity(List<UUID> out, Entity e) {
        if (e instanceof Player p) {
            add(out, p);
        } else if (e instanceof Projectile pr) {
            ProjectileSource src = pr.getShooter();
            if (src instanceof Player p) {
                add(out, p);
            }
        }
    }

    private static void add(List<UUID> out, Player p) {
        if (p != null && !out.contains(p.getUniqueId())) {
            out.add(p.getUniqueId());
        }
    }
}
