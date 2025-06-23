package drizmans.distantTicking.util;

import java.util.Objects;

/**
 * An immutable utility class to represent a chunk's X and Z coordinates.
 * Provides proper equals() and hashCode() implementations for use in Sets and Maps.
 */
public class ChunkCoord {
    private final int x;
    private final int z;

    /**
     * Constructs a new ChunkCoord object.
     * @param x The chunk's X coordinate.
     * @param z The chunk's Z coordinate.
     */
    public ChunkCoord(int x, int z) {
        this.x = x;
        this.z = z;
    }

    /**
     * Gets the chunk's X coordinate.
     * @return The X coordinate.
     */
    public int getX() {
        return x;
    }

    /**
     * Gets the chunk's Z coordinate.
     * @return The Z coordinate.
     */
    public int getZ() {
        return z;
    }

    /**
     * Compares this ChunkCoord with another object for equality.
     * Two ChunkCoord objects are equal if they have the same X and Z coordinates.
     * @param o The object to compare with.
     * @return true if the objects are equal, false otherwise.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChunkCoord that = (ChunkCoord) o;
        return x == that.x && z == that.z;
    }

    /**
     * Returns a hash code value for this ChunkCoord.
     * Essential for correct behavior in hash-based collections (HashSet, HashMap).
     * @return A hash code value for this object.
     */
    @Override
    public int hashCode() {
        return Objects.hash(x, z);
    }

    /**
     * Returns a string representation of this ChunkCoord in "X:Z" format.
     * @return A string representing the chunk coordinates.
     */
    @Override
    public String toString() {
        return x + ":" + z;
    }
}