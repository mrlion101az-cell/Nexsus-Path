package com.nexuscraft.nexuspath;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import java.util.List;

import static com.nexuscraft.nexuspath.Kit.expect;

public final class GuiTest {

    static PathGui gui(Kit.TestPlayer p) {
        return (PathGui) p.opened.getHolder();
    }

    static String name(Inventory inv, int slot) {
        ItemStack it = inv.getItem(slot);
        return it == null ? null : it.getItemMeta().getDisplayName();
    }

    static String lore(Inventory inv, int slot) {
        ItemStack it = inv.getItem(slot);
        return it == null ? "" : String.join("|", it.getItemMeta().getLore());
    }

    static int slotOf(PathGui g, String action) {
        for (var en : g.actions.entrySet()) {
            if (en.getValue().equals(action)) {
                return en.getKey();
            }
        }
        return -1;
    }

    public static void main(String[] args) {
        styleMenu();
        systemsMenu();
        paging();
        tryMode();
        throughTheListener();
        wrapping();
        details();
        Kit.finish();
    }

    static void styleMenu() {
        Env e = new Env();
        Kit.TestPlayer p = e.player("Ann");
        PathGui.openStyles(e.service, p, e.service.draftOf(p.getUniqueId()));
        PathGui g = gui(p);
        expect(!g.systems && g.getInventory() == p.opened, "style menu opened for her");
        int survivor = slotOf(g, "style:survivor");
        int farmer = slotOf(g, "style:farmer");
        int builder = slotOf(g, "style:builder");
        expect(survivor == PathGui.STYLE_SLOTS[0] && farmer == PathGui.STYLE_SLOTS[1] && builder == PathGui.STYLE_SLOTS[2], "styles fill the style slots in catalogue order");
        expect(name(p.opened, survivor).contains("Survivor") && !name(p.opened, survivor).contains("\u2714"), "unselected style has no tick");
        expect(lore(p.opened, survivor).contains("Brings 2 systems") && lore(p.opened, survivor).contains("Rime, Trials"), "lore counts and names its systems: " + lore(p.opened, survivor));
        expect(lore(p.opened, survivor).contains("Click to add"), "unselected lore says add");
        expect(slotOf(g, "all") == 37 && slotOf(g, "systems") == 40 && slotOf(g, "save") == 43 && slotOf(g, "close") == 45, "fixed buttons are where they should be");
        expect(lore(p.opened, 49).contains("Claims"), "the always-on shelf names locked systems: " + lore(p.opened, 49));
        expect(p.opened.getItem(4) != null && g.actions.get(4) == null, "the how-it-works book does nothing");

        // click a style: toggles on, menu reopens with the tick and glint
        expect(g.click(e.service, p, survivor), "clicking a style does something");
        PathGui g2 = gui(p);
        expect(g2 != g && g2.draft.styles.contains("survivor"), "style added to the draft in a fresh menu");
        expect(name(p.opened, survivor).contains("\u2714"), "selected style shows a tick");
        expect(lore(p.opened, survivor).contains("Selected"), "selected lore");
        expect(p.opened.getItem(survivor).getItemMeta().getEnchantmentGlintOverride() == Boolean.TRUE, "selected style glows");
        expect(p.opened.getItem(farmer).getItemMeta().getEnchantmentGlintOverride() != Boolean.TRUE, "unselected style does not glow");
        expect(e.store.peek(p.getUniqueId()) == null, "nothing is saved until Save");
        g2.click(e.service, p, farmer);
        expect(gui(p).draft.styles.size() == 2, "styles can be mixed");
        gui(p).click(e.service, p, survivor);
        expect(gui(p).draft.styles.size() == 1 && gui(p).draft.styles.contains("farmer"), "clicking again removes it");

        // everything
        gui(p).click(e.service, p, 37);
        PathGui ga = gui(p);
        expect(ga.draft.all && ga.draft.styles.isEmpty(), "everything clears styles");
        expect(name(p.opened, 37).contains("\u2714") && lore(p.opened, 43).contains("Everything"), "everything is shown selected and described");
        ga.click(e.service, p, farmer);
        expect(!gui(p).draft.all && gui(p).draft.styles.contains("farmer"), "picking a style ends everything");

        // the review screen, then back
        gui(p).click(e.service, p, 40);
        expect(gui(p).systems, "review opens the systems page");
        gui(p).click(e.service, p, 45);
        expect(!gui(p).systems && gui(p).draft.styles.contains("farmer"), "back returns to styles with the draft intact");

        // cancel
        gui(p).click(e.service, p, 45);
        expect(p.opened == null && e.store.peek(p.getUniqueId()) == null, "cancel closes and saves nothing");
        PathGui.openStyles(e.service, p, e.service.draftOf(p.getUniqueId()));
        expect(!gui(p).click(e.service, p, 0), "a slot with no action does nothing");
        expect(!gui(p).click(e.service, p, 4), "the info book does nothing");

        // save
        gui(p).click(e.service, p, slotOf(gui(p), "style:farmer"));
        gui(p).click(e.service, p, 43);
        expect(p.opened == null, "save closes the menu");
        Choice saved = e.store.peek(p.getUniqueId());
        expect(saved != null && saved.chosen && saved.styles.contains("farmer"), "save stores the choice");
        expect(e.service.isHidden(p.getUniqueId(), "NexusRime"), "and applies it");

        // reopening starts from what she saved, as a copy
        PathGui.openStyles(e.service, p, e.service.draftOf(p.getUniqueId()));
        expect(gui(p).draft.styles.contains("farmer") && gui(p).draft != saved, "reopening starts from the saved choice, as a copy");
        gui(p).click(e.service, p, slotOf(gui(p), "style:farmer"));
        expect(saved.styles.contains("farmer"), "toggling the draft leaves the saved choice alone");
        gui(p).click(e.service, p, 45);
        expect(saved.styles.contains("farmer"), "so does cancelling");

        // describe in the save button
        PathGui.openStyles(e.service, p, e.service.draftOf(p.getUniqueId()));
        expect(lore(p.opened, 43).contains("Farmer: 4 on, 3 hidden"), "the save button says what saving does: " + lore(p.opened, 43));
        Choice all = new Choice();
        all.all = true;
        PathGui.openStyles(e.service, p, all);
        expect(lore(p.opened, 43).contains("Everything, nothing hidden"), "and for everything");

        // more styles than slots: extras are not drawn, and nothing crashes
        Env f = new Env();
        Kit.MapSection many = new Kit.MapSection();
        for (int i = 0; i < 20; i++) {
            many.put("styles.s" + i + ".title", "S" + i);
        }
        f.catalog.load(many, "custom");
        Kit.TestPlayer q = f.player("Bob");
        PathGui.openStyles(f.service, q, new Choice());
        int drawn = 0;
        for (String a : gui(q).actions.values()) {
            if (a.startsWith("style:")) {
                drawn++;
            }
        }
        expect(drawn == PathGui.STYLE_SLOTS.length, "only as many styles as slots are drawn: " + drawn);
        expect(PathGui.material("NOT_A_THING", org.bukkit.Material.BOOK) == org.bukkit.Material.BOOK && PathGui.material(null, org.bukkit.Material.BOOK) == org.bukkit.Material.BOOK, "unknown icon falls back");
    }

