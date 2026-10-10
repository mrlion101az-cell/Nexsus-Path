package com.nexuscraft.nexuspath;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import java.util.Iterator;

/**
 * What NexusPath does with the server's own events: refuses and hides the commands of systems a
 * player hid, keeps its books on who joined, and runs its menus. (Hiding what other plugins do is
 * {@link ListenerGate}'s job.)
 */
final class PathListener implements Listener {

    private final Plugin plugin;
    private final PathService service;
    private final BlockOwners owners;

    PathListener(Plugin plugin, PathService service, BlockOwners owners) {
        this.plugin = plugin;
        this.service = service;
        this.owners = owners;
    }

    // ------------------------------------------------------------------ commands

    /** Runs before everyone else: a hidden system's command never reaches the plugin. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        String owner = CommandOwner.pluginOf(CommandOwner.label(event.getMessage()));
        if (owner == null || !service.isHidden(p.getUniqueId(), owner)) {
            return;
        }
        event.setCancelled(true);
        SystemInfo s = service.catalog().systemFor(owner);
        service.say(p, service.fill(service.cfg().msgBlocked, s, null));
    }

    /** The tab list never offers what is hidden. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onTabList(PlayerCommandSendEvent event) {
        Player p = event.getPlayer();
        if (service.hiddenOf(p.getUniqueId()).isEmpty()) {
            return;
        }
        Iterator<String> it = event.getCommands().iterator();
        while (it.hasNext()) {
            String owner = CommandOwner.pluginOf(it.next());
            if (owner != null && service.isHidden(p.getUniqueId(), owner)) {
                it.remove();
            }
        }
    }

    // ------------------------------------------------------------------ players

    /** First of all, so everyone else's join handling already sees the right game. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        service.refresh(p);
        if (!service.shouldInvite(p)) {
            return;
        }
        long ticks = service.cfg().inviteDelaySeconds * 20L;
        if (ticks <= 0) {
            service.invite(p);
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && service.shouldInvite(p)) {
                service.invite(p);
            }
        }, ticks);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        service.quit(event.getPlayer());
    }

    // ------------------------------------------------------------------ who touched which block

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() != null) {
            owners.note(event.getClickedBlock().getLocation(), event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlace(BlockPlaceEvent event) {
        owners.note(event.getBlock().getLocation(), event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player p && event.getInventory() != null && event.getInventory().getLocation() != null) {
            owners.note(event.getInventory().getLocation(), p.getUniqueId());
        }
    }

    // ------------------------------------------------------------------ our menus

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView() == null ? null : event.getView().getTopInventory();
        if (top == null || !(top.getHolder() instanceof PathGui gui)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p)) {
            return;
        }
        if (event.getClickedInventory() == top && event.getRawSlot() >= 0 && event.getRawSlot() < PathGui.SIZE) {
            gui.click(service, p, event.getRawSlot());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView() == null ? null : event.getView().getTopInventory();
        if (top != null && top.getHolder() instanceof PathGui) {
            event.setCancelled(true);
        }
    }
}
