package com.lightlogin.command;

import com.lightlogin.LightLogin;
import com.lightlogin.discord.DiscordBot;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class DiscordLinkCommand implements CommandExecutor {
    private final LightLogin plugin;
    private final DiscordBot discordBot;

    public DiscordLinkCommand(LightLogin plugin, DiscordBot discordBot) {
        this.plugin = plugin;
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

        if (args.length < 1) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                plugin.getConfig().getString("messages.prefix") + 
                "&cUsage: /discord <link|unlink> [discord-username]"));
            return true;
        }

        if (args[0].equalsIgnoreCase("link")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                    plugin.getConfig().getString("messages.prefix") + 
                    "&cUsage: /discord link <discord-username>"));
                return true;
            }

            String discordUsername = args[1];
            String[] discordParts = discordUsername.split("#");
            
            // Check if Discord username is in the correct format
            if (discordParts.length != 2 || discordParts[0].isEmpty() || discordParts[1].length() != 4) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                    plugin.getConfig().getString("messages.prefix") + 
                    plugin.getConfig().getString("messages.discord-invalid-username")));
                return true;
            }

            // Check if Discord account is already linked to another player
            if (plugin.getDatabaseManager().isDiscordAccountLinked(discordUsername)) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                    plugin.getConfig().getString("messages.prefix") + 
                    plugin.getConfig().getString("messages.discord-already-linked")));
                return true;
            }

            // Generate and send verification code
            String verificationCode = discordBot.generateVerificationCode(uuid);
            if (verificationCode == null) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                    plugin.getConfig().getString("messages.prefix") + 
                    plugin.getConfig().getString("messages.discord-verification-failed")));
                return true;
            }

            // Send DM to the user with the verification code
            String discordId = discordParts[0];
            discordBot.sendVerificationMessage(discordId, verificationCode);
            
            // Send success message to player
            String successMessage = plugin.getConfig().getString("messages.discord-verify-instructions")
                .replace("%code%", verificationCode);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                plugin.getConfig().getString("messages.prefix") + successMessage));
            
        } else if (args[0].equalsIgnoreCase("unlink")) {
            // Handle unlinking Discord account
            if (plugin.getDatabaseManager().unlinkDiscordAccount(uuid)) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                    plugin.getConfig().getString("messages.prefix") + 
                    plugin.getConfig().getString("messages.discord-unlinked")));
            } else {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                    plugin.getConfig().getString("messages.prefix") + 
                    plugin.getConfig().getString("messages.discord-not-linked")));
            }
        } else {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                plugin.getConfig().getString("messages.prefix") + 
                "&cUsage: /discord <link|unlink> [discord-username]"));
        }

        return true;
    }
}
