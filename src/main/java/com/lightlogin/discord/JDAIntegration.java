package com.lightlogin.discord;

import com.lightlogin.LightLogin;
import com.lightlogin.database.DatabaseManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.restaction.CommandCreateAction;
import org.bukkit.entity.Player;

import javax.security.auth.login.LoginException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class JDAIntegration extends ListenerAdapter {
    private final LightLogin plugin;
    private final DatabaseManager databaseManager;
    private JDA jda;
    private final String token;

    public JDAIntegration(LightLogin plugin, String token) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        this.token = token;
    }

    public void start() throws LoginException, InterruptedException {
        if (token == null || token.isEmpty() || token.equals("YOUR_DISCORD_BOT_TOKEN")) {
            plugin.getLogger().warning("Discord token not configured! Discord features will be disabled.");
            return;
        }

        jda = JDABuilder.createDefault(token)
                .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                .addEventListeners(this)
                .build();

        // Wait for JDA to be ready
        jda.awaitReady();
        
        // Register commands
        registerCommands();
        
        plugin.getLogger().info("Discord bot connected as: " + jda.getSelfUser().getAsTag());
    }

    private void registerCommands() {
        if (jda == null) return;
        
        Guild guild = jda.getGuilds().isEmpty() ? null : jda.getGuilds().get(0);
        CommandCreateAction commandAction = guild != null 
                ? guild.upsertCommand("verify", "Verify your Minecraft account with a verification code")
                : jda.upsertCommand("verify", "Verify your Minecraft account with a verification code");
        
        commandAction.addOption(net.dv8tion.jda.api.interactions.commands.OptionType.STRING, 
                "code", "Your verification code", true)
                .queue();
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("verify")) return;
        
        String code = event.getOption("code") != null 
                ? event.getOption("code").getAsString() 
                : null;
                
        if (code == null || code.isEmpty()) {
            event.reply("❌ Please provide a verification code!")
                 .setEphemeral(true)
                 .queue();
            return;
        }
        
        // Process verification in an async thread to avoid blocking
        event.deferReply(true).queue(interactionHook -> {
            try {
                verifyDiscordAccount(event.getUser().getId(), code, interactionHook);
            } catch (Exception e) {
                plugin.getLogger().severe("Error processing verification: " + e.getMessage());
                e.printStackTrace();
                interactionHook.editOriginal("❌ An error occurred while processing your request.").queue();
            }
        });
    }

    private void verifyDiscordAccount(String discordId, String code, net.dv8tion.jda.api.interactions.InteractionHook hook) {
        // Check if Discord account is already linked
        String checkLinkedQuery = "SELECT username FROM players WHERE discord_id = ?";
        try (PreparedStatement stmt = databaseManager.getConnection().prepareStatement(checkLinkedQuery)) {
            stmt.setString(1, discordId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    hook.editOriginal("❌ This Discord account is already linked to a Minecraft account!").queue();
                    return;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Database error checking linked account: " + e.getMessage());
            hook.editOriginal("❌ An error occurred. Please try again later.").queue();
            return;
        }

        // Check if code is valid
        String checkCodeQuery = "SELECT player_uuid FROM verification_codes WHERE code = ? AND used = 0 AND expires_at > CURRENT_TIMESTAMP";
        try (PreparedStatement stmt = databaseManager.getConnection().prepareStatement(checkCodeQuery)) {
            stmt.setString(1, code);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String playerUuid = rs.getString("player_uuid");
                    
                    // Mark code as used
                    String updateCodeQuery = "UPDATE verification_codes SET used = 1, used_at = CURRENT_TIMESTAMP, used_by = ? WHERE code = ?";
                    try (PreparedStatement updateStmt = databaseManager.getConnection().prepareStatement(updateCodeQuery)) {
                        updateStmt.setString(1, discordId);
                        updateStmt.setString(2, code);
                        updateStmt.executeUpdate();
                    }
                    
                    // Link Discord account to player
                    String linkQuery = "UPDATE players SET discord_id = ? WHERE uuid = ?";
                    try (PreparedStatement linkStmt = databaseManager.getConnection().prepareStatement(linkQuery)) {
                        linkStmt.setString(1, discordId);
                        linkStmt.setString(2, playerUuid);
                        linkStmt.executeUpdate();
                    }
                    
                    // Notify player in-game if online
                    Player player = plugin.getServer().getPlayer(UUID.fromString(playerUuid));
                    if (player != null && player.isOnline()) {
                        player.sendMessage(plugin.getConfig()
                            .getString("messages.prefix", "[LightLogin] ") + 
                            plugin.getConfig().getString("messages.discord-verify-success", 
                            "Your Discord account has been successfully linked!"));
                    }
                    
                    hook.editOriginal("✅ Successfully linked your Discord account! You can now use Discord commands.").queue();
                } else {
                    hook.editOriginal("❌ Invalid or expired verification code. Please try again or request a new one.").queue();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Database error during verification: " + e.getMessage());
            hook.editOriginal("❌ An error occurred while verifying your code. Please try again later.").queue();
        }
    }

    public void shutdown() {
        if (jda != null) {
            jda.shutdown();
        }
    }
    
    public boolean isEnabled() {
        return jda != null;
    }
}
