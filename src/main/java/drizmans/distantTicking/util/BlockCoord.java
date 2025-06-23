package drizmans.distantTicking.util;

import java.util.Objects;

/**
 * An immutable utility class to represent a block's X, Y, and Z coordinates
 * relative to the chunk origin (0-15 for X and Z, world height for Y).
 * Provides proper equals() and hashCode() implementations for use in Sets and Maps.
 */
public class BlockCoord {
    private final int x;
    private final int y;
    private final int z;

    /**
     * Constructs a new BlockCoord object.
     * @param x The block's X coordinate (0-15 within chunk).
     * @param y The block's Y coordinate (absolute world Y).
     * @param z The block's Z coordinate (0-15 within chunk).
     */
    public BlockCoord(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Gets the block's X coordinate (0-15 within chunk).
     * @return The X coordinate.
     */
    public int getX() {
        return x;
    }

    /**
     * Gets the block's Y coordinate (absolute world Y).
     * @return The Y coordinate.
     */
    public int getY() {
        return y;
    }

    /**
     * Gets the block's Z coordinate (0-15 within chunk).
     * @return The Z coordinate.
     */
    public int getZ() {
        return z;
    }

    /**
     * Compares this BlockCoord with another object for equality.
     * Two BlockCoord objects are equal if they have the same X, Y, and Z coordinates.
     * @param o The object to compare with.
     * @return true if the objects are equal, false otherwise.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BlockCoord that = (BlockCoord) o;
        return x == that.x && y == that.y && z == that.z;
    }

    /**
     * Returns a hash code value for this BlockCoord.
     * Essential for correct behavior in hash-based collections (HashSet, HashMap).
     * @return A hash code value for this object.
     */
    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }

    /**
     * Returns a string representation of this BlockCoord in "X:Y:Z" format.
     * @return A string representing the block coordinates.
     */
    @Override
    public String toString() {
        return x + ":" + y + ":" + z;
    }
}