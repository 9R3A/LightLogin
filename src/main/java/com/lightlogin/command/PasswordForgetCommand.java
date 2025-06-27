package com.lightlogin.command;

import com.lightlogin.LightLogin;
import com.lightlogin.database.DatabaseManager;
import com.lightlogin.discord.DiscordBot;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class PasswordForgetCommand implements CommandExecutor {
    private final LightLogin plugin;
    private final DatabaseManager databaseManager;
    private final DiscordBot discordBot;
    private final Map<UUID, String> resetCodes = new HashMap<>();
    private final Map<UUID, Long> codeExpiry = new HashMap<>();
    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()";
    private static final int CODE_LENGTH = 26;
    private static final long CODE_EXPIRY_MINUTES = 10;

    public PasswordForgetCommand(LightLogin plugin, DatabaseManager databaseManager, DiscordBot discordBot) {
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

            verifyAndResetPassword(player, uuid, code, newPassword);
        } else {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("messages.prefix") + 
                plugin.getConfig().getString("messages.password-reset-usage")));
        }

        return true;
    }

    private void requestPasswordReset(Player player, UUID uuid) {
        // Check if player has a linked Discord account
        String discordId = getDiscordId(uuid);
        if (discordId == null) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("messages.prefix") + 
                plugin.getConfig().getString("messages.password-reset-not-linked")));
            return;
        }

        // Generate a secure random code
        String code = generateSecureCode();
        resetCodes.put(uuid, code);
        codeExpiry.put(uuid, System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(CODE_EXPIRY_MINUTES));

        // Send the code via Discord DM
        String message = plugin.getConfig().getString("messages.password-reset-dm")
            .replace("%code%", code);

        discordBot.sendDirectMessage(discordId, message, player);

        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
            plugin.getConfig().getString("messages.prefix") + 
            plugin.getConfig().getString("messages.password-reset-requested")));
    }

    private void verifyAndResetPassword(Player player, UUID uuid, String code, String newPassword) {
        // Check if code exists and is not expired
        String savedCode = resetCodes.get(uuid);
        Long expiryTime = codeExpiry.get(uuid);

        if (savedCode == null || expiryTime == null || !savedCode.equals(code)) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("messages.prefix") + 
                plugin.getConfig().getString("messages.password-reset-invalid-code")));
            return;
        }

        if (System.currentTimeMillis() > expiryTime) {
            resetCodes.remove(uuid);
            codeExpiry.remove(uuid);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("messages.prefix") + 
                plugin.getConfig().getString("messages.password-reset-invalid-code")));
            return;
        }

        // Update password
        try {
            databaseManager.updatePassword(uuid, newPassword);
            resetCodes.remove(uuid);
            codeExpiry.remove(uuid);
            
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("messages.prefix") + 
                plugin.getConfig().getString("messages.password-reset-success")));
        } catch (SQLException e) {
            player.sendMessage(ChatColor.RED + "An error occurred while resetting your password. Please try again later.");
            plugin.getLogger().severe("Error resetting password: " + e.getMessage());
        }
    }

    private String getDiscordId(UUID playerUuid) {
        String sql = "SELECT discord_id FROM players WHERE uuid = ?";
        try (PreparedStatement stmt = databaseManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("discord_id");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error getting Discord ID: " + e.getMessage());
        }
        return null;
    }

    private String generateSecureCode() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
