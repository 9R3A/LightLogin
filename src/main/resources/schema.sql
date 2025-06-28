-- Players table for storing player authentication data
CREATE TABLE IF NOT EXISTS players (
    uuid VARCHAR(36) PRIMARY KEY,
    username VARCHAR(16) NOT NULL,
    password VARCHAR(255) NOT NULL,
    ip_address VARCHAR(45),
    last_login TIMESTAMP,
    discord_id VARCHAR(64),
    is_premium BOOLEAN DEFAULT FALSE,
    registered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(username),
    UNIQUE(discord_id)
);

-- Verification codes for Discord account linking
CREATE TABLE IF NOT EXISTS verification_codes (
    id INT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(10) NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    discord_id VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN DEFAULT FALSE,
    used_at TIMESTAMP NULL,
    used_by VARCHAR(64),
    FOREIGN KEY (player_uuid) REFERENCES players(uuid) ON DELETE CASCADE,
    INDEX idx_code (code),
    INDEX idx_player_uuid (player_uuid),
    INDEX idx_discord_id (discord_id)
);

-- Password reset codes
CREATE TABLE IF NOT EXISTS password_reset_codes (
    id INT AUTO_INCREMENT PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    code VARCHAR(26) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN DEFAULT FALSE,
    used_at TIMESTAMP NULL,
    FOREIGN KEY (player_uuid) REFERENCES players(uuid) ON DELETE CASCADE,
    INDEX idx_player_code (player_uuid, code)
);

-- Player sessions for tracking active logins
CREATE TABLE IF NOT EXISTS player_sessions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    session_token VARCHAR(64) NOT NULL,
    ip_address VARCHAR(45) NOT NULL,
    user_agent TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    is_valid BOOLEAN DEFAULT TRUE,
    FOREIGN KEY (player_uuid) REFERENCES players(uuid) ON DELETE CASCADE,
    INDEX idx_session_token (session_token),
    INDEX idx_player_uuid (player_uuid)
);

-- Player login attempts for rate limiting
CREATE TABLE IF NOT EXISTS login_attempts (
    id INT AUTO_INCREMENT PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    ip_address VARCHAR(45) NOT NULL,
    successful BOOLEAN DEFAULT FALSE,
    attempted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (player_uuid) REFERENCES players(uuid) ON DELETE CASCADE,
    INDEX idx_ip_attempt (ip_address, attempted_at),
    INDEX idx_player_attempt (player_uuid, attempted_at)
);
