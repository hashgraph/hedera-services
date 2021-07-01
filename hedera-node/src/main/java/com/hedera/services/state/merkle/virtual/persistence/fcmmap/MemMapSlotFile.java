package com.hedera.services.state.merkle.virtual.persistence.fcmmap;

import com.hedera.services.state.merkle.virtual.persistence.PositionableByteBufferSerializableDataInputStream;
import com.hedera.services.state.merkle.virtual.persistence.PositionableByteBufferSerializableDataOutputStream;
import com.hedera.services.state.merkle.virtual.persistence.SlotStore;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

/**
 * Memory Mapped File key value store, with a maximum number of items it can store and a max size for any item.
 * <p>
 * The data file one continuous buffer of slots. Each slot is a short for value length, followed by the key byte array
 * then the value byte array.
 * <p>
 * -------------------------------------------------
 * | Byte header | Data bytes | .... repeat
 * -------------------------------------------------
 */
class MemMapSlotFile implements Closeable {
    /** constant used for slot header to mean that slot is empty */
    private static final byte EMPTY = 0;
    /** constant used for slot header to mean that slot is used */
    private static final byte USED = 1;
    /** constant used for size of slot header in bytes */
    static final int HEADER_SIZE = 8;
    /** Number of key/values pairs to store in the file */
    private final int size;
    /** The number of bytes each slot takes up */
    private final int slotSize;
    /** index for this file */
    private final int fileIndex;

    private RandomAccessFile randomAccessFile;
    private FileChannel fileChannel;
    private MappedByteBuffer mappedBuffer;

    /** Readwrite lock for this file */
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /** Current state if the file is open or not */
    private boolean fileIsOpen = false;

    /** stack of empty slots not at end of file */
    private final Deque<Integer> freeSlotsForReuse = new ArrayDeque<>(100);

    /**
     * Index of first available slot at the end of file.
     * = 0 being first slot is free,
     * = "size" means no free slots are available at the end of the file.
     */
    private int nextFreeSlotAtEnd;

    /**
     * Create MapDataFile, you need to call open() after to actually open/create file.
     *
     * @param dataSize Data size in bytes
     * @param size     Number of key/values pairs to store in the file
     * @param file     The file we are storing the data in
     */
    public MemMapSlotFile(int dataSize, int size, File file, int fileIndex) throws IOException {
        this.size = size;
        /** The file we are storing the data in */
        this.fileIndex = fileIndex;
        this.slotSize = dataSize + HEADER_SIZE;
        // calculate file size in bytes
        /** total file size in bytes */
        int fileSize = this.slotSize * size;
        // Open the file
        // check if file existed before
        boolean fileExisted = file.exists();
        // open random access file
        randomAccessFile = new RandomAccessFile(file, "rw");
        if (fileExisted) {
            // TODO It seems we have a problem if the file was created at the wrong
            // size before. I had a file that had size 0 and couldn't recover.
            loadExistingFile(randomAccessFile);
        } else {
            // set size for new empty file
            randomAccessFile.setLength(fileSize);
            // mark first slot as free
            nextFreeSlotAtEnd = 0;
        }
        // get file channel and memory map the file
        fileChannel = randomAccessFile.getChannel();
        mappedBuffer = fileChannel.map(FileChannel.MapMode.READ_WRITE, 0, fileChannel.size());
        // mark file as open
        fileIsOpen = true;
    }

    //==================================================================================================================
    // Public API methods

    /**
     * Acquire a write lock for the given slot index
     *
     * @param slotIndex the slot index we want to be able to write to
     * @return stamp representing the lock that needs to be returned to releaseWriteLock
     */
    public Object acquireWriteLock(int slotIndex) {
        lock.writeLock().lock();
        return lock.writeLock();
    }

    /**
     * Release a previously acquired write lock
     *
     * @param slotIndex the slot index we are done writing to
     * @param lockStamp stamp representing the lock that you got from acquireWriteLock
     */
    public void releaseWriteLock(int slotIndex, Object lockStamp) {
        if (lockStamp == lock.writeLock()) lock.writeLock().unlock();
    }

    /**
     * Acquire a read lock for the given slot index
     *
     * @param slotIndex the slot index we want to be able to read to
     * @return stamp representing the lock that needs to be returned to releaseReadLock
     */
    public Object acquireReadLock(int slotIndex) {
        lock.readLock().lock();
        return lock.readLock();
    }

    /**
     * Release a previously acquired read lock
     *
     * @param slotIndex the slot index we are done reading from
     * @param lockStamp stamp representing the lock that you got from acquireReadLock
     */
    public void releaseReadLock(int slotIndex, Object lockStamp) {
        if (lockStamp == lock.readLock()) lock.readLock().unlock();
    }

    /**
     * Get the index for this file
     *
     * @return file index
     */
    public int getFileIndex() {
        return fileIndex;
    }

