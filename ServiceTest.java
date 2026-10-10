package com.nexuscraft.nexuspath;

import java.util.List;
import java.util.Set;

import static com.nexuscraft.nexuspath.Kit.expect;

public final class ServiceTest {

    public static void main(String[] args) {
        compute();
        refreshAndHiders();
        borrowing();
        invites();
        commitAndReset();
        words();
        pickable();
        Kit.finish();
    }

    static void compute() {
        Env e = new Env();
        Kit.TestPlayer p = e.player("Ann");
        // nothing chosen: nothing hidden
        expect(e.service.compute(p.getUniqueId(), false).isEmpty(), "unchosen hides nothing");

        e.choose(p, "survivor");
        Set<String> h = e.service.hiddenOf(p.getUniqueId());
        expect(h.equals(Env.set("nexusfarming", "nexusangler", "nexusredstone")), "survivor hides farming, angler, redstone: " + h);
        expect(!h.contains("nexusunlisted"), "unlisted is shown by default");
        expect(!h.contains("nexusclaims") && !h.contains("nexusmenu"), "locked and default-on are never hidden");
        expect(!h.contains("nexuspath") && !h.contains("nexustutorial") && !h.contains("vault"), "infrastructure and non-Nexus plugins are never hidden");

        // not installed: not hidden
        e.installed.remove("NexusAngler");
        expect(!e.service.compute(p.getUniqueId(), false).contains("nexusangler"), "not installed means not in the hidden set");
        e.installed.add("NexusAngler");

        // unassigned: hide policy
        e.cfg.unassignedVisible = false;
        expect(e.service.compute(p.getUniqueId(), false).contains("nexusunlisted"), "unassigned hidden when policy is hide");
        expect(e.service.compute(p.getUniqueId(), false).contains("nexusunlisted") && !e.service.compute(p.getUniqueId(), false).contains("nexusmenu"), "default-on survives the hide policy");
        e.cfg.unassignedVisible = true;

        // bypass
        expect(e.service.compute(p.getUniqueId(), true).isEmpty(), "bypass hides nothing");
        p.perms.add("nexuspath.bypass");
        e.service.refresh(p);
        expect(e.service.hiddenOf(p.getUniqueId()).isEmpty(), "refresh honours the bypass permission");
        p.perms.clear();
        e.service.refresh(p);
        expect(e.service.hiddenOf(p.getUniqueId()).size() == 3, "losing the bypass hides again");

        // everything
        Kit.TestPlayer q = e.player("Bob");
        Choice all = new Choice();
        all.all = true;
        e.service.commit(q, all);
        expect(e.service.hiddenOf(q.getUniqueId()).isEmpty(), "'everything' hides nothing");

        // a system outside the catalogue that is a Nexus plugin and not in any style
        e.installed.add("NexusBrandNew");
        expect(e.service.compute(p.getUniqueId(), false).isEmpty() == false && !e.service.compute(p.getUniqueId(), false).contains("nexusbrandnew"), "unlisted plugin shown under 'show'");
        e.cfg.unassignedVisible = false;
        expect(e.service.compute(p.getUniqueId(), false).contains("nexusbrandnew"), "unlisted plugin hidden under 'hide'");
        e.cfg.unassignedVisible = true;

        // mixed styles combine
        Kit.TestPlayer r = e.player("Cy");
        e.choose(r, "survivor", "farmer");
        expect(e.service.hiddenOf(r.getUniqueId()).equals(Env.set("nexusredstone")), "two styles hide only what neither names: " + e.service.hiddenOf(r.getUniqueId()));
    }

