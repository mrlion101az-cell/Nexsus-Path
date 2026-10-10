package com.nexuscraft.nexuspath;

import static com.nexuscraft.nexuspath.Kit.expect;

public final class ChoiceTest {

    public static void main(String[] args) {
        Env e = new Env();
        Catalog cat = e.catalog;
        SystemInfo rime = cat.system("NexusRime");
        SystemInfo farming = cat.system("NexusFarming");
        SystemInfo angler = cat.system("NexusAngler");
        SystemInfo claims = cat.system("NexusClaims");
        SystemInfo menu = cat.system("NexusMenu");
        SystemInfo ghost = cat.systemFor("NexusUnlisted");

        // nothing chosen: everything shows
        Choice c = new Choice();
        expect(c.isBlank(), "a new choice is blank");
        expect(c.visible(cat, true, rime) && c.visible(cat, false, rime), "unchosen sees everything");

        // chosen styles
        c.chosen = true;
        c.styles.add("survivor");
        expect(!c.isBlank(), "chosen is not blank");
        expect(c.visible(cat, true, rime), "style system visible");
        expect(!c.visible(cat, true, farming), "other style system hidden");
        expect(c.visible(cat, true, claims), "locked is always visible");
        expect(c.visible(cat, true, menu), "default-on system is visible");
        expect(c.visible(cat, true, ghost) && !c.visible(cat, false, ghost), "unassigned follows the unassigned policy");
        expect(!c.visible(cat, true, angler), "assigned to another style stays hidden even if unassigned is shown");

        // "everything"
        Choice all = new Choice();
        all.chosen = true;
        all.all = true;
        expect(all.visible(cat, false, farming) && all.visible(cat, false, ghost), "all sees everything");

        // hand-made exceptions
        Choice h = new Choice();
        h.chosen = true;
        h.styles.add("survivor");
        h.shown.add("nexusfarming");
        h.hidden.add("nexusrime");
        expect(h.visible(cat, true, farming), "shown exception makes it visible");
        expect(!h.visible(cat, true, rime), "hidden exception hides it");
        h.hidden.add("nexusclaims");
        expect(h.visible(cat, true, claims), "locked ignores hidden");
        h.hidden.add("nexusmenu");
        expect(!h.visible(cat, true, menu), "a hand-made hide beats default-on");

        // baseline ignores exceptions
        expect(h.baseline(cat, true, rime) && !h.baseline(cat, true, farming), "baseline is the styles alone");
        expect(h.baseline(cat, false, menu), "default-on is baseline");

        // setVisible both ways
        Choice s = new Choice();
        s.chosen = true;
        s.styles.add("survivor");
        s.setVisible(cat, true, farming, true);
        expect(s.shown.contains("nexusfarming") && s.visible(cat, true, farming), "adding an outside system records it");
        s.setVisible(cat, true, farming, false);
        expect(s.shown.isEmpty() && s.hidden.isEmpty() && !s.visible(cat, true, farming), "removing it again leaves no trace");
        s.setVisible(cat, true, rime, false);
        expect(s.hidden.contains("nexusrime") && !s.visible(cat, true, rime), "hiding a style system records it");
        s.setVisible(cat, true, rime, true);
        expect(s.hidden.isEmpty() && s.shown.isEmpty() && s.visible(cat, true, rime), "showing it again leaves no trace");
        s.setVisible(cat, true, rime, true);
        expect(s.shown.isEmpty(), "showing what is already in baseline records nothing");
        s.setVisible(cat, true, farming, false);
        expect(s.hidden.isEmpty() && s.shown.isEmpty(), "hiding what is already out records nothing");
        s.setVisible(cat, true, claims, false);
        expect(s.hidden.isEmpty() && s.visible(cat, true, claims), "locked cannot be switched");
        s.setVisible(cat, true, claims, true);
        expect(s.shown.isEmpty(), "locked cannot be recorded either");

        // all becomes a list when something is switched off
        Choice a = new Choice();
        a.chosen = true;
        a.all = true;
        a.setVisible(cat, true, farming, false);
        expect(!a.all, "switching one off ends 'all'");
        expect(!a.visible(cat, true, farming), "that one is now hidden");
        expect(a.visible(cat, true, rime) && a.visible(cat, true, angler) && a.visible(cat, true, menu), "everything else stays on");
        expect(!a.shown.contains("nexusclaims"), "locked systems are not listed");
        expect(a.shown.contains("nexusrime") && a.shown.contains("nexusangler"), "the rest are recorded as shown");
        Choice a2 = new Choice();
        a2.chosen = true;
        a2.all = true;
        a2.setVisible(cat, true, farming, true);
        expect(!a2.all && a2.visible(cat, true, farming) && a2.visible(cat, true, rime), "switching one on also turns 'all' into a list, still seeing everything");

        // tidy drops exceptions the styles already say
        Choice t = new Choice();
        t.chosen = true;
        t.styles.add("survivor");
        t.shown.add("nexusrime");      // already in baseline
        t.shown.add("nexusfarming");   // a real exception
        t.hidden.add("nexusfarming");  // already out of baseline
        t.hidden.add("nexustrials");   // a real exception
        t.shown.add("nexusghost");     // not in catalog: kept
        t.tidy(cat, true);
        expect(!t.shown.contains("nexusrime"), "redundant shown dropped");
        expect(t.shown.contains("nexusfarming"), "real shown kept");
        expect(!t.hidden.contains("nexusfarming"), "redundant hidden dropped");
        expect(t.hidden.contains("nexustrials"), "real hidden kept");
        expect(t.shown.contains("nexusghost"), "unknown ids are left alone");

        // toggleStyle
        Choice g = new Choice();
        g.all = true;
        g.toggleStyle("Farmer");
        expect(g.styles.contains("farmer") && !g.all, "toggling on adds the style and ends 'all'");
        g.toggleStyle("FARMER");
        expect(g.styles.isEmpty(), "toggling again removes it");

        // copy is deep
        Choice orig = new Choice();
        orig.chosen = true;
        orig.all = true;
        orig.reminders = 2;
        orig.styles.add("a");
        orig.shown.add("b");
        orig.hidden.add("c");
        Choice cp = orig.copy();
        cp.styles.add("z");
        cp.shown.clear();
        cp.hidden.add("y");
        cp.reminders = 9;
        cp.chosen = false;
        cp.all = false;
        expect(orig.styles.size() == 1 && orig.shown.size() == 1 && orig.hidden.size() == 1 && orig.reminders == 2 && orig.chosen && orig.all, "changing a copy leaves the original");
        Choice cp2 = orig.copy();
        expect(cp2.chosen && cp2.all && cp2.reminders == 2 && cp2.styles.contains("a") && cp2.shown.contains("b") && cp2.hidden.contains("c"), "a copy carries everything");

        // blank detection per field
        Choice b1 = new Choice();
        b1.reminders = 1;
        expect(!b1.isBlank(), "reminders alone is not blank");
        Choice b2 = new Choice();
        b2.all = true;
        expect(!b2.isBlank(), "all alone is not blank");
        Choice b3 = new Choice();
        b3.styles.add("x");
        expect(!b3.isBlank(), "styles alone is not blank");
        Choice b4 = new Choice();
        b4.shown.add("x");
        expect(!b4.isBlank(), "shown alone is not blank");
        Choice b5 = new Choice();
        b5.hidden.add("x");
        expect(!b5.isBlank(), "hidden alone is not blank");

        Kit.finish();
    }
}
