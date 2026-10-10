package com.nexuscraft.nexuspath;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static com.nexuscraft.nexuspath.Kit.expect;

/** The plugin class itself, started and stopped against the stub server, with a real catalogue and saved choices. */
public final class PluginTest {

    static final List<String> seen = new ArrayList<>();

    static String line(String path, String type, String value) {
        return path + "\t" + type + "\t" + value;
    }

    static void listen(HandlerList list, Plugin plugin, String tag) {
        list.register(new RegisteredListener(new Listener() { }, (l, ev) -> seen.add(tag), EventPriority.NORMAL, plugin, false));
    }

    static int gated(HandlerList list) {
        int n = 0;
        for (RegisteredListener rl : list.getRegisteredListeners()) {
            if (rl instanceof ListenerGate.Gated) {
                n++;
            }
        }
        return n;
    }

    /** Puts the custom catalogue back to the original (no entry for Farming). */
    static void restoreFarming(File dir) throws Exception {
        Files.writeString(new File(dir, "custom-catalog.yml").toPath(), String.join("\n",
                line("systems.NexusRime.blurb", "s", "Cold"),
                line("styles.odd.title", "s", "Odd"),
                line("styles.odd.systems", "l", "NexusGhost\u0001NexusRime")) + "\n");
    }

    static String run(CommandExecutor ex, org.bukkit.command.CommandSender who, String... args) {
        CommandTest.Console out = who instanceof CommandTest.Console c ? c : null;
        ex.onCommand(who, new Command(), "path", args);
        return out == null ? "" : String.join("\n", out.lines);
    }

