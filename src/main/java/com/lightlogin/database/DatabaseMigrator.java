package com.lightlogin.database;

import com.lightlogin.LightLogin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;

public class DatabaseMigrator {
    private final LightLogin plugin;
    private final DatabaseManager databaseManager;
    private static final String MIGRATION_RESOURCE = "schema.sql";
    private static final String MIGRATION_TABLE = "schema_migrations";
    private static final int CURRENT_SCHEMA_VERSION = 1;

    public DatabaseMigrator(LightLogin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    public void migrate() {
        createMigrationsTable();
        int currentVersion = getCurrentSchemaVersion();
        
        if (currentVersion < CURRENT_SCHEMA_VERSION) {
            plugin.getLogger().info("Updating database schema from version " + currentVersion + " to " + CURRENT_SCHEMA_VERSION);
            applyMigrations();
            updateSchemaVersion(CURRENT_SCHEMA_VERSION);
            plugin.getLogger().info("Database schema updated to version " + CURRENT_SCHEMA_VERSION);
        } else {
            plugin.getLogger().info("Database schema is up to date (version " + currentVersion + ")");
        }
    }

    private void createMigrationsTable() {
        String sql = "CREATE TABLE IF NOT EXISTS " + MIGRATION_TABLE + " (" +
                   "version INT PRIMARY KEY," +
                   "applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                   ")";
        
        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            
            // Insert initial version if not exists
            stmt.execute("INSERT OR IGNORE INTO " + MIGRATION_TABLE + " (version) VALUES (0)");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create migrations table: " + e.getMessage());
        }
    }

    private int getCurrentSchemaVersion() {
        String sql = "SELECT MAX(version) as version FROM " + MIGRATION_TABLE;
        
        try (var conn = databaseManager.getConnection();
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery(sql)) {
            
            return rs.next() ? rs.getInt("version") : 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get current schema version: " + e.getMessage());
            return 0;
        }
    }

    private void updateSchemaVersion(int version) {
        String sql = "INSERT OR REPLACE INTO " + MIGRATION_TABLE + " (version) VALUES (?)";
        
        try (var conn = databaseManager.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, version);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to update schema version: " + e.getMessage());
        }
    }

    private void applyMigrations() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(MIGRATION_RESOURCE);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            
            String sql = reader.lines().collect(Collectors.joining("\n"));
            
            // Split SQL by semicolon and execute each statement
            try (Connection conn = databaseManager.getConnection();
                 Statement stmt = conn.createStatement()) {
                
                // Disable auto-commit to run all statements in a transaction
                boolean oldAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);
                
                try {
                    // Split SQL by semicolon and execute each statement
                    String[] statements = sql.split(";\\s*\n");
                    for (String statement : statements) {
                        statement = statement.trim();
                        if (!statement.isEmpty()) {
                            stmt.execute(statement);
                        }
                    }
                    conn.commit();
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(oldAutoCommit);
                }
            }
        } catch (IOException | SQLException e) {
            plugin.getLogger().severe("Failed to apply database migrations: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
