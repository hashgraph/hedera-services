package com.hedera.services.state.merkle.v2.persistance;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * API for a slot store, that stores data in slots.
 *
 * It is assumed it support concurrent reading and writing across slots as long as no one tries to read/write to same
 * slot as same time.
 *
 * There is two modes of operation:
 * 1) requirePreAllocation = true. Here getNewSlot() and deleteSlot() are used for allocation of slots.
 * 2) requirePreAllocation = false. Here accessSlot is used without pre-allocation, for any valid positive slot location.
 */
public abstract class SlotStore {
    /** See class doc for def */
    protected final boolean requirePreAllocation;
    /** A slot is made up of a dataSize bytes. Must be a positive number. The value must be less than Integer.MAX_VALUE */
    protected final int slotSizeBytes;

    /**
     * Creates and opens slot store.
     *
     * @param requirePreAllocation if true getNewSlot() is only way to find a new safe slot to write to.
     * @param slotSizeBytes Slot data size in bytes
     */
    public SlotStore(boolean requirePreAllocation, int slotSizeBytes)
            throws IOException {
        this.requirePreAllocation = requirePreAllocation;
        this.slotSizeBytes = slotSizeBytes;
    }

    /**
     * Flush all data to disk and close all files only if there are no references to this data store
     */
    public abstract void close();

    /**
     * Access a slot for reading or writing.
     *
     * @param location the slot location, can be a existing slot or a new slot that we need to make available
     * @param create if true then a new slot will be created if it did not already exist.
     * @return a bytebuffer backed directly to storage, with position 0 being beginning of slot and being rewind-ed or
     * null if create=false and location did not exist
     */
    public abstract ByteBuffer accessSlot(long location, boolean create) throws IOException;

    /**
     * Write whole slot
     *
     * @param location the slot location, can be a existing slot or a new slot that we need to make available
     * @param create if true then a new slot will be created if it did not already exist.
     * @param slotData the data to write to the slot
     * null if create=false and location did not exist
     */
    public void writeSlot(long location, boolean create, byte[] slotData) throws IOException {
        ByteBuffer buf = accessSlot(location,create);
        buf.put(slotData);
    }

    /**
     * Finds a new slot ready for use
     *
     * @return Index for new slot ready for use
     */
    public abstract long getNewSlot();

    /**
     * Delete a slot, marking it as empty
     *
     * @param location the file and slot location to delete
     */
    public abstract void deleteSlot(long location) throws IOException;
}
