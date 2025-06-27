package com.lightlogin.command;

import com.lightlogin.LightLogin;
import com.lightlogin.database.DatabaseManager;
import com.lightlogin.listener.PlayerListener;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.jetbrains.annotations.NotNull;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

public class LoginCommand implements CommandExecutor {
    private final LightLogin plugin = LightLogin.getInstance();
    private final DatabaseManager databaseManager = plugin.getDatabaseManager();
    private final PlayerListener playerListener;

    public LoginCommand(PlayerListener playerListener) {
        this.playerListener = playerListener;
    }

    private String generateSessionToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();

        // Check if already logged in
        if (playerListener.isLoggedIn(uuid)) {
            player.sendMessage(ChatColor.YELLOW + "You are already logged in!");
            return true;
        }

        // Check if registered
        if (!databaseManager.isPlayerRegistered(uuid)) {
            player.sendMessage(ChatColor.RED + "You need to register first using /register <password> <confirmPassword>");
            return true;
        }

        // Check password
        if (args.length != 1) {
            player.sendMessage(ChatColor.RED + "Usage: /login <password>");
            return true;
        }

        String password = args[0];

        // Validate password
        if (databaseManager.validatePassword(uuid, password)) {
            playerListener.setLoggedIn(uuid, true);
            player.sendMessage(ChatColor.GREEN + "Successfully logged in!");
            
            // Update last login
            String ipAddress = player.getAddress() != null ? 
                             player.getAddress().getAddress().getHostAddress() : "unknown";
            databaseManager.updateLastLogin(uuid, ipAddress);
            
            // Create session for auto-login
            if (plugin.getConfig().getBoolean("auto-login.enabled", true)) {
                String token = generateSessionToken();
                long sessionDuration = plugin.getConfig().getLong("session.duration", 2592000); // 30 days default
                databaseManager.createSession(uuid, token, sessionDuration);
                
                // Store session in player's metadata for future validation
                player.setMetadata("session_token", new FixedMetadataValue(plugin, token));
            }
        } else {
            player.sendMessage(ChatColor.RED + "Incorrect password!");
        }

        return true;
    }
}
