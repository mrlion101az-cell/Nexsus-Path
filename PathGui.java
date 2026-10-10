package com.nexuscraft.nexuspath;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The two menus: pick your play styles, then look over (and adjust by hand) which systems are in your
 * game. Plain chest menus, so they work the same for Java and Bedrock players. Nothing is saved until
 * the player presses Save; closing the menu any other way changes nothing.
 */
final class PathGui implements InventoryHolder {

    static final int SIZE = 54;
    static final int PER_PAGE = 45;
    static final int[] STYLE_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    final Choice draft;
    final boolean systems;
    final int page;
    final boolean tryMode;
    final Map<Integer, String> actions = new HashMap<>();
    private Inventory inventory;

    private PathGui(Choice draft, boolean systems, int page, boolean tryMode) {
        this.draft = draft;
        this.systems = systems;
        this.page = page;
        this.tryMode = tryMode;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    // ------------------------------------------------------------------ the style menu

    static void openStyles(PathService svc, Player p, Choice draft) {
        PathGui gui = new PathGui(draft, false, 0, false);
        gui.inventory = Bukkit.createInventory(gui, SIZE, ColorText.translate("&b&lChoose how you play"));
        Catalog cat = svc.catalog();
        gui.put(4, button(Material.WRITABLE_BOOK, "&e&lHow this works", List.of(
                "&7Pick one style or mix a few.",
                "&7The server then keeps the rest out of your way:",
                "&7their commands, menus and effects stay hidden.",
                "&7Change it any time with &f/path&7.")), null, false);
        List<StyleInfo> styles = new ArrayList<>(cat.styles());
        for (int i = 0; i < styles.size() && i < STYLE_SLOTS.length; i++) {
            StyleInfo st = styles.get(i);
            boolean on = draft.styles.contains(Catalog.key(st.id()));
            int count = 0;
            List<String> names = new ArrayList<>();
            for (SystemInfo s : svc.pickable()) {
                for (String id : st.systems()) {
                    if (Catalog.key(id).equals(Catalog.key(s.id()))) {
                        count++;
                        if (names.size() < 5) {
                            names.add(s.name());
                        }
                    }
                }
            }
            List<String> lore = new ArrayList<>();
            lore.add("&7" + st.blurb());
            lore.add("");
            lore.add("&fBrings " + count + " systems:");
            lore.add("&7" + String.join(", ", names) + (count > names.size() ? ", ..." : ""));
            lore.add("");
            lore.add(on ? "&aSelected. &eClick to remove" : "&eClick to add");
            lore.add("&8/path info " + st.id() + " for the full tour");
            gui.put(STYLE_SLOTS[i], button(material(st.icon(), Material.BOOK), (on ? "&a&l\u2714 " : "&f&l") + st.title(), lore), "style:" + st.id(), on);
        }
        gui.put(37, button(Material.NETHER_STAR, (draft.all ? "&a&l\u2714 " : "&f&l") + "Everything",
                List.of("&7Keep every system. Nothing is hidden.", "", draft.all ? "&aSelected" : "&eClick to choose")), "all", draft.all);
        gui.put(40, button(Material.COMPARATOR, "&e&lReview and fine-tune",
                List.of("&7See every system and switch", "&7single ones on or off.", "", "&eClick to open")), "systems", false);
        gui.put(43, button(Material.LIME_DYE, "&a&lSave and play",
                List.of("&7" + describe(svc, p, draft), "", "&eClick to keep this")), "save", false);
        List<String> always = new ArrayList<>();
        for (SystemInfo s : svc.alwaysOn()) {
            always.add(s.name());
        }
        gui.put(49, button(Material.SHIELD, "&7Always on for everyone",
                List.of("&8" + (always.isEmpty() ? "nothing" : String.join(", ", always)), "&8Safety, land, money and staff tools are never hidden.")), null, false);
        gui.put(45, button(Material.BARRIER, "&c&lCancel", List.of("&7Close without changing anything.")), "close", false);
        p.openInventory(gui.inventory);
    }

    // ------------------------------------------------------------------ the systems menu

    static void openSystems(PathService svc, Player p, Choice draft, int page, boolean tryMode) {
        List<SystemInfo> all = svc.pickable();
        int pages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
        int pg = Math.max(0, Math.min(pages - 1, page));
        boolean canTry = tryMode && svc.store().peek(p.getUniqueId()) != null && svc.store().peek(p.getUniqueId()).chosen;
        PathGui gui = new PathGui(draft, true, pg, canTry);
        gui.inventory = Bukkit.createInventory(gui, SIZE, ColorText.translate("&b&lYour game"));
        Catalog cat = svc.catalog();
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = pg * PER_PAGE + i;
            if (idx >= all.size()) {
                break;
            }
            SystemInfo s = all.get(idx);
            // in try-out mode the tiles show the live game (borrowed systems count as in); otherwise the draft
            boolean on = canTry ? !svc.isHidden(p.getUniqueId(), s.id()) : draft.visible(cat, svc.cfg().unassignedVisible, s);
            int borrowed = canTry ? svc.borrowedMinutes(p.getUniqueId(), s.id()) : 0;
            List<String> lore = new ArrayList<>();
            lore.add("&7" + s.blurb());
            lore.add("");
            List<String> in = new ArrayList<>();
            for (StyleInfo st : cat.stylesOf(s.id())) {
                in.add(st.title());
            }
            lore.add(in.isEmpty() ? "&8In no style" : "&8In: " + String.join(", ", in));
            if (s.world()) {
                lore.add("&8Shared with everyone: hiding it only stops what you do yourself.");
            }
            lore.add(on ? "&aIn your game" : "&cNot in your game");
            lore.add(canTry ? (borrowed > 0 ? "&eBorrowed: " + borrowed + " min left" : on ? "&7(try-out mode: nothing to borrow)" : "&eClick to borrow for " + svc.cfg().tryMinutes + " minutes")
                    : on ? "&eClick to hide" : "&eClick to add");
            String action = canTry ? (on ? null : "try:" + s.id()) : "toggle:" + s.id();
            gui.put(i, button(material(s.icon(), Material.PAPER), (on ? "&a&l\u2714 " : "&c&l\u2718 ") + s.name(), lore), action, on);
        }
        gui.put(45, button(Material.ARROW, "&e\u25c0 Back to styles", List.of()), "styles", false);
        if (pg > 0) {
            gui.put(47, button(Material.SPECTRAL_ARROW, "&ePrevious page", List.of("&7Page " + pg + " of " + pages)), "page:" + (pg - 1), false);
        }
        gui.put(48, button(Material.PAPER, "&7Page " + (pg + 1) + " of " + pages, List.of()), null, false);
        if (pg < pages - 1) {
            gui.put(49, button(Material.SPECTRAL_ARROW, "&eNext page", List.of("&7Page " + (pg + 2) + " of " + pages)), "page:" + (pg + 1), false);
        }
        boolean chosen = svc.store().peek(p.getUniqueId()) != null && svc.store().peek(p.getUniqueId()).chosen;
        if (chosen) {
            gui.put(51, button(Material.CLOCK, canTry ? "&a&lTry-out mode: ON" : "&7Try-out mode: off",
                    List.of("&7When on, clicking a hidden system", "&7borrows it for " + svc.cfg().tryMinutes + " minutes", "&7instead of adding it.", "", "&eClick to switch")),
                    "mode", canTry);
        }
        gui.put(52, button(Material.LIME_DYE, "&a&lSave and play", List.of("&7" + describe(svc, p, draft))), "save", false);
        gui.put(53, button(Material.BARRIER, "&c&lCancel", List.of()), "close", false);
        p.openInventory(gui.inventory);
    }

