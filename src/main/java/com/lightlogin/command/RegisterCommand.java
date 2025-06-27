package com.lightlogin.command;

import com.lightlogin.LightLogin;
import com.lightlogin.database.DatabaseManager;
import com.lightlogin.listener.PlayerListener;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class RegisterCommand implements CommandExecutor {
    private final LightLogin plugin = LightLogin.getInstance();
    private final DatabaseManager databaseManager = plugin.getDatabaseManager();
    private final PlayerListener playerListener;

    public RegisterCommand(PlayerListener playerListener) {
        this.playerListener = playerListener;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();

        // Check if already registered
        if (databaseManager.isPlayerRegistered(uuid)) {
            player.sendMessage(ChatColor.RED + "You are already registered! Use /login <password>");
            return true;
        }


        // Check arguments
        if (args.length != 2) {
            player.sendMessage(ChatColor.RED + "Usage: /register <password> <confirmPassword>");
            return true;
        }

        String password = args[0];
        String confirmPassword = args[1];

        // Validate password
        if (!password.equals(confirmPassword)) {
            player.sendMessage(ChatColor.RED + "Passwords do not match!");
            return true;
        }

        if (password.length() < 6) {
            player.sendMessage(ChatColor.RED + "Password must be at least 6 characters long!");
            return true;
        }

        // Register the player
        if (databaseManager.registerPlayer(player, password)) {
            playerListener.setLoggedIn(uuid, true);
            player.sendMessage(ChatColor.GREEN + "Successfully registered and logged in!");
            
            // TODO: Send welcome message and instructions for Discord linking
        } else {
            player.sendMessage(ChatColor.RED + "Failed to register. Please try again later.");
        }

        return true;
    }
}
