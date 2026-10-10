package com.nexuscraft.nexuspath;

import java.util.List;

import static com.nexuscraft.nexuspath.Kit.expect;

public final class CatalogTest {

    public static void main(String[] args) {
        Env e = new Env();
        Catalog c = e.catalog;

        // basics
        expect(c.systems().size() == 7, "seven systems loaded");
        expect(c.styles().size() == 3, "three styles loaded");
        expect(c.system("nexusrime") != null && c.system("NEXUSRIME") == c.system("NexusRime"), "ids ignore case");
        expect(c.system("NexusRime").name().equals("Rime"), "name read");
        expect(c.system("NexusRime").icon().equals("BLUE_ICE"), "icon read");
        expect(c.system("NexusClaims").locked(), "locked read");
        expect(!c.system("NexusRime").locked(), "not locked by default");
        expect(c.system("NexusRedstone").world(), "world read");
        expect(!c.system("NexusTrials").events(), "events false read");
        expect(c.system("NexusMenu").byDefault(), "default read");
        expect(c.system("nothing") == null && c.system(null) == null, "unknown system is null");
        expect(Catalog.key("  AbC ").equals("abc") && Catalog.key(null).isEmpty(), "key trims and lowercases");

        // systemFor: unknown plugins get a plain unlisted entry
        SystemInfo u = c.systemFor("NexusSomething");
        expect(u.id().equals("NexusSomething") && !u.locked() && !u.world() && u.events() && !u.byDefault() && u.icon().equals("PAPER"), "unlisted system defaults");
        expect(c.systemFor("NexusRime") == c.system("NexusRime"), "systemFor returns the listed one");

        // assignment and style lookups
        expect(c.isAssigned("nexusrime") && c.isAssigned("NexusFarming"), "systems in styles are assigned");
        expect(!c.isAssigned("NexusMenu") && !c.isAssigned("NexusClaims"), "systems in no style are unassigned");
        expect(c.stylesOf("NexusFarming").size() == 2, "farming is in two styles");
        expect(c.stylesOf("NexusRime").size() == 1 && c.stylesOf("NexusRime").get(0).id().equals("survivor"), "rime is in survivor");
        expect(c.stylesOf("NexusMenu").isEmpty(), "menu is in no style");
        expect(c.style("FARMER").title().equals("Farmer"), "style lookup ignores case");

        // finish warns about locked and unknown systems named by a style
        expect(c.warnings().isEmpty(), "sample catalog has no warnings: " + c.warnings());
        Kit.MapSection bad = new Kit.MapSection();
        bad.put("styles.odd.systems", List.of("NexusClaims", "NexusGhost"));
        c.load(bad, "custom");
        c.finish();
        expect(c.warnings().size() == 2, "two warnings for locked and unknown: " + c.warnings());
        expect(c.warnings().stream().anyMatch(w -> w.contains("locked")), "locked warning");
        expect(c.warnings().stream().anyMatch(w -> w.contains("not in the catalog")), "unknown warning");
        expect(c.isAssigned("NexusGhost"), "unknown names still count as assigned");

        // override field by field, keeping the rest
        Env e2 = new Env();
        Kit.MapSection over = new Kit.MapSection();
        over.put("systems.nexusrime.blurb", "Cold").put("systems.nexusrime.locked", true);
        e2.catalog.load(over, "custom");
        SystemInfo r = e2.catalog.system("NexusRime");
        expect(r.blurb().equals("Cold") && r.locked(), "override changed blurb and locked");
        expect(r.name().equals("Rime") && r.icon().equals("BLUE_ICE") && r.id().equals("NexusRime"), "override kept name, icon and original id");
        expect(!r.world() && r.events() && !r.byDefault(), "override kept untouched flags");
        expect(e2.catalog.systems().size() == 7, "override did not add a system");

        // a new system
        Kit.MapSection add = new Kit.MapSection();
        add.put("systems.NexusNew.name", "New").put("systems.NexusNew.default", true);
        e2.catalog.load(add, "custom");
        SystemInfo n = e2.catalog.system("nexusnew");
        expect(n != null && n.id().equals("NexusNew") && n.name().equals("New") && n.byDefault(), "new system added");
        expect(n.icon().equals("PAPER") && n.blurb().isEmpty() && !n.locked() && !n.world() && n.events(), "new system defaults");
        Kit.MapSection nameless = new Kit.MapSection();
        nameless.put("systems.NexusBare.blurb", "x");
        e2.catalog.load(nameless, "custom");
        expect(e2.catalog.system("NexusBare").name().equals("NexusBare"), "name defaults to the id");

        // hidden systems and styles
        Kit.MapSection hide = new Kit.MapSection();
        hide.put("hidden-systems", List.of("NEXUSANGLER")).put("hidden-styles", List.of("Builder"));
        e2.catalog.load(hide, "custom");
        expect(e2.catalog.system("NexusAngler") == null, "hidden system removed");
        expect(e2.catalog.style("builder") == null && e2.catalog.styles().size() == 2, "hidden style removed");

        // style editing: replace, add, remove
        Env e3 = new Env();
        Kit.MapSection st = new Kit.MapSection();
        st.put("styles.survivor.add-systems", List.of("NexusFarming", "nexusrime")).put("styles.survivor.title", "Fighter");
        e3.catalog.load(st, "custom");
        StyleInfo s = e3.catalog.style("survivor");
        expect(s.title().equals("Fighter") && s.icon().equals("IRON_SWORD") && s.blurb().equals("Fight"), "style fields override one by one");
        expect(s.systems().equals(List.of("NexusRime", "NexusTrials", "NexusFarming")), "add-systems appends and skips existing: " + s.systems());
        Kit.MapSection rm = new Kit.MapSection();
        rm.put("styles.survivor.remove-systems", List.of("NEXUSRIME"));
        e3.catalog.load(rm, "custom");
        expect(e3.catalog.style("survivor").systems().equals(List.of("NexusTrials", "NexusFarming")), "remove-systems removes without case");
        Kit.MapSection rep = new Kit.MapSection();
        rep.put("styles.survivor.systems", List.of("NexusAngler")).put("styles.survivor.add-systems", List.of("NexusMenu"))
                .put("styles.survivor.remove-systems", List.of("NexusAngler"));
        e3.catalog.load(rep, "custom");
        expect(e3.catalog.style("survivor").systems().equals(List.of("NexusMenu")), "systems replaces, then add, then remove");
        Kit.MapSection fresh = new Kit.MapSection();
        fresh.put("styles.explorer.systems", List.of("NexusRime"));
        e3.catalog.load(fresh, "custom");
        StyleInfo x = e3.catalog.style("explorer");
        expect(x.id().equals("explorer") && x.title().equals("explorer") && x.icon().equals("BOOK") && x.blurb().isEmpty(), "new style defaults");

        // a non-section entry is skipped with a warning
        Kit.MapSection broken = new Kit.MapSection();
        broken.put("systems.NexusRime", "oops").put("styles.survivor", "oops");
        Env e4 = new Env();
        e4.catalog.load(broken, "custom");
        expect(e4.catalog.warnings().size() == 2, "two skipped entries warn: " + e4.catalog.warnings());
        expect(e4.catalog.system("NexusRime").name().equals("Rime"), "skipped entry changes nothing");

        // finish() works the assignments out afresh each time
        Env e5 = new Env();
        expect(e5.catalog.isAssigned("NexusRime"), "rime is assigned before");
        Kit.MapSection gone = new Kit.MapSection();
        gone.put("hidden-styles", List.of("survivor"));
        e5.catalog.load(gone, "custom");
        e5.catalog.finish();
        expect(!e5.catalog.isAssigned("NexusRime") && !e5.catalog.isAssigned("NexusTrials") && e5.catalog.isAssigned("NexusFarming"), "after a style is hidden its systems are no longer assigned");

        // clear
        e4.catalog.clear();
        expect(e4.catalog.systems().isEmpty() && e4.catalog.styles().isEmpty() && e4.catalog.warnings().isEmpty() && !e4.catalog.isAssigned("nexusrime"), "clear empties everything");

        // an empty root loads nothing and breaks nothing
        Catalog empty = new Catalog();
        empty.load(new Kit.MapSection(), "empty");
        empty.finish();
        expect(empty.systems().isEmpty() && empty.styles().isEmpty() && empty.warnings().isEmpty(), "empty root is fine");

        Kit.finish();
    }
}
