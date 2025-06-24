package drizmans.distantTicking.listener;

import drizmans.distantTicking.DistantTicking;
import drizmans.distantTicking.manager.ChunkDataManager;
import drizmans.distantTicking.manager.ForceLoadManager;
import drizmans.distantTicking.util.BlockCoord;
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

import java.util.Set;

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
     * If a tick-worthy block is placed, adds its location to the chunk's PDC
     * and adds the chunk to the force-load manager if it's the first such block.
     * @param event The BlockPlaceEvent.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) // MONITOR to ensure block actually placed, ignoreCancelled if plugin blocks it
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (TickWorthyBlocks.isTickWorthy(block.getType())) {
            Chunk chunk = block.getChunk();
            BlockCoord blockCoord = new BlockCoord(block.getX() & 0xF, block.getY(), block.getZ() & 0xF); // Use chunk-relative X,Z

            Set<BlockCoord> currentTickWorthyBlocks = chunkDataManager.getTickWorthyBlocks(chunk);
            boolean wasEmpty = currentTickWorthyBlocks.isEmpty();

            if (chunkDataManager.addTickWorthyBlock(chunk, blockCoord)) { // addTickWorthyBlock returns true if added
                // If the set was empty before adding this block, the chunk now needs to be force-loaded
                if (wasEmpty) {
                    forceLoadManager.addChunkToForceLoad(chunk.getWorld(), chunk.getX(), chunk.getZ());
                }
                plugin.getLogger().fine("Placed tick-worthy block at " + block.getLocation() + ". Chunk now tracks " + (currentTickWorthyBlocks.size() + 1) + " blocks.");
            }
        }
    }

    /**
     * Handles block break events.
     * If a tick-worthy block is broken, removes its location from the chunk's PDC
     * and removes the chunk from the force-load manager if it's the last such block.
     * @param event The BlockBreakEvent.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        // For accurate tracking, we assume the block was tick-worthy if its type matches,
        // and we attempt to remove its exact location.
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
     * Removes the block's location from the chunk's PDC and potentially removes the chunk
     * from force-loading if no tick-worthy blocks remain.
     * @param block The block that was removed.
     */
    private void handleBlockRemoval(Block block) {
        Chunk chunk = block.getChunk();
        BlockCoord blockCoord = new BlockCoord(block.getX() & 0xF, block.getY(), block.getZ() & 0xF); // Use chunk-relative X,Z

        Set<BlockCoord> currentTickWorthyBlocks = chunkDataManager.getTickWorthyBlocks(chunk);
        boolean wasNonEmpty = !currentTickWorthyBlocks.isEmpty();

        if (chunkDataManager.removeTickWorthyBlock(chunk, blockCoord)) { // removeTickWorthyBlock returns true if removed
            // If the set becomes empty after removing this block, the chunk no longer needs to be force-loaded
            if (wasNonEmpty && chunkDataManager.getTickWorthyBlocks(chunk).isEmpty()) {
                forceLoadManager.removeChunkFromForceLoad(chunk.getWorld(), chunk.getX(), chunk.getZ());
            }
            plugin.getLogger().fine("Removed tick-worthy block at " + block.getLocation() + ". Chunk now tracks " + chunkDataManager.getTickWorthyBlocksCount(chunk) + " blocks.");
        }
    }

    /**
     * Handles chunk load events.
     * When a chunk loads, it checks its PDC to see if it contains any stored tick-worthy block locations.
     * If so, it re-asserts its force-load status in the ForceLoadManager.
     * This is crucial for persistence across server restarts/reloads.
     * @param event The ChunkLoadEvent.
     */
    @EventHandler(priority = EventPriority.MONITOR) // MONITOR is good for observing loaded chunks
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        int tickWorthyBlocksCount = chunkDataManager.getTickWorthyBlocksCount(chunk);

        if (tickWorthyBlocksCount > 0) {
            // Re-assert the force-load status for this chunk based on its PDC data
            // This is crucial for persistence across server restarts/reloads.
            // addChunkToForceLoad handles the setForceLoaded(true) call.
            forceLoadManager.addChunkToForceLoad(chunk.getWorld(), chunk.getX(), chunk.getZ());
            plugin.getLogger().fine("Re-asserting force-load for chunk " + chunk.getX() + "," + chunk.getZ() + " in " + chunk.getWorld().getName() + " (tracks: " + tickWorthyBlocksCount + " blocks)");
        }
        // During transition, also attempt to clean up old PDC key if present
        chunkDataManager.removeOldPdcKey(chunk);
    }
}