    /**
     * Check if the file is full, with no available slots
     *
     * @return True if the file is full
     */
    public boolean isFileFull() {
        lock.readLock().lock();
        try {
            return nextFreeSlotAtEnd >= size && freeSlotsForReuse.isEmpty();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Closes the file and clears up all resources
     *
     * @throws IOException If there was a problem closing the file
     */
    public void close() throws IOException {
        lock.writeLock().lock();
        try {
            if (fileIsOpen) {
                mappedBuffer.force();
                mappedBuffer = null;
                fileChannel.close();
                fileChannel = null;
                randomAccessFile.close();
                randomAccessFile = null;
                // mark file as closed
                fileIsOpen = false;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Write data into a slot, your callable writer will be called with a output stream while file is locked
     *
     * @param slotIndex the slot index of the data to write
     * @param writer callable to write into the slot with output stream
     */
    public void writeSlot(int slotIndex, SlotStore.SlotWriter writer) throws IOException {
        throwIfClosed();
        if (writer == null) throw new IllegalArgumentException("Can not call writeSlot with null writer");
        // calculate the offset position of the value in the file
        final int slotOffset = slotSize * slotIndex;
        // create output stream
        final ByteBuffer subBuffer = mappedBuffer.slice();
        subBuffer.position(slotOffset + HEADER_SIZE);
        subBuffer.limit(subBuffer.position() + slotSize - HEADER_SIZE);
        final PositionableByteBufferSerializableDataOutputStream outputStream = new PositionableByteBufferSerializableDataOutputStream(subBuffer);
        // call write
        lock.writeLock().lock();
        try {
            writer.write(outputStream);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Read data from a slot, your callable reader will be called with a input stream while file is locked
     *
     * @param slotIndex the slot index of the data to get
     * @param reader callable to read slot from stream
     */
    public <R> R readSlot(int slotIndex, SlotStore.SlotReader<R> reader) throws IOException {
        throwIfClosed();
        if (reader == null) throw new IllegalArgumentException("Can not call readSlot with null reader");
        // calculate the offset position of the value in the file
        final int slotOffset = slotSize * slotIndex;
        // create input stream
        final ByteBuffer subBuffer = mappedBuffer.asReadOnlyBuffer();
        subBuffer.position(slotOffset + HEADER_SIZE);
        subBuffer.limit(subBuffer.position() + slotSize - HEADER_SIZE);
        final PositionableByteBufferSerializableDataInputStream inputStream = new PositionableByteBufferSerializableDataInputStream(subBuffer);
        // call read
        lock.readLock().lock();
        try {
            return reader.read(inputStream);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Finds a new slot in this file
     *
     * @return Slot index if the value was successfully stored, -1 if there was no space available
     */
    public synchronized int getNewSlot() {
        throwIfClosed();
        if (isFileFull()) return -1;
        lock.writeLock().lock(); // TODO seems like maybe we could have another lock for getting new slots and deleting
        try {
            // calc next available slot
            int slotIndex;
            if (nextFreeSlotAtEnd < size) {
                slotIndex = nextFreeSlotAtEnd;
                nextFreeSlotAtEnd++;
            } else if (!freeSlotsForReuse.isEmpty()) {
                slotIndex = freeSlotsForReuse.pop();
            } else {
                throw new RuntimeException("This should not happen, means we think there is free space but there is not." +
                        "nextFreeSlotAtEnd=" + nextFreeSlotAtEnd + " ,size=" + size);
            }
            // mark slot as used
            mappedBuffer.put(slotIndex * slotSize, USED);
            // return slot index
            return slotIndex;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Delete a slot, marking it as empty
     *
     * @param slotIndex The index of the slot to delete
     */
    public void deleteSlot(int slotIndex) throws IOException {
        throwIfClosed();
        lock.writeLock().lock(); // TODO seems like maybe we could have another lock for getting new slots and deleting
        try {
            // store EMPTY for the size in slot
            mappedBuffer.put(slotIndex, EMPTY);
            // add slot to stack of empty slots
            freeSlotsForReuse.push(slotIndex); // TODO could maybe check if it as the end and add slots back on nextFreeSlotAtEnd
        } finally {
            lock.writeLock().unlock();
        }
    }

    //==================================================================================================================
    // Private methods

    /**
     * Throw an exeption if closed
     */
    private void throwIfClosed() {
        if (!fileIsOpen) throw new IllegalStateException("Can not access from a closed file.");
    }

    /**
     * Read all slots and build list of all used slot locations as well as updating "freeSlotsForReuse" stack for any
     * empty slots in middle of data and updating nextFreeSlotAtEnd for the first free slot
     */
    private void loadExistingFile(RandomAccessFile file) throws IOException {
        // start at last slot
        final var fileLength = (int) file.length();
        if (fileLength == 0) {
            return;
        }
        int slotOffset = fileLength - slotSize;
        // keep track of the first real data from end of file
        boolean foundFirstData = false;
        // start assuming the file is full
        nextFreeSlotAtEnd = size;
        // read from end to start
        for (int i = (size - 1); i >= 0; i--) {
            file.seek(slotOffset);
            // read size from file
            long size = file.readLong();
            if (size != EMPTY) {
                // we found some real data
                foundFirstData = true;
            } else if (foundFirstData) {
                // this is a free slot in the middle of data so add to stack
                freeSlotsForReuse.push(i);
            } else {
                // we are still in the block of free slots at the end
                nextFreeSlotAtEnd = slotOffset;
            }
            // move key pointer to next slot
            slotOffset -= slotSize;
        }
    }

}
