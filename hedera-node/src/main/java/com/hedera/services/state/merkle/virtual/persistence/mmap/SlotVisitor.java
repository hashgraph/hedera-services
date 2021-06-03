package com.hedera.services.state.merkle.virtual.persistence.mmap;

import java.io.RandomAccessFile;

/**
 * Interface for a visitor that visits each used slot in a MemMapDataFile during startup using a random access file
 */
public interface SlotVisitor {
    /**
     * Visit a slot in a random access file
     *
     * @param fileIndex the index of the file for the slot we are visiting
     * @param slotIndex the index of the slot we are visiting
     * @param fileAtSlot the file containing the slot, with position set to the begining of slots data in the file
     */
    public void visitSlot(int fileIndex, int slotIndex, RandomAccessFile fileAtSlot);
}