    static void refreshAndHiders() {
        Env e = new Env();
        Kit.TestPlayer p = e.player("Ann");
        Kit.TestPlayer q = e.player("Bob");
        expect(!e.service.isHidden(p.getUniqueId(), "NexusFarming") && !e.service.anyoneHides("NexusFarming"), "nothing hidden before anyone chooses");
        e.choose(p, "survivor");
        expect(e.service.isHidden(p.getUniqueId(), "NexusFarming") && e.service.isHidden(p.getUniqueId(), "nexusfarming"), "isHidden ignores case");
        expect(!e.service.isHidden(p.getUniqueId(), "NexusRime"), "a shown system is not hidden");
        expect(!e.service.isHidden(q.getUniqueId(), "NexusFarming"), "another player is unaffected");
        expect(!e.service.isHidden(p.getUniqueId(), null), "null plugin is never hidden");
        expect(e.service.anyoneHides("NexusFarming") && !e.service.anyoneHides("NexusRime"), "anyoneHides tracks hiders");

        e.choose(q, "survivor");
        e.service.quit(p);
        expect(e.service.anyoneHides("NexusFarming"), "still hidden for the other player after one leaves");
        expect(!e.service.isHidden(p.getUniqueId(), "NexusFarming"), "a player who left hides nothing");
        e.service.quit(q);
        expect(!e.service.anyoneHides("NexusFarming"), "nobody hides it once both left");
        e.service.quit(q);
        expect(!e.service.anyoneHides("NexusFarming"), "quitting twice is harmless");

        // switching styles moves the counts
        Kit.TestPlayer r = e.player("Cy");
        e.choose(r, "survivor");
        expect(e.service.anyoneHides("NexusRedstone"), "survivor hides redstone");
        e.choose(r, "builder");
        expect(e.service.anyoneHides("NexusRime") && e.service.anyoneHides("NexusTrials") && e.service.anyoneHides("NexusAngler"), "builder hides rime, trials and angler");
        expect(!e.service.anyoneHides("NexusRedstone"), "redstone no longer hidden by anyone");

        // two players hide the same plugin: it stays hidden until both stop
        Env two = new Env();
        Kit.TestPlayer t1 = two.player("T1");
        Kit.TestPlayer t2 = two.player("T2");
        two.choose(t1, "survivor");
        two.choose(t2, "survivor");
        two.choose(t1, "farmer");
        expect(two.service.anyoneHides("NexusRime"), "t1 now hides rime and t2 still hides farming");
        expect(two.service.anyoneHides("NexusFarming"), "t2 still hides farming after t1 stopped");
        two.choose(t2, "farmer");
        expect(!two.service.anyoneHides("NexusFarming") && !two.service.anyoneHides("NexusAngler"), "once both stop, nobody hides it");

        // refreshing twice does not double count
        e.service.refresh(r);
        e.service.refresh(r);
        e.service.quit(r);
        expect(!e.service.anyoneHides("NexusTrials"), "repeated refreshes do not double count the hiders");

        // exports: only on change
        Env f = new Env();
        Kit.TestPlayer s = f.player("Dee");
        f.choose(s, "survivor");
        int n = f.exports.size();
        f.service.refresh(s);
        expect(f.exports.size() == n, "no new export when nothing changed");
        expect(f.exports.get(n - 1).equals("Dee:nexusangler,nexusfarming,nexusredstone"), "export lists hidden plugins: " + f.exports.get(n - 1));
        f.choose(s, "survivor", "farmer", "builder");
        expect(f.exports.get(f.exports.size() - 1).equals("Dee:nexusangler,nexusfarming,nexusredstone".replace("nexusangler,nexusfarming,nexusredstone", "")), "export of an empty set is published too: " + f.exports.get(f.exports.size() - 1));
        f.service.quit(s);
        expect(f.exports.get(f.exports.size() - 1).equals("Dee:forgotten"), "quit forgets");

        // refreshAll covers everyone online
        Env g = new Env();
        Kit.TestPlayer a = g.player("A");
        Kit.TestPlayer b = g.player("B");
        g.store.put(a.getUniqueId(), chosen("survivor"));
        g.store.put(b.getUniqueId(), chosen("farmer"));
        g.service.refreshAll();
        expect(g.service.isHidden(a.getUniqueId(), "NexusFarming") && g.service.isHidden(b.getUniqueId(), "NexusRime"), "refreshAll refreshes every player");
        try {
            g.service.hiddenOf(a.getUniqueId()).add("x");
            expect(false, "hiddenOf must be read-only");
        } catch (UnsupportedOperationException ok) {
            expect(true, "hiddenOf is read-only");
        }
        expect(g.service.hiddenOf(java.util.UUID.randomUUID()).isEmpty(), "unknown player hides nothing");

        // infrastructure
        expect(PathService.infrastructure("nexuspath") && PathService.infrastructure("nexustutorial") && !PathService.infrastructure("nexusrime"), "infrastructure names");
        Kit.TestPlayer perm = g.player("Perm");
        expect(!PathService.bypasses(perm), "no bypass without permission");
        perm.perms.add("nexuspath.bypass");
        expect(PathService.bypasses(perm), "bypass with permission");
    }

