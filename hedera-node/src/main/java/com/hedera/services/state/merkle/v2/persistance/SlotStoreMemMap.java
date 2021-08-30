package com.hedera.services.state.merkle.v2.persistance;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A data store backed by a series of files in a directory. Each of those files is made up of slots.
 * Each slot can store {@code dataSize} bytes worth of data.
 */
public final class SlotStoreMemMap extends SlotStore {
    /** Number of key/values pairs to store in the file. This is a computed value. */
    private final int slotsPerFile;
    /** The path of the directory to store storage files. This is never null. */
    private final Path storageDirectory;
    /** The prefix for each storage file. Will never be null, but can be the empty string. */
    private final String filePrefix;
    /** The extension for each storage file, for example "dat". Will never be null, but can be the empty string. */
    private final String fileExtension;
    /** Tracks whether this file is open or not */
    private final AtomicBoolean open;

    /**
     * List of all of our storage files. This is lazily created so we can default to the right array size.
     * Be careful to avoid an NPE.
     * */
    private final CopyOnWriteArrayList<MemMapSlotFile> files = new CopyOnWriteArrayList<>();

    /**
     * The current file to use for writes, if it is null we will check existing files for space then create a
     * new file if needed.
     */
    private final AtomicReference<MemMapSlotFile> currentFileForWriting = new AtomicReference<>();

    /**
     * Open all the files in this store. While opening it visits every used slot and calls slotVisitor.
     *
     * @param requirePreAllocation if true getNewSlot() is only way to find a new safe slot to write to.
     * @param slotSizeBytes Slot data size in bytes
     * @param fileSize The size of each storage file in bytes
     * @param storageDirectory The path of the directory to store storage files
     * @param filePrefix The prefix for each storage file
     * @param fileExtension The extension for each storage file, for example "dat"
     * @param reuseIfExists when true we try and reuse existing files.
     */
    public SlotStoreMemMap(boolean requirePreAllocation, int slotSizeBytes, int fileSize, Path storageDirectory,
                           String filePrefix, String fileExtension, boolean reuseIfExists)
            throws IOException {
        super(requirePreAllocation, slotSizeBytes);
        if (fileSize <= 0) throw new IOException("fileSize must be strictly positive");
        if (slotSizeBytes <= 0) throw new IOException("slotSizeBytes must be strictly positive");
        // 10K or something. If we allow it to be too big, we get some weird edge cases. Thoughts?
        if (fileSize < slotSizeBytes) throw new IOException("fileSize must be larger than slotSizeBytes");
        if (storageDirectory == null) throw new IllegalArgumentException("storageDirectory can not be null");
        if (filePrefix == null) throw new IllegalArgumentException("filePrefix can not be null");
        if (fileExtension == null) throw new IllegalArgumentException("fileExtension can not be null");
        this.slotsPerFile = fileSize / slotSizeBytes;
        this.storageDirectory = Objects.requireNonNull(storageDirectory);
        this.filePrefix = filePrefix;
        this.fileExtension = fileExtension;
        // create directory if needed
        if (!Files.exists(storageDirectory)) {
            // create directory
            Files.createDirectories(storageDirectory);
        } else if(reuseIfExists) {
            // open all existing files, we assume they are all full so we don't have to store information on
            // free space in files
            Files.list(storageDirectory)
                    .filter(filePath -> filePath.getFileName().toString().startsWith(filePrefix))
                    .mapToInt(this::fileIndexForPath)
                    .sorted()
                    .forEach(fileIndex -> {
                        try {
                            files.add(new MemMapSlotFile(slotSizeBytes, slotsPerFile, fileForIndex(fileIndex), fileIndex));
                        } catch (IOException e) {
                            e.printStackTrace(); // TODO something better maybe
                        }
                    });
        }
        // Change state to open
        open = new AtomicBoolean(true);
    }

    //==================================================================================================================
    // Public API methods

