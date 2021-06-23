package com.hedera.services.state.merkle.virtual.persistence;

import com.hedera.services.state.merkle.virtual.persistence.fcmmap.MemMapSlotStore;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;

/**
 * Interface for a slot store, that stores data in slots in on or more files
 */
public interface SlotStore {
    /**
     * Special Location for when not found
     */
    long NOT_FOUND_LOCATION = Long.MAX_VALUE;

    /**
     * Get the size for each storage slot in bytes.
     *
     * @return data size in bytes
     */
    int getSlotSizeBytes();

    /**
     * Get number of slots that can be stored in this storage
     *
     * @return number of total slots
     */
    int getSize();

    /**
     * Opens all the files in this store. While opening it visits every used slot and calls slotVisitor.
     *
     * @param slotSizeBytes Slot data size in bytes
     * @param fileSize The size of each storage file in bytes
     * @param storageDirectory The path of the directory to store storage files
     * @param filePrefix The prefix for each storage file
     * @param fileExtension The extension for each storage file, for example "dat"
     * @param slotVisitor Visitor that gets to visit every used slot. May be null.
     */
    void open(int slotSizeBytes, int fileSize, Path storageDirectory, String filePrefix, String fileExtension, MemMapSlotStore.SlotVisitor slotVisitor)
            throws IOException;

    /**
     * Add a reference to be tracked to this data store. This can only be closed when all references are removed.
     */
    void addReference();

    /**
     * Add a reference to be tracked to this data store
     */
    void removeReference();

    /**
     * Flush all data to disk and close all files only if there are no references to this data store
     */
    void close();

    /**
     * Get direct access to the slot in the base storage
     *
     * @param location slot location of the data to get
     * @return the could be a direct buffer onto real storage or a shared reused buffer
     */
    ByteBuffer accessSlot(long location);

    /**
     * Return a slot that was obtained by access, this may be the point when it is written to disk depending on
     * implementation.
     *
     * @param location slot location of the data to get
     * @param buffer the buffer obtained by accessSlot()
     */
    void returnSlot(long location, ByteBuffer buffer);

    /**
     * Finds a new slot ready for use
     *
     * @return Index for new slot ready for use
     */
    long getNewSlot();

    /**
     * Delete a slot, marking it as empty
     *
     * @param location the file and slot location to delete
     */
    void deleteSlot(long location);

    /**
     * Make sure all data is flushed to disk. This is an expensive operation. The OS will write all data to disk in the
     * background, so only call this if you need to insure it is written synchronously.
     */
    void sync();
}
