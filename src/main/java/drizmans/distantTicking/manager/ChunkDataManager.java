package drizmans.distantTicking.manager;

import drizmans.distantTicking.DistantTicking;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class ChunkDataManager {

    private final DistantTicking plugin;
    private final NamespacedKey tickWorthyCountKey;

    /**
     * Constructor for the ChunkDataManager.
     * @param plugin The main plugin instance.
     */
    public ChunkDataManager(DistantTicking plugin) {
        this.plugin = plugin;
        // Create a NamespacedKey unique to your plugin for storing data in PDC
        this.tickWorthyCountKey = new NamespacedKey(plugin, "tick_worthy_count");
    }

    /**
     * Gets the current count of tick-worthy blocks in a chunk's PDC.
     * @param chunk The chunk to query.
     * @return The count, or 0 if the data is not found or invalid.
     */
    public int getTickWorthyCount(Chunk chunk) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        return pdc.getOrDefault(tickWorthyCountKey, PersistentDataType.INTEGER, 0);
    }

    /**
     * Increments the count of tick-worthy blocks in a chunk's PDC.
     * @param chunk The chunk to modify.
     * @return The new count after incrementing.
     */
    public int incrementTickWorthyCount(Chunk chunk) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        int currentCount = getTickWorthyCount(chunk);
        int newCount = currentCount + 1;
        pdc.set(tickWorthyCountKey, PersistentDataType.INTEGER, newCount);
        return newCount;
    }

    /**
     * Decrements the count of tick-worthy blocks in a chunk's PDC.
     * Ensures the count doesn't go below zero.
     * @param chunk The chunk to modify.
     * @return The new count after decrementing.
     */
    public int decrementTickWorthyCount(Chunk chunk) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        int currentCount = getTickWorthyCount(chunk);
        int newCount = Math.max(0, currentCount - 1); // Ensure it doesn't go negative
        pdc.set(tickWorthyCountKey, PersistentDataType.INTEGER, newCount);
        return newCount;
    }

    /**
     * (Optional) Sets the count of tick-worthy blocks in a chunk's PDC directly.
     * Useful for admin commands or initial synchronization.
     * Ensures the count is not negative.
     * @param chunk The chunk to modify.
     * @param count The new count to set.
     */
    public void setTickWorthyCount(Chunk chunk, int count) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        pdc.set(tickWorthyCountKey, PersistentDataType.INTEGER, Math.max(0, count));
    }
}