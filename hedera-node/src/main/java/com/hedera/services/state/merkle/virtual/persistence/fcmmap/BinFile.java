package com.hedera.services.state.merkle.virtual.persistence.fcmmap;

import com.swirlds.fcmap.VKey;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.LongSupplier;

import static com.hedera.services.state.merkle.virtual.persistence.FCSlotIndex.NOT_FOUND_LOCATION;

/**
 * A BinFile contains a fixed number of bins. Each bin contains entries for key in a section of the hash space. Each
 * entry contains the mutation list for a key.
 *
 * TODO this class assumes that only one thread will call it
 *
 * Each bin contains a header of
 *      [int] a single int for number of entries, this can include deleted entries
 * then a array of stored values
 *      [int hash][int key class version][bytes key][mutation queue]
 * mutation queue is, oldest first
 *      [int size][mutation],[mutation],[mutation],[mutation]
 * mutation is two longs
 *      [long version][long slot index value]
 */
@SuppressWarnings({"DuplicatedCode", "jol"})
public final class BinFile<K extends VKey> {
    /** a mutation in mutation queue size, consists of a Version long and slot location value long */
    private static final int MUTATION_SIZE = Long.BYTES * 2;
    private static final int MUTATION_QUEUE_HEADER_SIZE = Integer.BYTES;

    /** A flag used in place of the mutation version indicating that the associated copy has been released. */
    private static final int RELEASED = -1;

    /**
     * Special key for a hash for a empty entry. -1 is known to be safe as it is all ones and keys are always shifted
     * left at least 2 by FCSlotIndexUsingMemMapFile.
     */
    private static final int EMPTY_ENTRY_HASH = -1;

    /**
     * We assume try and get the page size and us default of 4k if we fail as that is standard on linux
     */
    /*private static final int PAGE_SIZE_BYTES;
    static {
        int pageSize = 4096; // 4k is default on linux
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            Unsafe unsafe = (Unsafe)f.get(null);
            pageSize = unsafe.pageSize();
        } catch (NoSuchFieldException | IllegalAccessException e) {
            System.out.println("Failed to get page size via misc.unsafe");
            // try and get from system command
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            if (!isWindows) {
                ProcessBuilder builder = new ProcessBuilder()
                        .command("getconf", "PAGE_SIZE")
                        .directory(new File(System.getProperty("user.home")));
                try {
                    Process process = builder.start();
                    String output = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))
                            .lines()
                            .collect(Collectors.joining())
                            .trim();
                    try {
                        pageSize = Integer.parseInt(output);
                    } catch(NumberFormatException numberFormatException) {
                        System.out.println("Failed to get page size via running \"getconf\" command. so using default 4k\n"+
                                "\"getconf\" output was \""+output+"\"");
                    }
                } catch (IOException ioException) {
                    System.out.println("Failed to get page size via running \"getconf\" command. so using default 4k");
                    ioException.printStackTrace();
                }
            }
        }
        System.out.println("Page size: " + pageSize);
        PAGE_SIZE_BYTES = pageSize;
    }*/

    /** Special pointer value used for when a value has been deleted */
    public static final long DELETED_POINTER = -1;
    private boolean fileIsOpen;
    private final RandomAccessFile randomAccessFile;
    private final FileChannel fileChannel;
    private final MappedByteBuffer mappedBuffer;
    private final int numOfKeysPerBin;
    private final int numOfBinsPerFile;
    private final int maxNumberOfMutations;
    /** Size of a bin in bytes */
    private final int binSize;
    /** Size one key/mutationQueue in a bin */
    private final int keyMutationEntrySize;
    /** The size of the header in each bin, contains single int for number of entries stored */
    private final int binHeaderSize = Integer.BYTES;
    /** The offset to the mutation queue inside a keyMutationEntry */
    private final int queueOffsetInEntry;
    /** Single MutationQueueReference that we can reuse as we are synchronized */
    private final EntryReference entryReference = new EntryReference();
    /** Map containing list of mutated queues for each version older than the current one that has not yet been released. */
    private final Map<Long, List<Integer>> changedKeysPerVersion = new HashMap<>();
    /** Readwrite lock for this file */
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    /** list of offsets for mutated keys for current version */
    private List<Integer> changedKeysCurrentVersion = new ArrayList<>();
    /** The current version we are working on */
    private long currentVersion = -1;