    static Choice chosen(String... styles) {
        Choice c = new Choice();
        c.chosen = true;
        for (String s : styles) {
            c.styles.add(s);
        }
        return c;
    }

    static void borrowing() {
        Env e = new Env();
        Kit.TestPlayer p = e.player("Ann");
        e.cfg.tryMinutes = 10;
        e.choose(p, "survivor");
        PathService.TryResult r = e.service.tryIt(p, "NexusFarming");
        expect(r.ok(), "borrowing a hidden system works");
        expect(!e.service.isHidden(p.getUniqueId(), "NexusFarming"), "a borrowed system is not hidden");
        expect(e.service.borrowedMinutes(p.getUniqueId(), "NexusFarming") == 10, "ten minutes left");
        expect(!e.service.anyoneHides("NexusFarming"), "nobody hides it while it is borrowed");
        expect(p.heard("Farming") && p.heard("10"), "player is told: " + p.lastMessage());

        e.clock.addAndGet(5 * 60_000L);
        expect(e.service.borrowedMinutes(p.getUniqueId(), "nexusfarming") == 5, "five minutes left");
        e.clock.addAndGet(30_000L);
        expect(e.service.borrowedMinutes(p.getUniqueId(), "NexusFarming") == 5, "partial minutes round up");
        e.service.tick();
        expect(!e.service.isHidden(p.getUniqueId(), "NexusFarming"), "not expired yet");
        e.clock.addAndGet(4 * 60_000L + 30_000L);
        expect(e.service.borrowedMinutes(p.getUniqueId(), "NexusFarming") == 0, "time is up");
        int before = p.messages.size();
        e.service.tick();
        expect(e.service.isHidden(p.getUniqueId(), "NexusFarming"), "tick puts it back out");
        expect(p.messages.size() == before + 1 && p.heard("goes back out"), "player told it ends: " + p.lastMessage());
        e.service.tick();
        expect(p.messages.size() == before + 1, "ending is announced once");

        // compute honours an unexpired borrow even without a tick, and an expired one
        e.service.tryIt(p, "NexusAngler");
        e.clock.addAndGet(10 * 60_000L);
        expect(e.service.compute(p.getUniqueId(), false).contains("nexusangler"), "an expired borrow no longer counts in compute");
        e.service.refresh(p);

        // the list of borrowed systems
        Env bw = new Env();
        Kit.TestPlayer bp = bw.player("Bw");
        bw.cfg.tryMinutes = 10;
        bw.choose(bp, "survivor");
        expect(bw.service.borrowedOf(bp.getUniqueId()).isEmpty(), "nothing borrowed yet");
        bw.service.tryIt(bp, "NexusAngler");
        bw.clock.addAndGet(60_000L);
        bw.service.tryIt(bp, "NexusFarming");
        var lent = bw.service.borrowedOf(bp.getUniqueId());
        expect(new java.util.ArrayList<>(lent.keySet()).equals(List.of("nexusangler", "nexusfarming")) && lent.get("nexusangler") == 9 && lent.get("nexusfarming") == 10, "borrowed systems, soonest first, with minutes: " + lent);
        bw.clock.addAndGet(9 * 60_000L);
        lent = bw.service.borrowedOf(bp.getUniqueId());
        expect(new java.util.ArrayList<>(lent.keySet()).equals(List.of("nexusfarming")), "one that has run out (but not yet been ticked) is no longer listed: " + lent);
        expect(bw.service.borrowedOf(java.util.UUID.randomUUID()).isEmpty(), "an unknown player has none");

        // refusals
        PathService.TryResult none = e.service.tryIt(p, "NexusNothing");
        expect(!none.ok() && none.why().contains("no such"), "unknown system refused: " + none.why());
        e.installed.remove("NexusAngler");
        expect(!e.service.tryIt(p, "NexusAngler").ok(), "not installed refused");
        e.installed.add("NexusAngler");
        PathService.TryResult locked = e.service.tryIt(p, "NexusClaims");
        expect(!locked.ok() && locked.why().contains("always on"), "locked refused: " + locked.why());
        PathService.TryResult mine = e.service.tryIt(p, "NexusRime");
        expect(!mine.ok() && mine.why().contains("already part"), "already visible refused: " + mine.why());
        PathService.TryResult again = e.service.tryIt(p, "NexusFarming");
        expect(again.ok(), "can borrow again after it ended");
        PathService.TryResult twice = e.service.tryIt(p, "NexusFarming");
        expect(!twice.ok(), "already borrowed counts as part of the game");

        // an unlisted Nexus plugin can be borrowed
        Env f = new Env();
        Kit.TestPlayer q = f.player("Bob");
        f.cfg.unassignedVisible = false;
        f.choose(q, "survivor");
        expect(f.service.isHidden(q.getUniqueId(), "NexusUnlisted"), "unlisted hidden under 'hide'");
        expect(f.service.tryIt(q, "NexusUnlisted").ok() && !f.service.isHidden(q.getUniqueId(), "NexusUnlisted"), "unlisted can be borrowed");
        expect(!f.service.tryIt(q, "Vault").ok(), "a non-Nexus plugin cannot be borrowed");

        // a new choice, quit and reset all clear borrowing
        Env g = new Env();
        Kit.TestPlayer s = g.player("Cy");
        g.choose(s, "survivor");
        g.service.tryIt(s, "NexusFarming");
        g.choose(s, "survivor");
        expect(g.service.borrowedMinutes(s.getUniqueId(), "NexusFarming") == 0 && g.service.isHidden(s.getUniqueId(), "NexusFarming"), "saving a choice ends borrowing");
        g.service.tryIt(s, "NexusFarming");
        g.service.reset(s.getUniqueId(), s);
        expect(g.service.borrowedMinutes(s.getUniqueId(), "NexusFarming") == 0, "reset ends borrowing");
        g.choose(s, "survivor");
        g.service.tryIt(s, "NexusFarming");
        g.service.quit(s);
        expect(g.service.borrowedMinutes(s.getUniqueId(), "NexusFarming") == 0, "quit ends borrowing");

        // tick with an offline borrower drops the record without messaging anyone
        Env h = new Env();
        Kit.TestPlayer t = h.player("Dee");
        h.choose(t, "survivor");
        h.service.tryIt(t, "NexusFarming");
        org.bukkit.Bukkit.PLAYERS.clear();
        h.clock.addAndGet(11 * 60_000L);
        int msgs = t.messages.size();
        h.service.tick();
        expect(t.messages.size() == msgs, "an offline borrower is not messaged");
        org.bukkit.Bukkit.PLAYERS.put(t.getUniqueId(), t);
        h.service.tick();
        expect(t.messages.size() == msgs, "and the record was dropped");

        // two borrows end separately, the later one stays
        Env k = new Env();
        Kit.TestPlayer u = k.player("Eve");
        k.cfg.tryMinutes = 10;
        k.choose(u, "survivor");
        k.service.tryIt(u, "NexusFarming");
        k.clock.addAndGet(5 * 60_000L);
        k.service.tryIt(u, "NexusAngler");
        k.clock.addAndGet(5 * 60_000L);
        k.service.tick();
        expect(k.service.isHidden(u.getUniqueId(), "NexusFarming") && !k.service.isHidden(u.getUniqueId(), "NexusAngler"), "the earlier borrow ends and the later stays");
        expect(k.service.borrowedMinutes(u.getUniqueId(), "NexusAngler") == 5, "the later one has five minutes left");
    }

