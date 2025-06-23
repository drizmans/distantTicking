package drizmans.distantTicking.listener;

import drizmans.distantTicking.DistantTicking;
import drizmans.distantTicking.manager.ChunkDataManager;
import drizmans.distantTicking.manager.ForceLoadManager;
import drizmans.distantTicking.util.TickWorthyBlocks;
import org.bukkit.block.Block;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.world.ChunkLoadEvent;

public class BlockTrackingListener implements Listener {

    private final DistantTicking plugin;
    private final ChunkDataManager chunkDataManager;
    private final ForceLoadManager forceLoadManager;

    /**
     * Constructor for the BlockTrackingListener.
     * @param plugin The main plugin instance.
     * @param chunkDataManager The manager for chunk PDC data.
     * @param forceLoadManager The manager for force-loading chunks.
     */
    public BlockTrackingListener(DistantTicking plugin, ChunkDataManager chunkDataManager, ForceLoadManager forceLoadManager) {
        this.plugin = plugin;
        this.chunkDataManager = chunkDataManager;
        this.forceLoadManager = forceLoadManager;
    }

    /**
     * Handles block placement events.
     * If a tick-worthy block is placed, increments its count in the chunk's PDC
     * and adds the chunk to the force-load manager if it's the first such block.
     * @param event The BlockPlaceEvent.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) // MONITOR to ensure block actually placed, ignoreCancelled if plugin blocks it
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (TickWorthyBlocks.isTickWorthy(block.getType())) {
            Chunk chunk = block.getChunk();
            int newCount = chunkDataManager.incrementTickWorthyCount(chunk);

            // If count goes from 0 to 1, this chunk now needs to be force-loaded
            if (newCount == 1) {
                forceLoadManager.addChunkToForceLoad(chunk.getWorld(), chunk.getX(), chunk.getZ());
            }
            // Changed to FINE level for debug only
            plugin.getLogger().fine("Placed tick-worthy block at " + block.getLocation() + ". Chunk count: " + newCount);
        }
    }

    /**
     * Handles block break events.
     * If a tick-worthy block is broken, decrements its count in the chunk's PDC
     * and removes the chunk from the force-load manager if it's the last such block.
     * @param event The BlockBreakEvent.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        // Check if the block *was* tick-worthy before it was broken
        // This is a simplification; ideally, you might have stored its location in PDC
        // For now, if current type is tick-worthy, assume it was one we tracked.
        if (TickWorthyBlocks.isTickWorthy(block.getType())) {
            handleBlockRemoval(block);
        }
    }

    /**
     * Handles blocks destroyed by explosions.
     * Iterates through the list of blocks that will be removed by the explosion
     * and processes any tick-worthy blocks.
     * @param event The BlockExplodeEvent.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) { // event.blockList() gives list of blocks that WILL be removed
            if (TickWorthyBlocks.isTickWorthy(block.getType())) {
                handleBlockRemoval(block);
            }
        }
    }

    /**
     * Handles blocks destroyed by fire.
     * @param event The BlockBurnEvent.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        Block block = event.getBlock();
        if (TickWorthyBlocks.isTickWorthy(block.getType())) {
            handleBlockRemoval(block);
        }
    }

    /**
     * Helper method to centralize logic for handling tick-worthy block removal.
     * Decrements the count in the chunk's PDC and potentially removes the chunk
     * from force-loading if no tick-worthy blocks remain.
     * @param block The block that was removed.
     */
    private void handleBlockRemoval(Block block) {
        Chunk chunk = block.getChunk();
        int newCount = chunkDataManager.decrementTickWorthyCount(chunk);

        // If count goes from 1 to 0, this chunk no longer needs to be force-loaded
        if (newCount == 0) {
            forceLoadManager.removeChunkFromForceLoad(chunk.getWorld(), chunk.getX(), chunk.getZ());
        }
        // Changed to FINE level for debug only
        plugin.getLogger().fine("Removed tick-worthy block at " + block.getLocation() + ". Chunk count: " + newCount);
    }

    /**
     * Handles chunk load events.
     * When a chunk loads, it checks its PDC to see if it contains tick-worthy blocks.
     * If so, it re-asserts its force-load status in the ForceLoadManager.
     * This is crucial for persistence across server restarts/reloads.
     * @param event The ChunkLoadEvent.
     */
    @EventHandler(priority = EventPriority.MONITOR) // MONITOR is good for observing loaded chunks
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        int tickWorthyCount = chunkDataManager.getTickWorthyCount(chunk);

        if (tickWorthyCount > 0) {
            // Re-assert the force-load status for this chunk based on its PDC data
            // This is crucial for persistence across server restarts/reloads.
            // addChunkToForceLoad handles the setForceLoaded(true) call.
            forceLoadManager.addChunkToForceLoad(chunk.getWorld(), chunk.getX(), chunk.getZ());
            // Changed to FINE level for debug only
            plugin.getLogger().fine("Re-asserting force-load for chunk " + chunk.getX() + "," + chunk.getZ() + " in " + chunk.getWorld().getName() + " (count: " + tickWorthyCount + ")");
        }
    }
}