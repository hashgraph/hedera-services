package com.hedera.services.state.merkle.virtual.persistence.fcmmap;

import com.hedera.services.state.merkle.virtual.persistence.SlotStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * A data store backed by a series of files in a directory. Each of those files is made up of slots.
 * Each slot can store {@code dataSize} bytes worth of data.
 */
public final class MemMapSlotStore implements SlotStore {
    /**
     * A slot is made up of a header followed by dataSize bytes.
     *
     * <p>Must be a positive number. The value must be less than Integer.MAX_VALUE - HEADER_SIZE,
     * and must also be less than the fileSize - HEADER_SIZE. Such a dataSize would
     * represent a single slot taking up the entire file!</p>
     */
    private final int slotSizeBytes;
    /** Number of key/values pairs to store in the file. This is a computed value. */
    private final int size;

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
    private List<MemMapSlotFile> files;

    /**
     * The current file to use for writes, if it is null we will check existing files for space then create a
     * new file if needed.
     */
    private MemMapSlotFile currentFileForWriting = null;

    /** Read write lock for accessing and changing the files list and currentFileForWriting */
    private final ReentrantReadWriteLock filesLock = new ReentrantReadWriteLock();

    /**
     * Open all the files in this store. While opening it visits every used slot and calls slotVisitor.
     *
     * @param slotSizeBytes Slot data size in bytes
     * @param fileSize The size of each storage file in bytes
     * @param storageDirectory The path of the directory to store storage files
     * @param filePrefix The prefix for each storage file
     * @param fileExtension The extension for each storage file, for example "dat"
     */
    public MemMapSlotStore(int slotSizeBytes, int fileSize, Path storageDirectory, String filePrefix, String fileExtension)
            throws IOException {
        if (fileSize <= 0) throw new IOException("fileSize must be strictly positive");
        if (slotSizeBytes <= 0) throw new IOException("slotSizeBytes must be strictly positive");
        // TODO I feel like we should dramatically limit the slotSizeBytes so that it cannot be larger than something like
        // 10K or something. If we allow it to be too big, we get some weird edge cases. Thoughts?
        if (fileSize < slotSizeBytes + MemMapSlotFile.HEADER_SIZE) {
            throw new IOException("fileSize must be larger than slotSizeBytes plus " + MemMapSlotFile.HEADER_SIZE);
        }
        if (storageDirectory == null) throw new IllegalArgumentException("storageDirectory can not be null");
        if (filePrefix == null) throw new IllegalArgumentException("filePrefix can not be null");
        if (fileExtension == null) throw new IllegalArgumentException("fileExtension can not be null");
        this.slotSizeBytes = slotSizeBytes;
        this.size = fileSize / (slotSizeBytes + MemMapSlotFile.HEADER_SIZE);
        this.storageDirectory = Objects.requireNonNull(storageDirectory);
        this.filePrefix = filePrefix;
        this.fileExtension = fileExtension;
        // open files
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
                files.add(new MemMapSlotFile(slotSizeBytes, size, filePaths.get(i).toFile(), i));
            }
            // set the first file as the current one for writing
            if (!files.isEmpty()) currentFileForWriting = files.get(0);
        }
        // Change state to open
        open = new AtomicBoolean(true);
    }

    //==================================================================================================================
    // Public API methods

    /**
     * Get number of slots that can be stored in this storage
     *
     * @return number of total slots
     */
    @Override
    public int getSize() {
        return size;
    }

    /**
     * Flush all data to disk and close all files only if there are no references to this data store
     */
    @Override
    public void close() {
        filesLock.writeLock().lock();
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
            files = null;
        }
        filesLock.writeLock().unlock();
    }

    /**
     * Acquire a write lock for the given location
     *
     * @param location the location we want to be able to write to
     * @return stamp representing the lock that needs to be returned to releaseWriteLock
     */
    @Override
    public Object acquireWriteLock(long location) {
        filesLock.readLock().lock();
        MemMapSlotFile file = files.get(fileIndexFromLocation(location));
        filesLock.readLock().unlock();
        return file.acquireWriteLock(slotIndexFromLocation(location));
    }

    /**
     * Release a previously acquired write lock
     *
     * @param location the location we are finished writing to
     * @param lockStamp stamp representing the lock that you got from acquireWriteLock
     */
    @Override
    public void releaseWriteLock(long location, Object lockStamp) {
        filesLock.readLock().lock();
        MemMapSlotFile file = files.get(fileIndexFromLocation(location));
        filesLock.readLock().unlock();
        file.releaseWriteLock(slotIndexFromLocation(location), lockStamp);
    }

    /**
     * Acquire a read lock for the given location
     *
     * @param location the location we want to be able to read to
     * @return stamp representing the lock that needs to be returned to releaseReadLock
     */
    @Override
    public Object acquireReadLock(long location) {
        filesLock.readLock().lock();
        MemMapSlotFile file = files.get(fileIndexFromLocation(location));
        filesLock.readLock().unlock();
        return file.acquireReadLock(slotIndexFromLocation(location));
    }

    /**
     * Release a previously acquired read lock
     *
     * @param location the location we are finished reading from
     * @param lockStamp stamp representing the lock that you got from acquireReadLock
     */
    @Override
    public void releaseReadLock(long location, Object lockStamp) {
        filesLock.readLock().lock();
        MemMapSlotFile file = files.get(fileIndexFromLocation(location));
        filesLock.readLock().unlock();
        file.releaseReadLock(slotIndexFromLocation(location), lockStamp);
    }

    /**
     * Write data into a slot, your consumer writer will be called with a output stream while file is locked
     *
     * @param location slot location of the data to get
     * @param writer consumer to write into the slot with output stream
     */
    @Override
    public void writeSlot(long location, SlotWriter writer) throws IOException {
        filesLock.readLock().lock();
        MemMapSlotFile file = files.get(fileIndexFromLocation(location));
        filesLock.readLock().unlock();
        if (file == null) throw new IllegalArgumentException("There is no file for location ["+location+"]");
        file.writeSlot(slotIndexFromLocation(location), writer);
    }

    /**
     * Read data from a slot, your consumer reader will be called with a input stream while file is locked
     *
     * @param location slot location of the data to get
     * @param reader consumer to read slot from stream
     */
    @Override
    public <R> R readSlot(long location, SlotReader<R> reader) throws IOException {
        filesLock.readLock().lock();
        MemMapSlotFile file = files.get(fileIndexFromLocation(location));
        filesLock.readLock().unlock();
        if (file == null) throw new IllegalArgumentException("There is no file for location ["+location+"]");
        return file.readSlot(slotIndexFromLocation(location),reader);
    }

    /**
     * Finds a new slot ready for use
     *
     * @return File and slot location for a new location to store into
     */
    @Override
    public long getNewSlot() {
        throwIfNotOpen(); // We will end up throwing with an NPE while accessing "files" below anyway.
        filesLock.readLock().lock();
        MemMapSlotFile fileToWriteTo = currentFileForWriting;
        boolean currentFileHasRoom = fileToWriteTo != null && !fileToWriteTo.isFileFull();
        filesLock.readLock().unlock();
        // search for a file to write to if currentFileForWriting is missing or full
        if (!currentFileHasRoom) {
            filesLock.writeLock().lock();
            // current file is full or there is no current file
            // start by scanning existing files for free space
            currentFileForWriting = null;
            for (MemMapSlotFile file : files) {
                if (!file.isFileFull()) {
                    currentFileForWriting = file;
                    break;
                }
            }
            // all current files are full so create a new file
            if (currentFileForWriting == null) {
                // open new file
                final var newIndex = files.size();
                try {
                    currentFileForWriting = new MemMapSlotFile(slotSizeBytes, size, fileForIndex(newIndex).toFile(), newIndex);
                    files.add(currentFileForWriting);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            // we can now write to new file
            fileToWriteTo = currentFileForWriting;
            filesLock.writeLock().unlock();
        }
        // we now have a file for writing, get a new slot
        return locationFromParts(fileToWriteTo.getFileIndex(), fileToWriteTo.getNewSlot());
    }

    /**
     * Delete a slot, marking it as empty
     *
     * @param location the file and slot location to delete
     */
    @Override
    public void deleteSlot(long location) throws IOException {
        throwIfNotOpen();
        filesLock.readLock().lock();
        MemMapSlotFile file = files.get(fileIndexFromLocation(location));
        filesLock.readLock().unlock();
        if (file == null) throw new IllegalArgumentException("There is no file for location ["+location+"]");
        file.deleteSlot(slotIndexFromLocation(location));
    }

    //==================================================================================================================
    // Private methods

    /**
     * Little helper method that throws an ISE if the class isn't open
     */
    private void throwIfNotOpen() {
        if (!open.get()) {
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
    private static long locationFromParts(int file, int slot) {
        return (long)file << 32 | slot & 0xFFFFFFFFL;
    }

    /**
     * Get the file part out of a location long
     *
     * @param location long location containing file and slot locations
     * @return file part of location
     */
    private static int fileIndexFromLocation(long location) {
        return (int)(location >> 32);
    }

    /**
     * Get the slot part out of a location long
     *
     * @param location long location containing file and slot locations
     * @return slot part of location
     */
    private static int slotIndexFromLocation(long location) {
        return(int)location;
    }
}
