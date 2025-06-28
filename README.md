# LightLogin - Minecraft Authentication Plugin

A secure and feature-rich login plugin for Minecraft servers with Discord integration, supporting both Java and Bedrock Edition players.

## Features

- **Secure Authentication**: Password protection with BCrypt hashing
- **Auto-Login**: Session management for returning players
- **Discord Integration**: Link Minecraft accounts with Discord
- **Cross-Platform**: Supports both Java and Bedrock Edition players
- **Protection**: Prevents unauthorized actions before login
- **Easy Setup**: Simple configuration with detailed documentation

## Requirements

- Spigot/Paper 1.20.1 or higher
- Java 17 or higher
- (Optional) Discord Bot for account verification

## Installation

1. Download the latest release from the [releases page](https://github.com/9R3A/LightLogin/releases)
2. Place the `LightLogin.jar` in your server's `plugins` folder
3. Start your server to generate the default config
4. Edit the `plugins/LightLogin/config.yml` to your liking
5. Restart your server

## Configuration

Edit `plugins/LightLogin/config.yml` to configure:

- Database settings
- Discord bot integration
- Session management
- Protection settings
- Messages and localization

### Discord Bot Setup

1. Create a Discord bot at the [Discord Developer Portal](https://discord.com/developers/applications)
2. Copy the bot token
3. Enable the following intents:
   - Message Content
   - Server Members Intent
4. Add the bot to your server with the `bot` and `applications.commands` scopes
5. Set `discord.enabled: true` in the config
6. Add your bot token: `discord.token: "your-bot-token"`
7. Set your Discord server ID: `discord.guild-id: "your-server-id"`

## Commands

- `/register <password> <confirmPassword>` - Register a new account
- `/login <password>` - Login to your account
- `/changepassword <oldPassword> <newPassword>` - Change your password
- `/discord link` - Link your Discord account
- `/discord unlink` - Unlink your Discord account

## Permissions

- `lightlogin.*` - All permissions
- `lightlogin.admin` - Admin commands and bypasses
- `lightlogin.bypass` - Bypass login requirements (for staff)

## Building from Source

1. Clone the repository
2. Run `mvn clean package`
3. Find the compiled JAR in `target/LightLogin-1.0-SNAPSHOT.jar`

## Support

For support, please [open an issue](https://github.com/9r3a/LightLogin/issues) or join our [Discord server](soon).

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
