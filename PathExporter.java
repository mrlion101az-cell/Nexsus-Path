package com.nexuscraft.nexuspath;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Publishes what each player has hidden in two ways any other plugin can read without depending on
 * this one: a permission node {@code nexuspath.hidden.<plugin name in lower case>} that is true for
 * the player while they have that plugin hidden, and the same list in the player's persistent data
 * under the key {@code nexuspath:hidden} (comma separated).
 */
final class PathExporter implements Exporter {

    private final Plugin plugin;
    private final PathConfig cfg;
    private final NamespacedKey key;
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();

    PathExporter(Plugin plugin, PathConfig cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.key = new NamespacedKey(plugin, "hidden");
    }

    @Override
    public void publish(Player player, Set<String> hidden) {
        if (cfg.exportPermissions) {
            PermissionAttachment old = attachments.remove(player.getUniqueId());
            if (old != null) {
                player.removeAttachment(old);
            }
            if (!hidden.isEmpty()) {
                PermissionAttachment a = player.addAttachment(plugin);
                for (String k : hidden) {
                    a.setPermission("nexuspath.hidden." + k, true);
                }
                attachments.put(player.getUniqueId(), a);
            }
        }
        if (cfg.exportMetadata) {
            if (hidden.isEmpty()) {
                player.getPersistentDataContainer().remove(key);
            } else {
                player.getPersistentDataContainer().set(key, PersistentDataType.STRING, String.join(",", new TreeSet<>(hidden)));
            }
        }
    }

    @Override
    public void forget(Player player) {
        PermissionAttachment old = attachments.remove(player.getUniqueId());
        if (old != null) {
            player.removeAttachment(old);
        }
    }
}
