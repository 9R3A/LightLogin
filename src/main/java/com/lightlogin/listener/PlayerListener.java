package com.lightlogin.listener;

import com.lightlogin.LightLogin;
import com.lightlogin.database.DatabaseManager;
import org.bukkit.Bukkit;
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
// Floodgate API is optional - checked at runtime using reflection

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

        // Handle Bedrock players
        if (isBedrockPlayer(player)) {
            handleBedrockPlayer(player, uuid, ipAddress);
            return;
        }

        // Handle Java players
        handleJavaPlayer(player, uuid, ipAddress);
    }
    
    private boolean isBedrockPlayer(Player player) {
        try {
            // Check if Floodgate plugin is installed and loaded
            if (Bukkit.getPluginManager().getPlugin("floodgate") == null) {
                return false;
            }
            
            // Use reflection to check if player is from Bedrock
            try {
                // Get Floodgate API class
                Class<?> floodgateApiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                // Get getInstance() method
                java.lang.reflect.Method getInstanceMethod = floodgateApiClass.getMethod("getInstance");
                // Invoke getInstance()
                Object floodgateApi = getInstanceMethod.invoke(null);
                // Get isFloodgatePlayer method
                java.lang.reflect.Method isFloodgatePlayerMethod = floodgateApiClass.getMethod("isFloodgatePlayer", java.util.UUID.class);
                // Call isFloodgatePlayer with player's UUID
                return (boolean) isFloodgatePlayerMethod.invoke(floodgateApi, player.getUniqueId());
            } catch (ClassNotFoundException e) {
                plugin.getLogger().info("Floodgate API not found. Bedrock support disabled.");
                plugin.getLogger().info("To enable Bedrock support, install Floodgate on your server.");
                return false;
            } catch (Exception e) {
                plugin.getLogger().warning("Error checking Bedrock player status: " + e.getMessage());
                return false;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error checking if player is from Bedrock: " + e.getMessage());
            return false;
        }
    }
    
    private void handleBedrockPlayer(Player player, UUID uuid, String ipAddress) {
        // Auto-register Bedrock players if not registered
        if (!databaseManager.isPlayerRegistered(uuid)) {
            // Generate a random secure password for the Bedrock player
            String password = generateRandomPassword();
            if (databaseManager.registerPlayer(player, password)) {
                player.sendMessage(plugin.getConfig().getString(
                    "messages.bedrock-auto-register", 
                    "§aWelcome Bedrock player! You've been automatically registered."));
                setLoggedIn(uuid, true);
            } else {
                player.kickPlayer(plugin.getConfig().getString(
                    "messages.registration-error",
                    "§cFailed to register your account. Please contact an administrator."));
            }
        } else {
            // Auto-login Bedrock players
            setLoggedIn(uuid, true);
            player.sendMessage(plugin.getConfig().getString(
                "messages.bedrock-auto-login",
                "§aWelcome back, Bedrock player! You've been automatically logged in."));
        }
        databaseManager.updateLastLogin(uuid, ipAddress);
    }
    
    private void handleJavaPlayer(Player player, UUID uuid, String ipAddress) {
        // Check if player is registered
        if (!databaseManager.isPlayerRegistered(uuid)) {
            // Player needs to register
            player.sendMessage(plugin.getConfig().getString(
                "messages.register-prompt",
                "§aWelcome! Please register using /register <password> <confirmPassword>"));
        } else {
            // Check for auto-login if enabled
            if (plugin.getConfig().getBoolean("auto-login.enabled", true)) {
                boolean checkIp = plugin.getConfig().getBoolean("session.check-ip", false);
                
                if (databaseManager.hasValidSession(uuid, ipAddress, checkIp)) {
                    setLoggedIn(uuid, true);
                    player.sendMessage(plugin.getConfig().getString(
                        "messages.auto-login", 
                        "§aAutomatically logged in!"));
                    
                    // Update last login time
                    databaseManager.updateLastLogin(uuid, ipAddress);
                    return;
                }
            }
            
            player.sendMessage(plugin.getConfig().getString(
                "messages.login-required", 
                "§aWelcome back! Please login using /login <password>"));
        }

        // Save/update IP and last login
        databaseManager.updateLastLogin(uuid, ipAddress);
    }
    
    private String generateRandomPassword() {
        // Generate a secure random password for Bedrock players
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()";
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            int index = (int)(Math.random() * chars.length());
            sb.append(chars.charAt(index));
        }
        return sb.toString();
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
