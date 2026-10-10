package com.nexuscraft.nexuspath;

import org.bukkit.entity.Player;

import java.util.Set;

/** Tells the rest of the server which systems a player has hidden (so other plugins can ask too). */
interface Exporter {
    /** @param hidden lower-case plugin names the player has hidden right now (empty clears it) */
    void publish(Player player, Set<String> hidden);

    void forget(Player player);
}