    private static String describe(PathService svc, Player p, Choice draft) {
        if (draft.all) {
            return "Everything, nothing hidden";
        }
        Map<String, String> m = svc.summaryFor(p, draft);
        return m.get("styles") + ": " + m.get("shown") + " on, " + m.get("hidden") + " hidden";
    }

    // ------------------------------------------------------------------ clicking

    /** Does what a click on this slot asks for. Returns false if the slot does nothing. */
    boolean click(PathService svc, Player p, int slot) {
        String action = actions.get(slot);
        if (action == null) {
            return false;
        }
        Catalog cat = svc.catalog();
        if (action.equals("close")) {
            p.closeInventory();
        } else if (action.equals("save")) {
            p.closeInventory();
            svc.commit(p, draft);
        } else if (action.equals("styles")) {
            openStyles(svc, p, draft);
        } else if (action.equals("systems")) {
            openSystems(svc, p, draft, 0, false);
        } else if (action.equals("all")) {
            draft.all = true;
            draft.styles.clear();
            draft.shown.clear();
            draft.hidden.clear();
            openStyles(svc, p, draft);
        } else if (action.equals("mode")) {
            openSystems(svc, p, draft, page, !tryMode);
        } else if (action.startsWith("style:")) {
            draft.toggleStyle(action.substring(6));
            draft.tidy(cat, svc.cfg().unassignedVisible);
            openStyles(svc, p, draft);
        } else if (action.startsWith("toggle:")) {
            SystemInfo s = cat.systemFor(action.substring(7));
            draft.setVisible(cat, svc.cfg().unassignedVisible, s, !draft.visible(cat, svc.cfg().unassignedVisible, s));
            openSystems(svc, p, draft, page, false);
        } else if (action.startsWith("try:")) {
            PathService.TryResult r = svc.tryIt(p, action.substring(4));
            if (!r.ok()) {
                svc.say(p, svc.fill(svc.cfg().msgTryNo, null, Map.of("why", r.why())));
            }
            openSystems(svc, p, draft, page, true);
        } else if (action.startsWith("page:")) {
            try {
                openSystems(svc, p, draft, Integer.parseInt(action.substring(5)), tryMode);
            } catch (NumberFormatException bad) {
                return false;
            }
        } else {
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ small helpers

    private void put(int slot, ItemStack item, String action, boolean glint) {
        inventory.setItem(slot, item);
        if (action != null) {
            actions.put(slot, action);
        }
        if (glint) {
            ItemMeta meta = item.getItemMeta();
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
            item.setItemMeta(meta);
        }
    }

    private static ItemStack button(Material m, String name, List<String> lore) {
        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorText.translate(name));
        List<String> out = new ArrayList<>();
        for (String s : lore) {
            for (String piece : wrap(s, WRAP)) {
                out.add(ColorText.translate(piece));
            }
        }
        meta.setLore(out);
        item.setItemMeta(meta);
        return item;
    }

    /** Visible characters per lore line before it is wrapped. */
    static final int WRAP = 44;

    /** Splits a long lore line at spaces; every piece keeps the line's leading colour codes. */
    static List<String> wrap(String line, int width) {
        int start = 0;
        while (start + 1 < line.length() && line.charAt(start) == '&') {
            start += 2;
        }
        String codes = line.substring(0, start);
        String text = line.substring(start);
        List<String> out = new ArrayList<>();
        if (text.length() <= width) {
            out.add(line);
            return out;
        }
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            if (cur.length() > 0 && cur.length() + 1 + word.length() > width) {
                out.add(codes + cur);
                cur.setLength(0);
            }
            if (cur.length() > 0) {
                cur.append(' ');
            }
            cur.append(word);
        }
        if (cur.length() > 0) {
            out.add(codes + cur);
        }
        return out;
    }

    static Material material(String name, Material fallback) {
        Material m = name == null ? null : Material.matchMaterial(name);
        return m == null || m == Material.AIR ? fallback : m;
    }
}
