package com.lightlogin;

import com.lightlogin.command.DiscordLinkCommand;
import com.lightlogin.command.LoginCommand;
import com.lightlogin.command.PasswordForgetCommand;
import com.lightlogin.command.RegisterCommand;
import com.lightlogin.database.DatabaseManager;
import com.lightlogin.discord.DiscordBot;
import com.lightlogin.listener.PlayerListener;
import java.util.UUID;
import org.bukkit.plugin.java.JavaPlugin;


public class LightLogin extends JavaPlugin {
    private static LightLogin instance;
    private DatabaseManager databaseManager;
    private DiscordBot discordBot;
    private PlayerListener playerListener;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        
        try {
            // Initialize database
            this.databaseManager = new DatabaseManager(this);
            databaseManager.initializeDatabase();
            
            // Initialize player listener
            this.playerListener = new PlayerListener(this, databaseManager);
            getServer().getPluginManager().registerEvents(playerListener, this);
            
            // Register commands
            registerCommands();
            
            // Start Discord bot if enabled
            if (getConfig().getBoolean("discord.enabled", false)) {
                String token = getConfig().getString("discord.token");
                if (token != null && !token.isEmpty() && !token.equals("YOUR_DISCORD_BOT_TOKEN")) {
                    this.discordBot = new DiscordBot(token);
                    discordBot.start();
                    getLogger().info("Discord integration enabled!");
                } else {
                    getLogger().warning("Discord token not configured! Discord integration will be disabled.");
                }
            }
            
            getLogger().info("LightLogin has been enabled successfully!");
        } catch (Exception e) {
            getLogger().severe("Failed to enable LightLogin: " + e.getMessage());
            e.printStackTrace();
            // Disable the plugin if initialization fails
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void registerCommands() {
        // Register commands
        getCommand("login").setExecutor(new LoginCommand(playerListener));
        getCommand("register").setExecutor(new RegisterCommand(playerListener));
        
        // Register Discord commands
        getCommand("discord").setExecutor(new DiscordLinkCommand(this, discordBot));
        getCommand("passwordforget").setExecutor(new PasswordForgetCommand(this, databaseManager, discordBot));
        getCommand("pf").setExecutor(new PasswordForgetCommand(this, databaseManager, discordBot));
    }
    
    @Override
    public void onDisable() {
        // Shutdown Discord bot
        if (discordBot != null) {
            discordBot.shutdown();
        }
        
        // Close database connections
        if (databaseManager != null) {
            databaseManager.close();
        }
        
        getLogger().info("LightLogin has been disabled!");
    }

    public static LightLogin getInstance() {
        return instance;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public DiscordBot getDiscordBot() {
        return discordBot;
    }
    
    public PlayerListener getPlayerListener() {
        return playerListener;
    }
}
