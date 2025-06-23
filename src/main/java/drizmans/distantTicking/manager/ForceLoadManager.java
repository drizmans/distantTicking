package drizmans.distantTicking.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import drizmans.distantTicking.DistantTicking;
import drizmans.distantTicking.util.ChunkCoord;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ForceLoadManager {

    private final DistantTicking plugin;
    private final ChunkDataManager chunkDataManager;
    private final File dataFile;
    private final Gson gson;

    private final Map<String, Set<ChunkCoord>> forceLoadedChunks;
    private volatile boolean dataDirty = false;
    private BukkitTask consistencyCheckTask;
    private BukkitTask autoSaveTask;

    /**
     * Constructor for the ForceLoadManager.
     * @param plugin The main plugin instance.
     * @param chunkDataManager The manager for chunk PDC data, needed for consistency checks.
     */
    public ForceLoadManager(DistantTicking plugin, ChunkDataManager chunkDataManager) {
        this.plugin = plugin;
        this.chunkDataManager = chunkDataManager;
        this.dataFile = new File(plugin.getDataFolder(), "active_chunks.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();

        this.forceLoadedChunks = new ConcurrentHashMap<>();
    }

    /**
     * Loads the centralized force-loaded chunk data from active_chunks.json.
     * Also initiates the force-loading of these chunks in the server.
     */
    public void loadData() {
        if (!dataFile.exists()) {
            plugin.getLogger().info("active_chunks.json not found, creating new one."); // This is INFO level as it's important startup info
            plugin.getDataFolder().mkdirs();
            saveData(true);
            return;
        }

        try (FileReader reader = new FileReader(dataFile)) {
            Type type = new TypeToken<Map<String, HashSet<ChunkCoord>>>(){}.getType();
            Map<String, HashSet<ChunkCoord>> loadedMap = gson.fromJson(reader, type);

            if (loadedMap != null) {
                forceLoadedChunks.putAll(loadedMap);
            }
            plugin.getLogger().info("Loaded " + getTotalForceLoadedChunks() + " force-loaded chunks from active_chunks.json."); // INFO level
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to load active_chunks.json: " + e.getMessage()); // SEVERE level for errors
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            int loadedCount = 0;
            for (Map.Entry<String, Set<ChunkCoord>> entry : forceLoadedChunks.entrySet()) {
                String worldName = entry.getKey();
                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    plugin.getLogger().warning("World '" + worldName + "' not found on startup for force loading. Skipping its chunks."); // WARNING level
                    continue;
                }
                for (ChunkCoord coord : entry.getValue()) {
                    try {
                        Chunk chunk = world.getChunkAt(coord.getX(), coord.getZ());
                        chunk.setForceLoaded(true);
                        loadedCount++;
                        plugin.getLogger().fine("Force loaded chunk: " + worldName + " " + coord.toString()); // FINE level for individual chunk load
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to force load chunk " + coord.toString() + " in world " + worldName + ": " + e.getMessage()); // WARNING level for errors
                    }
                }
            }
            plugin.getLogger().info("Initiated force-loading for " + loadedCount + " chunks."); // INFO level
        });
    }

    /**
     * Saves the force-loaded chunk data to active_chunks.json asynchronously.
     */
    public void saveData() {
        saveData(false); // Default to asynchronous save
    }

    /**
     * Saves the force-loaded chunk data to active_chunks.json.
     * @param sync If true, saves synchronously on the current thread. If false, schedules an async task.
     */
    public void saveData(boolean sync) {
        if (!dataDirty && forceLoadedChunks.isEmpty()) {
            return;
        }

        dataDirty = false;

        Runnable saveRunnable = () -> {
            try (FileWriter writer = new FileWriter(dataFile)) {
                gson.toJson(forceLoadedChunks, writer);
                plugin.getLogger().info("Saved " + getTotalForceLoadedChunks() + " force-loaded chunks to active_chunks.json."); // INFO level for overall save
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to save active_chunks.json: " + e.getMessage()); // SEVERE level for errors
            }
        };

        if (sync) {
            saveRunnable.run();
        } else {
            new BukkitRunnable() {
                @Override
                public void run() {
                    saveRunnable.run();
                }
            }.runTaskAsynchronously(plugin);
        }
    }

    /**
     * Adds a chunk to the force-loaded list managed by this plugin and marks it for immediate force-loading.
     * This will persist across server restarts.
     * @param world The world of the chunk.
     * @param x The chunk X coordinate.
     * @param z The chunk Z coordinate.
     */
    public void addChunkToForceLoad(World world, int x, int z) {
        ChunkCoord coord = new ChunkCoord(x, z);
        Set<ChunkCoord> chunksInWorld = forceLoadedChunks.computeIfAbsent(world.getName(), k -> ConcurrentHashMap.newKeySet());
        if (chunksInWorld.add(coord)) {
            dataDirty = true;
            Bukkit.getScheduler().runTask(plugin, () -> {
                Chunk chunk = world.getChunkAt(x, z);
                if (!chunk.isForceLoaded()) {
                    chunk.setForceLoaded(true);
                    plugin.getLogger().fine("Forcing load on chunk: " + world.getName() + " " + coord); // FINE level
                }
            });
        }
    }

    /**
     * Removes a chunk from the force-loaded list managed by this plugin and un-force-loads it.
     * This will un-persist its force-load status.
     * @param world The world of the chunk.
     * @param x The chunk X coordinate.
     * @param z The chunk Z coordinate.
     */
    public void removeChunkFromForceLoad(World world, int x, int z) {
        ChunkCoord coord = new ChunkCoord(x, z);
        Set<ChunkCoord> chunksInWorld = forceLoadedChunks.get(world.getName());
        if (chunksInWorld != null && chunksInWorld.remove(coord)) {
            dataDirty = true;
            if (chunksInWorld.isEmpty()) {
                forceLoadedChunks.remove(world.getName());
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                Chunk chunk = world.getChunkAt(x, z);
                if (chunk.isForceLoaded()) {
                    chunk.setForceLoaded(false);
                    plugin.getLogger().fine("Un-forcing load on chunk: " + world.getName() + " " + coord); // FINE level
                }
            });
        }
    }

    /**
     * Gets a read-only view of all currently force-loaded chunks managed by this plugin.
     * @return A map where keys are world names and values are sets of ChunkCoord objects.
     */
    public Map<String, Set<ChunkCoord>> getForceLoadedChunks() {
        Map<String, Set<ChunkCoord>> immutableMap = new HashMap<>();
        for (Map.Entry<String, Set<ChunkCoord>> entry : forceLoadedChunks.entrySet()) {
            immutableMap.put(entry.getKey(), Collections.unmodifiableSet(entry.getValue()));
        }
        return Collections.unmodifiableMap(immutableMap);
    }

    /**
     * Calculates the total number of chunks currently marked for force-loading across all worlds.
     * @return The total count of force-loaded chunks.
     */
    public int getTotalForceLoadedChunks() {
        return forceLoadedChunks.values().stream().mapToInt(Set::size).sum();
    }

    /**
     * Starts an asynchronous task that periodically saves the force-loaded chunk data to disk.
     * @param intervalMinutes The interval in minutes between saves.
     */
    public void startAutoSaveTask(int intervalMinutes) {
        // Cancel any existing task to prevent duplicates on reload
        if (autoSaveTask != null && !autoSaveTask.isCancelled()) {
            autoSaveTask.cancel();
        }
        // Run asynchronously to avoid blocking the main server thread
        autoSaveTask = new BukkitRunnable() {
            @Override
            public void run() {
                saveData(); // Call the asynchronous save method
            }
        }.runTaskTimerAsynchronously(plugin, 20 * 60 * intervalMinutes, 20 * 60 * intervalMinutes);
    }

    /**
     * Stops the periodic auto-save task.
     * Should be called when the plugin is disabled or reloaded.
     */
    public void stopAutoSaveTask() {
        if (autoSaveTask != null && !autoSaveTask.isCancelled()) {
            autoSaveTask.cancel();
            autoSaveTask = null;
            plugin.getLogger().info("Auto-save task stopped."); // INFO level
        }
    }

    /**
     * Starts a periodic asynchronous task that performs a consistency check.
     * It iterates through chunks believed to be force-loaded by the plugin
     * and verifies their actual tick-worthy block count in PDC.
     * If a chunk has 0 tick-worthy blocks but is in the force-load list, it's removed.
     * @param intervalHours The interval in hours between consistency checks.
     */
    public void startConsistencyCheckTask(int intervalHours) {
        // Cancel any existing task to prevent duplicates on reload
        if (consistencyCheckTask != null && !consistencyCheckTask.isCancelled()) {
            consistencyCheckTask.cancel();
        }

        // Schedule the new consistency check task
        // Run asynchronously as it involves file I/O (chunk loading) and data manipulation
        consistencyCheckTask = new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getLogger().info("Starting periodic consistency check for force-loaded chunks..."); // INFO level
                int removedCount = 0;
                int checkedCount = 0;

                // Iterate over a copy of the world names to avoid ConcurrentModificationException
                // if worlds are unloaded or removed during the check.
                for (String worldName : new HashSet<>(forceLoadedChunks.keySet())) {
                    World world = Bukkit.getWorld(worldName);
                    if (world == null) {
                        plugin.getLogger().warning("Consistency check: World '" + worldName + "' not found. Removing its entries from force-load list."); // WARNING level
                        forceLoadedChunks.remove(worldName); // Remove reference to a missing world
                        dataDirty = true; // Mark dirty if a world was removed
                        continue;
                    }

                    // Use an iterator for safe removal from the Set<ChunkCoord> while iterating
                    // Make a copy to iterate safely if the underlying set changes
                    Set<ChunkCoord> chunksInWorldCopy = new HashSet<>(forceLoadedChunks.getOrDefault(worldName, Collections.emptySet()));
                    Iterator<ChunkCoord> iterator = chunksInWorldCopy.iterator();

                    while (iterator.hasNext()) {
                        ChunkCoord coord = iterator.next();
                        checkedCount++;
                        Chunk chunk;
                        try {
                            // getChunkAt will load the chunk if it's not already loaded.
                            // This might be a performance consideration for very large numbers of checks.
                            chunk = world.getChunkAt(coord.getX(), coord.getZ());
                        } catch (Exception e) {
                            plugin.getLogger().warning("Consistency check: Error getting chunk " + coord.toString() + " in world " + worldName + ": " + e.getMessage()); // WARNING level
                            continue; // Skip this chunk, try again next cycle
                        }

                        // Check the PDC count for this specific chunk
                        int pdcCount = chunkDataManager.getTickWorthyCount(chunk);

                        if (pdcCount == 0) {
                            // This chunk is in our force-load list, but its PDC shows 0 tick-worthy blocks.
                            // It should no longer be force-loaded by our plugin.
                            plugin.getLogger().fine("Consistency check: Removing chunk " + coord.toString() + " in " + worldName + " from force-load list (PDC count is 0)."); // FINE level
                            // Remove from the actual live map used by the manager.
                            // This also sets dataDirty and calls setForceLoaded(false) on the main thread.
                            removeChunkFromForceLoad(world, coord.getX(), coord.getZ());
                            removedCount++;
                        }
                    }
                    // After iterating all chunks for a world, if its set became empty, remove the world entry
                    if (forceLoadedChunks.get(worldName) != null && forceLoadedChunks.get(worldName).isEmpty()) {
                        forceLoadedChunks.remove(worldName);
                        dataDirty = true;
                    }
                }
                plugin.getLogger().info("Consistency check finished. Checked " + checkedCount + " chunks, removed " + removedCount + " outdated entries."); // INFO level
                // Ensure changes are saved after the consistency check
                saveData();
            }
        }.runTaskTimerAsynchronously(plugin, 20 * 60 * 60, 20 * 60 * 60 * intervalHours); // Run every `intervalHours`
    }

    /**
     * Stops the periodic consistency check task.
     * Should be called when the plugin is disabled or reloaded.
     */
    public void stopConsistencyCheckTask() {
        if (consistencyCheckTask != null && !consistencyCheckTask.isCancelled()) {
            consistencyCheckTask.cancel();
            consistencyCheckTask = null;
            plugin.getLogger().info("Consistency check task stopped."); // INFO level
        }
    }
}