    static void systemsMenu() {
        Env e = new Env();
        Kit.TestPlayer p = e.player("Ann");
        Choice d = e.service.draftOf(p.getUniqueId());
        d.styles.add("survivor");
        d.chosen = true;
        PathGui.openSystems(e.service, p, d, 0, false);
        PathGui g = gui(p);
        expect(g.systems && g.page == 0 && !g.tryMode, "systems menu open");
        // pickable: Rime Farming Angler Redstone Trials Menu Unlisted
        expect(name(p.opened, 0).contains("Rime") && name(p.opened, 0).contains("\u2714"), "rime is in her game");
        expect(name(p.opened, 1).contains("Farming") && name(p.opened, 1).contains("\u2718"), "farming is out");
        expect(g.actions.get(0).equals("toggle:NexusRime") && g.actions.get(1).equals("toggle:NexusFarming"), "tiles toggle");
        expect(p.opened.getItem(6) != null && name(p.opened, 6).contains("NexusUnlisted"), "unlisted plugins are listed");
        expect(p.opened.getItem(7) == null, "and nothing beyond");
        expect(lore(p.opened, 0).contains("In: Survivor") && lore(p.opened, 5).contains("In no style"), "which styles include it: " + lore(p.opened, 0) + " / " + lore(p.opened, 5));
        expect(lore(p.opened, 3).contains("Shared with everyone"), "world systems say so: " + lore(p.opened, 3));
        expect(!lore(p.opened, 0).contains("Shared with everyone"), "others do not");
        expect(lore(p.opened, 1).contains("Not in your game") && lore(p.opened, 1).contains("Click to add"), "off tile says add");
        expect(lore(p.opened, 0).contains("In your game") && lore(p.opened, 0).contains("Click to hide"), "on tile says hide");
        expect(p.opened.getItem(0).getItemMeta().getEnchantmentGlintOverride() == Boolean.TRUE && p.opened.getItem(1).getItemMeta().getEnchantmentGlintOverride() != Boolean.TRUE, "on tiles glow");
        expect(name(p.opened, 48).contains("Page 1 of 1") && p.opened.getItem(47) == null && p.opened.getItem(49) == null, "one page: no previous or next");
        expect(slotOf(g, "styles") == 45 && slotOf(g, "save") == 52 && slotOf(g, "close") == 53, "buttons in place");
        expect(p.opened.getItem(51) == null, "no try-out switch for someone who has not chosen yet");

        // toggling edits the draft only
        g.click(e.service, p, 1);
        PathGui g2 = gui(p);
        expect(g2.draft.shown.contains("nexusfarming") && g2.draft.visible(e.catalog, true, e.catalog.system("NexusFarming")), "toggling farming on adds it by hand");
        expect(name(p.opened, 1).contains("\u2714"), "the tile updates");
        g2.click(e.service, p, 0);
        expect(gui(p).draft.hidden.contains("nexusrime"), "toggling rime off hides it by hand");
        expect(e.store.peek(p.getUniqueId()) == null, "still nothing saved");
        gui(p).click(e.service, p, 52);
        Choice saved = e.store.peek(p.getUniqueId());
        expect(saved.chosen && saved.shown.contains("nexusfarming") && saved.hidden.contains("nexusrime"), "save keeps the hand-made changes");
        expect(!e.service.isHidden(p.getUniqueId(), "NexusFarming") && e.service.isHidden(p.getUniqueId(), "NexusRime"), "and applies them");

        // an everything draft becomes a list when one is switched off
        Env f = new Env();
        Kit.TestPlayer q = f.player("Bob");
        Choice all = new Choice();
        all.all = true;
        all.chosen = true;
        PathGui.openSystems(f.service, q, all, 0, false);
        expect(name(q.opened, 1).contains("\u2714"), "everything shows every tile on");
        gui(q).click(f.service, q, 1);
        expect(!gui(q).draft.all && !gui(q).draft.visible(f.catalog, true, f.catalog.system("NexusFarming")) && gui(q).draft.visible(f.catalog, true, f.catalog.system("NexusRime")), "switching one off leaves the others on");
    }