    /**
     * Construct a new BinFile
     *
     * @param file the path to the file
     * @param keySizeBytes the size of serialized key in bytes
     * @param numOfKeysPerBin the max number of keys we can store in each bin, ie. max hash collisions
     * @param numOfBinsPerFile the number of bins, this is how many bins the hash space is divided into to avoid collisions.
     * @param maxNumberOfMutations the maximum number of mutations that can be stored for each key
     * @throws IOException if there was a problem opening the file
     */
    public BinFile(Path file, int keySizeBytes, int numOfKeysPerBin, int numOfBinsPerFile, int maxNumberOfMutations) throws IOException {
        //this.file = file;
        this.numOfKeysPerBin = numOfKeysPerBin;
        this.numOfBinsPerFile = numOfBinsPerFile;
        this.maxNumberOfMutations = maxNumberOfMutations;
        // calculate size of key,mutation store which contains:
        final int mutationArraySize = maxNumberOfMutations * MUTATION_SIZE;
        final int serializedKeySize = Integer.BYTES + keySizeBytes; // we store a int for class version
        queueOffsetInEntry = Integer.BYTES + serializedKeySize;
        keyMutationEntrySize = queueOffsetInEntry + MUTATION_QUEUE_HEADER_SIZE + mutationArraySize;
        binSize = binHeaderSize + (numOfKeysPerBin * keyMutationEntrySize);
        final int fileSize = binSize * numOfBinsPerFile;
        // OPEN FILE
        // check if file existed before
        boolean fileExisted = Files.exists(file);
        // open random access file
        randomAccessFile = new RandomAccessFile(file.toFile(), "rw");
        if (!fileExisted) {
            // set size for new empty file
            randomAccessFile.setLength(fileSize);
        }
        // get file channel and memory map the file
        fileChannel = randomAccessFile.getChannel();
        mappedBuffer = fileChannel.map(FileChannel.MapMode.READ_WRITE, 0, fileSize);
        // mark file as open
        fileIsOpen = true;
    }

    //==================================================================================================================
    // Public API methods

    /**
     * Sync data and close file, after this this BinFile is not usable.
     *
     * @throws IOException if there was a problem closing files and syncing everything to disk
     */
    public void close() throws IOException {
        lock.writeLock().lock();
        if (fileIsOpen) {
            fileIsOpen = false;
            mappedBuffer.force();
            fileChannel.force(true);
            fileChannel.close();
            randomAccessFile.close();
        }
        lock.writeLock().unlock();
    }

    /**
     * Get version "version" of value for key or next oldest version.
     *
     * @param version The version of value to get
     * @param keySubHash The sub part of hash that is within this file
     * @param key The key
     * @return the stored value or FCSlotIndex.NOT_FOUND_LOCATION if not found.
     */
    public long getSlot(long version, int keySubHash, K key) {
        lock.readLock().lock();
        int entryOffset = findEntryOffsetForKeyInBin(keySubHash, key);
        long slotIndex = (entryOffset != -1) ? getMutationValue(entryOffset+queueOffsetInEntry,version) : NOT_FOUND_LOCATION;
        lock.readLock().unlock();
        return slotIndex;
    }

