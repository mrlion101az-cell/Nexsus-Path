package com.nexuscraft.nexuspath;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * What one player picked: some styles, plus the systems they switched on or off by hand on top.
 * A player who has not picked yet sees everything.
 */
final class Choice {

    boolean chosen;
    /** Picked "everything": nothing is hidden. */
    boolean all;
    final Set<String> styles = new LinkedHashSet<>();
    /** Systems switched on by hand although no picked style includes them. */
    final Set<String> shown = new LinkedHashSet<>();
    /** Systems switched off by hand although a picked style includes them. */
    final Set<String> hidden = new LinkedHashSet<>();
    /** How many times they have been reminded to choose. */
    int reminders;

    Choice copy() {
        Choice c = new Choice();
        c.chosen = chosen;
        c.all = all;
        c.styles.addAll(styles);
        c.shown.addAll(shown);
        c.hidden.addAll(hidden);
        c.reminders = reminders;
        return c;
    }

    boolean isBlank() {
        return !chosen && !all && styles.isEmpty() && shown.isEmpty() && hidden.isEmpty() && reminders == 0;
    }

    /** Does this player have the system in their game? */
    boolean visible(Catalog cat, boolean unassignedVisible, SystemInfo s) {
        if (s.locked() || !chosen || all) {
            return true;
        }
        String k = Catalog.key(s.id());
        if (hidden.contains(k)) {
            return false;
        }
        return shown.contains(k) || baseline(cat, unassignedVisible, s);
    }

    /** Would the system be there from the picked styles alone, with no hand-made exceptions? */
    boolean baseline(Catalog cat, boolean unassignedVisible, SystemInfo s) {
        if (s.byDefault()) {
            return true;
        }
        String k = Catalog.key(s.id());
        for (String style : styles) {
            StyleInfo st = cat.style(style);
            if (st != null) {
                for (String sys : st.systems()) {
                    if (Catalog.key(sys).equals(k)) {
                        return true;
                    }
                }
            }
        }
        return !cat.isAssigned(k) && unassignedVisible;
    }

    /** Switches one system on or off by hand (never a locked one). */
    void setVisible(Catalog cat, boolean unassignedVisible, SystemInfo s, boolean want) {
        if (s.locked()) {
            return;
        }
        if (all) {
            // "everything" becomes a list: every system stays on, except what is switched off now
            all = false;
            for (SystemInfo each : cat.systems()) {
                if (!each.locked()) {
                    shown.add(Catalog.key(each.id()));
                }
            }
        }
        String k = Catalog.key(s.id());
        if (want) {
            hidden.remove(k);
            if (!baseline(cat, unassignedVisible, s)) {
                shown.add(k);
            }
        } else {
            shown.remove(k);
            if (baseline(cat, unassignedVisible, s)) {
                hidden.add(k);
            }
        }
    }

    /** Drops hand-made exceptions that the picked styles already say. */
    void tidy(Catalog cat, boolean unassignedVisible) {
        shown.removeIf(k -> {
            SystemInfo s = cat.system(k);
            return s != null && baseline(cat, unassignedVisible, s);
        });
        hidden.removeIf(k -> {
            SystemInfo s = cat.system(k);
            return s != null && !baseline(cat, unassignedVisible, s);
        });
    }

    void toggleStyle(String id) {
        String k = Catalog.key(id);
        if (!styles.remove(k)) {
            styles.add(k);
        }
        all = false;
    }
}
