package drizmans.distantTicking.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import drizmans.distantTicking.DistantTicking;
import drizmans.distantTicking.util.BlockCoord;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataAdapterContext; // Import this
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkDataManager {

    private final DistantTicking plugin;
    private final NamespacedKey oldTickWorthyCountKey;
    private final NamespacedKey tickWorthyBlocksKey;
    private final Gson gson;

    private final PersistentDataType<String, Set<BlockCoord>> BLOCK_COORD_SET_TYPE;


    /**
     * Constructor for the ChunkDataManager.
     * @param plugin The main plugin instance.
     */
    public ChunkDataManager(DistantTicking plugin) {
        this.plugin = plugin;
        this.oldTickWorthyCountKey = new NamespacedKey(plugin, "tick_worthy_count");
        this.tickWorthyBlocksKey = new NamespacedKey(plugin, "tick_worthy_blocks");
        this.gson = new GsonBuilder().create();

        this.BLOCK_COORD_SET_TYPE = new PersistentDataType<String, Set<BlockCoord>>() {
            @Override
            public Class<String> getPrimitiveType() {
                return String.class;
            }

            @Override
            public Class<Set<BlockCoord>> getComplexType() {
                // This cast is a common workaround for Java's type erasure with generics
                return (Class<Set<BlockCoord>>) (Class<?>) Set.class;
            }

            @Override
            public String toPrimitive(Set<BlockCoord> complex, PersistentDataAdapterContext context) {
                // Serialize Set<BlockCoord> to JSON string
                if (complex == null || complex.isEmpty()) {
                    return "[]"; // Return empty JSON array for empty set
                }
                return gson.toJson(complex);
            }

            @Override
            public Set<BlockCoord> fromPrimitive(String primitive, PersistentDataAdapterContext context) {
                // Deserialize JSON string to Set<BlockCoord>
                if (primitive == null || primitive.isEmpty() || primitive.equals("[]")) {
                    return ConcurrentHashMap.newKeySet(); // Return an empty, thread-safe set
                }
                Type type = new TypeToken<HashSet<BlockCoord>>(){}.getType();
                Set<BlockCoord> deserialized = gson.fromJson(primitive, type);

                // Populate a new ConcurrentHashMap.KeySetView from the deserialized HashSet
                if (deserialized != null) {
                    Set<BlockCoord> concurrentSet = ConcurrentHashMap.newKeySet();
                    concurrentSet.addAll(deserialized);
                    return concurrentSet;
                }
                return ConcurrentHashMap.newKeySet();
            }
        };
    }

    /**
     * Gets the set of tick-worthy block coordinates in a chunk's PDC.
     * @param chunk The chunk to query.
     * @return A thread-safe set of BlockCoord objects, or an empty set if no data is found.
     */
    public Set<BlockCoord> getTickWorthyBlocks(Chunk chunk) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        return pdc.getOrDefault(tickWorthyBlocksKey, BLOCK_COORD_SET_TYPE, ConcurrentHashMap.newKeySet());
    }

    /**
     * Adds a BlockCoord to the set of tick-worthy blocks in a chunk's PDC.
     * @param chunk The chunk to modify.
     * @param blockCoord The BlockCoord to add.
     * @return true if the block was added, false if it was already present.
     */
    public boolean addTickWorthyBlock(Chunk chunk, BlockCoord blockCoord) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        Set<BlockCoord> currentBlocks = getTickWorthyBlocks(chunk);
        boolean added = currentBlocks.add(blockCoord);
        if (added) {
            pdc.set(tickWorthyBlocksKey, BLOCK_COORD_SET_TYPE, currentBlocks);
        }
        return added;
    }

    /**
     * Removes a BlockCoord from the set of tick-worthy blocks in a chunk's PDC.
     * @param chunk The chunk to modify.
     * @param blockCoord The BlockCoord to remove.
     * @return true if the block was removed, false if it was not present.
     */
    public boolean removeTickWorthyBlock(Chunk chunk, BlockCoord blockCoord) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        Set<BlockCoord> currentBlocks = getTickWorthyBlocks(chunk);
        boolean removed = currentBlocks.remove(blockCoord);
        if (removed) {
            if (currentBlocks.isEmpty()) {
                pdc.remove(tickWorthyBlocksKey);
            } else {
                pdc.set(tickWorthyBlocksKey, BLOCK_COORD_SET_TYPE, currentBlocks);
            }
        }
        return removed;
    }

    /**
     * Sets the entire set of tick-worthy block coordinates in a chunk's PDC.
     * Useful for admin commands or initial synchronization (e.g., /dt refresh).
     * @param chunk The chunk to modify.
     * @param blockCoords The new set of BlockCoord objects to set.
     */
    public void setTickWorthyBlocks(Chunk chunk, Set<BlockCoord> blockCoords) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        if (blockCoords == null || blockCoords.isEmpty()) {
            pdc.remove(tickWorthyBlocksKey);
        } else {
            pdc.set(tickWorthyBlocksKey, BLOCK_COORD_SET_TYPE, blockCoords);
        }
    }

    /**
     * Gets the number of tick-worthy blocks in a chunk's PDC based on the new location-based storage.
     * This replaces the old getTickWorthyCount.
     * @param chunk The chunk to query.
     * @return The count of stored tick-worthy block locations.
     */
    public int getTickWorthyBlocksCount(Chunk chunk) {
        return getTickWorthyBlocks(chunk).size();
    }

    /**
     * Removes the old 'tick_worthy_count' PDC key from a chunk.
     * This is for cleanup purposes during the transition.
     * @param chunk The chunk to clean.
     * @return true if the old key was present and removed, false otherwise.
     */
    public boolean removeOldPdcKey(Chunk chunk) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        if (pdc.has(oldTickWorthyCountKey, PersistentDataType.INTEGER)) {
            pdc.remove(oldTickWorthyCountKey);
            plugin.getLogger().fine("Removed old 'tick_worthy_count' PDC key from chunk (" + chunk.getX() + ", " + chunk.getZ() + ") in " + chunk.getWorld().getName());
            return true;
        }
        return false;
    }
}