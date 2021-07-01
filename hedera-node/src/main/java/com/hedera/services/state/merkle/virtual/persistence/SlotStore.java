package com.hedera.services.state.merkle.virtual.persistence;

import java.io.IOException;
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
     * Get number of slots that can be stored in this storage
     *
     * @return number of total slots
     */
    int getSize();

    /**
     * Flush all data to disk and close all files only if there are no references to this data store
     */
    void close();

    /**
     * Acquire a write lock for the given location
     *
     * @param location the location we want to be able to write to
     * @return stamp representing the lock that needs to be returned to releaseWriteLock
     */
    Object acquireWriteLock(long location);

    /**
     * Release a previously acquired write lock
     *
     * @param location the location we are finished writing to
     * @param lockStamp stamp representing the lock that you got from acquireWriteLock
     */
    void releaseWriteLock(long location, Object lockStamp);

    /**
     * Write data into a slot, your slot writer will be called with a output stream while file is locked
     *
     * @param location slot location of the data to get
     * @param writer slot writer to write into the slot with output stream
     */
    void writeSlot(long location, SlotWriter writer) throws IOException;

    /**
     * Acquire a read lock for the given location
     *
     * @param location the location we want to be able to read to
     * @return stamp representing the lock that needs to be returned to releaseReadLock
     */
    Object acquireReadLock(long location);

    /**
     * Release a previously acquired read lock
     *
     * @param location the location we are finished reading from
     * @param lockStamp stamp representing the lock that you got from acquireReadLock
     */
    void releaseReadLock(long location, Object lockStamp);

    /**
     * Read data from a slot, your consumer reader will be called with a input stream while file is locked
     *
     * @param location slot location of the data to get
     * @param reader consumer to read slot from stream
     * @return object read by reader
     */
    <R> R readSlot(long location, SlotReader<R> reader) throws IOException;

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
    void deleteSlot(long location) throws IOException;

    /**
     * Interface for a slot writer
     */
    interface SlotWriter {
        void write(PositionableByteBufferSerializableDataOutputStream outputStream) throws IOException;
    }

    /**
     * Interface for a slot reader
     */
    interface SlotReader<R> {
        R read(PositionableByteBufferSerializableDataInputStream inputStream) throws IOException;
    }

    /**
     * Interface for factory for creating a slot store
     */
    interface SlotStoreFactory {
        /**
         * Creates and opens slot store.
         *
         * @param slotSizeBytes Slot data size in bytes
         * @param fileSize The size of each storage file in bytes
         * @param storageDirectory The path of the directory to store storage files
         * @param filePrefix The prefix for each storage file
         * @param fileExtension The extension for each storage file, for example "dat"
         */
        SlotStore open(int slotSizeBytes, int fileSize, Path storageDirectory, String filePrefix, String fileExtension)
                throws IOException;
    }
}
