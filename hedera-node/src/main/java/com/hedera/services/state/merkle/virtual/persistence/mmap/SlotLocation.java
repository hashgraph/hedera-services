package com.hedera.services.state.merkle.virtual.persistence.mmap;

import java.util.Objects;

/**
 * Simple record for the location of a value in a slot within a file
 */
final class SlotLocation {
    private final int fileIndex;
    private final int slotIndex;

    SlotLocation(int fileIndex, int slotIndex) {
        this.fileIndex = fileIndex;
        this.slotIndex = slotIndex; // Validate it is non-negative?
    }

    public int fileIndex() {
        return fileIndex;
    }

    int slotIndex() {
        return slotIndex;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SlotLocation that = (SlotLocation) o;
        return fileIndex == that.fileIndex && slotIndex == that.slotIndex;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileIndex, slotIndex);
    }
}
