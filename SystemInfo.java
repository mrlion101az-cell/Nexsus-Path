package com.nexuscraft.nexuspath;

/**
 * One thing a player can have in their game or not: a Nexus plugin.
 *
 * @param id        the plugin's name
 * @param locked    never hidden from anybody (safety, claims, money, moderation, staff tools)
 * @param world     shared with everyone, so hiding it only stops what the player does themselves
 * @param events    this plugin's event handlers may be skipped for players who hid it
 * @param byDefault on for everyone unless they switch it off, whatever styles they pick
 */
record SystemInfo(String id, String name, String icon, String blurb, boolean locked, boolean world, boolean events, boolean byDefault) {
}
