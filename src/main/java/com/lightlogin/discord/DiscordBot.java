package com.lightlogin.discord;

import com.lightlogin.LightLogin;
import com.lightlogin.database.DatabaseManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import net.dv8tion.jda.api.utils.GatewayIntent;
import net.dv8tion.jda.api.utils.concurrent.Task;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class DiscordBot extends ListenerAdapter {
    private final String token;
    private JDA jda;
    private final LightLogin plugin = LightLogin.getInstance();
    private final DatabaseManager databaseManager = plugin.getDatabaseManager();
    private final Random random = new Random();

    public DiscordBot(String token) {
        this.token = token;
    }

    public void start() {
        try {
            jda = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                    .addEventListeners(this)
                    .build();
            jda.awaitReady();
            updateCommands();
            plugin.getLogger().info("Discord bot started successfully!");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to start Discord bot: " + e.getMessage());
        }
    }

    private void updateCommands() {
        if (jda == null) return;
        
        CommandListUpdateAction commands = jda.updateCommands();
        
        // Add global commands
        commands.addCommands(
            Commands.slash("verify", "Verify your Minecraft account")
                .addOption(OptionType.STRING, "code", "Your verification code", true)
        );
        
        commands.queue();
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("verify")) {
            String code = event.getOption("code") != null ? 
                event.getOption("code").getAsString() : null;
                
            if (code == null || code.isEmpty()) {
                event.reply("❌ Please provide a verification code!")
                    .setEphemeral(true).queue();
                return;
            }
            
            // Verify the code and get the associated UUID
            String sql = "SELECT uuid FROM discord_codes WHERE code = ? AND expires_at > CURRENT_TIMESTAMP";
            try (PreparedStatement stmt = databaseManager.getConnection().prepareStatement(sql)) {
                stmt.setString(1, code);
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    UUID playerUuid = UUID.fromString(rs.getString("uuid"));
                    String discordId = event.getUser().getId();
                    
                    // Check if this Discord account is already linked to another player
                    if (databaseManager.isDiscordAccountLinked(discordId)) {
                        event.reply("❌ This Discord account is already linked to another Minecraft account.")
                            .setEphemeral(true).queue();
                        return;
                    }
                    
                    // Link the Discord account
                    databaseManager.linkDiscordAccount(playerUuid, discordId);
                    
                    // Delete the used code
                    try (PreparedStatement deleteStmt = databaseManager.getConnection()
                            .prepareStatement("DELETE FROM discord_codes WHERE code = ?")) {
                        deleteStmt.setString(1, code);
                        deleteStmt.executeUpdate();
                    }
                    
                    // Assign role if configured
                    String roleName = plugin.getConfig().getString("discord.verified-role");
                    if (roleName != null && !roleName.isEmpty() && event.getGuild() != null) {
                        // Find role by name
                        event.getGuild().getRoles().stream()
                            .filter(role -> role.getName().equalsIgnoreCase(roleName))
                            .findFirst()
                            .ifPresent(role -> 
                                event.getGuild().addRoleToMember(event.getUser(), role).queue()
                            );
                    }
                    
                    // Notify the player in-game if online
                    Player player = plugin.getServer().getPlayer(playerUuid);
                    if (player != null && player.isOnline()) {
                        player.sendMessage(plugin.getConfig()
                            .getString("messages.prefix") + 
                            plugin.getConfig().getString("messages.discord-verify-success"));
                    }
                    
                    event.reply("✅ " + plugin.getConfig().getString("messages.discord-verify-success"))
                        .setEphemeral(true).queue();
                } else {
                    event.reply("❌ " + plugin.getConfig().getString("messages.discord-verify-failed"))
                        .setEphemeral(true).queue();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to verify code", e);
                event.reply("❌ An error occurred. Please try again later.")
                    .setEphemeral(true).queue();
            }
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        
        // Handle DMs for verification
        if (event.isFromGuild()) return;
        
        String message = event.getMessage().getContentRaw();
        
        // Check if the message is a command
        if (message.equalsIgnoreCase("!verify")) {
            // Check if user is already linked
            String checkQuery = "SELECT username FROM players WHERE discord_id = ?";
            try (PreparedStatement stmt = databaseManager.getConnection().prepareStatement(checkQuery)) {
                stmt.setString(1, event.getAuthor().getId());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String userTag = event.getAuthor().getName() + "#" + event.getAuthor().getDiscriminator();
                        event.getChannel().sendMessage("✅ You are already linked to the Minecraft account: " + userTag)
                            .queue();
                        return;
                    }
                }
                
                // If not linked, send instructions
                event.getChannel().sendMessage("🔗 To link your Discord account with Minecraft, please follow these steps:\n" +
                    "1. Join the Minecraft server and type `/discord link`\n" +
                    "2. You'll receive a verification code\n" +
                    "3. Come back here and type `/verify <your-code>` in any channel where the bot can see you\n\n" +
                    "If you need help, contact a server administrator.").queue();
                        
            } catch (SQLException e) {
                event.getChannel().sendMessage("❌ An error occurred. Please try again later.").queue();
                plugin.getLogger().severe("Error checking Discord link status: " + e.getMessage());
            }
        }
    }
    
    public String generateVerificationCode(UUID playerUuid) {
        // Generate a random 6-digit code
        String code = String.format("%06d", random.nextInt(1000000));
        
        // Store the code in the database with expiration
        String sql = "INSERT INTO discord_codes (uuid, code, expires_at) VALUES (?, ?, DATETIME('now', '+10 minutes'))";
        try (PreparedStatement stmt = databaseManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, code);
            stmt.executeUpdate();
            
            // Clean up old codes
            try (PreparedStatement cleanup = databaseManager.getConnection()
                    .prepareStatement("DELETE FROM discord_codes WHERE expires_at < CURRENT_TIMESTAMP")) {
                cleanup.executeUpdate();
            }
            
            return code;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to generate verification code: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Send a verification message to a Discord user
     * @param discordId The Discord ID of the user
     * @param code The verification code to send
     */
    /**
     * Send a direct message to a user with a fallback to a channel if DMs are closed
     * @param discordId The Discord ID or username#discriminator of the user
     * @param message The message to send
     * @param player The Minecraft player (can be null if not applicable)
     * @return true if the message was sent successfully, false otherwise
     */
    public boolean sendDirectMessage(String discordId, String message, Player player) {
        if (jda == null) return false;
        
        // Try to resolve user by ID first
        try {
            long userId = Long.parseLong(discordId);
            return sendDirectMessageById(userId, message, player);
        } catch (NumberFormatException e) {
            // If not a numeric ID, try to find by username#discriminator
            return sendDirectMessageByTag(discordId, message, player);
        }
    }
    
    private boolean sendDirectMessageById(long userId, String message, Player player) {
        return jda.retrieveUserById(userId).map(user -> {
            user.openPrivateChannel().flatMap(channel -> 
                channel.sendMessage(message)
            ).queue(
                success -> {},
                error -> handleDmError(user, message, player)
            );
            return true;
        }).complete() != null;
    }
    
    private boolean sendDirectMessageByTag(String userTag, String message, Player player) {
        CompletableFuture<Boolean> sent = new CompletableFuture<>();
        
        for (Guild guild : jda.getGuilds()) {
            List<Member> members = guild.findMembers(member -> {
                User user = member.getUser();
                String memberTag = user.getName() + "#" + user.getDiscriminator();
                return memberTag.equalsIgnoreCase(userTag) || user.getId().equals(userTag);
            }).get();
            
            if (!members.isEmpty()) {
                User user = members.get(0).getUser();
                user.openPrivateChannel().queue(
                    channel -> channel.sendMessage(message).queue(
                        success -> sent.complete(true),
                        error -> {
                            handleDmError(user, message, player);
                            sent.complete(false);
                        }
                    ),
                    error -> {
                        handleDmError(user, message, player);
                        sent.complete(false);
                    }
                );
                return sent.join();
            }
        }
        return false;
    }
    
    private void handleDmError(net.dv8tion.jda.api.entities.User user, String message, Player player) {
        String userTag = user.getName() + "#" + user.getDiscriminator();
        plugin.getLogger().warning("Could not send DM to " + userTag);
        
        // Try to send to a fallback channel
        String channelId = plugin.getConfig().getString("discord.verification-channel");
        if (channelId != null && !channelId.isEmpty()) {
            TextChannel channel = jda.getTextChannelById(channelId);
            if (channel != null) {
                String notice = plugin.getConfig().getString("messages.password-reset-channel-notice")
                    .replace("%user%", user.getAsMention());
                channel.sendMessage(notice).queue();
                
                // Send the actual message to the channel with a mention
                channel.sendMessage(user.getAsMention() + " " + message).queue();
            }
        }
        
        // Notify the player in-game if possible
        if (player != null && player.isOnline()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("messages.prefix") + 
                plugin.getConfig().getString("messages.password-reset-dm-failed")));
        }
    }
    
    public void sendVerificationMessage(String discordId, String code) {
        if (jda == null || code == null) return;
        
        String message = "🔑 **" + plugin.getConfig().getString("messages.discord-verification-sent") + "**\n\n" +
                       plugin.getConfig().getString("messages.discord-verify-instructions")
                           .replace("%code%", "`" + code + "`") + 
                       "\n\nThis code will expire in 10 minutes.";
        
        sendDirectMessage(discordId, message, null);
    }
    
    public void shutdown() {
        if (jda != null) {
            jda.shutdown();
        }
    }
}
