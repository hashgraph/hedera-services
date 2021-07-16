package com.hedera.services.state.merkle.v2.persistance;

import com.swirlds.fcmap.VKey;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LongIndexMemMap<K extends VKey> implements LongIndex<K> {

    //==================================================================================================================
    // Config

    private final int numOfFiles;
    private final int shiftRightCountForFileSubKey;

    //==================================================================================================================
    // State

    /** Array of BinFiles we are using, only set once in constructor and not changed */
    private final BinFile<K>[] files;

    //==================================================================================================================
    // Constructors

    /**
     * Create a new LongIndexMemMap
     *
     * @param storageDirectory the directory to store index files
     * @param name the name prefix for index files
     * @param numOfBins the total number of bins to create in all files
     * @param numOfFiles the total number of files to create
     * @param keySizeBytes The size of a serialized key in bytes
     * @param numOfKeysPerBin The number of keys for each bin
     * @throws IOException If there was a problem opening file
     */
    public LongIndexMemMap(Path storageDirectory, String name, int numOfBins, int numOfFiles, int keySizeBytes,
                                      int numOfKeysPerBin) throws IOException {
        if (!positivePowerOfTwo(numOfFiles)) throw new IllegalArgumentException("numOfFiles["+numOfFiles+"] must be a positive power of two.");
        if (!positivePowerOfTwo(numOfBins)) throw new IllegalArgumentException("numOfBins["+numOfBins+"] must be a positive power of two.");
        if (numOfBins <= (2*numOfFiles)) throw new IllegalArgumentException("numOfBins["+numOfBins+"] must be at least twice the size of numOfFiles["+numOfFiles+"].");
        this.numOfFiles = numOfFiles;
        int numOfBinsPerFile = numOfBins / numOfFiles;
        int maxNumberOfKeys = numOfKeysPerBin * numOfBins;
        // print info
        System.out.printf("For [%s] creating %,d files each containing %,d bins, with %,d keys per bin and %,d total keys entries.\n",
                name,numOfFiles, numOfBinsPerFile,numOfKeysPerBin, maxNumberOfKeys);
        // compute shiftRightCountForFileSubKey
        shiftRightCountForFileSubKey = Integer.bitCount(numOfFiles-1);
        // create storage directory if it doesn't exist
        if (!Files.exists(storageDirectory)) Files.createDirectories(storageDirectory);
        // create files
        //noinspection unchecked
        files = new BinFile[numOfFiles];
        for (int i = 0; i < numOfFiles; i++) {
            files[i] = new BinFile<>(storageDirectory.resolve(name+"_"+i+".index"),keySizeBytes, numOfKeysPerBin, numOfBinsPerFile);
        }
    }

    //==================================================================================================================
    // LongIndex Implementation

    /**
     * Put a key into map
     *
     * @param key   a non-null key
     * @param value a value that can be null
     * @throws IOException if there was a problem storing the key/value
     */
    @Override
    public void put(K key, long value) throws IOException {
        if (key == null) throw new IllegalArgumentException("Key can not be null");
        int keyHash = key.hashCode();
        // find right bin file and ask bin file
        getFileForKey(keyHash).put(getFileSubKeyHash(keyHash), key, value);
    }

    /**
     * Get a value from map
     *
     * @param key a non-null key
     * @return the found value or null if one was not stored
     * @throws IOException if there was a problem get the key/value
     */
    @Override
    public Long get(K key) throws IOException {
        if (key == null) throw new IllegalArgumentException("Key can not be null");
        int keyHash = key.hashCode();
        // find right bin file and ask bin file
        return getFileForKey(keyHash).get(getFileSubKeyHash(keyHash), key);
    }

    /**
     * Remove a key/value from map
     *
     * @param key a non-null key
     * @return the old value if there was one or null
     * @throws IOException if there was a problem removing the key/value
     */
    @Override
    public Long remove(K key) throws IOException {
        if (key == null) throw new IllegalArgumentException("Key can not be null");
        int keyHash = key.hashCode();
        // find right bin file and ask bin file
        return getFileForKey(keyHash).remove(getFileSubKeyHash(keyHash), key);
    }

    /**
     * Close this index
     *
     * @throws IOException if there was a problem closing
     */
    @Override
    public void close() throws IOException {
        for (BinFile<K> file:files) {
            file.close();
        }
    }

    //==================================================================================================================
    // Util functions

    /** Simple way to check of a integer is a power of two */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean positivePowerOfTwo(int n){
        return n > 0 && (n & n-1)==0;
    }

    /** for a given hash find out which file it is in */
    private BinFile<K> getFileForKey(int keyHash) {
        final int fileBitMask = numOfFiles-1;
        return files[keyHash & fileBitMask];
    }

    /** Gets the sub key hash from key hash, this is the part of key that specifies the bin inside a file */
    private int getFileSubKeyHash(int keyHash) {
        return keyHash >>> shiftRightCountForFileSubKey;
    }


    //==================================================================================================================
    // BinFile inner class

    /**
     * A {@link BinFile} represents a file on disk that contains a fixed number of "bins".  The hashing algorithm will
     * hash an incoming key to a specific {@link BinFile}, and within the {@link BinFile} to a specific bin.
     * <p>
     * In a typical hash map, each bin contains key/value pairs. When given a key, the hash map will hash
     * the key, find the associated bin, and then walk over each key/value pair checking that the key hashes
     * match (since multiple different key hashes can hash to the same bin), and then check that the keys are
     * equal (using "equals" and not just an identity check). The value of the first equal key is returned
     * (or replaced, depending on the operation).
     * <p>
     * Each bin contains a header of
     *      [int] a single int for number of entries, this can include deleted entries
     * then an array of stored values
     *      [int hash][int key class version][long value]
     */
    public static final class BinFile<K extends VKey> {
        /**
         * Special key for a hash for a empty entry. -1 is known to be safe as it is all ones and keys are always shifted
         * left at least 2 by FCSlotIndexUsingMemMapFile.
         */
        private static final int EMPTY_ENTRY_HASH = -1;
        private static final int NOT_FOUND = -1;
        private final AtomicBoolean fileIsOpen;
        private final FileChannel fileChannel;
        private final MappedByteBuffer mappedBuffer;
        private final int numOfKeysPerBin;
        private final int numOfBinsPerFile;
        /** Size of a bin in bytes */
        private final int binSize;
        /** Size of one key/value entry in a bin */
        private final int keyValueEntrySize;
        /** The size of the header in each bin, contains single int for number of entries stored */
        private final int binHeaderSize = Long.BYTES;
        /** Size of serialized key in bytes */
        private final int keySizeBytes;
        private final int valueOffsetInEntry;

        /**
         * Construct a new BinFile
         *
         * @param file the path to the file
         * @param keySizeBytes the size of serialized key in bytes
         * @param numOfKeysPerBin the max number of keys we can store in each bin, ie. max hash collisions
         * @param numOfBinsPerFile the number of bins, this is how many bins the hash space is divided into to avoid collisions.
         * @throws IOException if there was a problem opening the file
         */
        public BinFile(Path file, int keySizeBytes, int numOfKeysPerBin, int numOfBinsPerFile) throws IOException {
            //this.file = file;
            this.numOfKeysPerBin = numOfKeysPerBin;
            this.numOfBinsPerFile = numOfBinsPerFile;
            this.keySizeBytes = keySizeBytes;
            int serializedKeySizeBytes = Long.BYTES + keySizeBytes;
            // calculate size of key,mutation store which contains:
            valueOffsetInEntry = Long.BYTES + serializedKeySizeBytes;
            keyValueEntrySize = valueOffsetInEntry + Long.BYTES;
            binSize = binHeaderSize + (numOfKeysPerBin * keyValueEntrySize);
            final int fileSize = binSize * numOfBinsPerFile;
            // OPEN FILE
            // check if file existed before
            if (!Files.exists(file)) {
                // create file
                PersistenceUtils.createFile(file, fileSize);
            }
            // open file
            fileChannel = FileChannel.open(file,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.READ
            );
            mappedBuffer = fileChannel.map(FileChannel.MapMode.READ_WRITE, 0, fileSize);
            // mark file as open
            fileIsOpen = new AtomicBoolean(true);
        }

        //==================================================================================================================
        // Public API methods

        /**
         * Sync data and close file, after this this BinFile is not usable.
         *
         * @throws IOException if there was a problem closing files and syncing everything to disk
         */
        public void close() throws IOException {
            if (fileIsOpen.getAndSet(false)) {
                mappedBuffer.force();
                fileChannel.force(true);
                fileChannel.close();
            }
        }

        /**
         * Put a key into map
         *
         * @param keySubHash The sub part of hash that is within this file
         * @param key a non-null key
         * @param value a value that can be null
         * @throws IOException if there was a problem storing the key/value
         */
        public void put(int keySubHash, K key, long value) throws IOException {
            // first we need to see if a entry exists for key
            final int binOffset = findBinOffset(keySubHash);
            // read count of mutation queues.
            final int entryCount = (int)mappedBuffer.getLong(binOffset);
            int entryOffset = findEntryOffsetForKeyInBin(binOffset,entryCount,keySubHash,key);

            // if no entry exists we need to create one
            if (entryOffset == NOT_FOUND) {
                // first search for empty entry
                for(int i=0; i< entryCount; i++) {
                    final int eOffset = binOffset + binHeaderSize + (keyValueEntrySize * i);
                    final var hash = mappedBuffer.getLong(eOffset);
                    if (hash == EMPTY_ENTRY_HASH) {
                        entryOffset = eOffset;
                        break;
                    }
                }
                // see if we found one, if not append one on the end
                if (entryOffset == NOT_FOUND) {
                    if (entryCount >= numOfKeysPerBin) throw new RuntimeException("We have hit numOfKeysPerBin in BinFile.putSlot(), entryCount="+entryCount);
                    // find next entry index
                    @SuppressWarnings("UnnecessaryLocalVariable") final int nextEntryIndex = entryCount;
                    // increment entryCount
                    mappedBuffer.putLong(binOffset,entryCount + 1);
                    // compute new entryOffset
                    entryOffset = binOffset + binHeaderSize + (keyValueEntrySize * nextEntryIndex);
                    // write hash
                    mappedBuffer.putLong(entryOffset, keySubHash);
                    // write serialized key version
                    mappedBuffer.putLong(entryOffset + Long.BYTES, key.getVersion());
                    // write serialized key
                    final var subBuffer = mappedBuffer.slice();
                    subBuffer.position(entryOffset + Long.BYTES + Long.BYTES);
    //                subBuffer.limit(keySizeBytes); // TODO there should be a limit
                    key.serialize(subBuffer);
                }
            }
            // write value
            mappedBuffer.putLong(entryOffset+valueOffsetInEntry,value);
        }

        /**
         * Get a value from map
         *
         * @param keySubHash The sub part of hash that is within this file
         * @param key a non-null key
         * @return the found value or null if one was not stored
         * @throws IOException if there was a problem get the key/value
         */
        public Long get(int keySubHash, K key) throws IOException {
            int entryOffset = findEntryOffsetForKeyInBin(keySubHash, key);
            if (entryOffset == NOT_FOUND) return null;
            // read the value long
            return mappedBuffer.getLong(entryOffset+valueOffsetInEntry);
        }

        /**
         * Remove a key/value from map
         *
         * @param keySubHash The sub part of hash that is within this file
         * @param key a non-null key
         * @return the old value if there was one or null
         * @throws IOException if there was a problem removing the key/value
         */
        public Long remove(int keySubHash, K key) throws IOException {
            int entryOffset = findEntryOffsetForKeyInBin(keySubHash, key);
            Long oldValue = null;
            if (entryOffset != NOT_FOUND) {
                // read the old value long
                oldValue = mappedBuffer.getLong(entryOffset+valueOffsetInEntry);
                // mark entry as empty
                mappedBuffer.putLong(entryOffset,EMPTY_ENTRY_HASH);
            }
            return oldValue;
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
            final int queueCount = (int)mappedBuffer.getLong(binOffset);
            return findEntryOffsetForKeyInBin(binOffset,queueCount,keySubHash,key);
        }

        private int findEntryOffsetForKeyInBin(int binOffset, int queueCount, int keySubHash, K key) {
            // iterate searching
            int foundOffset = NOT_FOUND;
            try {
                for (int i = 0; i < queueCount; i++) {
                    final int keyQueueOffset = binOffset + binHeaderSize + (keyValueEntrySize * i);
                    // read hash
                    final var readHash = mappedBuffer.getLong(keyQueueOffset);
                    // dont need to check for EMPTY_ENTRY_HASH as it will never match keySubHash
                    if (readHash == keySubHash && keyEquals(keyQueueOffset + Long.BYTES, key)) {
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
            int version = (int)mappedBuffer.getLong(offset);
            // position input stream for deserialize
            final var subBuffer = mappedBuffer.slice().asReadOnlyBuffer();
            subBuffer.position(offset + Long.BYTES);
            subBuffer.limit(subBuffer.position() + keySizeBytes);
            return key.equals(subBuffer, version);
        }
    }
}
