# distantTicking

## 🚀 Overview

DistantTicking is a powerful PaperMC plugin designed to enhance your server's performance while keeping your essential redstone contraptions and automated farms continuously active, regardless of player proximity. Say goodbye to AFK farms and chunks unloading at inconvenient times\!

Traditionally, Minecraft only "ticks" chunks within a player's `simulation-distance`. This means complex machines and farms stop working when players move too far away or log off. DistantTicking solves this by intelligently detecting and permanently loading chunks that contain player-placed "tick-worthy" blocks by tracking their **exact locations**.

## ✨ Features

- **Intelligent Chunk Loading:** Automatically detects and force-loads chunks by tracking the **specific locations** of player-placed blocks (e.g., Furnaces, Hoppers, Redstone, Pistons, Rails).
- **Persistent Ticking:** Ensures your machines, farms, and redstone contraptions keep working 24/7, even when no players are nearby or in the same dimension.
- **Performance Optimization:** Allows server owners to lower the global `simulation-distance` in `server.properties` (e.g., to 4-8) for better overall TPS and reduced server load, while DistantTicking manages the critical ticking areas.
- **Configurable Tick-Worthy Blocks:** Easily customize which block materials trigger chunk loading directly in the `config.yml`.
- **Self-Healing Data:** Robust persistence using a hybrid approach of a central index and per-chunk Persistent Data Containers (PDCs). Recovers gracefully from accidental data file deletions or server crashes.
- **Periodic Consistency Checks:** Automatically cleans up outdated force-loaded entries if blocks are removed without proper plugin notification (e.g., world editing, server crashes).
- **Admin Commands:** Intuitive commands for monitoring plugin status, listing force-loaded chunks, refreshing chunk data, and debugging.
- **Old Data Cleanup Utility:** A specific command to remove legacy tick-worthy block data stored in PDCs from previous plugin versions.
- **Debug Logging:** Specific debug mode to get detailed plugin logs without flooding the entire server console.

## ⚙️ How it Works

DistantTicking operates on a hybrid data storage model:

1.  **Per-Chunk Data (PDC):** When a player places a configurable "tick-worthy" block, the plugin marks that chunk's data using Minecraft's built-in Persistent Data Containers (PDCs). Instead of just a count, it now stores the **specific X, Y, Z coordinates** of each tick-worthy block within that chunk. This data is serialized to a JSON string and stored directly within the chunk's file, persisting even if the main plugin data file is lost.
2.  **Centralized Index (`active_chunks.json`):** A lightweight JSON file maintains a list of all chunks that are currently force-loaded by the plugin across all worlds. This file is loaded on server startup to immediately initiate force-loading of all relevant chunks.
3.  **Synchronization:**
    - When a player places a tick-worthy block, its chunk is added to the centralized index and force-loaded _if it's the first tick-worthy block in that chunk_.
    - When a player breaks (or a block is destroyed) a tick-worthy block, its location is removed from the chunk's PDC. If, after removal, _no tick-worthy blocks remain_ in that chunk's PDC, it's removed from the centralized index and un-force-loaded.
    - A periodic background task verifies that chunks in the centralized index still have corresponding tick-worthy blocks in their PDC. If a chunk's PDC shows no stored block locations, the entry is removed from the centralized index, ensuring "self-healing" from data inconsistencies.

This approach provides precise tracking, reliable persistence, and efficient resource management.

## 📥 Installation

1.  Download the latest `DistantTicking-X.Y.Z.jar` from the build/libs folder.
2.  Place the `DistantTicking-X.Y.Z.jar` file into your server's `plugins/` folder.
3.  Restart your PaperMC server.
4.  A `DistantTicking` folder will be created in your `plugins/` directory, containing `config.yml` and `active_chunks.json`.
5.  IMPORTANT! If you add this to an existing world, you will want to run `/dt refresh <chunk radius> <vertical-up> <vertical-down>` to populate the new location-based tracking data for existing blocks. I advise doing this surgically, with a low chunk radius and appropriate vertical limits, as scanning many chunks or large vertical ranges can be slow.

## 🛠️ Configuration (`config.yml`)

The `config.yml` file is automatically generated on first run. You can customize the plugin's behavior here.

```yaml
# DistantTicking Plugin Configuration

settings:
  # Interval for automatically saving the force-loaded chunks data to active_chunks.json.
  # Measured in minutes. (20 ticks = 1 second)
  auto-save-interval-minutes: 5 # Default: 5

  # Interval for the periodic consistency check.
  # This check verifies if chunks marked for force-loading still contain tick-worthy blocks in their PDC.
  # Measured in hours.
  consistency-check-interval-hours: 6 # Default: 6

  # Enable debug mode for more verbose console logging.
  # Set to 'true' to see detailed plugin activity (e.g., individual chunk loads/unloads).
  # These logs are *only* for DistantTicking and will not flood your console with other plugin's debug messages.
  debug-mode: false # Default: false

# List of materials that, when placed by a player, should make their chunk tick-worthy and force-loaded.
# Ensure material names are valid Minecraft Material enums (case-insensitive in plugin, but use uppercase for clarity).
#
# IMPORTANT: Even non-BlockEntities (like Redstone Wire, Repeaters, Pistons) will cause a chunk to stay loaded,
# allowing them to function continuously if they are part of an active redstone circuit.
#
tick-worthy-blocks:
  - Blocks you want to tick
# Suggestion for server owners:
# For optimal performance, consider lowering your server's 'simulation-distance' in server.properties
# to a value like 4-8. This allows DistantTicking to manage persistent ticking for your machines
# while significantly reducing the load on your server's CPU for areas without critical contraptions.
```

## 🎮 Commands

All commands start with `/dt` (or `/distantticking`).

| Command                                              | Description                                                                                                                                                                | Permission                          |
| :--------------------------------------------------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :---------------------------------- |
| `/dt help`                                           | Displays the plugin's help message.                                                                                                                                        | `distantticking.command.base`       |
| `/dt pdcinfo`                                        | Shows the count of tick-worthy blocks (from PDC) in your current chunk.                                                                                                    | `distantticking.command.pdcinfo`    |
| `/dt status`                                         | Displays the current status of the DistantTicking plugin.                                                                                                                  | `distantticking.command.status`     |
| `/dt list [page]`                                    | Lists all chunks currently force-loaded by the plugin, with pagination.                                                                                                    | `distantticking.command.list`       |
| `/dt removehere`                                     | Removes the chunk you are standing in from the force-load list.                                                                                                            | `distantticking.command.removehere` |
| `/dt reload`                                         | Reloads the plugin's `config.yml` and restarts internal tasks.                                                                                                             | `distantticking.command.reload`     |
| `/dt refresh <radius> <vertical-up> <vertical-down>` | Scans a square area of chunks defined by the <radius> argument, and clamped by the vertical up and down args to prevent detecting unwanted map structures (eg mineshafts.) | `distantticking.command.refresh`    |

## 🔑 Permissions

All permissions default to `op`.

- `distantticking.command.base`
- `distantticking.command.pdcinfo`
- `distantticking.command.status`
- `distantticking.command.list`
- `distantticking.command.removehere`
- `distantticking.command.reload`
