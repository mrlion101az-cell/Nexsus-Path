package com.nexuscraft.nexuspath;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;

import java.util.ArrayList;
import java.util.List;

import static com.nexuscraft.nexuspath.Kit.expect;

public final class ListenerTest {

    public static void main(String[] args) {
        Env e = new Env();
        Kit.NexusFarming farming = new Kit.NexusFarming();
        Kit.NexusRime rime = new Kit.NexusRime();
        Kit.NexusClaims claims = new Kit.NexusClaims();
        Env.command(farming, "farm", "plow");
        Env.command(rime, "rime");
        Env.command(claims, "claim");
        Bukkit.COMMANDS.put("help", new org.bukkit.command.Command());   // not a plugin command
        PathListener l = new PathListener(e.plugin, e.service, e.owners);
        Kit.TestPlayer ann = e.player("Ann");
        Kit.TestPlayer bob = e.player("Bob");
        e.choose(ann, "survivor");

        // CommandOwner
        expect(CommandOwner.label("/Farm sow wheat").equals("farm") && CommandOwner.label("farm").equals("farm") && CommandOwner.label("  /FARM").equals("farm"), "label parsing");
        expect(CommandOwner.label(null).isEmpty() && CommandOwner.label("/").isEmpty(), "empty labels");
        expect(CommandOwner.pluginOf("farm").equals("NexusFarming") && CommandOwner.pluginOf("plow").equals("NexusFarming"), "alias resolves to the owner");
        expect(CommandOwner.pluginOf("minecraft:farm").equals("NexusFarming"), "namespaced label resolves");
        expect(CommandOwner.pluginOf("help") == null && CommandOwner.pluginOf("nothing") == null && CommandOwner.pluginOf("") == null && CommandOwner.pluginOf(null) == null, "non-plugin commands have no owner");

        // blocked command
        PlayerCommandPreprocessEvent hid = new PlayerCommandPreprocessEvent(ann, "/farm sow");
        l.onCommand(hid);
        expect(hid.isCancelled(), "ann's hidden command is cancelled");
        expect(ann.heard("Farming is not part of the game you chose") && ann.heard("/path try nexusfarming"), "she is told why: " + ann.lastMessage());
        PlayerCommandPreprocessEvent alias = new PlayerCommandPreprocessEvent(ann, "/PLOW");
        l.onCommand(alias);
        expect(alias.isCancelled(), "aliases are blocked");
        PlayerCommandPreprocessEvent ns = new PlayerCommandPreprocessEvent(ann, "/nexusfarming:farm");
        Bukkit.COMMANDS.put("nexusfarming:farm", Bukkit.COMMANDS.get("farm"));
        l.onCommand(ns);
        expect(ns.isCancelled(), "the plugin-prefixed form is blocked too");
        PlayerCommandPreprocessEvent mine = new PlayerCommandPreprocessEvent(ann, "/rime");
        l.onCommand(mine);
        expect(!mine.isCancelled(), "her own system's command works");
        PlayerCommandPreprocessEvent lock = new PlayerCommandPreprocessEvent(ann, "/claim");
        l.onCommand(lock);
        expect(!lock.isCancelled(), "locked systems' commands always work");
        PlayerCommandPreprocessEvent plain = new PlayerCommandPreprocessEvent(ann, "/help");
        l.onCommand(plain);
        expect(!plain.isCancelled(), "other commands are untouched");
        PlayerCommandPreprocessEvent bobs = new PlayerCommandPreprocessEvent(bob, "/farm sow");
        l.onCommand(bobs);
        expect(!bobs.isCancelled(), "bob chose nothing: nothing is blocked");
        PlayerCommandPreprocessEvent unknown = new PlayerCommandPreprocessEvent(ann, "/zzz");
        l.onCommand(unknown);
        expect(!unknown.isCancelled(), "an unknown command is not ours to block");
        // borrowed: allowed
        e.service.tryIt(ann, "NexusFarming");
        PlayerCommandPreprocessEvent borrowed = new PlayerCommandPreprocessEvent(ann, "/farm sow");
        l.onCommand(borrowed);
        expect(!borrowed.isCancelled(), "a borrowed system's command works");
        e.clock.addAndGet(11 * 60_000L);
        e.service.tick();

        // tab list
        List<String> cmds = new ArrayList<>(List.of("farm", "plow", "rime", "claim", "help", "zzz"));
        PlayerCommandSendEvent send = new PlayerCommandSendEvent(ann, cmds);
        l.onTabList(send);
        expect(cmds.equals(List.of("rime", "claim", "help", "zzz")), "hidden commands leave the tab list: " + cmds);
        List<String> all = new ArrayList<>(List.of("farm", "rime"));
        l.onTabList(new PlayerCommandSendEvent(bob, all));
        expect(all.size() == 2, "bob's tab list is untouched");

        // join: refresh and invite
        Env f = new Env();
        PathListener lf = new PathListener(f.plugin, f.service, f.owners);
        Kit.TestPlayer cy = f.player("Cy");
        f.cfg.inviteDelaySeconds = 0;
        lf.onJoin(new PlayerJoinEvent(cy, ""));
        expect(cy.heard("/path"), "a new player is invited at once when the delay is zero: " + cy.lastMessage());
        expect(f.store.peek(cy.getUniqueId()).reminders == 1, "the reminder is counted");
        f.cfg.inviteDelaySeconds = 90;
        int msgs = cy.messages.size();
        lf.onJoin(new PlayerJoinEvent(cy, ""));
        expect(cy.messages.size() == msgs, "with a delay the invite is scheduled, not sent now");
        f.choose(cy, "farmer");
        msgs = cy.messages.size();
        f.cfg.inviteDelaySeconds = 0;
        lf.onJoin(new PlayerJoinEvent(cy, ""));
        expect(cy.messages.size() == msgs, "a player who chose is not invited");
        expect(f.service.isHidden(cy.getUniqueId(), "NexusRime"), "joining refreshes what is hidden");
        // join of a returning player with a saved choice applies it before anyone else's handler
        Env g = new Env();
        PathListener lg = new PathListener(g.plugin, g.service, g.owners);
        Kit.TestPlayer dee = g.player("Dee");
        g.store.put(dee.getUniqueId(), ServiceTest.chosen("survivor"));
        lg.onJoin(new PlayerJoinEvent(dee, ""));
        expect(g.service.isHidden(dee.getUniqueId(), "NexusFarming"), "a saved choice is applied on join");
        lg.onQuit(new PlayerQuitEvent(dee, ""));
        expect(!g.service.isHidden(dee.getUniqueId(), "NexusFarming") && !g.service.anyoneHides("NexusFarming"), "quit clears it");

        // block owners: interact, place, open
        Block b = Kit.WORLD.getBlockAt(3, 64, 3);
        l.onInteract(new PlayerInteractEvent(ann, Action.RIGHT_CLICK_BLOCK, null, b));
        expect(e.owners.ownerOf(b.getLocation()).equals(ann.getUniqueId()), "interacting marks the block");
        l.onInteract(new PlayerInteractEvent(ann, Action.RIGHT_CLICK_AIR, null, null));
        expect(e.owners.size() == 1, "interacting with air marks nothing");
        Block b2 = Kit.WORLD.getBlockAt(4, 64, 4);
        l.onPlace(new BlockPlaceEvent(b2, bob));
        expect(e.owners.ownerOf(b2.getLocation()).equals(bob.getUniqueId()), "placing marks the block");

        // BlockOwners: expiry, bounds, replacement
        e.clock.addAndGet(29 * 60_000L);
        expect(e.owners.ownerOf(b2.getLocation()) != null, "still remembered after 29 minutes");
        e.clock.addAndGet(2 * 60_000L);
        expect(e.owners.ownerOf(b2.getLocation()) == null, "forgotten after 30 minutes");
        e.owners.note(b.getLocation(), bob.getUniqueId());
        expect(e.owners.ownerOf(b.getLocation()).equals(bob.getUniqueId()), "the last toucher wins");
        e.owners.note(null, bob.getUniqueId());
        e.owners.note(b.getLocation(), null);
        expect(e.owners.ownerOf(b.getLocation()).equals(bob.getUniqueId()), "null location or player is ignored");
        expect(e.owners.ownerOf(null) == null, "null location has no owner");
        BlockOwners small = new BlockOwners(e.clock::get);
        for (int i = 0; i < 5000; i++) {
            small.note(new org.bukkit.Location(Kit.WORLD, i, 64, 0), bob.getUniqueId());
        }
        expect(small.size() == 4096, "the memory is bounded: " + small.size());
        expect(small.ownerOf(new org.bukkit.Location(Kit.WORLD, 0, 64, 0)) == null && small.ownerOf(new org.bukkit.Location(Kit.WORLD, 4999, 64, 0)) != null, "the oldest go first");
        expect(BlockOwners.key(new org.bukkit.Location(null, 1, 2, 3)) == null, "a location without a world has no key");

        // opening a container marks it
        org.bukkit.inventory.Inventory chest = new org.bukkit.inventory.Inventory();
        chest.stubSetLocation(new org.bukkit.Location(Kit.WORLD, 11, 64, 11));
        InventoryView chestView = new InventoryView();
        chestView.setTopInventory(chest);
        chestView.setPlayer(bob);
        l.onOpen(new org.bukkit.event.inventory.InventoryOpenEvent(chestView));
        expect(e.owners.ownerOf(chest.getLocation()) != null && e.owners.ownerOf(chest.getLocation()).equals(bob.getUniqueId()), "opening a chest marks it");
        InventoryView menuView = new InventoryView();
        menuView.setTopInventory(new org.bukkit.inventory.Inventory());
        menuView.setPlayer(bob);
        int before = e.owners.size();
        l.onOpen(new org.bukkit.event.inventory.InventoryOpenEvent(menuView));
        expect(e.owners.size() == before, "opening a virtual menu marks nothing");
        InventoryView nobody = new InventoryView();
        nobody.setTopInventory(chest);
        l.onOpen(new org.bukkit.event.inventory.InventoryOpenEvent(nobody));
        expect(e.owners.ownerOf(chest.getLocation()).equals(bob.getUniqueId()), "a view with no viewer marks nothing");

        // BlockOwners: the 30 minute boundary, forgetting frees memory, reading refreshes a block's place in the queue
        BlockOwners edge = new BlockOwners(e.clock::get);
        org.bukkit.Location spot = new org.bukkit.Location(Kit.WORLD, 7, 70, 7);
        edge.note(spot, ann.getUniqueId());
        e.clock.addAndGet(30 * 60_000L);
        expect(edge.ownerOf(spot) != null, "exactly 30 minutes later it is still remembered");
        e.clock.addAndGet(1);
        expect(edge.ownerOf(spot) == null && edge.size() == 0, "one millisecond more and it is forgotten for good");
        BlockOwners lru = new BlockOwners(e.clock::get);
        org.bukkit.Location first = new org.bukkit.Location(Kit.WORLD, 0, 64, 1);
        lru.note(first, ann.getUniqueId());
        for (int i = 1; i <= 4095; i++) {
            lru.note(new org.bukkit.Location(Kit.WORLD, i, 64, 0), bob.getUniqueId());
        }
        expect(lru.ownerOf(first) != null, "the first block is still there at the limit (and has now been read)");
        lru.note(new org.bukkit.Location(Kit.WORLD, 9999, 64, 0), bob.getUniqueId());
        expect(lru.ownerOf(first) != null && lru.ownerOf(new org.bukkit.Location(Kit.WORLD, 1, 64, 0)) == null, "a block that was read recently outlives older ones that were not");

        // CommandOwner edge cases
        expect(CommandOwner.label("/ x").isEmpty() && CommandOwner.label(" /y z").equals("y"), "a slash then a space gives an empty label");
        Bukkit.COMMANDS.put("orphan", new org.bukkit.command.PluginCommand("orphan", null));
        expect(CommandOwner.pluginOf("orphan") == null, "a command with no plugin has no owner");

        // our own menus: clicks and drags are cancelled, and only inside our menu
        PathGui.openStyles(e.service, ann, e.service.draftOf(ann.getUniqueId()));
        PathGui gui = (PathGui) ann.opened.getHolder();
        InventoryView view = new InventoryView();
        view.setTopInventory(ann.opened);
        view.setPlayer(ann);
        InventoryClickEvent click = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 10, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        l.onClick(click);
        expect(click.isCancelled(), "clicks in our menu are cancelled");
        InventoryDragEvent drag = new InventoryDragEvent(view);
        l.onDrag(drag);
        expect(drag.isCancelled(), "drags in our menu are cancelled");
        InventoryView other = new InventoryView();
        other.setTopInventory(new org.bukkit.inventory.Inventory());
        other.setPlayer(ann);
        InventoryClickEvent foreign = new InventoryClickEvent(other, InventoryType.SlotType.CONTAINER, 10, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        l.onClick(foreign);
        expect(!foreign.isCancelled(), "clicks in other menus are left alone");
        InventoryDragEvent foreignDrag = new InventoryDragEvent(other);
        l.onDrag(foreignDrag);
        expect(!foreignDrag.isCancelled(), "drags in other menus are left alone");
        InventoryView none = new InventoryView();
        InventoryClickEvent empty = new InventoryClickEvent(none, InventoryType.SlotType.CONTAINER, 10, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        l.onClick(empty);
        expect(!empty.isCancelled(), "a view with no top inventory is ignored");
        expect(gui != null, "the holder is our menu");

        // slot 0 is a real button in the systems menu: a click on it must reach the menu
        PathGui.openSystems(e.service, ann, e.service.draftOf(ann.getUniqueId()), 0, false);
        InventoryView sysView = new InventoryView();
        sysView.setTopInventory(ann.opened);
        sysView.setPlayer(ann);
        boolean had = ((PathGui) ann.opened.getHolder()).draft.visible(e.catalog, true, e.catalog.system("NexusRime"));
        l.onClick(new InventoryClickEvent(sysView, InventoryType.SlotType.CONTAINER, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL));
        boolean now = ((PathGui) ann.opened.getHolder()).draft.visible(e.catalog, true, e.catalog.system("NexusRime"));
        expect(had != now, "a click on slot 0 toggles the first system");

        // the delayed invite (the scheduler stub keeps the task so the test can run it)
        Env d = new Env();
        PathListener ld = new PathListener(d.plugin, d.service, d.owners);
        Kit.TestPlayer del = d.player("Del");
        d.cfg.inviteDelaySeconds = 90;
        org.bukkit.scheduler.BukkitScheduler.LATER.clear();
        ld.onJoin(new PlayerJoinEvent(del, ""));
        expect(org.bukkit.scheduler.BukkitScheduler.LATER.size() == 1 && del.messages.isEmpty(), "the invite is scheduled, not sent");
        org.bukkit.scheduler.BukkitScheduler.LATER.get(0).run();
        expect(del.heard("/path") && d.store.peek(del.getUniqueId()).reminders == 1, "when the time comes the invite is sent");
        Kit.TestPlayer del2 = d.player("Del2");
        org.bukkit.scheduler.BukkitScheduler.LATER.clear();
        ld.onJoin(new PlayerJoinEvent(del2, ""));
        d.choose(del2, "farmer");
        int sofar = del2.messages.size();
        org.bukkit.scheduler.BukkitScheduler.LATER.get(0).run();
        expect(del2.messages.size() == sofar, "a player who chose while waiting is not invited");
        Kit.TestPlayer del3 = d.player("Del3");
        org.bukkit.scheduler.BukkitScheduler.LATER.clear();
        ld.onJoin(new PlayerJoinEvent(del3, ""));
        del3.online = false;
        org.bukkit.scheduler.BukkitScheduler.LATER.get(0).run();
        expect(!del3.heard("/path"), "a player who left while waiting is not invited");

        Kit.finish();
    }
}