    static void invites() {
        Env e = new Env();
        Kit.TestPlayer p = e.player("Ann");
        expect(e.service.shouldInvite(p), "a new player is invited");
        e.service.invite(p);
        expect(p.heard("/path"), "invite message sent: " + p.lastMessage());
        expect(e.store.peek(p.getUniqueId()).reminders == 1 && e.store.dirty(), "reminder counted and store marked dirty");
        e.service.invite(p);
        e.service.invite(p);
        expect(!e.service.shouldInvite(p), "three reminders is the limit");
        e.cfg.maxReminders = 5;
        expect(e.service.shouldInvite(p), "a bigger limit invites again");
        e.cfg.maxReminders = 3;

        e.cfg.inviteMode = "off";
        Kit.TestPlayer q = e.player("Bob");
        expect(!e.service.shouldInvite(q), "invites can be switched off");
        e.cfg.inviteMode = "join";
        q.perms.add("nexuspath.bypass");
        expect(!e.service.shouldInvite(q), "staff with bypass are not invited");
        q.perms.clear();
        e.choose(q, "farmer");
        expect(!e.service.shouldInvite(q), "someone who chose is not invited");
        e.service.reset(q.getUniqueId(), q);
        expect(e.service.shouldInvite(q), "after a reset they are invited again");
        // reset keeps the reminder count so a reset player is not nagged forever
        Kit.TestPlayer r = e.player("Cy");
        e.service.invite(r);
        e.service.invite(r);
        e.service.invite(r);
        e.choose(r, "farmer");
        e.service.reset(r.getUniqueId(), r);
        expect(e.store.peek(r.getUniqueId()).reminders == 3 && !e.service.shouldInvite(r), "reset keeps the reminder count");
    }

