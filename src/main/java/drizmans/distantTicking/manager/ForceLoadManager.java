package drizmans.distantTicking.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import drizmans.distantTicking.DistantTicking;
import drizmans.distantTicking.util.BlockCoord;
import drizmans.distantTicking.util.ChunkCoord;
import drizmans.distantTicking.util.TickWorthyBlocks;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
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
            plugin.getLogger().info("active_chunks.json not found, creating new one.");
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
            plugin.getLogger().info("Loaded " + getTotalForceLoadedChunks() + " force-loaded chunks from active_chunks.json.");
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to load active_chunks.json: " + e.getMessage());
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            int loadedCount = 0;
            for (Map.Entry<String, Set<ChunkCoord>> entry : forceLoadedChunks.entrySet()) {
                String worldName = entry.getKey();
                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    plugin.getLogger().warning("World '" + worldName + "' not found on startup for force loading. Skipping its chunks.");
                    continue;
                }
                for (ChunkCoord coord : entry.getValue()) {
                    try {
                        Chunk chunk = world.getChunkAt(coord.getX(), coord.getZ());
                        chunk.setForceLoaded(true);
                        loadedCount++;
                        plugin.getLogger().fine("Force loaded chunk: " + worldName + " " + coord.toString());
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to force load chunk " + coord.toString() + " in world " + worldName + ": " + e.getMessage());
                    }
                }
            }
            plugin.getLogger().info("Initiated force-loading for " + loadedCount + " chunks.");
        });
    }

    /**
     * Saves the force-loaded chunk data to active_chunks.json asynchronously.
     */
    public void saveData() {
        saveData(false);
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
                plugin.getLogger().info("Saved " + getTotalForceLoadedChunks() + " force-loaded chunks to active_chunks.json.");
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to save active_chunks.json: " + e.getMessage());
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
                    plugin.getLogger().fine("Forcing load on chunk: " + world.getName() + " " + coord);
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
                    plugin.getLogger().fine("Un-forcing load on chunk: " + world.getName() + " " + coord);
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
        if (autoSaveTask != null && !autoSaveTask.isCancelled()) {
            autoSaveTask.cancel();
        }
        autoSaveTask = new BukkitRunnable() {
            @Override
            public void run() {
                saveData();
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
            plugin.getLogger().info("Auto-save task stopped.");
        }
    }

    /**
     * Starts a periodic asynchronous task that performs a detailed consistency check.
     * It iterates through chunks believed to be force-loaded by the plugin.
     * For each chunk, it retrieves the stored tick-worthy block locations from PDC.
     * It then verifies if the block at each stored location still exists and is a tick-worthy type.
     * Outdated or invalid locations are removed from PDC. If a chunk's PDC becomes empty, it's removed from force-load.
     * @param intervalHours The interval in hours between consistency checks.
     */
    public void startConsistencyCheckTask(int intervalHours) {
        if (consistencyCheckTask != null && !consistencyCheckTask.isCancelled()) {
            consistencyCheckTask.cancel();
        }

        consistencyCheckTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Call the encapsulated check logic
                ConsistencyCheckResult result = performConsistencyCheck();
                plugin.getLogger().info("Detailed consistency check finished. Checked " + result.getTotalChunksChecked() + " chunks. Removed " + result.getRemovedBlockEntries() + " outdated block entries and un-force loaded " + result.getUnforceLoadedChunks() + " chunks. Duration: " + (result.getDurationMillis() / 1000.0) + " seconds.");
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
            plugin.getLogger().info("Consistency check task stopped.");
        }
    }

    /**
     * Performs the detailed consistency check and returns the results.
     * This method contains the core logic for checking block validity.
     * @return A ConsistencyCheckResult object containing metrics of the check.
     */
    public ConsistencyCheckResult performConsistencyCheck() { // Made public for direct calling by command
        long startTime = System.currentTimeMillis();
        int removedEntriesCount = 0; // Number of individual block entries removed from PDC
        int unforceLoadedChunksCount = 0; // Number of chunks fully unforce-loaded
        int totalChunksChecked = 0; // Total chunks processed by this check

        for (String worldName : new HashSet<>(forceLoadedChunks.keySet())) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("Consistency check: World '" + worldName + "' not found. Removing its entries from force-load list.");
                forceLoadedChunks.remove(worldName);
                dataDirty = true;
                continue;
            }

            Set<ChunkCoord> chunksInWorldCopy = new HashSet<>(forceLoadedChunks.getOrDefault(worldName, Collections.emptySet()));
            Iterator<ChunkCoord> iterator = chunksInWorldCopy.iterator();

            while (iterator.hasNext()) {
                ChunkCoord coord = iterator.next();
                totalChunksChecked++; // Increment total chunks checked
                Chunk chunk;
                try {
                    // getChunkAt will load the chunk if it's not already loaded.
                    chunk = world.getChunkAt(coord.getX(), coord.getZ());
                } catch (Exception e) {
                    plugin.getLogger().warning("Consistency check: Error getting chunk " + coord.toString() + " in world " + worldName + ": " + e.getMessage());
                    continue;
                }

                Set<BlockCoord> trackedBlocks = chunkDataManager.getTickWorthyBlocks(chunk);
                Set<BlockCoord> validatedBlocks = ConcurrentHashMap.newKeySet();

                int originalTrackedCount = trackedBlocks.size();
                for (BlockCoord blockCoord : trackedBlocks) {
                    try {
                        Block block = chunk.getBlock(blockCoord.getX(), blockCoord.getY(), blockCoord.getZ());
                        if (block != null && !block.getType().isAir() && TickWorthyBlocks.isTickWorthy(block.getType())) {
                            validatedBlocks.add(blockCoord);
                        } else {
                            plugin.getLogger().fine("Consistency check: Block at " + block.getLocation() + " in " + chunk.getWorld().getName() + " is no longer tick-worthy or missing. Removing from PDC.");
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Consistency check: Error checking block at " + blockCoord.toString() + " in chunk " + coord.toString() + " in world " + worldName + ": " + e.getMessage());
                    }
                }

                if (validatedBlocks.size() != originalTrackedCount) {
                    chunkDataManager.setTickWorthyBlocks(chunk, validatedBlocks);
                    removedEntriesCount += (originalTrackedCount - validatedBlocks.size());
                    dataDirty = true;
                }

                if (validatedBlocks.isEmpty()) {
                    plugin.getLogger().fine("Consistency check: Removing chunk " + coord.toString() + " in " + worldName + " from force-load list (PDC block locations count is now 0).");
                    removeChunkFromForceLoad(world, coord.getX(), coord.getZ());
                    unforceLoadedChunksCount++;
                }
            }
            if (forceLoadedChunks.get(worldName) != null && forceLoadedChunks.get(worldName).isEmpty()) {
                forceLoadedChunks.remove(worldName);
                dataDirty = true;
            }
        }
        saveData();

        long duration = System.currentTimeMillis() - startTime;
        return new ConsistencyCheckResult(totalChunksChecked, removedEntriesCount, unforceLoadedChunksCount, duration);
    }
}