    /**
     * Get slot index for given key, if nothing is stored for key then call newValueSupplier to get a slot index and
     * store it for key.
     *
     * @param version The version of value to get
     * @param keySubHash The sub part of hash that is within this file
     * @param key the key to get or store slot index
     * @param newValueSupplier supplier to get a slot index if nothing was stored for key
     * @return either the slot index that was stored for key or the new slot index provided by supplier
     */
    public long getSlotIfAbsentPut(long version, int keySubHash, K key, LongSupplier newValueSupplier) {
        lock.readLock().lock();
        int entryOffset = findEntryOffsetForKeyInBin(keySubHash, key);
        if (entryOffset != -1) {
            long slotIndex = getMutationValue(entryOffset + queueOffsetInEntry, version);
            lock.readLock().unlock();
            return slotIndex;
        } else {
            lock.readLock().unlock();
            lock.writeLock().lock();
            long newValue = newValueSupplier.getAsLong();
            EntryReference entry = getOrCreateEntry(keySubHash, key);
            // so we now have a entry, existing or a new one we just have to add/update the version in mutation queue
            writeValueIntoMutationQueue(entry.offset+queueOffsetInEntry, entry.wasCreated, version, newValue);
            lock.writeLock().unlock();
            return newValue;
        }
    }

    /**
     * Put a value for a version of key. This could be a update if that key already has a value for this version or a
     * add if the version is new.
     *
     * @param version the version to save value for
     * @param keySubHash the sub part of hash that is within this file
     * @param key the key
     * @param value the value to save
     * @return true if a new version was added or false if it was updating an existing version
     */
    public boolean putSlot(long version, int keySubHash, K key, long value) {
        lock.writeLock().lock();
        EntryReference entry = getOrCreateEntry(keySubHash, key);
        // so we now have a entry, existing or a new one we just have to add/update the version in mutation queue
        long oldValue = writeValueIntoMutationQueue(entry.offset+queueOffsetInEntry, entry.wasCreated, version, value);
        lock.writeLock().unlock();
        return oldValue != NOT_FOUND_LOCATION;
    }

    /**
     * delete a version of value for a key, returning the old value
     *
     * @param version the version of key to delete
     * @param keySubHash the sub part of hash that is within this file
     * @param key the key
     * @return the value for the deleted key if there was one or FCSlotIndex.NOT_FOUND_LOCATION
     */
    public long removeKey(long version, int keySubHash, K key) {
        lock.writeLock().lock();
        int entryOffset = findEntryOffsetForKeyInBin(keySubHash, key);
        long slotIndex = NOT_FOUND_LOCATION;
        if (entryOffset != -1) {
            // we only need to write a deleted mutation if there was already a entry for the key
            slotIndex = writeValueIntoMutationQueue(entryOffset + queueOffsetInEntry, false, version, DELETED_POINTER);
        }
        lock.writeLock().unlock();
        return slotIndex;
    }

    /**
     * Called to inform us when a version has changed
     *
     * @param oldVersion the old version number
     * @param newVersion the new version number
     */
    public void versionChanged(long oldVersion, long newVersion) {
        lock.writeLock().lock();
        currentVersion = newVersion;
        // stash changedKeysCurrentVersion and create new list
        changedKeysPerVersion.put(oldVersion, changedKeysCurrentVersion);
        changedKeysCurrentVersion = new ArrayList<>();
        lock.writeLock().unlock();
    }

    /**
     * Release a version, cleaning up all references to it in file.
     *
     * @param version the version to release
     */
    public void releaseVersion(long version) {
        lock.writeLock().lock();
        // version deleting entries for this version
        List<Integer> changedKeys = (version == currentVersion || currentVersion == -1) ?
                changedKeysCurrentVersion : changedKeysPerVersion.remove(version);
        // TODO maybe need to clear out changedKeysCurrentVersion but seems if current version has been released we are done
        if (changedKeys != null) {
            for (Integer mutationQueueOffset : changedKeys) {
                markQueue(mutationQueueOffset, version);
            }
        }
        lock.writeLock().unlock();
    }

    /**
     * Acquire a write lock for the given location
     *
     * @param keySubHash the keySubHash we want to be able to write to
     * @return stamp representing the lock that needs to be returned to releaseWriteLock
     */
    public Object acquireWriteLock(int keySubHash) {
        lock.writeLock().lock();
        return lock.writeLock();
    }

