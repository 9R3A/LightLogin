package com.lightlogin.command;

import com.lightlogin.LightLogin;
import com.lightlogin.database.DatabaseManager;
import com.lightlogin.discord.JDAIntegration;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.security.SecureRandom;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class PasswordForgetCommand implements CommandExecutor {
    private final LightLogin plugin;
    private final DatabaseManager databaseManager;
    private final JDAIntegration discordBot;
    private final Map<UUID, String> resetCodes = new HashMap<>();
    private final Map<UUID, Long> codeExpiry = new HashMap<>();
    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()";
    private static final int CODE_LENGTH = 26;
    private static final long CODE_EXPIRY_MINUTES = 10;

    public PasswordForgetCommand(LightLogin plugin, DatabaseManager databaseManager, JDAIntegration discordBot) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.discordBot = discordBot;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();

        if (args.length == 0) {
            // Request password reset code
            requestPasswordReset(player, uuid);
            return true;
        } else if (args.length >= 2) {
            // Verify code and update password
            String code = args[0];
            String newPassword = args[1];

            if (newPassword.length() < 6) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.prefix") + 
                    "&cPassword must be at least 6 characters long!"));
                return true;
            }

            processResetCode(player, code);
            verifyAndResetPassword(player, uuid, newPassword);
        } else {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("messages.prefix") + 
                plugin.getConfig().getString("messages.password-reset-usage")));
        }

        return true;
    }

    private void requestPasswordReset(Player player, UUID uuid) {
        // Check if Discord integration is enabled
        if (!plugin.isDiscordEnabled()) {
            player.sendMessage(ChatColor.RED + "Password reset via Discord is not available. Please contact an administrator.");
            return;
        }

        // Check if player has a linked Discord account
        String discordId = getDiscordId(uuid);
        if (discordId == null || discordId.isEmpty()) {
            player.sendMessage(ChatColor.RED + "You don't have a Discord account linked. Please contact an administrator.");
            return;
        }

        // Generate a new reset code
        String resetCode = generateResetCode();
        resetCodes.put(uuid, resetCode);
        codeExpiry.put(uuid, System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(CODE_EXPIRY_MINUTES));

        // Store the reset code in the database
        storeResetCode(uuid, resetCode);

        // Notify the player
        player.sendMessage(ChatColor.GREEN + "A password reset code has been sent to your linked Discord account.");
        player.sendMessage(ChatColor.YELLOW + "Please check your DMs from the Discord bot.");
    }

    private void verifyAndResetPassword(Player player, UUID uuid, String newPassword) {
        try {
            databaseManager.updatePassword(uuid, newPassword);
            resetCodes.remove(uuid);
            codeExpiry.remove(uuid);
            clearUsedResetCode(uuid);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("messages.prefix") + 
                plugin.getConfig().getString("messages.password-reset-success")));
        } catch (SQLException e) {
            player.sendMessage(ChatColor.RED + "An error occurred while resetting your password. Please try again later.");
            plugin.getLogger().severe("Error resetting password: " + e.getMessage());
        }
    }

    private void processResetCode(Player player, String code) {
        UUID uuid = player.getUniqueId();
        String storedCode = resetCodes.get(uuid);
        Long expiryTime = codeExpiry.get(uuid);

        if (storedCode == null || expiryTime == null) {
            player.sendMessage(ChatColor.RED + "No password reset request found. Please request a new reset code.");
            return;
        }

        if (expiryTime < System.currentTimeMillis()) {
            player.sendMessage(ChatColor.RED + "Reset code has expired. Please request a new one.");
            resetCodes.remove(uuid);
            codeExpiry.remove(uuid);
            clearUsedResetCode(uuid);
            return;
        }

        if (!storedCode.equalsIgnoreCase(code)) {
            player.sendMessage(ChatColor.RED + "Invalid reset code. Please try again or request a new code.");
            return;
        }

        // Code is valid, prompt for new password
        player.sendMessage(ChatColor.GREEN + "Please enter your new password now.");
    }

    private void clearUsedResetCode(UUID playerUuid) {
        String sql = "DELETE FROM password_reset_codes WHERE player_uuid = ?";
        try (var conn = databaseManager.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error clearing reset code: " + e.getMessage());
        }
    }

    private String getDiscordId(UUID playerUuid) {
        String sql = "SELECT discord_id FROM players WHERE uuid = ?";
        try (var conn = databaseManager.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            try (var rs = stmt.executeQuery()) {
                return rs.next() ? rs.getString("discord_id") : null;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error getting Discord ID: " + e.getMessage());
            return null;
        }
    }

    private void storeResetCode(UUID playerUuid, String code) {
        String sql = "INSERT INTO password_reset_codes (player_uuid, code, expires_at) VALUES (?, ?, ?)";
        try (var conn = databaseManager.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, code);
            stmt.setTimestamp(3, new Timestamp(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(CODE_EXPIRY_MINUTES)));
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error storing reset code: " + e.getMessage());
        }
    }

    private String generateResetCode() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
