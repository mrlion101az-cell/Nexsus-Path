package com.nexuscraft.nexuspath;

import org.bukkit.NamespacedKey;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.nexuscraft.nexuspath.Kit.expect;

/** Saving and loading choices, publishing them to other plugins, and the config. */
public final class StoreTest {

    public static void main(String[] args) throws Exception {
        store();
        exporter();
        config();
        Kit.finish();
    }

    static void store() throws Exception {
        File dir = Files.createTempDirectory("nexuspath-test").toFile();
        File file = new File(dir, "sub/players.yml");
        List<String> warnings = new ArrayList<>();
        PathStore s = new PathStore(file, warnings::add);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID blank = UUID.randomUUID();
        Choice ca = new Choice();
        ca.chosen = true;
        ca.styles.add("survivor");
        ca.styles.add("farmer");
        ca.shown.add("nexusangler");
        ca.hidden.add("nexusrime");
        ca.reminders = 2;
        s.put(a, ca);
        Choice cb = new Choice();
        cb.chosen = true;
        cb.all = true;
        s.put(b, cb);
        s.put(blank, new Choice());
        expect(s.dirty() && s.size() == 3, "puts mark the store dirty");
        s.flush();
        expect(!s.dirty() && file.isFile(), "flush writes the file (and makes the folder)");

        PathStore t = new PathStore(file, warnings::add);
        t.load();
        expect(t.size() == 2, "blank records are not written: " + t.size());
        Choice ra = t.peek(a);
        expect(ra != null && ra.chosen && !ra.all && ra.reminders == 2, "flags and reminders round trip");
        expect(new ArrayList<>(ra.styles).equals(List.of("survivor", "farmer")), "styles keep their order: " + ra.styles);
        expect(ra.shown.equals(Set.of("nexusangler")) && ra.hidden.equals(Set.of("nexusrime")), "exceptions round trip");
        expect(t.peek(b).all && t.peek(b).chosen && t.peek(b).styles.isEmpty(), "everything round trips");
        expect(t.peek(blank) == null, "a blank record is gone");
        expect(!t.dirty(), "loading is not a change");

        // saving does nothing when nothing changed
        long stamp = file.lastModified();
        file.setLastModified(stamp - 100_000);
        long old = file.lastModified();
        t.flush();
        expect(file.lastModified() == old, "a clean store does not write");
        t.markDirty();
        t.flush();
        expect(file.lastModified() != old, "a dirty store writes");

        // of() creates, forget removes
        UUID c = UUID.randomUUID();
        Choice created = t.of(c);
        expect(created != null && t.of(c) == created && t.peek(c) == created, "of creates once");
        t.forget(c);
        expect(t.peek(c) == null && t.dirty(), "forget removes and marks dirty");
        t.flush();
        t.forget(c);
        expect(!t.dirty(), "forgetting someone unknown is not a change");

        // load replaces what was in memory, and clears dirty
        t.put(UUID.randomUUID(), ca.copy());
        t.load();
        expect(t.size() == 2 && !t.dirty(), "load starts over");

        // bad ids are skipped with a warning, good ones survive
        File bad = new File(dir, "bad.yml");
        Files.write(bad.toPath(), List.of("players.not-a-uuid.chosen\tb\ttrue", "players." + a + ".chosen\tb\ttrue", "players." + a + ".styles\tl\tfarmer"));
        PathStore u = new PathStore(bad, warnings::add);
        u.load();
        expect(u.size() == 1 && u.peek(a).styles.contains("farmer") && warnings.size() == 1 && warnings.get(0).contains("not-a-uuid"), "a bad id is skipped with a warning: " + warnings);
        expect(!u.peek(a).all, "a record without 'all' is not 'everything'");
        Files.write(bad.toPath(), List.of("players." + a + ".styles\tl\tfarmer"));
        u.load();
        expect(!u.peek(a).chosen && !u.peek(a).all && u.peek(a).reminders == 0, "missing flags default to off");
        // keys are lower-cased on the way in
        Files.write(bad.toPath(), List.of("players." + b + ".chosen\tb\ttrue", "players." + b + ".shown\tl\tNexusRime"));
        u.load();
        expect(u.peek(b).shown.contains("nexusrime"), "ids are normalised when read");

        // a missing file, and a store with no file at all
        PathStore none = new PathStore(new File(dir, "missing.yml"), warnings::add);
        none.load();
        expect(none.size() == 0, "a missing file is an empty store");
        PathStore mem = new PathStore(null, warnings::add);
        mem.load();
        mem.put(a, ca);
        mem.flush();
        expect(mem.size() == 1, "a store without a file works in memory and flush does nothing");

        // a save that cannot happen warns instead of throwing
        File blocked = new File(dir, "blocked");
        Files.write(blocked.toPath(), List.of("x"));
        PathStore broken = new PathStore(new File(blocked, "players.yml"), warnings::add);
        broken.put(a, ca);
        warnings.clear();
        broken.flush();
        expect(warnings.size() == 1 && warnings.get(0).contains("Could not save") && broken.dirty(), "a failed save warns and stays dirty: " + warnings);
    }