    /**
     * Release a previously acquired write lock
     *
     * @param keySubHash  the keySubHash we are done writing to
     * @param lockStamp stamp representing the lock that you got from acquireWriteLock
     */
    public void releaseWriteLock(int keySubHash, Object lockStamp) {
        if (lockStamp == lock.writeLock()) lock.writeLock().unlock();
    }

    /**
     * Acquire a read lock for the given location
     *
     * @param keySubHash the keySubHash we want to be able to read to
     * @return stamp representing the lock that needs to be returned to releaseReadLock
     */
    public Object acquireReadLock(int keySubHash) {
        lock.readLock().lock();
        return lock.readLock();
    }

    /**
     * Release a previously acquired read lock
     *
     * @param keySubHash  the keySubHash we are done reading from
     * @param lockStamp stamp representing the lock that you got from acquireReadLock
     */
    public void releaseReadLock(int keySubHash, Object lockStamp) {
        if (lockStamp == lock.readLock()) lock.readLock().unlock();
    }

    //==================================================================================================================
    // Bin Methods

    /**
     * Find the offset inside file for the bin for
     *
     * @param keySubHash the inside file part of key's hash
     * @return the pointer inside this file for the bin that will contain that key
     */
    private int findBinOffset(int keySubHash) {
        return (keySubHash % numOfBinsPerFile) * binSize;
    }

    //==================================================================================================================
    // Entry Methods

