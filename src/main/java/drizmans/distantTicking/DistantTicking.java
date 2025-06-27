package drizmans.distantTicking;

import drizmans.distantTicking.command.DistantTickingCommand;
import drizmans.distantTicking.listener.BlockTrackingListener;
import drizmans.distantTicking.manager.ChunkDataManager;
import drizmans.distantTicking.manager.ForceLoadManager;
import drizmans.distantTicking.config.PluginConfig;
import drizmans.distantTicking.util.ChunkCoord;
import drizmans.distantTicking.util.TickWorthyBlocks;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.World;
import org.bukkit.Chunk;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.SimpleFormatter;


public final class DistantTicking extends JavaPlugin implements Listener {

    private static DistantTicking instance;
    private ChunkDataManager chunkDataManager;
    private ForceLoadManager forceLoadManager;
    private PluginConfig pluginConfig;

    /**
     * Provides static access to the main plugin instance.
     * @return The singleton instance of DistantTicking.
     */
    public static DistantTicking getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        // Plugin startup logic
        instance = this; // Set static instance

        // 1. Initialize Configuration
        this.pluginConfig = new PluginConfig(this);

        // --- CORRECTED LOGGING SETUP FOR PLUGIN-SPECIFIC DEBUGGING ---
        Level desiredLevel = pluginConfig.getLoggingLevel(); // FINE if debug, INFO otherwise

        // First, ensure the plugin's logger is at the desired level
        getLogger().setLevel(desiredLevel);

        // Remove default handlers to prevent parent handlers (e.g., root logger's console handler)
        // from re-filtering our messages if they have a higher level than 'FINE'.
        // This makes your plugin's logger independent for console output.
        getLogger().setUseParentHandlers(false);

        // Add a custom ConsoleHandler directly to your plugin's logger
        // This ensures only messages from THIS plugin get handled by it.
        boolean consoleHandlerExists = false;
        for (Handler handler : getLogger().getHandlers()) {
            if (handler instanceof ConsoleHandler) {
                consoleHandlerExists = true;
                // If a console handler already exists (e.g., from a reload), just update its level
                handler.setLevel(desiredLevel);
                // Always set to SimpleFormatter to avoid CraftBukkit internal class dependency
                handler.setFormatter(new SimpleFormatter());
            }
        }
        if (!consoleHandlerExists) {
            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setLevel(desiredLevel);
            // Use SimpleFormatter for guaranteed compilation and runtime
            consoleHandler.setFormatter(new SimpleFormatter());

            getLogger().addHandler(consoleHandler);
        }

        TickWorthyBlocks.initialize(this.pluginConfig); // Initialize tick-worthy blocks from config

        // 2. Initialize Managers
        this.chunkDataManager = new ChunkDataManager(this);
        this.forceLoadManager = new ForceLoadManager(this, chunkDataManager);

        // 3. Load the centralized force-load data from file
        this.forceLoadManager.loadData();

        // 4. Register Event Listeners
        getServer().getPluginManager().registerEvents(new BlockTrackingListener(this, chunkDataManager, forceLoadManager), this);
        getServer().getPluginManager().registerEvents(this, this);

        // 5. Register Commands
        getCommand("dt").setExecutor(new DistantTickingCommand(this, chunkDataManager, forceLoadManager, pluginConfig));

        // 6. Start auto-save task using interval from config
        this.forceLoadManager.startAutoSaveTask(pluginConfig.getAutoSaveIntervalMinutes());

        // 7. Start consistency check task using interval from config
        this.forceLoadManager.startConsistencyCheckTask(pluginConfig.getConsistencyCheckIntervalHours());

        getLogger().info("DistantTicking has been enabled!");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic

        // Remove our custom handler to prevent duplicate messages on re-enable/reload
        for (Handler handler : getLogger().getHandlers()) {
            if (handler instanceof ConsoleHandler) {
                handler.setLevel(Level.OFF); // Turn off its output before removing
                getLogger().removeHandler(handler);
            }
        }
        // Restore parent handlers if you want the plugin to use them after disable
        getLogger().setUseParentHandlers(true); // <--- IMPORTANT: Restore this

        // 1. Stop the consistency check task to prevent errors during shutdown
        this.forceLoadManager.stopConsistencyCheckTask();
        // 2. Stop the auto-save task
        this.forceLoadManager.stopAutoSaveTask();

        // 3. Save any pending force-load data (synchronously to ensure it finishes before plugin disables)
        this.forceLoadManager.saveData(true);

        // 4. Un-force-load all chunks that were loaded by this plugin
        int unloadedCount = 0;
        for (Map.Entry<String, Set<ChunkCoord>> entry : new HashMap<>(forceLoadManager.getForceLoadedChunks()).entrySet()) {
            World world = getServer().getWorld(entry.getKey());
            if (world != null) {
                for (ChunkCoord coord : entry.getValue()) {
                    Chunk chunk = world.getChunkAt(coord.getX(), coord.getZ());
                    if (chunk.isForceLoaded()) {
                        chunk.setForceLoaded(false);
                        unloadedCount++;
                    }
                }
            }
        }
        getLogger().info("Un-force loaded " + unloadedCount + " chunks on shutdown.");

        getLogger().info("DistantTicking has been disabled!");
        instance = null; // Clear static instance
    }

    /**
     * Provides access to the ChunkDataManager instance.
     * @return The ChunkDataManager instance.
     */
    public ChunkDataManager getChunkDataManager() {
        return chunkDataManager;
    }

    /**
     * Provides access to the ForceLoadManager instance.
     * @return The ForceLoadManager instance.
     */
    public ForceLoadManager getForceLoadManager() {
        return forceLoadManager;
    }

    /**
     * Provides access to the PluginConfig instance.
     * @return The PluginConfig instance.
     */
    public PluginConfig getPluginConfig() {
        return pluginConfig;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Only trigger if the player count is now 1
        if (Bukkit.getOnlinePlayers().size() == 1) {
            forceLoadManager.handlePlayerJoin();
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // We run this on the next tick to ensure the player list is updated
        Bukkit.getScheduler().runTask(this, () -> {
            if (Bukkit.getOnlinePlayers().isEmpty()) {
                forceLoadManager.handlePlayerQuit();
            }
        });
    }
}