    static void exporter() {
        Env e = new Env();
        PathConfig cfg = new PathConfig();
        Kit.TestPlugin plugin = new Kit.TestPlugin();
        PathExporter x = new PathExporter(plugin, cfg);
        Kit.TestPlayer p = e.player("Ann");
        NamespacedKey key = new NamespacedKey(plugin, "hidden");

        x.publish(p, Set.of("nexusfarming", "nexusangler"));
        expect(p.stubAttachments().size() == 1, "one attachment");
        PermissionAttachment a = p.stubAttachments().get(0);
        expect(a.getPermissions().equals(java.util.Map.of("nexuspath.hidden.nexusfarming", true, "nexuspath.hidden.nexusangler", true)), "a true permission per hidden plugin: " + a.getPermissions());
        expect("nexusangler,nexusfarming".equals(p.getPersistentDataContainer().get(key, PersistentDataType.STRING)), "metadata is sorted and comma separated");

        x.publish(p, Set.of("nexusrime"));
        expect(p.stubAttachments().size() == 1 && p.stubAttachments().get(0).getPermissions().equals(java.util.Map.of("nexuspath.hidden.nexusrime", true)), "publishing again replaces the attachment");
        expect(p.stubAttachments().get(0) != a, "the old attachment is gone");
        expect("nexusrime".equals(p.getPersistentDataContainer().get(key, PersistentDataType.STRING)), "metadata replaced");

        x.publish(p, Set.of());
        expect(p.stubAttachments().isEmpty(), "publishing nothing removes the attachment");
        expect(!p.getPersistentDataContainer().has(key, PersistentDataType.STRING), "and the metadata");

        x.publish(p, Set.of("nexusrime"));
        x.forget(p);
        expect(p.stubAttachments().isEmpty(), "forget removes the attachment");
        expect(p.getPersistentDataContainer().has(key, PersistentDataType.STRING), "forget leaves the saved metadata (it is only overwritten on the next publish)");
        x.forget(p);
        expect(p.stubAttachments().isEmpty(), "forgetting twice is harmless");

        // switches
        cfg.exportPermissions = false;
        Kit.TestPlayer q = e.player("Bob");
        x.publish(q, Set.of("nexusrime"));
        expect(q.stubAttachments().isEmpty() && q.getPersistentDataContainer().has(key, PersistentDataType.STRING), "permissions off, metadata on");
        cfg.exportPermissions = true;
        cfg.exportMetadata = false;
        Kit.TestPlayer r = e.player("Cy");
        x.publish(r, Set.of("nexusrime"));
        expect(r.stubAttachments().size() == 1 && !r.getPersistentDataContainer().has(key, PersistentDataType.STRING), "permissions on, metadata off");

        // through the service
        Env f = new Env();
        PathConfig fc = f.cfg;
        Kit.TestPlayer s = f.player("Dee");
        PathExporter real = new PathExporter(plugin, fc);
        PathService svc = new PathService(fc, f.catalog, f.store, () -> f.installed, Env::everyone, f.clock::get, real);
        Choice c = new Choice();
        c.styles.add("survivor");
        svc.commit(s, c);
        expect(s.stubAttachments().size() == 1 && s.stubAttachments().get(0).getPermissions().containsKey("nexuspath.hidden.nexusfarming"), "the service publishes through the exporter");
        svc.quit(s);
        expect(s.stubAttachments().isEmpty(), "and forgets on quit");
    }

