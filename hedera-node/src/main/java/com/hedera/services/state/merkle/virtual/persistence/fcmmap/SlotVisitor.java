package com.hedera.services.state.merkle.virtual.persistence.fcmmap;

import java.io.RandomAccessFile;

/**
 * Interface for a visitor that visits each used slot in a MemMapDataFile during startup using a random access file
 */
public interface SlotVisitor {
    /**
     * Visit a slot in a random access file
     *
     * @param location the location for the slot we are visiting
     * @param fileAtSlot the file containing the slot, with position set to the begining of slots data in the file
     */
    public void visitSlot(long location, RandomAccessFile fileAtSlot);
}