    static void paging() {
        Env e = new Env();
        for (int i = 0; i < 60; i++) {
            e.installed.add("NexusExtra" + i);
        }
        Kit.TestPlayer p = e.player("Ann");
        Choice d = new Choice();
        PathGui.openSystems(e.service, p, d, 0, false);
        PathGui g = gui(p);
        // 7 pickable + 60 = 67 -> 2 pages
        expect(name(p.opened, 48).contains("Page 1 of 2"), "two pages: " + name(p.opened, 48));
        expect(p.opened.getItem(44) != null && p.opened.getItem(47) == null && p.opened.getItem(49) != null, "page one is full and offers next only");
        expect(g.actions.get(49).equals("page:1"), "next goes to page 1");
        g.click(e.service, p, 49);
        PathGui g2 = gui(p);
        expect(g2.page == 1 && name(p.opened, 48).contains("Page 2 of 2"), "page two");
        expect(p.opened.getItem(21) != null && p.opened.getItem(22) == null, "page two holds the other 22 (67-45)");
        expect(p.opened.getItem(47) != null && g2.actions.get(47).equals("page:0") && p.opened.getItem(49) == null, "page two offers previous only");
        g2.click(e.service, p, 47);
        expect(gui(p).page == 0, "previous goes back");
        // asking for a page out of range clamps
        PathGui.openSystems(e.service, p, d, 99, false);
        expect(gui(p).page == 1, "too high a page clamps to the last");
        PathGui.openSystems(e.service, p, d, -5, false);
        expect(gui(p).page == 0, "too low a page clamps to the first");
        // toggling on page two keeps the page
        PathGui.openSystems(e.service, p, d, 1, false);
        gui(p).click(e.service, p, 0);
        expect(gui(p).page == 1, "toggling keeps the page");
        // a garbage page action does nothing
        gui(p).actions.put(30, "page:xyz");
        expect(!gui(p).click(e.service, p, 30), "a bad page number is ignored");
        gui(p).actions.put(31, "mystery");
        expect(!gui(p).click(e.service, p, 31), "an unknown action is ignored");
        // an empty server still draws one page
        Env f = new Env();
        f.installed.clear();
        Kit.TestPlayer q = f.player("Bob");
        PathGui.openSystems(f.service, q, new Choice(), 0, false);
        expect(name(q.opened, 48).contains("Page 1 of 1") && q.opened.getItem(0) == null, "no systems: one empty page");
    }