    static void commitAndReset() {
        Env e = new Env();
        Kit.TestPlayer p = e.player("Ann");
        Choice d = e.service.draftOf(p.getUniqueId());
        expect(d.isBlank(), "draft of a new player is blank");
        d.styles.add("farmer");
        expect(e.store.peek(p.getUniqueId()) == null, "changing a draft saves nothing");
        e.service.commit(p, d);
        Choice saved = e.store.peek(p.getUniqueId());
        expect(saved.chosen && saved.styles.contains("farmer"), "commit saves and marks chosen");
        expect(e.service.isHidden(p.getUniqueId(), "NexusRime"), "commit applies at once");
        expect(p.heard("Farmer") && p.heard("systems on"), "saved message names the styles: " + p.lastMessage());
        Choice d2 = e.service.draftOf(p.getUniqueId());
        d2.styles.clear();
        expect(saved.styles.contains("farmer"), "a draft is a copy");

        // 'everything' message
        Choice all = new Choice();
        all.all = true;
        e.service.commit(p, all);
        expect(p.heard("everything") && e.service.hiddenOf(p.getUniqueId()).isEmpty(), "everything message and nothing hidden");

        // commit tidies
        Choice t = new Choice();
        t.styles.add("survivor");
        t.shown.add("nexusrime");
        e.service.commit(p, t);
        expect(e.store.peek(p.getUniqueId()).shown.isEmpty(), "commit tidies redundant exceptions");

        // reset
        e.service.reset(p.getUniqueId(), p);
        Choice fresh = e.store.peek(p.getUniqueId());
        expect(!fresh.chosen && fresh.styles.isEmpty() && e.service.hiddenOf(p.getUniqueId()).isEmpty(), "reset clears the choice and the hiding");
        // reset of an offline player is fine
        Env f = new Env();
        java.util.UUID ghost = java.util.UUID.randomUUID();
        f.service.reset(ghost, null);
        expect(f.store.peek(ghost) != null && !f.store.peek(ghost).chosen, "reset works for someone offline");
    }

