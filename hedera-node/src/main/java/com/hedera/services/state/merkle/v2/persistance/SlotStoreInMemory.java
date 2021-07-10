package com.hedera.services.state.merkle.v2.persistance;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A super simple implementation of slot store using in memory hash map.
 */
public class SlotStoreInMemory extends SlotStore {
    private final ConcurrentHashMap<Long,ByteBuffer> data = new ConcurrentHashMap<>();
    private final AtomicLong index = new AtomicLong(0);

    /**
     * Creates and opens slot store.
     *
     * @param requirePreAllocation if true getNewSlot() is only way to find a new safe slot to write to.
     * @param slotSizeBytes        Slot data size in bytes
     */
    public SlotStoreInMemory(boolean requirePreAllocation, int slotSizeBytes) throws IOException {
        super(requirePreAllocation, slotSizeBytes);
    }

    /**
     * Flush all data to disk and close all files only if there are no references to this data store
     */
    @Override
    public void close() {
        data.clear();
    }

    /**
     * Access a slot for reading or writing.
     *
     * @param location the slot location, can be a existing slot or a new slot that we need to make available
     * @param create if true then a new slot will be created if it did not already exist.
     * @return a bytebuffer backed directly to storage, with position 0 being beginning of slot and being rewind-ed or
     * null if create=false and location did not exist
     */
    @Override
    public ByteBuffer accessSlot(long location, boolean create) {
        if(create) {
            return data.computeIfAbsent(location, aLong -> ByteBuffer.allocate(slotSizeBytes));
        } else {
            ByteBuffer buffer = data.get(location);
            buffer.rewind();
            return buffer;
        }
    }

    /**
     * Finds a new slot ready for use
     *
     * @return Index for new slot ready for use
     */
    @Override
    public long getNewSlot() {
        return index.getAndIncrement();
    }

    /**
     * Delete a slot, marking it as empty
     *
     * @param location the file and slot location to delete
     */
    @Override
    public void deleteSlot(long location) {
        data.remove(location);
    }
}
