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
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

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
    private final AtomicBoolean fileIsOpen = new AtomicBoolean(false);

    /** stack of empty slots not at end of file */
    private final ConcurrentLinkedDeque<Integer> freeSlotsForReuse = new ConcurrentLinkedDeque<>();

    /**
     * Index of first available slot at the end of file.
     * = 0 being first slot is free,
     * = "size" means no free slots are available at the end of the file.
     */
    private final AtomicInteger nextFreeSlotAtEnd = new AtomicInteger();

    /**
     * Create MapDataFile, you need to call open() after to actually open/create file.
     *
     * @param dataSize Data size in bytes
     * @param size     Number of key/values pairs to store in the file
     * @param file     The file we are storing the data in
     */
    public MemMapSlotFile(int dataSize, int size, File file, int fileIndex) throws IOException {
        this.slotSize = dataSize + HEADER_SIZE;
        this.size = size;
        this.fileIndex = fileIndex;
        // calculate total file size in bytes
        int fileSize = this.slotSize * size;
        // check if file existed before
        boolean fileExisted = file.exists();
        // open random access file
        randomAccessFile = new RandomAccessFile(file, "rw");
        if (fileExisted) {
            // check file length
            final var fileLength = (int) file.length();
            if (fileLength != fileSize) throw new IOException("File ["+file.getName()+"] exists but is wrong length. fileLength = "+fileLength+" should be "+fileSize);
            // start assuming file is full
            nextFreeSlotAtEnd.set(size);
            // now read file and find empty slots and correct nextFreeSlotAtEnd
            loadExistingFile(randomAccessFile);
        } else {
            // set size for new empty file
            randomAccessFile.setLength(fileSize);
            // mark first slot as free
            nextFreeSlotAtEnd.set(0);
        }
        // get file channel and memory map the file
        fileChannel = randomAccessFile.getChannel();
        mappedBuffer = fileChannel.map(FileChannel.MapMode.READ_WRITE, 0, fileSize);
        // mark file as open
        fileIsOpen.set(true);
    }

    //==================================================================================================================
    // Public API methods

    /**
     * Acquire a write lock for the given slot index
     *
     * @param slotIndex the slot index we want to be able to write to
     * @return stamp representing the lock that needs to be returned to releaseWriteLock
     */
    @SuppressWarnings("unused") // can be used in future for locking on region of file
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
    @SuppressWarnings("unused") // can be used in future for locking on region of file
    public void releaseWriteLock(int slotIndex, Object lockStamp) {
        if (lockStamp == lock.writeLock()) lock.writeLock().unlock();
    }

    /**
     * Acquire a read lock for the given slot index
     *
     * @param slotIndex the slot index we want to be able to read to
     * @return stamp representing the lock that needs to be returned to releaseReadLock
     */
    @SuppressWarnings("unused") // can be used in future for locking on region of file
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
    @SuppressWarnings("unused") // can be used in future for locking on region of file
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
     * Closes the file and clears up all resources
     *
     * @throws IOException If there was a problem closing the file
     */
    public void close() throws IOException {
        lock.writeLock().lock();
        try {
            if (fileIsOpen.getAndSet(false)) {
                mappedBuffer.force();
                mappedBuffer = null;
                fileChannel.close();
                fileChannel = null;
                randomAccessFile.close();
                randomAccessFile = null;
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
        lock.writeLock().lock(); // <--- TODO Not sure we need to lock here. At least, we should be locking on bin??
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
        lock.readLock().lock(); // <--- TODO Not sure we need to lock here. At least, we should be locking on bin??
        try {
            return reader.read(inputStream);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Finds a new slot in this file, if there is one available
     *
     * This trys to be concurrent and if the file is full
     *
     * @return Slot index if the value was successfully stored, -1 if there was no space available
     */
    public int getNewSlot() {
        throwIfClosed();
        // grab free slot at the end
        int nextFreeSlot = nextFreeSlotAtEnd.getAndIncrement();
        // check if we ran out of free slots
        if (nextFreeSlot >= size) {
            // there are no free slots at end so try and grab one from queue
            Integer freeSlot = freeSlotsForReuse.poll();
            // check if we got one
            if (freeSlot == null) {
                // there are no free slots so this file is full
                return -1;
            }
            // we have a free slot to use
            nextFreeSlot = freeSlot;
        }
        // mark slot as used, TODO it is possible locking is not needed here as no other thread can be writing or reading this part of file.
        lock.writeLock().lock();
        try {
            mappedBuffer.put(nextFreeSlot * slotSize, USED);
        } finally {
            lock.writeLock().unlock();
        }
        // return the new slot
        return nextFreeSlot;
    }

    /**
     * Delete a slot, marking it as empty
     *
     * @param slotIndex The index of the slot to delete
     */
    public void deleteSlot(int slotIndex) {
        throwIfClosed();
        // mark slot as EMPTY
        lock.writeLock().lock(); // <---- TODO not sure this is needed if concurrent writes works
        try {
            mappedBuffer.put(slotIndex, EMPTY);
        } finally {
            lock.writeLock().unlock();
        }
        // add slot to queue of empty slots
        freeSlotsForReuse.add(slotIndex); // <--- TODO Might be wrong, what if multiple threads call deleteSlot with the same slotIndex?
    }

    //==================================================================================================================
    // Private methods

    /**
     * Throw an exception if closed
     */
    private void throwIfClosed() {
        if (!fileIsOpen.get()) throw new IllegalStateException("Can not access from a closed file.");
    }

    /**
     * Read all slots and build list of all used slot locations as well as updating "freeSlotsForReuse" stack for any
     * empty slots in middle of data and updating nextFreeSlotAtEnd for the first free slot
     */
    private void loadExistingFile(RandomAccessFile file) throws IOException {
        // keep track of the first real data from end of file
        boolean foundFirstData = false;
        // read from end to start
        for (int slotIndex = (size-1); slotIndex >= 0; slotIndex --) {
            int slotOffset = slotIndex * slotSize;
            file.seek(slotOffset);
            // read size from file
            long header = file.readLong();
            if (header != EMPTY) {
                if (!foundFirstData) {
                    // we found some real data
                    foundFirstData = true;
                    // we know the last slot we visited was last free one at the end
                    nextFreeSlotAtEnd.set(slotIndex+1);
                } else {
                    // we found a free slot in the middle so add to queue
                    freeSlotsForReuse.push(slotIndex);
                }
            }
        }
    }
}