    static void words() {
        Env e = new Env();
        Kit.TestPlayer p = e.player("Ann");
        e.cfg.tryMinutes = 7;
        SystemInfo rime = e.catalog.system("NexusRime");
        expect(e.service.fill("{system}/{id}/{minutes}", rime, null).equals("Rime/nexusrime/7"), "fill substitutes system, id and minutes");
        expect(e.service.fill("{system}", null, null).equals("{system}"), "no system leaves the placeholder");
        expect(e.service.fill("{a}{b}", null, java.util.Map.of("a", "1", "b", "2")).equals("12"), "extra values substituted");
        e.service.say(p, "&ahello");
        expect(p.lastMessage().equals("§b§l[Path] §r§ahello"), "say prefixes and colours: " + p.lastMessage());

        Choice c = new Choice();
        c.chosen = true;
        var m = e.service.summaryFor(p, c);
        expect(m.get("styles").equals("your own mix"), "no styles reads as a mix");
        c.styles.add("survivor");
        c.styles.add("farmer");
        m = e.service.summaryFor(p, c);
        expect(m.get("styles").equals("Survivor + Farmer"), "styles joined: " + m.get("styles"));
        // pickable: rime farming angler redstone trials menu unlisted = 7 ; on: all but redstone
        expect(m.get("shown").equals("6") && m.get("hidden").equals("1"), "counts: " + m);
        e.installed.remove("NexusRedstone");
        m = e.service.summaryFor(p, c);
        expect(m.get("shown").equals("6") && m.get("hidden").equals("0"), "uninstalled systems are not counted: " + m);
        Choice ghost = new Choice();
        ghost.styles.add("nostyle");
        expect(e.service.summaryFor(p, ghost).get("styles").equals("nostyle"), "an unknown style shows its id");
    }

    static void pickable() {
        Env e = new Env();
        List<SystemInfo> pick = e.service.pickable();
        List<String> ids = pick.stream().map(SystemInfo::id).toList();
        expect(ids.equals(List.of("NexusRime", "NexusFarming", "NexusAngler", "NexusRedstone", "NexusTrials", "NexusMenu", "NexusUnlisted")), "pickable in catalogue order then unlisted Nexus plugins: " + ids);
        expect(!ids.contains("NexusClaims") && !ids.contains("NexusPath") && !ids.contains("NexusTutorial") && !ids.contains("Vault"), "locked, infrastructure and non-Nexus plugins are not pickable");
        e.installed.remove("NexusRime");
        expect(!e.service.pickable().stream().anyMatch(s -> s.id().equals("NexusRime")), "uninstalled not pickable");
        List<String> always = e.service.alwaysOn().stream().map(SystemInfo::id).toList();
        expect(always.equals(List.of("NexusClaims")), "always-on lists installed locked systems: " + always);
        e.installed.remove("NexusClaims");
        expect(e.service.alwaysOn().isEmpty(), "uninstalled locked systems are not listed");
        // unlisted keeps the real-case name
        e.installed.add("NEXUSLOUD");
        expect(e.service.pickable().stream().anyMatch(s -> s.id().equals("NEXUSLOUD")), "unlisted plugin keeps its real name");
    }
}
