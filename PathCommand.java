package com.nexuscraft.nexuspath;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** /path: choose how you play, look at what that means, borrow something, or (for admins) set it for someone. */
final class PathCommand implements TabExecutor {

    private static final List<String> PLAYER_SUBS = List.of("open", "info", "try", "status", "everything", "reset");
    private static final List<String> ADMIN_SUBS = List.of("set", "show", "hide", "reload", "check", "gate", "offer");

    private final PathService service;
    private final ListenerGate gate;
    private final Runnable reload;
    private final java.util.function.BooleanSupplier tutorialPresent;

    PathCommand(PathService service, ListenerGate gate, Runnable reload, java.util.function.BooleanSupplier tutorialPresent) {
        this.service = service;
        this.gate = gate;
        this.reload = reload;
        this.tutorialPresent = tutorialPresent;
    }

    private static boolean admin(CommandSender s) {
        return s.hasPermission("nexuspath.admin");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "", "open" -> open(sender, args);
            case "info" -> info(sender, args);
            case "try" -> tryIt(sender, args);
            case "status" -> status(sender);
            case "everything" -> everything(sender);
            case "reset" -> reset(sender, args);
            case "set", "show", "hide", "reload", "check", "gate", "offer" -> {
                if (!admin(sender)) {
                    sender.sendMessage(ColorText.translate("&cOnly staff can do that."));
                } else {
                    adminCommand(sender, sub, args);
                }
            }
            default -> sender.sendMessage(ColorText.translate("&7Try &f/path&7, &f/path info&7, &f/path status&7, &f/path try <system>&7."));
        }
        return true;
    }

    // ------------------------------------------------------------------ players

    private void open(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 2) {
            if (!admin(sender)) {
                sender.sendMessage(ColorText.translate("&cOnly staff can open it for somebody else."));
                return;
            }
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(ColorText.translate("&c" + args[1] + " is not online."));
                return;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(ColorText.translate("&7Players open it with /path. Staff: /path open <player>."));
            return;
        }
        PathGui.openStyles(service, target, service.draftOf(target.getUniqueId()));
    }

    private void info(CommandSender sender, String[] args) {
        Catalog cat = service.catalog();
        if (args.length < 2) {
            sender.sendMessage(ColorText.translate("&b&lPlay styles &7(&f/path info <name>&7 for the full tour)"));
            for (StyleInfo st : cat.styles()) {
                sender.sendMessage(ColorText.translate(" &f" + st.id() + " &8- &e" + st.title() + "&7: " + st.blurb()));
            }
            return;
        }
        StyleInfo st = cat.style(args[1]);
        if (st != null) {
            sender.sendMessage(ColorText.translate("&b&l" + st.title() + " &7- " + st.blurb()));
            int shown = 0;
            int more = 0;
            for (SystemInfo s : service.pickable()) {
                boolean in = false;
                for (String id : st.systems()) {
                    in |= Catalog.key(id).equals(Catalog.key(s.id()));
                }
                if (!in) {
                    continue;
                }
                if (shown >= 15) {
                    more++;
                    continue;
                }
                shown++;
                String learn = tutorialPresent.getAsBoolean() ? " &8(/tutorial start " + lessonId(s.id()) + ")" : "";
                sender.sendMessage(ColorText.translate(" &e" + s.name() + "&7: " + s.blurb() + learn));
            }
            if (more > 0) {
                sender.sendMessage(ColorText.translate(" &7... and " + more + " more. &f/path&7 to pick."));
            }
            return;
        }
        SystemInfo s = cat.system(args[1]);
        if (s == null) {
            sender.sendMessage(ColorText.translate("&cThere is no style or system called " + args[1] + ". Try &f/path info&c."));
            return;
        }
        sender.sendMessage(ColorText.translate("&b&l" + s.name() + " &7- " + s.blurb()));
        List<String> in = new ArrayList<>();
        for (StyleInfo each : cat.stylesOf(s.id())) {
            in.add(each.title());
        }
        sender.sendMessage(ColorText.translate(s.locked() ? " &7Always on for everyone." : in.isEmpty() ? " &7In no style: add it by hand in &f/path&7." : " &7In: &f" + String.join(", ", in)));
        if (tutorialPresent.getAsBoolean()) {
            sender.sendMessage(ColorText.translate(" &7Learn it: &f/tutorial start " + lessonId(s.id())));
        }
    }

    /** NexusTutorial's lesson ids are the plugin name without the Nexus prefix. */
    static String lessonId(String plugin) {
        String k = Catalog.key(plugin);
        return k.startsWith("nexus") ? k.substring(5) : k;
    }

    private void tryIt(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(ColorText.translate("&cOnly players can borrow a system."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ColorText.translate("&7Usage: &f/path try <system>&7. &f/path status&7 shows what is hidden."));
            return;
        }
        PathService.TryResult r = service.tryIt(p, args[1]);
        if (!r.ok()) {
            service.say(p, service.fill(service.cfg().msgTryNo, null, java.util.Map.of("why", r.why())));
        }
    }

    private void status(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(ColorText.translate("&7Players only."));
            return;
        }
        Choice c = service.store().peek(p.getUniqueId());
        if (c == null || !c.chosen) {
            sender.sendMessage(ColorText.translate("&7You have not chosen yet, so you see everything. &f/path&7 to choose."));
            return;
        }
        if (PathService.bypasses(p)) {
            sender.sendMessage(ColorText.translate("&7Staff bypass is on for you: nothing is hidden, whatever you chose."));
        }
        Set<String> hidden = service.hiddenOf(p.getUniqueId());
        sender.sendMessage(ColorText.translate("&b&lYour game: &f" + (c.all ? "everything" : String.join(", ", service.summaryFor(p, c).get("styles")))));
        List<String> lent = new ArrayList<>();
        for (java.util.Map.Entry<String, Integer> b : service.borrowedOf(p.getUniqueId()).entrySet()) {
            lent.add(service.catalog().systemFor(b.getKey()).name() + " (" + b.getValue() + " min)");
        }
        if (!lent.isEmpty()) {
            sender.sendMessage(ColorText.translate("&7Borrowed: &a" + String.join("&7, &a", lent)));
        }
        if (hidden.isEmpty()) {
            sender.sendMessage(ColorText.translate("&7Nothing is hidden right now."));
            return;
        }
        List<String> names = new ArrayList<>();
        for (String k : hidden) {
            names.add(service.catalog().systemFor(k).name());
        }
        sender.sendMessage(ColorText.translate("&7Hidden (" + hidden.size() + "): &c" + String.join("&7, &c", names)));
        sender.sendMessage(ColorText.translate("&7Borrow one with &f/path try <system>&7."));
    }

    private void everything(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(ColorText.translate("&7Players only."));
            return;
        }
        Choice c = service.draftOf(p.getUniqueId());
        c.all = true;
        c.styles.clear();
        c.shown.clear();
        c.hidden.clear();
        service.commit(p, c);
    }

    private void reset(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            if (!admin(sender)) {
                sender.sendMessage(ColorText.translate("&cOnly staff can reset somebody else."));
                return;
            }
            Player t = Bukkit.getPlayerExact(args[1]);
            if (t == null) {
                sender.sendMessage(ColorText.translate("&c" + args[1] + " is not online."));
                return;
            }
            service.reset(t.getUniqueId(), t);
            service.say(t, service.cfg().msgReset);
            sender.sendMessage(ColorText.translate("&aCleared " + t.getName() + "'s choices."));
            return;
        }
        if (sender instanceof Player p) {
            service.reset(p.getUniqueId(), p);
            service.say(p, service.cfg().msgReset);
        } else {
            sender.sendMessage(ColorText.translate("&7Usage: /path reset <player>"));
        }
    }

    // ------------------------------------------------------------------ staff

    private void adminCommand(CommandSender sender, String sub, String[] args) {
        switch (sub) {
            case "reload" -> {
                reload.run();
                sender.sendMessage(ColorText.translate("&aNexusPath reloaded."));
            }
            case "check" -> check(sender);
            case "gate" -> gateCommand(sender, args);
            case "offer" -> offer(sender, args);
            case "set" -> {
                Player t = args.length < 3 ? null : Bukkit.getPlayerExact(args[1]);
                if (args.length < 3 || t == null) {
                    sender.sendMessage(ColorText.translate("&7Usage: &f/path set <online player> <style...|all>"));
                    return;
                }
                Choice c = new Choice();
                for (int i = 2; i < args.length; i++) {
                    if (args[i].equalsIgnoreCase("all")) {
                        c.all = true;
                    } else if (service.catalog().style(args[i]) != null) {
                        c.styles.add(Catalog.key(args[i]));
                    } else {
                        sender.sendMessage(ColorText.translate("&cThere is no style called " + args[i] + ". &f/path info&c lists them."));
                        return;
                    }
                }
                service.commit(t, c);
                sender.sendMessage(ColorText.translate("&aSet " + t.getName() + "'s game."));
            }
            default -> {
                Player t = args.length < 3 ? null : Bukkit.getPlayerExact(args[1]);
                SystemInfo s = args.length < 3 ? null : service.catalog().system(args[2]);
                if (t == null || s == null) {
                    sender.sendMessage(ColorText.translate("&7Usage: &f/path " + sub + " <online player> <system>"));
                    return;
                }
                Choice c = service.draftOf(t.getUniqueId());
                if (!c.chosen) {
                    c.all = true; // never chose: start from everything, then change just this one (commit marks them chosen)
                }
                c.setVisible(service.catalog(), service.cfg().unassignedVisible, s, sub.equals("show"));
                service.commit(t, c);
                sender.sendMessage(ColorText.translate("&a" + (sub.equals("show") ? "Showed " : "Hid ") + s.name() + (sub.equals("show") ? " to " : " from ") + t.getName() + "."));
            }
        }
    }

    /**
     * For NexusTutorial's reward commands: {@code path offer {player} {lesson}} opens the picker for a player who
     * has not chosen yet, but only when the lesson is one of {@code tutorial.offer-after-lessons}. Without a lesson
     * it always opens (for {@code final-commands}). Anything else is silently ignored, because the tutorial runs
     * it after every lesson.
     */
    private void offer(CommandSender sender, String[] args) {
        Player t = args.length < 2 ? null : Bukkit.getPlayerExact(args[1]);
        if (t == null) {
            if (args.length < 2) {
                sender.sendMessage(ColorText.translate("&7Usage: &f/path offer <online player> [lesson]"));
            }
            return;
        }
        if (args.length >= 3 && !service.cfg().offerAfterLessons.contains(Catalog.key(args[2]))) {
            return;
        }
        if (service.canOffer(t)) {
            PathGui.openStyles(service, t, service.draftOf(t.getUniqueId()));
        }
    }

    private void gateCommand(CommandSender sender, String[] args) {
        String what = args.length < 2 ? "status" : args[1].toLowerCase(Locale.ROOT);
        if (what.equals("on")) {
            gate.setEnabled(true);
        } else if (what.equals("off")) {
            gate.setEnabled(false);
        }
        sender.sendMessage(ColorText.translate("&7Event gate: " + (gate.enabled() ? "&aon" : "&coff")
                + "&7. &f" + gate.wrapped + "&7 handlers wrapped, &f" + gate.skipped + "&7 skipped so far."));
    }

    private void check(CommandSender sender) {
        List<String> problems = new ArrayList<>(service.catalog().warnings());
        for (String w : problems) {
            sender.sendMessage(ColorText.translate("&e" + w));
        }
        int missing = 0;
        List<String> have = new ArrayList<>();
        for (SystemInfo s : service.pickable()) {
            have.add(s.id());
        }
        for (SystemInfo s : service.catalog().systems()) {
            if (!s.locked() && !have.contains(s.id())) {
                missing++;
            }
        }
        sender.sendMessage(ColorText.translate("&7" + have.size() + " systems here can be picked; &f" + missing + "&7 in the catalog are not installed. "
                + service.alwaysOn().size() + " are always on."));
        if (problems.isEmpty()) {
            sender.sendMessage(ColorText.translate("&aThe catalog has no problems."));
        }
    }

    // ------------------------------------------------------------------ tab

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(PLAYER_SUBS);
            if (admin(sender)) {
                out.addAll(ADMIN_SUBS);
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("info"))) {
            service.catalog().styles().forEach(s -> out.add(s.id()));
            service.pickable().forEach(s -> out.add(s.id().toLowerCase(Locale.ROOT)));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("try")) {
            for (String k : sender instanceof Player p ? service.hiddenOf(p.getUniqueId()) : Set.<String>of()) {
                out.add(k);
            }
        } else if (args.length >= 2 && admin(sender) && List.of("set", "show", "hide", "reset", "open", "offer").contains(args[0].toLowerCase(Locale.ROOT))) {
            if (args.length == 2) {
                Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
            } else if (args[0].equalsIgnoreCase("set")) {
                service.catalog().styles().forEach(s -> out.add(s.id()));
                out.add("all");
            } else {
                service.catalog().systems().forEach(s -> out.add(s.id()));
            }
        }
        String typed = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(typed));
        return out;
    }
}
