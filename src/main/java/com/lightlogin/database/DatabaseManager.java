package com.lightlogin.database;

import com.lightlogin.LightLogin;
import org.bukkit.entity.Player;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.*;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatabaseManager {
    private static final Logger logger = Logger.getLogger("LightLogin");
    private final LightLogin plugin;
    private Connection connection;
    private final String DB_URL = "jdbc:h2:./plugins/LightLogin/data;MODE=MySQL";
    private DatabaseMigrator migrator;
    
    public DatabaseManager(LightLogin plugin) {
        this.plugin = plugin;
    }

    public void initializeDatabase() {
        try {
            Class.forName("org.h2.Driver");
            connection = DriverManager.getConnection(DB_URL);
            
            // Initialize migrator and run migrations
            this.migrator = new DatabaseMigrator(plugin, this);
            migrator.migrate();
            
            // Create any additional tables not covered by migrations
            createAdditionalTables();
        } catch (ClassNotFoundException | SQLException e) {
            logger.log(Level.SEVERE, "Failed to initialize database", e);
        }
    }
    
    private void createAdditionalTables() {
        // Any additional tables that aren't in the main schema can go here
        try (Statement stmt = connection.createStatement()) {
            // Add any additional table creation statements here if needed
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to create additional tables", e);
        }
    }

    public boolean registerPlayer(Player player, String password) {
        String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
        String query = "INSERT INTO players (uuid, username, password_hash, ip_address) VALUES (?, ?, ?, ?)";
        
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, player.getUniqueId().toString());
            stmt.setString(2, player.getName());
            stmt.setString(3, hashedPassword);
            stmt.setString(4, player.getAddress().getAddress().getHostAddress());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to register player", e);
            return false;
        }
    }

    public boolean isPlayerRegistered(UUID uuid) {
        String query = "SELECT 1 FROM players WHERE uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to check if player is registered", e);
            return false;
        }
    }

    public boolean validatePassword(UUID uuid, String password) {
        String query = "SELECT password_hash FROM players WHERE uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String hashedPassword = rs.getString("password_hash");
                    return BCrypt.checkpw(password, hashedPassword);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to validate password", e);
        }
        return false;
    }

    public void updateLastLogin(UUID uuid, String ipAddress) {
        String query = "UPDATE players SET last_login = CURRENT_TIMESTAMP, ip_address = ? WHERE uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, ipAddress);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update last login", e);
        }
    }

    public void createSession(UUID uuid, String token, long expiresIn) {
        String query = "INSERT INTO sessions (uuid, token, expires_at) VALUES (?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE token = VALUES(token), expires_at = VALUES(expires_at)";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, token);
            stmt.setTimestamp(3, new Timestamp(System.currentTimeMillis() + expiresIn * 1000));
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create session", e);
        }
    }

    public boolean validateSession(UUID uuid, String token) {
        String query = "SELECT 1 FROM sessions WHERE uuid = ? AND token = ? AND expires_at > CURRENT_TIMESTAMP";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, token);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to validate session: " + e.getMessage());
            return false;
        }
    }
    
    public boolean hasValidSession(UUID uuid, String ipAddress, boolean checkIp) {
        String query = "SELECT s.token, p.ip_address FROM sessions s " +
                     "JOIN players p ON s.uuid = p.uuid " +
                     "WHERE s.uuid = ? AND s.expires_at > CURRENT_TIMESTAMP";
                     
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    if (checkIp) {
                        String storedIp = rs.getString("ip_address");
                        return storedIp != null && storedIp.equals(ipAddress);
                    }
                    return true;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to check valid session: " + e.getMessage());
        }
        return false;
    }

    public void linkDiscordAccount(UUID uuid, String discordId) {
        String sql = "UPDATE players SET discord_id = ? WHERE uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, discordId);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to link Discord account", e);
        }
    }
    
    public boolean unlinkDiscordAccount(UUID uuid) {
        String sql = "UPDATE players SET discord_id = NULL WHERE uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            int updated = stmt.executeUpdate();
            return updated > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to unlink Discord account", e);
            return false;
        }
    }
    
    /**
     * Update a player's password
     * @param uuid The player's UUID
     * @param newPassword The new password (plaintext, will be hashed)
     * @return true if the password was updated successfully
     * @throws SQLException If a database error occurs
     */
    public boolean updatePassword(UUID uuid, String newPassword) throws SQLException {
        String hashedPassword = BCrypt.hashpw(newPassword, BCrypt.gensalt());
        String sql = "UPDATE players SET password = ? WHERE uuid = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, hashedPassword);
            stmt.setString(2, uuid.toString());
            int updated = stmt.executeUpdate();
            return updated > 0;
        }
    }
    
    /**
     * Check if a Discord account is already linked to any player
     * @param discordUsername The Discord username to check (format: username#discriminator)
     * @return true if the Discord account is already linked, false otherwise
     */
    public boolean isDiscordAccountLinked(String discordUsername) {
        String sql = "SELECT COUNT(*) FROM players WHERE discord_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, discordUsername);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to check if Discord account is linked", e);
        }
        return false;
    }

    public Connection getConnection() {
        return connection;
    }
    
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to close database connection", e);
        }
    }
}
