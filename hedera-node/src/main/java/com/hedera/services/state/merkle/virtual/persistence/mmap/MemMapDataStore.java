package com.hedera.services.state.merkle.virtual.persistence.mmap;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A data store backed by a series of files in a directory. Each of those files is made up of slots.
 * Each slot can store {@code dataSize} bytes worth of data.
 */
public final class MemMapDataStore {
    /** Special Location for when not found */
    public static final long NOT_FOUND_LOCATION = Long.MAX_VALUE;
    /**
     * A slot is made up of a header followed by dataSize bytes.
     *
     * <p>Must be a positive number. The value must be less than Integer.MAX_VALUE - HEADER_SIZE,
     * and must also be less than the fileSize - HEADER_SIZE. Such a dataSize would
     * represent a single slot taking up the entire file!</p>
     */
    private final int dataSize;

    /** The path of the directory to store storage files. This is never null. */
    private final Path storageDirectory;
    /** The prefix for each storage file. Will never be null, but can be the empty string. */
    private final String filePrefix;
    /** The extension for each storage file, for example "dat". Will never be null, but can be the empty string. */
    private final String fileExtension;
    /** Number of key/values pairs to store in the file. This is a computed value. */
    private final int size;
    /** Tracks whether this file is open or not */
    private boolean open = false;

    /**
     * List of all of our storage files. This is lazily created so we can default to the right array size.
     * Be careful to avoid an NPE.
     * */
    private List<MemMapDataFile> files;

    /**
     * The current file to use for writes, if it is null we will check existing files for space then create a
     * new file if needed.
     */
    private MemMapDataFile currentFileForWriting = null;

    /**
     * Create new MemMapStorage with defaults:
     *  - key size of 32
     *  - value size of 1024
     *  - file size of 512Mb
     *  - location of "{working_dir}/data/jp_db"
     */
    public MemMapDataStore() {
        this(1024,512, Path.of("data/jp_db"),"store_", "dat");
    }

    /**
     * Create new MemMapStorage
     *
     * @param dataSize Slot data size in bytes
     * @param fileSize The size of each storage file in bytes
     * @param storageDirectory The path of the directory to store storage files
     * @param filePrefix The prefix for each storage file
     * @param fileExtension The extension for each storage file, for example "dat"
     */
    public MemMapDataStore(int dataSize, int fileSize, Path storageDirectory, String filePrefix, String fileExtension) {
        if (fileSize <= 0) {
            throw new IllegalArgumentException("fileSize must be strictly positive");
        }

        if (dataSize <= 0) {
            throw new IllegalArgumentException("dataSize must be strictly positive");
        }

        // TODO I feel like we should dramatically limit the dataSize so that it cannot be larger than something like
        // 10K or something. If we allow it to be too big, we get some weird edge cases. Thoughts?
        if (fileSize < dataSize + MemMapDataFile.HEADER_SIZE) {
            throw new IllegalArgumentException("fileSize must be larger than dataSize plus " + MemMapDataFile.HEADER_SIZE);
        }

        this.dataSize = dataSize;
        this.storageDirectory = Objects.requireNonNull(storageDirectory);
        this.filePrefix = filePrefix == null ? "" : filePrefix;
        this.fileExtension = fileExtension == null ? "" : fileExtension;
        this.size = fileSize / (dataSize + MemMapDataFile.HEADER_SIZE);
    }

    /**
     * Get the specified data size in bytes.
     *
     * @return data size in bytes
     */
    public int getDataSize() {
        return dataSize;
    }

    /**
     * Opens all the files in this store. While opening it visits every used slot and calls slotVisitor.
     *
     * @param slotVisitor Visitor that gets to visit every used slot. May be null.
     */
    public void open(SlotVisitor slotVisitor) {
        if (open) {
            throw new IllegalStateException("Cannot open an already open store");
        }

        try {
            if (!Files.exists(storageDirectory)) {
                // create directory
                Files.createDirectories(storageDirectory);
                // create empty list for files
                files = new ArrayList<>();
            } else {
                // find all storage files in directory with prefix
                List<Path> filePaths = Files.list(storageDirectory)
                        .filter(path -> {
                            final String fileName = path.getFileName().toString();
                            return fileName.startsWith(filePrefix) && fileName.endsWith(fileExtension);
                        })
                        .sorted(Comparator.comparingInt(this::indexForFile))
                        .collect(Collectors.toList());
                // open files for each path
                files = new ArrayList<>(filePaths.size());
                for (int i = 0; i < filePaths.size(); i++) {
                    files.add(new MemMapDataFile(dataSize, size, filePaths.get(i).toFile(), i));
                }
                // set the first file as the current one for writing
                if (!files.isEmpty()) currentFileForWriting = files.get(0);
                // open all the files
                for (MemMapDataFile file : files) {
                    file.open(slotVisitor);
                }
            }

            // Change state to open
            open = true;
        } catch (IOException e) {
            throw new IllegalStateException("Error opening storage directory", e);
        }
    }

