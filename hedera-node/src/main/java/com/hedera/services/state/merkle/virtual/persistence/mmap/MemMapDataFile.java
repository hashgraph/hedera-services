package com.hedera.services.state.merkle.virtual.persistence.mmap;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Memory Mapped File key value store, with a maximum number of items it can store and a max size for any item.
 *
 * The data file one continuous buffer of slots. Each slot is a short for value length, followed by the key byte array
 * then the value byte array.
 *
 * -------------------------------------------------
 * | Short(value length) | Key bytes | Value bytes | .... repeat
 * -------------------------------------------------
 */
class MemMapDataFile implements Closeable {
    /** constant used for slot size to mean that slot is empty */
    private static final short EMPTY = -1;
    /** Key size in bytes */
    private final int keySize;
    /** Number of key/values pairs to store in the file */
    private final int size;
    /** The file we are storing the data in */
    private final File file;
    /** The number of bytes each slot takes up */
    private final int slotSize;
    /** total file size in bytes */
    private final int fileSize;

    private RandomAccessFile randomAccessFile;
    private FileChannel fileChannel;
    private MappedByteBuffer mappedBuffer;
    /** Current state if the file is open or not */
    private boolean fileIsOpen = false;

    /** stack of empty slots not at end of file */
    private final Deque<Integer> freeSlotsForReuse = new ArrayDeque<>(100);
    /**
     * Index of first available slot at the end of file.
     *  = 0 being first slot is free,
     *  = "size" means no free slots are available at the end of the file.
     */
    private int nextFreeSlotAtEnd;

    /**
     * Create MapDataFile,  you need to call open() after to actually open/create file.
     *
     * @param keySize Key size in bytes
     * @param valueSize Value size in bytes, has to be less than Short.MAX_VALUE
     * @param size Number of key/values pairs to store in the file
     * @param file The file we are storing the data in
     */
    public MemMapDataFile(int keySize, int valueSize, int size, File file) {
        if (valueSize > Short.MAX_VALUE) {
            throw new IllegalArgumentException("You can not store data values bigger than Short.MAX_VALUE");
        }

        this.keySize = keySize;
        this.size = size;
        this.file = file;
        // calculate slot size
        slotSize = 2 + keySize + valueSize;
        // calculate file size in bytes
        fileSize = slotSize * size;
    }

    /**
     * Read the slot map from data store. This is expensive as it requires
     *
     * @return List of all slot locations in this file
     * @throws IOException If there was a problem opening the file
     */
    public List<SlotKeyLocation> open() throws IOException {
        List<SlotKeyLocation> slotKeyLocations = null;
        if (!fileIsOpen) {
            // check if file existed before
            boolean fileExisted = file.exists();
            // open random access file
            randomAccessFile = new RandomAccessFile(file, "rw");
            if (fileExisted) {
                slotKeyLocations = loadExistingFile(randomAccessFile);
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
        return slotKeyLocations;
    }

    /**
     * Read all slots and build list of all used slot locations as well as updating "freeSlotsForReuse" stack for any
     * empty slots in middle of data and updating nextFreeSlotAtEnd for the first free slot
     *
     * @return index of key to slot index
     */
    private List<SlotKeyLocation> loadExistingFile(RandomAccessFile file) throws IOException {
        List<SlotKeyLocation> slotKeyLocations = new ArrayList<>(size);
        // start at last slot
        int slotOffset = fileSize - slotSize;
        // keep track of the first real data from end of file
        boolean foundFirstData = false;
        // start assuming the file is full
        nextFreeSlotAtEnd = size;
        // read from end to start
        for (int i = (size-1); i >= 0; i--) {
            file.seek(slotOffset);
            // read size from file
            short size = file.readShort();
            if (size != EMPTY) {
                // we found some real data
                foundFirstData = true;
                // read key from file
                byte[] keyBytes = new byte[keySize];
                file.read(keyBytes);
                // store key in index map
                slotKeyLocations.add(new SlotKeyLocation(keyBytes, i));
            } else if(foundFirstData) {
                // this is a free slot in the middle of data so add to stack
                freeSlotsForReuse.push(i);
            } else {
                // we are still in the block of free slots at the end
                nextFreeSlotAtEnd = slotOffset;
            }
            // move key pointer to next slot
            slotOffset -= slotSize;
        }
        return slotKeyLocations;
    }

    /**
     * Check if the file is full, with no available slots
     *
     * @return True if the file is full
     */
    public boolean isFileFull() {
        return nextFreeSlotAtEnd == size && freeSlotsForReuse.isEmpty();
    }

    /**
     * Closes the file and clears up all resources
     *
     * @throws IOException If there was a problem closing the file
     */
    public void close() throws IOException {
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
    }

    /**
     * Get a ByteBuffer for the data at a given slot index
     *
     * @param slotIndex the slot index of the data to get
     * @return light weight ByteBuffer direct to data
     */
    public ByteBuffer get(int slotIndex){
        if (!fileIsOpen) throw new IllegalStateException("Can not access from a closed file.");
        // calculate the offset position of the value in the file
        final int slotOffset = slotSize * slotIndex;
        // get the size of value from slot
        short slotSize = mappedBuffer.getShort(slotOffset);
        // read the value from the file
        // Sadly, this excellent API is only available in JDK 13+ :-(
//        return mappedBuffer.slice(slotOffset + 2 + keySize, slotSize);

        // I hope this works...
        mappedBuffer.mark();
        mappedBuffer.position(slotOffset + 2 + keySize);
        final var buf = mappedBuffer.slice();
        buf.limit(slotSize);
        mappedBuffer.reset();
        return buf;
    }

    /**
     * Stores a new value for key or updates if the key already has a value.
     *
     * @param key bytearray value with length less than $keySize
     * @param value bytearray value with length less than $valueSize
     * @return Slot index if the value was successfully stored, -1 if there was no space available
     */
    public int add(Key key, byte[] value){
        if (!fileIsOpen) throw new IllegalStateException("Can not access from a closed file.");
        if (isFileFull()) return -1;
        // calc next available slot
        int slotIndex;
        if (nextFreeSlotAtEnd < size) {
            slotIndex = nextFreeSlotAtEnd;
            nextFreeSlotAtEnd = nextFreeSlotAtEnd + 1;
        } else {
            slotIndex = freeSlotsForReuse.pop();
        }
        update(slotIndex, key, value);
        return slotIndex;
    }

    /**
     * Updates an existing value for key or updates if the key already has a value.
     *
     * @param slotIndex the index of the slot to update
     * @param key bytearray value with length less than $keySize
     * @param value bytearray value with length less than $valueSize
     */
    public void update(int slotIndex, Key key, byte[] value){
        if (!fileIsOpen) throw new IllegalStateException("Can not access from a closed file.");
        // move to slot
        mappedBuffer.position(slotIndex*slotSize);
        // store the size in slot
        mappedBuffer.putShort((short)value.length);
        // store the key in slot
        mappedBuffer.put(key.getBytes());
        // store the value in slot
        mappedBuffer.put(value);
    }

    /**
     * Delete a slot
     *
     * @param slotIndex The index of the slot to delete
     */
    public void delete(int slotIndex){
        if (!fileIsOpen) throw new IllegalStateException("Can not access from a closed file.");
        // move to slot
        mappedBuffer.position(slotIndex);
        // store EMPTY for the size in slot
        mappedBuffer.putShort(EMPTY);
        // add slot to stack of empty slots
        freeSlotsForReuse.push(slotIndex);
    }

    /** Called to force sync the data store to disk */
    public void sync(){
        mappedBuffer.force();
    }
}
