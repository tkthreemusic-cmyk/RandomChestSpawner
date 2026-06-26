# RandomChestSpawner - Minecraft Plugin

A Spigot/Paper plugin that spawns chests with random obtainable items at random locations for online players.

## Features

- **Random Chest Spawning**: Spawns a chest with 5-18 random obtainable items
- **Center-Based Distance**: Spawns at the center of all connected players, minimum 100 blocks away
- **Smart Distance**: Maximum 1000 blocks, or larger if players are very spread out (respects world boundaries)
- **Ground Placement**: Places chests **on the ground** on top of the highest solid block (never floating)
- **World Bounds**: Coordinates clamped to world limits (-10000 to 10000 on X/Z axes)
- **Chat Announcements**: Notifies all online players when a chest spawns with coordinates
- **Auto-Management**: Automatically starts spawning when a player joins; stops when server is empty
- **Periodic Spawning**: Spawns a new chest every 30 minutes when at least one player is online

## Requirements

- Java 17+
- Spigot or Paper server (1.20+)
- Maven (for building)

## Building

```bash
mvn clean package
```

The compiled JAR will be in `target/RandomChestSpawner-1.0.0.jar`

## Installation

1. Build the plugin or download the JAR
2. Place the JAR file in your server's `plugins` folder
3. Restart or reload your server
4. The plugin will automatically start when players join

## Configuration

The plugin works out-of-the-box with default settings:

- **Spawn Distance**: 100-1000 blocks from random online player
- **Items per Chest**: 5-18 random obtainable items
- **Spawn Interval**: Every 30 minutes
- **Spawn Trigger**: Requires at least 1 player online

## Commands

Currently no commands are required - the plugin runs automatically.

## Permissions

No permissions required - all online players receive chest spawn announcements.

## How It Works

1. When a player joins (and is the only player), a chest spawns immediately
2. Every 30 minutes, if players are online:
   - A random online player is selected
   - A random angle and distance (100-1000 blocks) is calculated
   - The chest is placed on the topmost solid block at that location
   - All online players see a chat message with the chest coordinates
3. When the last player leaves, the spawn scheduler stops

## File Structure

```
RandomChestSpawner/
├── pom.xml
├── README.md
└── src/
    └── main/
        ├── java/
        │   └── com/
        │       └── example/
        │           └── randomchest/
        │               └── RandomChestPlugin.java
        └── resources/
            └── plugin.yml
```

## License

This project is open source and can be modified as needed.
