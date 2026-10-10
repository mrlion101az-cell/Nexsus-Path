package com.nexuscraft.nexuspath;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Settings from config.yml, with every value held to something sensible. */
final class PathConfig {

    boolean unassignedVisible = true;
    String inviteMode = "join";
    int inviteDelaySeconds = 90;
    int maxReminders = 3;
    int tryMinutes = 10;
    boolean eventsEnabled = true;
    boolean exportPermissions = true;
    boolean exportMetadata = true;
    /** Lesson ids (lower case) whose completion in NexusTutorial opens the picker for a player who has not chosen. */
    List<String> offerAfterLessons = new ArrayList<>();

    String prefix = "&b&l[Path] &r";
    String msgInvite = "&eWelcome! Choose how you want to play and the server will fit itself around you: &f/path";
    String msgSaved = "&aYour game is set: {styles}. &f{shown} &asystems on, &f{hidden} &ahidden. Change your mind any time with &f/path&a.";
    String msgEverything = "&aYou chose everything: nothing is hidden. &f/path&a changes that.";
    String msgBlocked = "&7{system} is not part of the game you chose. &e/path try {id}&7 borrows it for {minutes} minutes, or &e/path&7 to change your game.";
    String msgTryStart = "&aBorrowing &f{system}&a for {minutes} minutes.";
    String msgTryEnd = "&e{system}&7 goes back out of your game.";
    String msgTryNo = "&cYou cannot borrow that: {why}";
    String msgReset = "&7Your choices are cleared: you see everything again. &f/path&7 to choose.";

    void load(ConfigurationSection c) {
        unassignedVisible = !"hide".equals(c.getString("unassigned", "show").toLowerCase(Locale.ROOT));
        inviteMode = "off".equals(c.getString("invite.mode", inviteMode).toLowerCase(Locale.ROOT)) ? "off" : "join";
        inviteDelaySeconds = clamp(c.getInt("invite.delay-seconds", inviteDelaySeconds), 0, 3600);
        maxReminders = clamp(c.getInt("invite.max-reminders", maxReminders), 1, 50);
        tryMinutes = clamp(c.getInt("try.minutes", tryMinutes), 1, 240);
        eventsEnabled = c.getBoolean("events.enabled", eventsEnabled);
        exportPermissions = c.getBoolean("export.permissions", exportPermissions);
        exportMetadata = c.getBoolean("export.metadata", exportMetadata);
        offerAfterLessons = new ArrayList<>();
        for (String id : c.getStringList("tutorial.offer-after-lessons")) {
            offerAfterLessons.add(Catalog.key(id));
        }
        prefix = c.getString("messages.prefix", prefix);
        msgInvite = c.getString("messages.invite", msgInvite);
        msgSaved = c.getString("messages.saved", msgSaved);
        msgEverything = c.getString("messages.everything", msgEverything);
        msgBlocked = c.getString("messages.blocked", msgBlocked);
        msgTryStart = c.getString("messages.try-start", msgTryStart);
        msgTryEnd = c.getString("messages.try-end", msgTryEnd);
        msgTryNo = c.getString("messages.try-no", msgTryNo);
        msgReset = c.getString("messages.reset", msgReset);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
