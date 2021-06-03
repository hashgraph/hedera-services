package com.hedera.services.state.merkle.virtual.persistence.mmap;

import java.util.Objects;

/**
 * Simple record for the location of a value in a slot within a file
 */
final class ValueLocation {
    private final MemMapDataFile file;
    private final int slotIndex;

    ValueLocation(MemMapDataFile file, int slotIndex) {
        this.file = Objects.requireNonNull(file);
        this.slotIndex = slotIndex; // Validate it is non-negative?
    }

    MemMapDataFile file() {
        return file;
    }

    int slotIndex() {
        return slotIndex;
    }
}
