package com.lightlogin.command;

import com.lightlogin.LightLogin;
import com.lightlogin.discord.JDAIntegration;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class DiscordLinkCommand implements CommandExecutor {
    private final LightLogin plugin;
    private final JDAIntegration discordBot;

    public DiscordLinkCommand(LightLogin plugin, JDAIntegration discordBot) {
        this.plugin = plugin;
        this.discordBot = discordBot;
    }

    private String generateVerificationCode(UUID playerUuid) {
        // Generate a random 6-digit code
        int code = 100000 + new java.util.Random().nextInt(900000);
        String codeStr = String.valueOf(code);
        
        // Store the code in the database
        String sql = "INSERT INTO verification_codes (code, player_uuid, expires_at) VALUES (?, ?, DATE_ADD(NOW(), INTERVAL 10 MINUTE))";
        try (var conn = plugin.getDatabaseManager().getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, codeStr);
            stmt.setString(2, playerUuid.toString());
            stmt.executeUpdate();
            return codeStr;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to generate verification code: " + e.getMessage());
            return null;
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 1) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                plugin.getConfig().getString("messages.prefix", "&7[LightLogin] &c") + 
                "Usage: /discord <link|unlink>"));
            return true;
        }

        if (args[0].equalsIgnoreCase("link")) {
            if (!plugin.isDiscordEnabled()) {
                player.sendMessage(ChatColor.RED + "Discord integration is not enabled on this server.");
                return true;
            }
            
            // Generate a verification code
            String code = generateVerificationCode(player.getUniqueId());
            if (code == null) {
                player.sendMessage(ChatColor.RED + "Failed to generate verification code. Please try again later.");
                return true;
            }
            
            // Send instructions to the player
            player.sendMessage(ChatColor.GREEN + "Please check your DMs from the Discord bot and use the following command there:");
            player.sendMessage(ChatColor.YELLOW + "/verify " + code);
            return true;
        } else if (args[0].equalsIgnoreCase("unlink")) {
            // Unlink Discord account
            String sql = "UPDATE players SET discord_id = NULL WHERE uuid = ?";
            try (var conn = plugin.getDatabaseManager().getConnection();
                 var stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, player.getUniqueId().toString());
                int updated = stmt.executeUpdate();
                
                if (updated > 0) {
                    player.sendMessage(ChatColor.GREEN + "Successfully unlinked your Discord account!");
                } else {
                    player.sendMessage(ChatColor.RED + "No Discord account was linked to your Minecraft account.");
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to unlink Discord account: " + e.getMessage());
                player.sendMessage(ChatColor.RED + "Failed to unlink your Discord account. Please try again later.");
            }
        } else {
            player.sendMessage(ChatColor.RED + "Unknown subcommand. Use /discord link or /discord unlink");
        }
        
        return true;
    }
}
