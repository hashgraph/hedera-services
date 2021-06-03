package com.hedera.services.state.merkle.virtual.persistence.mmap;

/**
 * Very simple record for the header of a slot
 */
final class SlotKeyLocation {
    private final byte[] key;
    private final int slotIndex;

    SlotKeyLocation(byte[] key, int slotIndex) {
        this.key = key;
        this.slotIndex = slotIndex;
    }

    byte[] key() {
        return key;
    }

    int slotIndex() {
        return slotIndex;
    }
}
