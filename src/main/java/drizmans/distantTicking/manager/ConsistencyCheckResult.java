package drizmans.distantTicking.manager;

/**
 * A data class to hold the results of a consistency check operation.
 */
public class ConsistencyCheckResult {
    private final int totalChunksChecked;
    private final int removedBlockEntries;
    private final int unforceLoadedChunks;
    private final long durationMillis;

    public ConsistencyCheckResult(int totalChunksChecked, int removedBlockEntries, int unforceLoadedChunks, long durationMillis) {
        this.totalChunksChecked = totalChunksChecked;
        this.removedBlockEntries = removedBlockEntries;
        this.unforceLoadedChunks = unforceLoadedChunks;
        this.durationMillis = durationMillis;
    }

    public int getTotalChunksChecked() {
        return totalChunksChecked;
    }

    public int getRemovedBlockEntries() {
        return removedBlockEntries;
    }

    public int getUnforceLoadedChunks() {
        return unforceLoadedChunks;
    }

    public long getDurationMillis() {
        return durationMillis;
    }
}