    public static void main(String[] args) throws Exception {
        Kit.reset();
        File dir = Files.createTempDirectory("nexuspath-plugin").toFile();
        JavaPlugin.DATA_FOLDER = dir;
        // the catalogue "inside the jar" (in the stub's own file format)
        JavaPlugin.RESOURCES.put("catalog.yml", String.join("\n",
                line("systems.NexusFarming.name", "s", "Farming"),
                line("systems.NexusRime.name", "s", "Rime"),
                line("systems.NexusRime.blurb", "s", "Hordes"),
                line("systems.NexusClaims.name", "s", "Claims"),
                line("systems.NexusClaims.locked", "b", "true"),
                line("systems.NexusTrials.name", "s", "Trials"),
                line("systems.NexusTrials.events", "b", "false"),
                line("systems.NexusAngler.name", "s", "Angler"),
                line("styles.survivor.title", "s", "Survivor"),
                line("styles.survivor.systems", "l", "NexusRime\u0001NexusTrials"),
                line("styles.farmer.title", "s", "Farmer"),
                line("styles.farmer.systems", "l", "NexusFarming\u0001NexusAngler")));
        // the server's own additions
        Files.writeString(new File(dir, "custom-catalog.yml").toPath(), String.join("\n",
                line("systems.NexusRime.blurb", "s", "Cold"),
                line("styles.odd.title", "s", "Odd"),
                line("styles.odd.systems", "l", "NexusGhost\u0001NexusRime")) + "\n");

        // what the plugin logs
        java.util.logging.Logger log = java.util.logging.Logger.getLogger("stub");
        List<String> infos = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        log.addHandler(new java.util.logging.Handler() {
            @Override
            public void publish(java.util.logging.LogRecord r) {
                (r.getLevel() == java.util.logging.Level.WARNING ? warnings : infos).add(r.getMessage());
            }

            @Override
            public void flush() { }

            @Override
            public void close() { }
        });

        Kit.NexusFarming farming = new Kit.NexusFarming();
        Kit.NexusClaims claims = new Kit.NexusClaims();
        Kit.NexusTrials trials = new Kit.NexusTrials();
        Kit.NexusRime rime = new Kit.NexusRime();
        Kit.NexusTutorial tutorial = new Kit.NexusTutorial();
        Kit.Vault vault = new Kit.Vault();
        for (Plugin p : new Plugin[] {farming, claims, trials, rime, tutorial, vault}) {
            PluginManager.PLUGINS.add(p);
        }
        HandlerList moves = new HandlerList();
        HandlerList dispenses = new HandlerList();
        for (HandlerList list : new HandlerList[] {moves, dispenses}) {
            String kind = list == moves ? "move" : "dispense";
            listen(list, farming, "farming:" + kind);
            listen(list, claims, "claims:" + kind);
            listen(list, trials, "trials:" + kind);
            listen(list, rime, "rime:" + kind);
            listen(list, tutorial, "tutorial:" + kind);
            listen(list, vault, "vault:" + kind);
        }

        // Ann chose Survivor in an earlier life of the server
        Kit.TestPlayer ann = Kit.online(new Kit.TestPlayer("Ann", 0, 0));
        Files.writeString(new File(dir, "players.yml").toPath(), String.join("\n",
                line("players." + ann.getUniqueId() + ".chosen", "b", "true"),
                line("players." + ann.getUniqueId() + ".styles", "l", "survivor")) + "\n");

        PathPlugin plugin = new PathPlugin();
        plugin.onEnable();

        expect(JavaPlugin.CALLS.contains("saveDefaultConfig") && JavaPlugin.CALLS.contains("saveResource:custom-catalog.yml:false"), "default files are written: " + JavaPlugin.CALLS);
        boolean hasListener = false;
        boolean hasPlugin = false;
        PathListener pathListener = null;
        for (Listener l : PluginManager.REGISTERED) {
            if (l instanceof PathListener pl) {
                hasListener = true;
                pathListener = pl;
            }
            if (l == plugin) {
                hasPlugin = true;
            }
        }
        expect(hasListener && hasPlugin, "both listeners are registered");
        expect(BukkitScheduler.REPEATING.size() == 2, "the tick and save tasks are scheduled");
        expect(infos.contains("NexusPath enabled -- 5 systems, 3 styles."), "the start-up line says what was loaded: " + infos);
        expect(infos.contains("NexusPath: 4 event handlers of other plugins are now gated."), "and how many handlers were wrapped: " + infos);
        expect(warnings.size() == 1 && warnings.get(0).contains("NexusGhost"), "catalogue problems are warned about: " + warnings);

        // wrapped: farming and rime (listed, not locked, events on). Not claims (locked), trials (events off), tutorial (infrastructure), vault.
        expect(gated(moves) == 2 && gated(dispenses) == 2, "exactly the two gate-able plugins are wrapped: " + gated(moves));
        for (RegisteredListener rl : moves.getRegisteredListeners()) {
            if (rl instanceof ListenerGate.Gated g) {
                expect(g.getPlugin() == farming || g.getPlugin() == rime, "only farming and rime are wrapped");
            }
        }

        // the saved choice was loaded and applied to the player who is online
        expect(plugin != null, "plugin started");
        PlayerMoveEvent mv = new PlayerMoveEvent(ann, null, null);
        seen.clear();
        moves.call(mv);
        expect(!seen.contains("farming:move"), "Ann hid farming: skipped");
        expect(seen.contains("rime:move") && seen.contains("trials:move") && seen.contains("claims:move") && seen.contains("tutorial:move") && seen.contains("vault:move"), "everything else runs: " + seen);
        expect(ann.stubAttachments().size() == 1 && ann.stubAttachments().get(0).getPermissions().containsKey("nexuspath.hidden.nexusfarming"), "the hidden set is exported to her permissions");
        expect(!ann.stubAttachments().get(0).getPermissions().containsKey("nexuspath.hidden.nexusclaims") && !ann.stubAttachments().get(0).getPermissions().containsKey("nexuspath.hidden.nexustutorial"), "locked and infrastructure plugins are not hidden");

        // block ownership goes through the plugin's own wiring
        Block box = Kit.WORLD.getBlockAt(5, 64, 5);
        pathListener.onInteract(new PlayerInteractEvent(ann, Action.RIGHT_CLICK_BLOCK, null, box));
        seen.clear();
        dispenses.call(new BlockDispenseEvent(box, null, null));
        expect(!seen.contains("farming:dispense") && seen.contains("rime:dispense"), "a dispenser Ann used is hers: farming skipped: " + seen);
        seen.clear();
        dispenses.call(new BlockDispenseEvent(Kit.WORLD.getBlockAt(9, 9, 9), null, null));
        expect(seen.contains("farming:dispense"), "a dispenser nobody touched runs everything");

        // the command is wired up, and reads the custom catalogue
        CommandExecutor ex = plugin.getCommand("path").getExecutor();
        expect(ex instanceof PathCommand, "the /path executor is installed");
        expect(plugin.getCommand("path").getTabCompleter() instanceof PathCommand, "and its tab completer");
        CommandTest.Console con = new CommandTest.Console();
        con.perms.add("nexuspath.admin");
        run(ex, con, "info", "NexusRime");
        expect(con.heard("Cold") && !con.heard("Hordes"), "the custom catalogue overrides the built-in one: " + con.lines);
        con.lines.clear();
        run(ex, con, "gate");
        expect(con.heard("Event gate: ") && con.heard("on") && con.heard("4"), "the gate is on and has wrapped 4 handlers: " + con.lines);

        // /path set writes through, the save task flushes it
        run(ex, con, "set", "Ann", "farmer");
        expect(!ann.stubAttachments().isEmpty() && !ann.stubAttachments().get(0).getPermissions().containsKey("nexuspath.hidden.nexusfarming"), "set takes effect at once");
        File saved = new File(dir, "players.yml");
        String before = Files.readString(saved.toPath());
        BukkitScheduler.REPEATING.get(1).run();
        String after = Files.readString(saved.toPath());
        expect(!before.equals(after) && after.contains("farmer"), "the save task writes players.yml");

        // a plugin that enables late is gated and everyone is refreshed
        run(ex, con, "set", "Ann", "survivor");
        expect(!ann.stubAttachments().get(0).getPermissions().containsKey("nexuspath.hidden.nexusangler"), "Angler is not installed yet, so it is not hidden");
        Kit.NexusAngler angler = new Kit.NexusAngler();
        PluginManager.PLUGINS.add(angler);
        listen(moves, angler, "angler:move");
        plugin.onPluginEnable(new PluginEnableEvent(angler));
        expect(gated(moves) == 3, "the late plugin's handler is wrapped too (Angler is listed): " + gated(moves));
        expect(ann.stubAttachments().get(0).getPermissions().containsKey("nexuspath.hidden.nexusangler"), "and Ann, who chose Survivor, now hides it without anyone doing anything");
        Kit.NexusAngler angler2 = new Kit.NexusAngler();
        PluginManager.PLUGINS.add(angler2);
        plugin.onPluginEnable(new PluginEnableEvent(plugin));
        expect(gated(moves) == 3, "NexusPath's own enable event changes nothing");

        // reload re-reads the catalogue: new blurb, Farming joins Survivor (so Ann stops hiding it), Farming's handlers are no longer gated
        Files.writeString(new File(dir, "custom-catalog.yml").toPath(), String.join("\n",
                line("systems.NexusRime.blurb", "s", "Warm"),
                line("systems.NexusFarming.events", "b", "false"),
                line("styles.survivor.add-systems", "l", "NexusFarming"),
                line("styles.odd.title", "s", "Odd"),
                line("styles.odd.systems", "l", "NexusGhost\u0001NexusRime")) + "\n");
        expect(ann.stubAttachments().get(0).getPermissions().containsKey("nexuspath.hidden.nexusfarming"), "before the reload Ann hides farming");
        run(ex, con, "reload");
        con.lines.clear();
        run(ex, con, "info", "NexusRime");
        expect(con.heard("Warm") && !con.heard("Cold"), "the reload picked up the new blurb: " + con.lines);
        expect(!ann.stubAttachments().get(0).getPermissions().containsKey("nexuspath.hidden.nexusfarming"), "and everyone was refreshed: Ann no longer hides farming");
        int farmingWrapped = 0;
        for (RegisteredListener rl : moves.getRegisteredListeners()) {
            if (rl instanceof ListenerGate.Gated g && g.getPlugin() == farming) {
                farmingWrapped++;
            }
        }
        expect(farmingWrapped == 0 && gated(moves) == 2, "Farming's handlers were unwrapped because its entry now says events: false: " + gated(moves));
        con.lines.clear();
        run(ex, con, "check");
        expect(con.lines.stream().filter(l -> l.contains("NexusGhost")).count() == 1, "the catalogue was cleared before it was loaded again (one warning, not two): " + con.lines);
        run(ex, con, "set", "Ann", "survivor");
        PluginTest.restoreFarming(dir);
        run(ex, con, "reload");
        expect(gated(moves) == 3, "wrapped again when the entry is gone: " + gated(moves));

        // reload picks up config changes and does not wrap twice
        plugin.getConfig().set("events.enabled", false);
        JavaPlugin.CALLS.clear();
        run(ex, con, "reload");
        expect(JavaPlugin.CALLS.contains("reloadConfig"), "reload re-reads the config file");
        expect(gated(moves) == 3, "reload does not wrap twice: " + gated(moves));
        seen.clear();
        con.lines.clear();
        run(ex, con, "gate");
        expect(con.heard("off"), "events.enabled false switches the gate off: " + con.lines);
        moves.call(mv);
        expect(seen.contains("farming:move"), "with the gate off nothing is skipped");

        // switching off restores every handler, stops the tasks and saves
        String beforeDisable = Files.readString(saved.toPath());
        run(ex, con, "set", "Ann", "all");
        expect(Files.readString(saved.toPath()).equals(beforeDisable), "set only changes memory until the next save");
        plugin.onDisable();
        expect(BukkitScheduler.HANDLES.size() == 2 && BukkitScheduler.HANDLES.stream().allMatch(org.bukkit.scheduler.BukkitTask::isCancelled), "both repeating tasks are cancelled");
        expect(!Files.readString(saved.toPath()).equals(beforeDisable), "disable saves what was changed since the last save");
        expect(gated(moves) == 0 && gated(dispenses) == 0, "disable puts every original handler back");
        expect(moves.getRegisteredListeners().length == 7 && dispenses.getRegisteredListeners().length == 6, "and loses none");

        // a second server start with the gate switched off from the start
        Kit.reset();
        JavaPlugin.DATA_FOLDER = dir;
        JavaPlugin.RESOURCES.put("catalog.yml", "");
        PathPlugin second = new PathPlugin();
        second.getConfig().set("events.enabled", false);
        second.onEnable();
        CommandTest.Console con2 = new CommandTest.Console();
        con2.perms.add("nexuspath.admin");
        run(second.getCommand("path").getExecutor(), con2, "gate");
        expect(con2.heard("off"), "the gate starts off when the config says so");
        second.onDisable();

        Kit.finish();
    }
}
