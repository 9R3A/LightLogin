package com.lightlogin;

import com.lightlogin.command.DiscordLinkCommand;
import com.lightlogin.command.LoginCommand;
import com.lightlogin.command.PasswordForgetCommand;
import com.lightlogin.command.RegisterCommand;
import com.lightlogin.database.DatabaseManager;
import com.lightlogin.discord.JDAIntegration;
import com.lightlogin.listener.PlayerListener;
import org.bukkit.plugin.java.JavaPlugin;

import javax.security.auth.login.LoginException;


public class LightLogin extends JavaPlugin {
    private static LightLogin instance;
    private DatabaseManager databaseManager;
    private JDAIntegration discordBot;
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
            
            // Start Discord bot if enabled
            if (getConfig().getBoolean("discord.enabled", false)) {
                String token = getConfig().getString("discord.token");
                if (token != null && !token.isEmpty() && !token.equals("YOUR_DISCORD_BOT_TOKEN")) {
                    this.discordBot = new JDAIntegration(this, token);
                    try {
                        discordBot.start();
                        getLogger().info("Discord integration enabled!");
                    } catch (Exception e) {
                        getLogger().severe("Failed to start Discord bot: " + e.getMessage());
                        e.printStackTrace();
                        this.discordBot = null;
                    }
                } else {
                    getLogger().warning("Discord token not configured! Discord integration will be disabled.");
                }
            }
            
            // Register commands after Discord bot is initialized
            registerCommands();
            
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
        if (getCommand("login") != null) {
            getCommand("login").setExecutor(new LoginCommand(playerListener));
        } else {
            getLogger().warning("Login command not found in plugin.yml!");
        }
        
        if (getCommand("register") != null) {
            getCommand("register").setExecutor(new RegisterCommand(playerListener));
        } else {
            getLogger().warning("Register command not found in plugin.yml!");
        }
        
        // Register Discord commands if Discord is enabled
        if (discordBot != null) {
            if (getCommand("discord") != null) {
                getCommand("discord").setExecutor(new DiscordLinkCommand(this, discordBot));
            } else {
                getLogger().warning("Discord command not found in plugin.yml!");
            }
            
            if (getCommand("passwordforget") != null) {
                getCommand("passwordforget").setExecutor(new PasswordForgetCommand(this, databaseManager, discordBot));
            } else {
                getLogger().warning("passwordforget command not found in plugin.yml!");
            }
            
            if (getCommand("pf") != null) {
                getCommand("pf").setExecutor(new PasswordForgetCommand(this, databaseManager, discordBot));
            }
        }
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

    public JDAIntegration getDiscordBot() {
        return discordBot;
    }
    
    public boolean isDiscordEnabled() {
        return discordBot != null && discordBot.isEnabled();
    }
    
    public PlayerListener getPlayerListener() {
        return playerListener;
    }
}