    /**
     * Flush all data to disk and close all files only if there are no references to this data store
     */
    @Override
    public synchronized void close() {
        if (open.get()) {
            // Note: files is never null when we are in the open state.
            for (MemMapSlotFile file : files) {
                try {
                    file.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            open.set(false);
            files.clear();
        }
    }

    /**
     * Access a slot for reading or writing.
     *
     * @param location the slot location, can be a existing slot or a new slot that we need to make available
     * @param create   if true then a new slot will be created if it did not already exist.
     * @return a bytebuffer backed directly to storage, with position 0 being beginning of slot and being rewind-ed or
     * null if create=false and location did not exist
     */
    @Override
    public ByteBuffer accessSlot(long location, boolean create) throws IOException {
        int fileIndex = fileIndexFromLocation(location);
        if (fileIndex >= files.size()) {
            // file doesn't exist yet
            if (create) {
                // create new files till we have enough
                synchronized (this) {
                    for (int newFileIndex = files.size(); newFileIndex <= fileIndex; newFileIndex++) {
                        files.add(new MemMapSlotFile(slotSizeBytes, slotsPerFile, fileForIndex(newFileIndex), newFileIndex));
                    }
                }
            } else {
                return null;
            }
        }
        MemMapSlotFile file = files.get(fileIndex);
        return file.accessSlot(slotIndexFromLocation(location));
    }

    /**
     * Write whole slot
     *
     * @param location the slot location, can be a existing slot or a new slot that we need to make available
     * @param create   if true then a new slot will be created if it did not already exist.
     * @param slotData the data to write to the slot
     */
    @Override
    public void writeSlot(long location, boolean create, byte[] slotData) throws IOException {
        int fileIndex = fileIndexFromLocation(location);
        if (fileIndex >= files.size()) {
            // file doesn't exist yet
            if (create) {
                // create new files till we have enough
                synchronized (this) {
                    for (int newFileIndex = files.size(); newFileIndex <= fileIndex; newFileIndex++) {
                        files.add(new MemMapSlotFile(slotSizeBytes, slotsPerFile, fileForIndex(newFileIndex), newFileIndex));
                    }
                }
            } else {
                throw new IOException("Tried to write to non-existent slot ["+location+"]");
            }
        }
        MemMapSlotFile file = files.get(fileIndex);
        file.writeSlot(slotIndexFromLocation(location), slotData);
    }

    /**
     * Finds a new slot ready for use
     *
     * @return Index for new slot ready for use
     */
    @Override
    public long getNewSlot() {
        // first try getting a slot form the currentFileForWriting
        var slot = getNewSlotIfAvailable();
        if (slot != -1) return slot;
        // doh the currentFileForWriting is full, so lets search existing files for free space
        for (MemMapSlotFile oldFile : files) {
            int slotIndex = oldFile.getNewSlot();
            if (slotIndex != -1) {
                // great we got one lets update currentFileForWriting for future callers
                currentFileForWriting.set(oldFile);
                // lets return it
                return locationFromParts(oldFile.getFileIndex(), slotIndex);
            }
        }
        // we we got here, so there is no room in any file, we have to create a new file
        synchronized (this) { // we only want one thread to be creating a file at a time
            // It is possible that multiple threads all found that there was no slot available
            // and are piled up at this synchronization point. The first one created a new file,
            // and the rest are now coming into this critical section. So we should check whether
            // there are any free slots again, before proceeding to create *another* new file.
            // Note that I don't bother checking for old free slots again. If a new file was created,
            // there will be no need, and if a new file wasn't created but an old slot is available,
            // we're so close to needing a new file we might as well create one anyway.
            slot = getNewSlotIfAvailable();
            if (slot != -1) return slot;
            // get index for new file
            final var newIndex = files.size();
            // create new file
            MemMapSlotFile newFile;
            try {
                newFile = new MemMapSlotFile(slotSizeBytes, slotsPerFile, fileForIndex(newIndex), newIndex);
                // add to files
                files.add(newFile);
                // set it as the currentFileForWriting
                currentFileForWriting.set(newFile);
                // grab and return new slot
                return locationFromParts(newFile.getFileIndex(), newFile.getNewSlot());
            } catch (IOException e) { // TODO better handling of fail to create new file
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }

    private long getNewSlotIfAvailable() {
        // first try getting a slot form the currentFileForWriting
        MemMapSlotFile file = currentFileForWriting.get();
        if (file != null) {
            // we have a currentFileForWriting, see if it has a free slot
            int slotIndex = file.getNewSlot();
            if (slotIndex != -1) {
                // great we got one, lets return it
                return locationFromParts(file.getFileIndex(), slotIndex);
            }
        }
        return -1;
    }

    /**
     * Delete a slot, marking it as empty
     *
     * @param location the file and slot location to delete
     */
    @Override
    public void deleteSlot(long location) throws IOException {
        int fileIndex = fileIndexFromLocation(location);
        if (fileIndex < files.size()) {
            files.get(fileIndex).deleteSlot(slotIndexFromLocation(location));
        }
    }

    //==================================================================================================================
    // Private methods

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
     * Get the file index from the file path
     *
     * @param filePath the path to slot store file
     * @return the index of the file from the file name
     */
    private int fileIndexForPath(Path filePath) {
        final String fileNameStr = filePath.getFileName().toString();
        final String fileIndexStr = fileNameStr.substring(filePrefix.length(),fileNameStr.length()-fileExtension.length()-1);
        return Integer.parseInt(fileIndexStr);
    }

    /**
     * Util method to create a long location out of the two parts
     *
     * @param file The file location
     * @param slot The slot location
     * @return long containing file and slot location
     */
    private long locationFromParts(int file, int slot) {
        return ((long)file * (long)slotsPerFile) + (long)slot;
    }

    /**
     * Get the file part out of a location long
     *
     * @param location long location containing file and slot locations
     * @return file part of location
     */
    private int fileIndexFromLocation(long location) {
        return (int)(location/slotsPerFile);
    }

    /**
     * Get the slot part out of a location long
     *
     * @param location long location containing file and slot locations
     * @return slot part of location
     */
    private int slotIndexFromLocation(long location) {
        return (int)(location % slotsPerFile);
    }

    /**
     * Memory Mapped File key value store, with a maximum number of items it can store and a max size for any item.
     * <p>
     * The data file one continuous buffer of slots.
     * <p>
     * -------------------------------------------------
     * | Data bytes | .... repeat
     * -------------------------------------------------
     */
    static class MemMapSlotFile implements Closeable {
        /**
         * Number of key/values pairs to store in the file
         */
        private final int size;
        /**
         * The number of bytes each slot takes up
         */
        private final int slotSize;
        /**
         * index for this file
         */
        private final int fileIndex;

        private FileChannel fileChannel;
        private MappedByteBuffer mappedBuffer;

        /**
         * Current state if the file is open or not
         */
        private final AtomicBoolean fileIsOpen = new AtomicBoolean(false);

        /**
         * stack of empty slots not at end of file
         */
        private final ConcurrentLinkedDeque<Integer> freeSlotsForReuse = new ConcurrentLinkedDeque<>();

        /**
         * Index of first available slot at the end of file.
         * = 0 being first slot is free,
         * = "size" means no free slots are available at the end of the file.
         */
        private final AtomicInteger nextFreeSlotAtEnd = new AtomicInteger(0);

        /**
         * Create MapDataFile, with a new file
         *
         * @param dataSize Data size in bytes
         * @param size     Number of key/values pairs to store in the file
         * @param file     The file we are storing the data in
         */
        public MemMapSlotFile(int dataSize, int size, Path file, int fileIndex) throws IOException {
            this.slotSize = dataSize;
            this.size = size;
            this.fileIndex = fileIndex;
            // calculate total file size in bytes
            int fileSize = this.slotSize * size;
            // check file doesn't exist and create if needed
            if (!Files.exists(file)) {
                // create file
                PersistenceUtils.createFile(file, fileSize);
            } else {
                // mark whole file as full
                nextFreeSlotAtEnd.set(size);
            }
            // open file
            fileChannel = FileChannel.open(file,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.READ
            );
            // get file channel and memory map the file
            mappedBuffer = fileChannel.map(FileChannel.MapMode.READ_WRITE, 0, fileSize);
            // mark file as open
            fileIsOpen.set(true);
        }


        //==================================================================================================================
        // Public API methods

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
            if (fileIsOpen.getAndSet(false)) {
                mappedBuffer.force();
                mappedBuffer = null;
                fileChannel.close();
                fileChannel = null;
            }
        }

        /**
         * Access a slot for reading or writing.
         *
         * @param slotIndex the slot location, can be a existing slot or a new slot that we need to make available
         * @return a bytebuffer backed directly to storage, with position 0 being beginning of slot and being rewind-ed or
         * null if create=false and location did not exist
         */
        public ByteBuffer accessSlot(int slotIndex) throws IOException {
            if (slotIndex > size)
                throw new IOException("Tried to write to slot index [" + slotIndex + "] which is greater than file size [" + size + "].");
            if (slotIndex < 0)
                throw new IOException("Tried to write to slot index [" + slotIndex + "] which is less than zero.");
            ByteBuffer subBuffer = mappedBuffer.slice();
            subBuffer.position(slotIndex * slotSize);
            subBuffer.limit((slotIndex * slotSize) + slotSize);
            return subBuffer;
        }


        /**
         * Access a slot for reading or writing.
         *
         * @param slotIndex the slot location, can be a existing slot or a new slot that we need to make available
         * @param slotData the data to write to the slot
         */
        public void writeSlot(int slotIndex, byte[] slotData) throws IOException {
            if (slotIndex > size)
                throw new IOException("Tried to write to slot index [" + slotIndex + "] which is greater than file size [" + size + "].");
            if (slotIndex < 0)
                throw new IOException("Tried to write to slot index [" + slotIndex + "] which is less than zero.");
            mappedBuffer.position(slotIndex * slotSize);
            mappedBuffer.put(slotData,0, slotSize);
        }

        /**
         * Finds a new slot ready for use
         *
         * @return Index for new slot ready for use
         */
        public int getNewSlot() {
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
            // return the new slot
            return nextFreeSlot;
        }

        /**
         * Delete a slot, marking it as empty
         *
         * @param slotIndex the location to delete
         */
        public void deleteSlot(int slotIndex) throws IOException {
            if (slotIndex > size)
                throw new IOException("Tried to delete slot index [" + slotIndex + "] which is greater than file size [" + size + "].");
            if (slotIndex < 0)
                throw new IOException("Tried to delete slot index [" + slotIndex + "] which is less than zero.");
            freeSlotsForReuse.add(slotIndex);
        }
    }
}