    static void config() {
        PathConfig c = new PathConfig();
        // defaults
        expect(c.unassignedVisible && c.inviteMode.equals("join") && c.inviteDelaySeconds == 90 && c.maxReminders == 3 && c.tryMinutes == 10
                && c.eventsEnabled && c.exportPermissions && c.exportMetadata, "defaults");
        c.load(new Kit.MapSection());
        expect(c.unassignedVisible && c.inviteMode.equals("join") && c.inviteDelaySeconds == 90 && c.maxReminders == 3 && c.tryMinutes == 10, "an empty section keeps defaults");
        Kit.MapSection m = new Kit.MapSection();
        m.put("unassigned", "HIDE").put("invite.mode", "OFF").put("invite.delay-seconds", 5).put("invite.max-reminders", 7).put("try.minutes", 30)
                .put("events.enabled", false).put("export.permissions", false).put("export.metadata", false)
                .put("messages.prefix", "P ").put("messages.invite", "I").put("messages.saved", "S").put("messages.everything", "E")
                .put("messages.blocked", "B").put("messages.try-start", "TS").put("messages.try-end", "TE").put("messages.try-no", "TN").put("messages.reset", "R");
        c.load(m);
        expect(!c.unassignedVisible && c.inviteMode.equals("off") && c.inviteDelaySeconds == 5 && c.maxReminders == 7 && c.tryMinutes == 30, "values read");
        expect(!c.eventsEnabled && !c.exportPermissions && !c.exportMetadata, "switches read");
        expect(c.prefix.equals("P ") && c.msgInvite.equals("I") && c.msgSaved.equals("S") && c.msgEverything.equals("E") && c.msgBlocked.equals("B")
                && c.msgTryStart.equals("TS") && c.msgTryEnd.equals("TE") && c.msgTryNo.equals("TN") && c.msgReset.equals("R"), "messages read");
        // tutorial lessons
        expect(c.offerAfterLessons.isEmpty(), "no lessons by default");
        Kit.MapSection tl = new Kit.MapSection();
        tl.put("tutorial.offer-after-lessons", List.of("Tour", " WELCOME "));
        c.load(tl);
        expect(c.offerAfterLessons.equals(List.of("tour", "welcome")), "lesson ids are normalised: " + c.offerAfterLessons);
        c.load(new Kit.MapSection());
        expect(c.offerAfterLessons.isEmpty(), "reloading without them clears the list");
        // limits
        Kit.MapSection lim = new Kit.MapSection();
        lim.put("invite.delay-seconds", -4).put("invite.max-reminders", 0).put("try.minutes", 0);
        c.load(lim);
        expect(c.inviteDelaySeconds == 0 && c.maxReminders == 1 && c.tryMinutes == 1, "low values are raised to the floor");
        lim.put("invite.delay-seconds", 99999).put("invite.max-reminders", 9999).put("try.minutes", 9999);
        c.load(lim);
        expect(c.inviteDelaySeconds == 3600 && c.maxReminders == 50 && c.tryMinutes == 240, "high values are cut to the ceiling");
        lim.put("invite.delay-seconds", 0).put("invite.max-reminders", 1).put("try.minutes", 1);
        c.load(lim);
        expect(c.inviteDelaySeconds == 0 && c.maxReminders == 1 && c.tryMinutes == 1, "the limits themselves are allowed");
        lim.put("invite.delay-seconds", 3600).put("invite.max-reminders", 50).put("try.minutes", 240);
        c.load(lim);
        expect(c.inviteDelaySeconds == 3600 && c.maxReminders == 50 && c.tryMinutes == 240, "the upper limits themselves are allowed");
        // odd words
        Kit.MapSection odd = new Kit.MapSection();
        odd.put("unassigned", "banana").put("invite.mode", "sometimes");
        c.load(odd);
        expect(c.unassignedVisible && c.inviteMode.equals("join"), "anything but 'hide' shows and anything but 'off' invites");
        // reload is idempotent: loading the same thing twice gives the same
        Kit.MapSection same = new Kit.MapSection();
        same.put("try.minutes", 12);
        c.load(same);
        c.load(same);
        expect(c.tryMinutes == 12, "loading twice is fine");

        // ColorText
        expect(ColorText.translate("&aHi &lthere &zno").equals("§aHi §lthere &zno") && ColorText.translate(null).isEmpty(), "colour codes");
        expect(ColorText.translate("&A&K").equals("§A§K"), "upper-case codes too");
    }
}
