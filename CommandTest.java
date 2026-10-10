package com.nexuscraft.nexuspath;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.nexuscraft.nexuspath.Kit.expect;

public final class CommandTest {

    /** Something that is not a player: the console. */
    static final class Console implements CommandSender {
        final List<String> lines = new ArrayList<>();
        final Set<String> perms = new java.util.HashSet<>();

        @Override
        public boolean hasPermission(String node) {
            return perms.contains(node);
        }

        @Override
        public void sendMessage(String message) {
            lines.add(message);
        }

        @Override
        public void sendMessage(net.kyori.adventure.text.Component message) {
            lines.add(message.toString());
        }

        @Override
        public String getName() {
            return "CONSOLE";
        }

        boolean heard(String s) {
            return lines.stream().anyMatch(l -> l.contains(s));
        }
    }

    static boolean reloaded;
    static boolean tutorial;

    static PathCommand command(Env e, ListenerGate gate) {
        reloaded = false;
        return new PathCommand(e.service, gate, () -> reloaded = true, () -> tutorial);
    }

    static void run(PathCommand c, CommandSender s, String... args) {
        expect(c.onCommand(s, new Command(), "path", args), "the command always reports success: " + String.join(" ", args));
    }

    public static void main(String[] args) {
        Env e = new Env();
        Kit.NexusFarming farming = new Kit.NexusFarming();
        ListenerGate gate = GateTest.gateFor(e);
        PathCommand c = command(e, gate);
        Kit.TestPlayer ann = e.player("Ann");
        Kit.TestPlayer staff = e.player("Staff");
        staff.perms.add("nexuspath.admin");
        Console con = new Console();
        con.perms.add("nexuspath.admin");

        // open
        run(c, ann);
        expect(ann.opened != null && ann.opened.getHolder() instanceof PathGui, "/path opens the style menu");
        ann.opened = null;
        run(c, ann, "open");
        expect(ann.opened != null, "/path open too");
        ann.opened = null;
        run(c, ann, "OPEN");
        expect(ann.opened != null, "subcommands ignore case");
        ann.opened = null;
        run(c, ann, "open", "Bob");
        expect(ann.opened == null && ann.heard("Only staff"), "a player cannot open it for somebody else");
        Kit.TestPlayer bob = e.player("Bob");
        run(c, staff, "open", "Bob");
        expect(bob.opened != null && staff.opened == null, "staff open it for another player");
        run(c, staff, "open", "Nobody");
        expect(staff.heard("Nobody is not online"), "an offline player is reported");
        run(c, con, "open");
        expect(con.heard("Players open it"), "the console is told how");
        run(c, con, "open", "Ann");
        expect(ann.opened != null, "the console can open it for a player");

        // unknown subcommand
        run(c, ann, "banana");
        expect(ann.heard("Try"), "unknown subcommand gives help");

        // info
        run(c, ann, "info");
        expect(ann.heard("Play styles") && ann.heard("survivor") && ann.heard("Fight"), "info lists the styles");
        ann.messages.clear();
        run(c, ann, "info", "survivor");
        expect(ann.heard("Survivor") && ann.heard("Rime") && ann.heard("Trials") && !ann.heard("Farming"), "a style's tour lists its systems");
        expect(!ann.heard("/tutorial"), "no tutorial hint without NexusTutorial");
        expect(!ann.heard("more"), "a short tour does not say '... and 0 more'");
        expect(ann.messages.size() == 3, "a two-system tour is a header and two lines: " + ann.messages);
        tutorial = true;
        ann.messages.clear();
        run(c, ann, "info", "survivor");
        expect(ann.heard("/tutorial start rime") && ann.heard("/tutorial start trials"), "with NexusTutorial each system points at its lesson");
        ann.messages.clear();
        run(c, ann, "info", "NexusRime");
        expect(ann.heard("Rime") && ann.heard("In: ") && ann.heard("Survivor") && ann.heard("/tutorial start rime"), "a system's page");
        ann.messages.clear();
        run(c, ann, "info", "nexusmenu");
        expect(ann.heard("In no style"), "a system in no style says so");
        ann.messages.clear();
        run(c, ann, "info", "NexusClaims");
        expect(ann.heard("Always on"), "a locked system says it is always on");
        tutorial = false;
        ann.messages.clear();
        run(c, ann, "info", "zzz");
        expect(ann.heard("no style or system called zzz"), "an unknown name is reported");
        expect(PathCommand.lessonId("NexusRime").equals("rime") && PathCommand.lessonId("Vault").equals("vault") && PathCommand.lessonId("NEXUSAngler").equals("angler"), "lesson ids");
        // status
        ann.messages.clear();
        run(c, ann, "status");
        expect(ann.heard("have not chosen"), "status before choosing");
        e.choose(ann, "survivor");
        ann.messages.clear();
        run(c, ann, "status");
        expect(ann.heard("Survivor") && ann.heard("Hidden (3)") && ann.heard("Farming") && ann.heard("Angler") && ann.heard("Redstone"), "status lists what is hidden");
        expect(!ann.heard("min)") && !ann.heard("Borrowed") && ann.heard("Borrow one with"), "nothing borrowed: no minutes shown, and the way to borrow is explained");
        e.service.tryIt(ann, "NexusFarming");
        ann.messages.clear();
        run(c, ann, "status");
        expect(ann.heard("Hidden (2)") && ann.messages.stream().filter(m -> m.contains("Hidden (")).noneMatch(m -> m.contains("Farming")), "a borrowed system is not listed as hidden");
        e.clock.addAndGet(11 * 60_000L);
        e.service.tick();
        e.service.tryIt(ann, "NexusFarming");
        e.clock.addAndGet(3 * 60_000L);
        e.service.tryIt(ann, "NexusAngler");
        ann.messages.clear();
        run(c, ann, "status");
        expect(ann.heard("Borrowed: ") && ann.heard("Farming (7 min)") && ann.heard("Angler (10 min)"), "borrowed systems are listed with their minutes: " + ann.messages);
        expect(ann.heard("Hidden (1)") && ann.heard("Redstone"), "and they are no longer in the hidden list");
        expect(ann.messages.stream().filter(m -> m.contains("Borrowed")).findFirst().orElse("").indexOf("Farming") < ann.messages.stream().filter(m -> m.contains("Borrowed")).findFirst().orElse("").indexOf("Angler"), "the one running out first is listed first");
        con.lines.clear();
        run(c, con, "status");
        expect(con.heard("Players only"), "status is for players");
        Choice all = new Choice();
        all.all = true;
        e.service.commit(ann, all);
        ann.messages.clear();
        run(c, ann, "status");
        expect(ann.heard("everything") && ann.heard("Nothing is hidden"), "status for everything");
        e.choose(ann, "survivor");
        ann.perms.add("nexuspath.bypass");
        e.service.refresh(ann);
        ann.messages.clear();
        run(c, ann, "status");
        expect(ann.heard("bypass"), "status mentions the staff bypass");
        ann.perms.clear();
        e.service.refresh(ann);

        // everything
        Choice busy = new Choice();
        busy.styles.add("survivor");
        busy.shown.add("nexusfarming");
        busy.hidden.add("nexusrime");
        e.service.commit(ann, busy);
        expect(!e.store.peek(ann.getUniqueId()).shown.isEmpty() || !e.store.peek(ann.getUniqueId()).hidden.isEmpty(), "set up: she has by-hand changes");
        run(c, ann, "everything");
        Choice evr = e.store.peek(ann.getUniqueId());
        expect(evr.all && evr.styles.isEmpty() && evr.shown.isEmpty() && evr.hidden.isEmpty(), "everything leaves no old picks behind: " + evr.styles + evr.shown + evr.hidden);
        expect(e.store.peek(ann.getUniqueId()).all && e.service.hiddenOf(ann.getUniqueId()).isEmpty(), "/path everything keeps it all");
        con.lines.clear();
        run(c, con, "everything");
        expect(con.heard("Players only"), "everything is for players");

        // try
        e.choose(ann, "survivor");
        ann.messages.clear();
        run(c, ann, "try");
        expect(ann.heard("Usage"), "try needs a system");
        run(c, ann, "try", "nexusfarming");
        expect(!e.service.isHidden(ann.getUniqueId(), "NexusFarming"), "try borrows it");
        ann.messages.clear();
        run(c, ann, "try", "NexusRime");
        expect(ann.heard("cannot borrow") && ann.heard("already part"), "a refused try explains why: " + ann.lastMessage());
        run(c, con, "try", "NexusRime");
        expect(con.heard("Only players"), "try is for players");

        // reset
        run(c, ann, "reset");
        expect(!e.store.peek(ann.getUniqueId()).chosen && ann.heard("choices are cleared"), "a player resets themselves");
        e.choose(bob, "farmer");
        run(c, ann, "reset", "Bob");
        expect(e.store.peek(bob.getUniqueId()).chosen && ann.heard("Only staff"), "a player cannot reset someone else");
        run(c, staff, "reset", "Bob");
        expect(!e.store.peek(bob.getUniqueId()).chosen && staff.heard("Cleared Bob") && bob.heard("choices are cleared"), "staff reset someone");
        staff.messages.clear();
        run(c, staff, "reset", "Nobody");
        expect(staff.heard("Nobody is not online"), "reset of an offline player is reported");
        run(c, con, "reset");
        expect(con.heard("Usage"), "console reset needs a player");

        // admin commands need the permission
        for (String sub : new String[] {"set", "show", "hide", "reload", "check", "gate"}) {
            ann.messages.clear();
            run(c, ann, sub, "Bob", "survivor");
            expect(ann.heard("Only staff"), "players cannot use " + sub);
        }
        expect(!reloaded, "a player could not reload");

        // set
        staff.messages.clear();
        run(c, staff, "set");
        expect(staff.heard("Usage"), "set needs arguments");
        staff.messages.clear();
        run(c, staff, "set", "Nobody", "survivor");
        expect(staff.heard("Usage"), "set needs an online player");
        staff.messages.clear();
        run(c, staff, "set", "Bob", "wizard");
        expect(staff.heard("no style called wizard"), "set rejects unknown styles");
        expect(!e.store.peek(bob.getUniqueId()).chosen, "and changes nothing then");
        run(c, staff, "set", "Bob", "survivor", "builder");
        Choice b = e.store.peek(bob.getUniqueId());
        expect(b.chosen && b.styles.size() == 2 && staff.heard("Set Bob's game"), "set applies styles");
        run(c, staff, "set", "Bob", "all");
        expect(e.store.peek(bob.getUniqueId()).all && e.service.hiddenOf(bob.getUniqueId()).isEmpty(), "set all");
        run(c, staff, "set", "Bob", "FARMER");
        expect(e.store.peek(bob.getUniqueId()).styles.contains("farmer"), "style names ignore case");

        // show / hide
        run(c, staff, "hide", "Bob", "NexusAngler");
        expect(staff.heard("Hid Angler from Bob"), "hide reports");
        run(c, staff, "show", "Bob", "NexusRime");
        expect(staff.heard("Showed Rime to Bob"), "show reports");
        // for a player who never chose: show/hide starts from everything
        Kit.TestPlayer cy = e.player("Cy");
        run(c, staff, "hide", "Cy", "NexusRime");
        Choice cc = e.store.peek(cy.getUniqueId());
        expect(cc.chosen && !cc.all && e.service.isHidden(cy.getUniqueId(), "NexusRime") && !e.service.isHidden(cy.getUniqueId(), "NexusFarming"), "hide on an unchosen player hides just that one");
        run(c, staff, "show", "Cy", "NexusRime");
        expect(!e.service.isHidden(cy.getUniqueId(), "NexusRime"), "show brings it back");
        staff.messages.clear();
        run(c, staff, "hide", "Cy");
        expect(staff.heard("/path hide <online player>"), "hide needs a system");
        staff.messages.clear();
        run(c, staff, "hide", "Cy", "NexusNope");
        expect(staff.heard("/path hide <online player>"), "hide needs a catalogue system");
        staff.messages.clear();
        run(c, staff, "show", "Nobody", "NexusRime");
        expect(staff.heard("/path show <online player>"), "show needs an online player");
        run(c, staff, "hide", "Cy", "NexusClaims");
        expect(!e.service.isHidden(cy.getUniqueId(), "NexusClaims"), "a locked system cannot be hidden by staff either");

        // reload / check / gate
        run(c, staff, "reload");
        expect(reloaded && staff.heard("reloaded"), "reload runs the reload action");
        run(c, con, "check");
        expect(con.heard("can be picked") && con.heard("The catalog has no problems"), "check on a clean catalogue: " + con.lines);
        expect(con.heard("7 systems here can be picked; \u00a7f0\u00a77 in the catalog are not installed. 1 are always on."), "check counts: " + con.lines);
        e.installed.remove("NexusRime");
        con.lines.clear();
        run(c, con, "check");
        expect(con.heard("6 systems here can be picked; \u00a7f1\u00a77 in the catalog are not installed."), "check counts a missing plugin: " + con.lines);
        e.installed.add("NexusRime");
        e.installed.add("NexusExtraOne");
        con.lines.clear();
        run(c, con, "check");
        expect(con.heard("8 systems here can be picked; \u00a7f0\u00a77"), "check counts an unlisted plugin as pickable: " + con.lines);
        e.installed.remove("NexusExtraOne");
        con.lines.clear();
        Kit.MapSection oops = new Kit.MapSection();
        oops.put("styles.odd.systems", List.of("NexusGhost"));
        e.catalog.load(oops, "custom");
        e.catalog.finish();
        con.lines.clear();
        run(c, con, "check");
        expect(con.heard("not in the catalog") && !con.heard("no problems"), "check lists problems");
        con.lines.clear();
        run(c, con, "gate");
        expect(con.heard("Event gate: ") && con.heard("on"), "gate status");
        run(c, con, "gate", "off");
        expect(!gate.enabled() && con.heard("off"), "gate off");
        run(c, con, "gate", "on");
        expect(gate.enabled(), "gate on");
        gate.wrapped = 12;
        gate.skipped = 345;
        con.lines.clear();
        run(c, con, "gate", "status");
        expect(con.heard("12") && con.heard("345") && gate.enabled(), "gate status shows counters");
        run(c, con, "gate", "sideways");
        expect(gate.enabled(), "an unknown gate word changes nothing");
        run(c, con, "set", "Ann", "survivor");
        expect(con.heard("Set Ann's game"), "the console can run admin commands");

        // tab completion
        List<String> t = c.onTabComplete(ann, new Command(), "path", new String[] {""});
        expect(t.containsAll(List.of("open", "info", "try", "status", "everything", "reset")) && !t.contains("reload") && !t.contains("set"), "players see player subcommands: " + t);
        t = c.onTabComplete(staff, new Command(), "path", new String[] {""});
        expect(t.containsAll(List.of("open", "set", "show", "hide", "reload", "check", "gate")), "staff also see staff subcommands: " + t);
        t = c.onTabComplete(ann, new Command(), "path", new String[] {"RE"});
        expect(t.equals(List.of("reset")), "prefix filter ignores case: " + t);
        t = c.onTabComplete(ann, new Command(), "path", new String[] {"info", "sur"});
        expect(t.equals(List.of("survivor")), "info completes styles: " + t);
        t = c.onTabComplete(ann, new Command(), "path", new String[] {"info", "nexusri"});
        expect(t.equals(List.of("nexusrime")), "info completes systems: " + t);
        e.choose(ann, "survivor");
        t = c.onTabComplete(ann, new Command(), "path", new String[] {"try", ""});
        expect(t.contains("nexusfarming") && !t.contains("nexusrime"), "try completes only hidden systems: " + t);
        t = c.onTabComplete(con, new Command(), "path", new String[] {"try", ""});
        expect(t.isEmpty(), "the console has nothing to try");
        t = c.onTabComplete(staff, new Command(), "path", new String[] {"set", ""});
        expect(t.contains("Ann") && t.contains("Bob"), "set completes players: " + t);
        t = c.onTabComplete(staff, new Command(), "path", new String[] {"set", "Ann", ""});
        expect(t.contains("survivor") && t.contains("all"), "set then completes styles and all: " + t);
        t = c.onTabComplete(staff, new Command(), "path", new String[] {"hide", "Ann", "nexusf"});
        expect(t.equals(List.of("NexusFarming")), "hide completes systems: " + t);
        t = c.onTabComplete(ann, new Command(), "path", new String[] {"set", ""});
        expect(t.isEmpty(), "players get no staff completions");


        // offer: for NexusTutorial's reward commands
        Env o = new Env();
        PathCommand oc = command(o, GateTest.gateFor(o));
        Kit.TestPlayer eve = o.player("Eve");
        Kit.TestPlayer ops = o.player("Ops");
        ops.perms.add("nexuspath.admin");
        o.cfg.offerAfterLessons = new ArrayList<>(List.of("tour", "welcome"));
        run(oc, ops, "offer", "Eve", "intro");
        expect(eve.opened == null && ops.messages.isEmpty(), "a lesson that is not on the list does nothing, quietly");
        run(oc, ops, "offer", "Eve", "TOUR");
        expect(eve.opened != null && eve.opened.getHolder() instanceof PathGui, "a listed lesson opens the picker (ignoring case)");
        expect(ops.messages.isEmpty(), "and says nothing to the sender");
        eve.opened = null;
        run(oc, ops, "offer", "Eve");
        expect(eve.opened != null, "with no lesson it always opens (for final-commands)");
        eve.opened = null;
        o.choose(eve, "farmer");
        run(oc, ops, "offer", "Eve", "tour");
        run(oc, ops, "offer", "Eve");
        expect(eve.opened == null, "a player who already chose is left alone");
        o.service.reset(eve.getUniqueId(), eve);
        eve.perms.add("nexuspath.bypass");
        run(oc, ops, "offer", "Eve", "tour");
        expect(eve.opened == null, "so is staff with bypass");
        eve.perms.clear();
        run(oc, ops, "offer", "Nobody", "tour");
        run(oc, ops, "offer", "Nobody");
        expect(ops.messages.isEmpty(), "an offline player is ignored without noise, with or without a lesson");
        run(oc, ops, "offer");
        expect(ops.heard("/path offer <online player>"), "no player: usage");
        run(oc, eve, "offer", "Ops", "tour");
        expect(eve.heard("Only staff") && ops.opened == null, "players cannot push the picker at others");
        run(oc, con, "offer", "Eve", "welcome");
        expect(eve.opened != null, "the console (how the tutorial runs it) works");
        List<String> ot = oc.onTabComplete(ops, new Command(), "path", new String[] {"offer", ""});
        expect(ot.contains("Eve") && ot.contains("Ops"), "offer completes players: " + ot);
        expect(oc.onTabComplete(ops, new Command(), "path", new String[] {"o"}).containsAll(List.of("open", "offer")), "offer is in the staff list");


        // long style tours are cut
        Env big = new Env();
        for (int i = 0; i < 20; i++) {
            big.installed.add("NexusBulk" + i);
        }
        Kit.MapSection bulk = new Kit.MapSection();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            ids.add("NexusBulk" + i);
        }
        bulk.put("styles.huge.systems", ids);
        big.catalog.load(bulk, "custom");
        Kit.TestPlayer dee = big.player("Dee");
        command(big, GateTest.gateFor(big)).onCommand(dee, new Command(), "path", new String[] {"info", "huge"});
        expect(dee.heard("and 5 more"), "tours show 15 and count the rest: " + dee.lastMessage());
        expect(dee.messages.size() == 17, "header, fifteen systems and the 'more' line: " + dee.messages.size());

        Kit.finish();
    }
}