    /**
     * Find the index inside a bin for which mutation queue is used for storing mutations for a given key
     *
     * @param keySubHash The keys inside file sub hash
     * @param key The key object
     * @return the offset of mutation key in bin or -1 if not found
     */
    private int findEntryOffsetForKeyInBin(int keySubHash, K key) {
        final int binOffset = findBinOffset(keySubHash);
        // read count of mutation queues.
        int queueCount = mappedBuffer.getInt(binOffset);
        // iterate searching
        int foundOffset = -1;
        try {
            for (int i = 0; i < queueCount; i++) {
                final int keyQueueOffset = binOffset + binHeaderSize + (keyMutationEntrySize * i);
                // read hash
                final int readHash = mappedBuffer.getInt(keyQueueOffset);
                // dont need to check for EMPTY_ENTRY_HASH as it will never match keySubHash
                if (readHash == keySubHash && keyEquals(keyQueueOffset + Integer.BYTES, key)) {
                    // we found the key
                    foundOffset = keyQueueOffset;
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace(); // TODO Something here.
        }
        return foundOffset;
    }

    /**
     * Check if a key equals one stored in the file at the given offset
     *
     * @param offset the offset into file where key to compare with hash been serialized
     * @param key The key to compare with
     * @return true if the key object .equals() the serialized key stored in file
     */
    private boolean keyEquals(int offset, K key) throws IOException {
        // read serialization version
        int version = mappedBuffer.getInt(offset);
        // position input stream for deserialize
        mappedBuffer.position(offset+Integer.BYTES);
        return key.equals(mappedBuffer,version);
    }

    private EntryReference getOrCreateEntry(int keySubHash, K key) {
        entryReference.wasCreated = false;
        // first we need to see if a entry exists for key
        int entryOffset = findEntryOffsetForKeyInBin(keySubHash, key);
        // if no entry exists we need to create one
        if (entryOffset == -1) {
            final int binOffset = findBinOffset(keySubHash);
//            System.out.println(file.getFileName().toString() + ", key=" + key + ", keySubHash=" + keySubHash + ", binOffset=" + binOffset);

            final int entryCount = mappedBuffer.getInt(binOffset);
            // first search for empty entry
            for(int i=0; i< entryCount; i++) {
                final int eOffset = binOffset + binHeaderSize + (keyMutationEntrySize * i);
                final int hash = mappedBuffer.getInt(eOffset);
                if (hash == EMPTY_ENTRY_HASH) {
                    entryOffset = eOffset;
                    break;
                }
            }
            // see if we found one, if not append one on the end
            if (entryOffset == -1) {
                if (entryCount >= numOfKeysPerBin) throw new RuntimeException("We have hit numOfKeysPerBin in BinFile.putSlot(), entryCount="+entryCount);
                // find next entry index
                @SuppressWarnings("UnnecessaryLocalVariable") final int nextEntryIndex = entryCount;
                // increment entryCount
                mappedBuffer.putInt(binOffset,entryCount + 1);
                // compute new entryOffset
                entryOffset = binOffset + binHeaderSize + (keyMutationEntrySize * nextEntryIndex);
                // write hash
                mappedBuffer.putInt(entryOffset,keySubHash);
                // write serialized key version
                mappedBuffer.putInt(entryOffset+Integer.BYTES,key.getVersion());
                // write serialized key
                mappedBuffer.position(entryOffset+Integer.BYTES+Integer.BYTES);
                try {
                    key.serialize(mappedBuffer);
                } catch (IOException e) {
                    e.printStackTrace(); // TODO something better
                }
                // no need to write a size of new mutation queue as we will always set it in putMutationValue but
                // we do need to keep track of if the mutation queue was created.
                entryReference.wasCreated = true;
            }
        }
        entryReference.offset = entryOffset;
        return entryReference;
    }

    //==================================================================================================================
    // Mutation Queue Methods

    private long getMutationValue(int mutationQueueOffset, long version) {
        int mutationCount = mappedBuffer.getInt(mutationQueueOffset);
        // start with newest searching for version
        for (int i = mutationCount-1; i >= 0; i--) {
            int mutationOffset = getMutationOffset(mutationQueueOffset,i);
            long mutationVersion = mappedBuffer.getLong(mutationOffset);
            if (mutationVersion <= version) {
                final long slotLocation =  mappedBuffer.getLong(mutationOffset+Long.BYTES);
                if (slotLocation != DELETED_POINTER) {
                    return slotLocation;
                } else { // explicit deleted means it was deleted for version, so return not found
                    return NOT_FOUND_LOCATION;
                }
            }
        }
        return NOT_FOUND_LOCATION;
    }

    /**
     * Write a new value into a specific version in mutation queue. This can be used for put and delete;
     *
     * @param mutationQueueOffset the offset location for mutation queue
     * @param isEmptyMutationQueue if this is writing to a brand new mutation queue
     * @param version the version to write value for, this will always be the latest version
     * @param value the value to save for version
     * @return the old version that was changed if there was one this version or NOT_FOUND_LOCATION if there was not a
     *         mutation for this version to change.
     */
    private long writeValueIntoMutationQueue(int mutationQueueOffset, boolean isEmptyMutationQueue, long version, long value) {
        int mutationCount = mappedBuffer.getInt(mutationQueueOffset);
        int mutationOffset;
        long oldSlotIndex = NOT_FOUND_LOCATION;
        if (isEmptyMutationQueue) { // new empty mutation queue
            // write a mutation count of one
            mappedBuffer.putInt(mutationQueueOffset,1);
            // use first mutation to write to
            mutationOffset = getMutationOffset(mutationQueueOffset,0);
            // write version into mutation
            mappedBuffer.putLong(mutationOffset, version);
        } else {
            // check if the newest version saved is same version
            final int newestMutationOffset = getMutationOffset(mutationQueueOffset, mutationCount - 1);
            final long newestVersionInQueue = mappedBuffer.getLong(newestMutationOffset);
            // read the old slot value
            oldSlotIndex = mappedBuffer.getLong(newestMutationOffset + Long.BYTES);
            // update for create new mutation
            if (newestVersionInQueue == version) { // update
                mutationOffset = newestMutationOffset;
            } else { // new version
                // clean out any old mutations that are no longer needed
                mutationCount = sweepQueue(mutationQueueOffset, mutationCount);
                // check we have not run out of mutations
                if (mutationCount >= maxNumberOfMutations) throw new IllegalStateException("We ran out of mutations.");
                // increment mutation count
                mappedBuffer.putInt(mutationQueueOffset, mutationCount + 1);
                // get the next empty mutation to write to
                mutationOffset = getMutationOffset(mutationQueueOffset, mutationCount);
                // write version into mutation
                mappedBuffer.putLong(mutationOffset, version);
            }
        }
        // write value into mutation
        mappedBuffer.putLong(mutationOffset+Long.BYTES,value);
        // stash mutation queue for later clean up, if this is a new mutation. If it was updating a mutation
        // then when that mutation was made it would have already been added.
        if (oldSlotIndex == NOT_FOUND_LOCATION) {
            changedKeysCurrentVersion.add(mutationQueueOffset);
        }
        return oldSlotIndex;
    }

    /**
     * As part of a classic mark+sweep design, marks any mutation in the queue
     * with the given version. A subsequent "sweep" will remove all released mutations that
     * are no longer needed.
     *
     * @param mutationQueueOffset The offset of the mutation queue to process
     * @param version The mutation version to try to mark as RELEASED.
     */
    private void markQueue(int mutationQueueOffset, long version) {
        // Normally, copies are released in the order in which they were
        // copied, which means I am more likely to iterate less if I
        // start at the beginning of the queue (oldest) and work my way
        // down towards the newest. I can terminate prematurely if the
        // mutation I'm looking at is newer than my version.
        final var mutationCount = mappedBuffer.getInt(mutationQueueOffset);
        for (int i=0; i<mutationCount; i++) {
            final var mutationOffset = getMutationOffset(mutationQueueOffset, i);
            final var mutationVersion = mappedBuffer.getLong(mutationOffset);
            if (mutationVersion == version) {
                // This is the mutation. Mark it by setting the version to RELEASED.
                mappedBuffer.putLong(mutationOffset, RELEASED);
            } else if (mutationVersion > version) {
                // There is no such mutation here (which is surprising, but maybe it got swept already).
                return;
            }
        }
    }

    /**
     * "Sweeps" all unneeded mutations from the queue, shifting all
     * subsequent mutations left.
     *
     * @param mutationQueueOffset The offset to the mutation queue to be swept.
     * @return The new mutation count after sweeping
     */
    private int sweepQueue(final int mutationQueueOffset, final int mutationCount) {
        // Look through the mutations and collect the indexes of each mutation that can be removed.
        // Then move surviving mutations forward into the earliest available slots.
        // Visit all of the mutations. Anything marked RELEASED can be replaced.
        int i = 0;
        int j = 1;
        for (; i < mutationCount; i++, j++) {
            final var offset = getMutationOffset(mutationQueueOffset, i);
            final var version = mappedBuffer.getLong(offset);
            if (version == RELEASED) {
                // This mutation has been released. Now we need to look for the first non-released mutation
                // and copy it here.
                while (j < mutationCount) {
                    final var offset2 = getMutationOffset(mutationQueueOffset, j);
                    final var version2 = mappedBuffer.getLong(offset2);
                    if (version2 != RELEASED) {
                        // Copy from mutation2 to mutation
                        mappedBuffer.putLong(offset, version2);
                        mappedBuffer.putLong(offset + Long.BYTES, mappedBuffer.getLong(offset2 + Long.BYTES));
                        // Tombstone the old mutation location (it will be removed by this code)
                        mappedBuffer.putLong(offset2, RELEASED);
                        // Step out of the inner loop and let "i" increment again. But increment j so that next time
                        // we need a keeper mutation, we start from looking one past j (since we already know by this
                        // point that only SWEPT mutations or keepers are behind j).
                        break;
                    } else {
                        j++;
                    }
                }

                // If j is >= mutationCount, then we have decided there is NOTHING later in this queue, so
                // we can finish our iteration.
                if (j >= mutationCount) {
                    break;
                }
            }
        }

        // "i" will tell us how many mutations we still have.
        return i;
    }

    private int getMutationOffset(int mutationQueueOffset, int mutationIndex) {
        return mutationQueueOffset + Integer.BYTES + (mutationIndex*MUTATION_SIZE);
    }

    //==================================================================================================================
    // Simple Struts

    private static class EntryReference {
        public int offset;
        public boolean wasCreated;
    }
}
