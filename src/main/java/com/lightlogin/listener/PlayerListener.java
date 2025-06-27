package com.lightlogin.listener;

import com.lightlogin.LightLogin;
import com.lightlogin.database.DatabaseManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerListener implements Listener {
    private final Set<UUID> loggedInPlayers = new HashSet<>();
    private final LightLogin plugin;
    private final DatabaseManager databaseManager;

    public PlayerListener(LightLogin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String ipAddress = player.getAddress() != null ? 
                         player.getAddress().getAddress().getHostAddress() : "unknown";

        // Check if player is registered
        if (!databaseManager.isPlayerRegistered(uuid)) {
            // Player needs to register
            player.sendMessage("§aWelcome! Please register using /register <password> <confirmPassword>");
        } else {
            // Check for auto-login
            // Check if auto-login is enabled and IP matches if required
            if (plugin.getConfig().getBoolean("auto-login.enabled", true)) {
                boolean checkIp = plugin.getConfig().getBoolean("session.check-ip", false);
                
                if (databaseManager.hasValidSession(uuid, ipAddress, checkIp)) {
                    setLoggedIn(uuid, true);
                    player.sendMessage(plugin.getConfig().getString("messages.auto-login", "§aAutomatically logged in!"));
                    
                    // Update last login time
                    databaseManager.updateLastLogin(uuid, ipAddress);
                    return;
                }
            }
            
            player.sendMessage(plugin.getConfig().getString("messages.login-required", "§aWelcome back! Please login using /login <password>"));
        }

        // Save/update IP and last login
        databaseManager.updateLastLogin(uuid, ipAddress);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        loggedInPlayers.remove(uuid);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!isLoggedIn(event.getPlayer().getUniqueId())) {
            // Prevent movement if not logged in
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!isLoggedIn(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!isLoggedIn(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (!isLoggedIn(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        if (!isLoggedIn(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!isLoggedIn(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!isLoggedIn(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (!isLoggedIn(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            Player player = (Player) event.getDamager();
            if (!isLoggedIn(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            if (!isLoggedIn(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            if (!isLoggedIn(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    public boolean isLoggedIn(UUID uuid) {
        return loggedInPlayers.contains(uuid);
    }

    public void setLoggedIn(UUID uuid, boolean loggedIn) {
        if (loggedIn) {
            loggedInPlayers.add(uuid);
        } else {
            loggedInPlayers.remove(uuid);
        }
    }
}