    static void tryMode() {
        Env e = new Env();
        Kit.TestPlayer p = e.player("Ann");
        e.choose(p, "survivor");
        PathGui.openSystems(e.service, p, e.service.draftOf(p.getUniqueId()), 0, false);
        expect(name(p.opened, 51).contains("Try-out mode: off") && slotOf(gui(p), "mode") == 51, "chosen players get the try-out switch");
        gui(p).click(e.service, p, 51);
        PathGui g = gui(p);
        expect(g.tryMode && name(p.opened, 51).contains("ON"), "try-out mode switched on");
        expect(g.actions.get(1).equals("try:NexusFarming") && g.actions.get(0) == null, "hidden tiles borrow; shown tiles do nothing");
        expect(lore(p.opened, 1).contains("borrow for 10 minutes") && lore(p.opened, 0).contains("nothing to borrow"), "lore explains: " + lore(p.opened, 1));
        g.click(e.service, p, 1);
        expect(!e.service.isHidden(p.getUniqueId(), "NexusFarming"), "clicking borrows it");
        expect(gui(p).tryMode && name(p.opened, 1).contains("\u2714"), "try-out mode stays on and the tile now shows in");
        expect(lore(p.opened, 1).contains("Borrowed: 10 min left") && gui(p).actions.get(1) == null, "a borrowed tile says how long is left and does nothing");
        expect(e.store.peek(p.getUniqueId()).hidden.isEmpty() && e.store.peek(p.getUniqueId()).shown.isEmpty(), "borrowing changes nothing that is saved");
        // failing borrow tells her
        gui(p).actions.put(2, "try:NexusClaims");
        int before = p.messages.size();
        gui(p).click(e.service, p, 2);
        expect(p.messages.size() > before && p.heard("always on"), "a refused borrow explains: " + p.lastMessage());
        // paging keeps try mode
        for (int i = 0; i < 50; i++) {
            e.installed.add("NexusExtra" + i);
        }
        PathGui.openSystems(e.service, p, e.service.draftOf(p.getUniqueId()), 0, true);
        gui(p).click(e.service, p, 49);
        expect(gui(p).tryMode && gui(p).page == 1, "paging keeps try-out mode");
        // switching mode back off
        gui(p).click(e.service, p, 51);
        expect(!gui(p).tryMode && gui(p).actions.get(1) != null && gui(p).actions.get(1).startsWith("toggle:"), "switching off returns tiles to toggling");
        // a player who never chose cannot be in try mode
        Kit.TestPlayer q = e.player("Bob");
        PathGui.openSystems(e.service, q, new Choice(), 0, true);
        expect(!gui(q).tryMode, "no try-out mode before choosing");
    }