    /**
     * Flush all data to disk and close all files
     */
    public void close() {
        if (open) {
            // Note: files is never null when we are in the open state.
            for (MemMapDataFile file : files) {
                try {
                    file.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        open = false;
        files = null;
    }

    /**
     * Get direct access to the slot in the base storage
     *
     * @param location the file and slot location of the data to get
     * @return the real ByteBuffer containing the data positioned and marked at the data location
     */
    public ByteBuffer accessSlot(long location) {
        // Technically, we should throw if !open, but because this is a super hot fast path,
        // we're going to play fast and loose and let an NPE get thrown in that case instead.
        // Either way, it is a runtime exception.
        return files.get(fileIndexFromLocation(location)).accessSlot(slotIndexFromLocation(location));
    }

    /**
     * Finds a new slot in this file
     *
     * @return File and slot location for a new location to store into
     */
    public long getNewSlot() {
        throwIfNotOpen(); // We will end up throwing with an NPE while accessing "files" below anyway.

        // search for a file to write to if currentFileForWriting is missing or full
        if (currentFileForWriting == null || (currentFileForWriting.isFileFull())) {
            // current file is full or there is no current file
            // start by scanning existing files for free space
            currentFileForWriting = null;
            for (MemMapDataFile file : files) {
                if (!file.isFileFull()) {
                    currentFileForWriting = file;
                    break;
                }
            }
            // all current files are full so create a new file
            if (currentFileForWriting == null) {
                // open new file
                final var newIndex = files.size();
                currentFileForWriting = new MemMapDataFile(dataSize, size, fileForIndex(newIndex).toFile(), newIndex);
                files.add(currentFileForWriting);
                try {
                    currentFileForWriting.open(null);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        // we now have a file for writing, get a new slot
        return locationFromParts(currentFileForWriting.fileIndex, currentFileForWriting.getNewSlot());
    }

    /**
     * Delete a slot, marking it as empty
     *
     * @param location the file and slot location to delete
     */
    public void deleteSlot(long location) {
        throwIfNotOpen();
        files.get(fileIndexFromLocation(location)).deleteSlot(slotIndexFromLocation(location));
    }

    /**
     * Make sure all data is flushed to disk. This is an expensive operation. The OS will write all data to disk in the
     * background, so only call this if you need to insure it is written synchronously.
     */
    public void sync() {
        throwIfNotOpen();
        for (MemMapDataFile dataFile : files) {
            dataFile.sync();
        }
    }

    /**
     * Little helper method that throws an ISE if the class isn't open
     */
    private void throwIfNotOpen() {
        if (!open) {
            throw new IllegalStateException("MemMapDataStore is not open");
        }
    }

    /**
     * Get the index for file, given the file path
     *
     * @param file Path to file
     * @return integer index for file
     */
    private int indexForFile(Path file) {
        String fileName = file.getFileName().toString();
        String strIndex = fileName.substring(filePrefix.length(), fileName.length() - (fileExtension.length()+1));
        return Integer.parseInt(strIndex);
    }

    /**
     * Get the Path for a file with given index
     *
     * @param index integer index for file, starts at 0
     * @return Path to file
     */
    private Path fileForIndex(int index) {
        return storageDirectory.resolve(filePrefix + index + "." + fileExtension);
    }

    /**
     * Util method to create a long location out of the two parts
     *
     * @param file The file location
     * @param slot The slot location
     * @return long containg file and slot location
     */
    public static long locationFromParts(int file, int slot) {
        return (long)file << 32 | slot & 0xFFFFFFFFL;
    }

    /**
     * Get the file part out of a location long
     *
     * @param location long location containing file and slot locations
     * @return file part of location
     */
    public static int fileIndexFromLocation(long location) {
        return (int)(location >> 32);
    }

    /**
     * Get the slot part out of a location long
     *
     * @param location long location containing file and slot locations
     * @return slot part of location
     */
    public static int slotIndexFromLocation(long location) {
        return(int)location;
    }

    /**
     * Memory Mapped File key value store, with a maximum number of items it can store and a max size for any item.
     *
     * The data file one continuous buffer of slots. Each slot is a short for value length, followed by the key byte array
     * then the value byte array.
     *
     * -------------------------------------------------
     * | Byte header | Data bytes | .... repeat
     * -------------------------------------------------
     */
    static class MemMapDataFile implements Closeable {
        /** constant used for slot header to mean that slot is empty */
        private static final byte EMPTY = 0;
        /** constant used for slot header to mean that slot is used */
        private static final byte USED = 1;
        /** constant used for size of slot header in bytes */
        static final int HEADER_SIZE = 8;
        /** Number of key/values pairs to store in the file */
        private final int size;
        /** The file we are storing the data in */
        private final File file;
        /** The number of bytes each slot takes up */
        private final int slotSize;
        /** total file size in bytes */
        private final int fileSize;
        /** index for this file */
        private final int fileIndex;

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
         * Create MapDataFile, you need to call open() after to actually open/create file.
         *
         * @param dataSize Data size in bytes
         * @param size Number of key/values pairs to store in the file
         * @param file The file we are storing the data in
         */
        public MemMapDataFile(int dataSize, int size, File file, int fileIndex) {
            this.size = size;
            this.file = file;
            this.fileIndex = fileIndex;
            this.slotSize = dataSize + HEADER_SIZE;
            // calculate file size in bytes
            fileSize = this.slotSize * size;
        }

        /**
         * Open the data file as memory mapped file. Visiting each slot via random access file before memory mapping.
         *
         * @param slotVisitor Visitor that gets to visit every used slot, can be null if we know this is new file
         * @throws IOException If there was a problem opening the file
         */
        public void open(SlotVisitor slotVisitor) throws IOException {
            if (!fileIsOpen) {
                // check if file existed before
                boolean fileExisted = file.exists();
                // open random access file
                randomAccessFile = new RandomAccessFile(file, "rw");
                if (fileExisted) {
                    // TODO It seems we have a problem if the file was created at the wrong
                    // size before. I had a file that had size 0 and couldn't recover.
                    loadExistingFile(randomAccessFile, slotVisitor);
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
        }

        /**
         * Read all slots and build list of all used slot locations as well as updating "freeSlotsForReuse" stack for any
         * empty slots in middle of data and updating nextFreeSlotAtEnd for the first free slot
         *
         * @param slotVisitor Visitor that gets to visit every used slot
         */
        private void loadExistingFile(RandomAccessFile file, SlotVisitor slotVisitor) throws IOException {
            // start at last slot
            final var fileLength = (int)file.length();
            if (fileLength == 0) {
                return;
            }

            int slotOffset = fileLength - slotSize;
            // keep track of the first real data from end of file
            boolean foundFirstData = false;
            // start assuming the file is full
            nextFreeSlotAtEnd = size;
            // read from end to start
            for (int i = (size-1); i >= 0; i--) {
                file.seek(slotOffset);
                // read size from file
                short size = file.readByte();
                if (size != EMPTY) {
                    // we found some real data
                    foundFirstData = true;
                    // read key from file
                    if (slotVisitor != null) {
                        slotVisitor.visitSlot(locationFromParts(fileIndex, i), file);
                    }
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
         * Get direct access to the slot in the base storage
         *
         * @param slotIndex the slot index of the data to get
         * @return the real ByteBuffer containing the data positioned and marked at the data location
         */
        public ByteBuffer accessSlot(int slotIndex){
            if (!fileIsOpen) throw new IllegalStateException("Can not access from a closed file.");
            // calculate the offset position of the value in the file
            final int slotOffset = slotSize * slotIndex;
            // position and mark buffer
            mappedBuffer.position(slotOffset + HEADER_SIZE);
            mappedBuffer.mark();
            return mappedBuffer;
        }

        /**
         * Finds a new slot in this file
         *
         * @return Slot index if the value was successfully stored, -1 if there was no space available
         */
        public int getNewSlot(){
            if (!fileIsOpen) throw new IllegalStateException("Can not access from a closed file.");
            if (isFileFull()) return -1;
            // calc next available slot
            int slotIndex;
            if (nextFreeSlotAtEnd < size) {
                slotIndex = nextFreeSlotAtEnd;
                nextFreeSlotAtEnd++;
            } else {
                slotIndex = freeSlotsForReuse.pop();
            }
            // mark slot as used
            mappedBuffer.put(slotIndex * slotSize, USED);
            // return slot index
            return slotIndex;
        }


        /**
         * Delete a slot, marking it as empty
         *
         * @param slotIndex The index of the slot to delete
         */
        public void deleteSlot(int slotIndex){
            if (!fileIsOpen) throw new IllegalStateException("Can not access from a closed file.");
            // store EMPTY for the size in slot
            mappedBuffer.put(slotIndex, EMPTY);
            // add slot to stack of empty slots
            freeSlotsForReuse.push(slotIndex); // TODO could maybe check if it as the end and add slots back on nextFreeSlotAtEnd
        }

        /**
         * Make sure all data is flushed to disk. This is an expensive operation. The OS will write all data to disk in the
         * background, so only call this if you need to insure it is written synchronously.
         */
        public void sync(){
            mappedBuffer.force();
        }
    }
}