    static void throughTheListener() {
        Env e = new Env();
        PathListener l = new PathListener(e.plugin, e.service, e.owners);
        Kit.TestPlayer p = e.player("Ann");
        PathGui.openStyles(e.service, p, e.service.draftOf(p.getUniqueId()));
        InventoryView view = new InventoryView();
        view.setTopInventory(p.opened);
        view.setPlayer(p);
        int slot = PathPlugin_styleSlot(p);
        InventoryClickEvent click = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        l.onClick(click);
        expect(click.isCancelled() && gui(p).draft.styles.contains("survivor"), "a click through the listener reaches the menu");
        // a click in the player's own inventory (bottom) does not trigger the menu
        Kit.TestPlayer q = e.player("Bob");
        PathGui.openStyles(e.service, q, e.service.draftOf(q.getUniqueId()));
        InventoryView v2 = new InventoryView();
        v2.setTopInventory(q.opened);
        v2.setBottomInventory(new Inventory());
        v2.setPlayer(q);
        InventoryClickEvent bottom = new InventoryClickEvent(v2, InventoryType.SlotType.QUICKBAR, PathGui.STYLE_SLOTS[0], ClickType.LEFT, InventoryAction.PICKUP_ALL);
        l.onClick(bottom);
        expect(bottom.isCancelled() && gui(q).draft.styles.isEmpty(), "a click in the bottom inventory is cancelled but does nothing");
        // out of range slot
        InventoryClickEvent far = new InventoryClickEvent(v2, InventoryType.SlotType.CONTAINER, 99, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        l.onClick(far);
        expect(far.isCancelled() && gui(q).draft.styles.isEmpty(), "an out-of-range slot does nothing");
        InventoryClickEvent negative = new InventoryClickEvent(v2, InventoryType.SlotType.CONTAINER, -1, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        l.onClick(negative);
        expect(negative.isCancelled() && gui(q).draft.styles.isEmpty(), "slot -1 (outside the window) does nothing");
    }

    static void wrapping() {
        expect(PathGui.wrap("&7short line", 44).equals(List.of("&7short line")), "a short line is untouched");
        expect(PathGui.wrap("", 44).equals(List.of("")), "an empty line stays one empty line");
        String text = "The quick brown fox jumps over the lazy dog and then keeps running across the field";
        List<String> pieces = PathGui.wrap("&7&o" + text, 44);
        expect(pieces.size() == 2, "wrapped into two: " + pieces);
        expect(pieces.stream().allMatch(p -> p.startsWith("&7&o")), "each piece keeps the colour codes");
        expect(pieces.stream().allMatch(p -> p.substring(4).length() <= 44), "no piece is wider than the limit: " + pieces);
        expect(String.join(" ", pieces.stream().map(p -> p.substring(4)).toList()).equals(text), "no words lost or changed");
        expect(PathGui.wrap("&8" + "x".repeat(60), 44).equals(List.of("&8" + "x".repeat(60))), "a single long word is not cut");
        expect(PathGui.wrap("&7" + "a".repeat(44), 44).size() == 1 && PathGui.wrap("&7" + "a".repeat(45) + " b", 44).size() == 2, "the limit itself fits, one more does not");
        expect(PathGui.wrap("plain words " + "w".repeat(40), 44).equals(List.of("plain words", "w".repeat(40))), "lines with no colour code wrap too");
        // end to end: a long blurb shows as several short lore lines
        Env e = new Env();
        Kit.MapSection longer = new Kit.MapSection();
        longer.put("styles.survivor.blurb", text + " " + text);
        e.catalog.load(longer, "custom");
        Kit.TestPlayer p = e.player("Ann");
        PathGui.openStyles(e.service, p, new Choice());
        ItemStack tile = p.opened.getItem(PathGui.STYLE_SLOTS[0]);
        boolean ok = true;
        int lines = 0;
        for (String l : tile.getItemMeta().getLore()) {
            lines++;
            ok &= l.replaceAll("\u00a7.", "").length() <= PathGui.WRAP;
        }
        expect(ok && lines > 6, "the tile's lore lines are all short: " + tile.getItemMeta().getLore());
    }

    static boolean glows(Inventory inv, int slot) {
        return inv.getItem(slot) != null && inv.getItem(slot).getItemMeta().getEnchantmentGlintOverride() == Boolean.TRUE;
    }

    /** Exact wording and edge cases of the tiles, which the behaviour tests above do not pin down. */
    static void details() {
        // exactly one page worth, and one more
        Env e = new Env();
        for (int i = 0; i < 38; i++) {
            e.installed.add("NexusPage" + i);
        }
        expect(e.service.pickable().size() == 45, "set up exactly 45 pickable systems: " + e.service.pickable().size());
        Kit.TestPlayer p = e.player("Ann");
        PathGui.openSystems(e.service, p, new Choice(), 0, false);
        expect(name(p.opened, 48).contains("Page 1 of 1") && p.opened.getItem(49) == null, "45 systems fit one page");
        e.installed.add("NexusPage38");
        PathGui.openSystems(e.service, p, new Choice(), 0, false);
        expect(name(p.opened, 48).contains("Page 1 of 2") && p.opened.getItem(49) != null, "46 systems need two pages");
        expect(lore(p.opened, 49).contains("Page 2 of 2"), "the next button says which page it goes to: " + lore(p.opened, 49));
        PathGui.openSystems(e.service, p, new Choice(), 1, false);
        expect(lore(p.opened, 47).contains("Page 1 of 2"), "the previous button says which page it goes to: " + lore(p.opened, 47));

        // tiles in the style menu
        Env f = new Env();
        Kit.TestPlayer q = f.player("Bob");
        PathGui.openStyles(f.service, q, new Choice());
        int survivor = PathGui.STYLE_SLOTS[0];
        List<String> lore = q.opened.getItem(survivor).getItemMeta().getLore();
        expect(lore.equals(List.of("\u00a77Fight", "", "\u00a7fBrings 2 systems:", "\u00a77Rime, Trials", "", "\u00a7eClick to add", "\u00a78/path info survivor for the full tour")), "the style tile reads exactly: " + lore);
        expect(!lore(q.opened, survivor).contains("..."), "a style whose systems all fit does not end in '...'");
        // more than five systems: five are named, then '...'
        Kit.MapSection six = new Kit.MapSection();
        six.put("styles.big.title", "Big").put("styles.big.systems", List.of("NexusRime", "NexusFarming", "NexusAngler", "NexusRedstone", "NexusTrials", "NexusMenu"));
        f.catalog.load(six, "custom");
        PathGui.openStyles(f.service, q, new Choice());
        String big = null;
        for (int i = 0; i < PathGui.STYLE_SLOTS.length; i++) {
            ItemStack it = q.opened.getItem(PathGui.STYLE_SLOTS[i]);
            if (it != null && it.getItemMeta().getDisplayName().contains("Big")) {
                big = String.join("|", it.getItemMeta().getLore());
            }
        }
        expect(big != null && big.contains("Brings 6 systems") && big.contains("Rime, Farming, Angler, Redstone, Trials, ..."), "five named then '...': " + big);
        // exactly five names: no '...'
        Kit.MapSection five = new Kit.MapSection();
        five.put("styles.big.systems", List.of("NexusRime", "NexusFarming", "NexusAngler", "NexusRedstone", "NexusTrials"));
        f.catalog.load(five, "custom");
        PathGui.openStyles(f.service, q, new Choice());
        for (int i = 0; i < PathGui.STYLE_SLOTS.length; i++) {
            ItemStack it = q.opened.getItem(PathGui.STYLE_SLOTS[i]);
            if (it != null && it.getItemMeta().getDisplayName().contains("Big")) {
                big = String.join("|", it.getItemMeta().getLore());
            }
        }
        expect(big.contains("Trials") && !big.contains("..."), "exactly five names fit without '...': " + big);

        // the always-on shelf and the other fixed buttons
        expect(lore(q.opened, 49).equals("\u00a78Claims|\u00a78Safety, land, money and staff tools are|\u00a78never hidden."), "always-on shelf: " + lore(q.opened, 49));
        expect(lore(q.opened, 40).contains("See every system") && lore(q.opened, 43).contains("Click to keep this") && lore(q.opened, 45).contains("Close without changing"), "button lore");
        // only selections glow: none of the plain buttons do
        for (int slot : new int[] {4, 37, 40, 43, 45, 49}) {
            expect(!glows(q.opened, slot), "slot " + slot + " does not glow when nothing is selected");
        }
        Choice all = new Choice();
        all.all = true;
        PathGui.openStyles(f.service, q, all);
        expect(glows(q.opened, 37), "'everything' glows when selected");

        // system tiles show the blurb
        PathGui.openSystems(f.service, q, new Choice(), 0, false);
        expect(lore(q.opened, 0).startsWith("\u00a77Frozen hordes|"), "a system tile starts with its blurb: " + lore(q.opened, 0));
        for (int slot : new int[] {45, 47, 48, 52, 53}) {
            expect(!glows(q.opened, slot), "systems menu slot " + slot + " does not glow");
        }
        PathGui.openSystems(f.service, q, new Choice(), 0, false);
        // nothing chosen yet: no try-out button, and a chosen player's systems menu in non-try mode stays so after toggling
        Kit.TestPlayer r = f.player("Cy");
        f.choose(r, "survivor");
        PathGui.openSystems(f.service, r, f.service.draftOf(r.getUniqueId()), 0, false);
        gui(r).click(f.service, r, 1);
        expect(!gui(r).tryMode && gui(r).actions.get(1).startsWith("toggle:"), "toggling in normal mode does not switch try-out mode on");
        gui(r).click(f.service, r, 51);
        expect(gui(r).tryMode && gui(r).actions.get(1) != null && gui(r).actions.get(1).startsWith("try:"), "try-out mode on: hidden tiles borrow");

        // 'everything' wipes by-hand changes from the draft
        Choice d = new Choice();
        d.chosen = true;
        d.styles.add("survivor");
        d.shown.add("nexusfarming");
        d.hidden.add("nexusrime");
        PathGui.openStyles(f.service, q, d);
        gui(q).click(f.service, q, 37);
        expect(gui(q).draft.all && gui(q).draft.shown.isEmpty() && gui(q).draft.hidden.isEmpty() && gui(q).draft.styles.isEmpty(), "everything clears styles and by-hand changes");

        // clicking a style drops by-hand changes the styles now say themselves
        Choice t = new Choice();
        t.chosen = true;
        t.styles.add("survivor");
        t.shown.add("nexusfarming");
        PathGui.openStyles(f.service, q, t);
        gui(q).click(f.service, q, PathGui.STYLE_SLOTS[1]);   // farmer: now farming is part of the style
        expect(gui(q).draft.styles.contains("farmer") && gui(q).draft.shown.isEmpty(), "adding a style tidies the by-hand list: " + gui(q).draft.shown);
        // reviewing never starts in try-out mode, even for someone who has chosen
        f.choose(q, "survivor");
        PathGui.openStyles(f.service, q, f.service.draftOf(q.getUniqueId()));
        gui(q).click(f.service, q, 40);
        expect(gui(q).systems && !gui(q).tryMode, "the review page opens in normal mode");
        // navigation buttons do not glow, a system tile's lore has its blank separator
        PathGui.openSystems(f.service, q, new Choice(), 0, false);
        expect(f.service.pickable().size() < PathGui.PER_PAGE, "one page");
        for (int i = 0; i < 70; i++) {
            f.installed.add("NexusNav" + i);
        }
        PathGui.openSystems(f.service, q, new Choice(), 0, false);
        expect(!glows(q.opened, 49), "the next-page button does not glow");
        PathGui.openSystems(f.service, q, new Choice(), 1, false);
        expect(!glows(q.opened, 47), "the previous-page button does not glow");
        expect(q.opened.getItem(0).getItemMeta().getLore().get(1).isEmpty(), "a blank line follows the blurb");

        // icons
        expect(PathGui.material("WHEAT", org.bukkit.Material.BOOK) == org.bukkit.Material.WHEAT, "a real icon is used");
        expect(PathGui.material("AIR", org.bukkit.Material.BOOK) == org.bukkit.Material.BOOK, "air is never an icon");

        // a lone ampersand is not a colour code
        expect(PathGui.wrap("&", 44).equals(List.of("&")) && PathGui.wrap("a&", 44).equals(List.of("a&")), "stray ampersands are left alone");
        // a piece may be exactly as wide as the limit
        List<String> edge = PathGui.wrap("&7" + "a".repeat(20) + " " + "b".repeat(23) + " c", 44);
        expect(edge.size() == 2 && edge.get(0).length() == 2 + 44 && edge.get(1).equals("&7c"), "the first piece is filled to the limit: " + edge);
        // wrapping at the edge
        expect(PathGui.wrap("&7" + "a".repeat(20) + " " + "b".repeat(23), 44).size() == 1, "a line that is exactly the width fits");
        expect(PathGui.wrap("&7" + "a".repeat(20) + " " + "b".repeat(24), 44).size() == 2, "one over the width wraps");
    }

    static int PathPlugin_styleSlot(Kit.TestPlayer p) {
        return slotOf(gui(p), "style:survivor");
    }
}
