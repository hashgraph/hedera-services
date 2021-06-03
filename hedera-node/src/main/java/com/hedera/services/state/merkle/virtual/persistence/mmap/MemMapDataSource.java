package com.hedera.services.state.merkle.virtual.persistence.mmap;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class MemMapDataSource {
    private static final int MB = 1024*1024;

    /** Key size in bytes */
    private final int keySize;
    /** Value size in bytes */
    private final int valueSize;
    /** The path of the directory to store storage files */
    private final Path storageDirectory;
    /** The prefix for each storage file */
    private final String filePrefix;
    /** The extension for each storage file, for example "dat" */
    private final String fileExtension;
    /** Number of key/values pairs to store in the file */
    private final int size;

    /** List of all of our storage files */
    private List<MemMapDataFile> files;

    /**
     * Map to find a value in our storage.
     * At the moment, this means that we are storing all keys in memory for all time.
     * At some point, we're going to want to virtualize this as well by turning it
     * into a B+ tree on disk.
     */
    private final Map<Key, ValueLocation> keyToLocationMap = new HashMap<>();

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
    public MemMapDataSource() {
        this(32,1024,512, Path.of("data/jp_db"),"store_", "dat");
    }

    /**
     * Create new MemMapStorage
     *
     * @param keySize Key size in bytes
     * @param valueSize Value size in bytes, has to be less than Short.MAX_VALUE
     * @param fileSize The size of each storage file in MB
     * @param storageDirectory The path of the directory to store storage files
     * @param filePrefix The prefix for each storage file
     * @param fileExtension The extension for each storage file, for example "dat"
     */
    public MemMapDataSource(int keySize, int valueSize, int fileSize, Path storageDirectory, String filePrefix, String fileExtension) {
        // TODO Add validation for all the inputs
        this.keySize = keySize;
        this.valueSize = valueSize;
        this.storageDirectory = storageDirectory;
        this.filePrefix = filePrefix;
        this.fileExtension = fileExtension;
        final long fileSizeBytes = (long)fileSize * (long)MB;
        final int slotSize = 2 + keySize + valueSize;
        this.size = (int)(fileSizeBytes / slotSize);
    }

    public void open() {
        try {
            if (!Files.exists(storageDirectory)) {
                // create directory
                Files.createDirectories(storageDirectory);
                // create empty list for files
                files = new ArrayList<>();
            } else {
                // collect all storage files
                files = Files.list(storageDirectory)
                        .filter(path -> path.getFileName().startsWith(filePrefix) && path.getFileName().endsWith(fileExtension))
                        .map(path -> new MemMapDataFile(keySize, valueSize, size,path.toFile()))
                        .collect(Collectors.toList());
                // set the first file as the current one for writing
                if (!files.isEmpty()) currentFileForWriting = files.get(0);
                // open all the files and build index
                for (MemMapDataFile file : files) {
                    List<SlotKeyLocation> slotLocations = file.open();
                    for (SlotKeyLocation slotLocation : slotLocations) {
                        // TODO If the key is generic (as I would like), then we need some way to create it.
                        keyToLocationMap.put(new Key(slotLocation.key()), new ValueLocation(file, slotLocation.slotIndex()));
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Error opening storage directory.",e);
        }
    }

    public void close() {
        for (MemMapDataFile file : files) {
            try {
                file.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        files = null;
    }

    /**
     * Gets a *read only* ByteBuffer with data based on the given key.
     *
     * @param key The key. If null, returns null.
     * @return A ByteBuffer with data based on the key. If the data doesn't exist, null is returned.
     */
    public ByteBuffer get(Key key) {
        final var location = keyToLocationMap.get(key);
        if(location != null) {
            final var value = location.file().get(location.slotIndex());
            value.rewind();
            return value.asReadOnlyBuffer();
        } else {
            return null;
        }
    }

    /**
     * Sets the given bytes as the value for the given key.
     *
     * @param key The key. Must not be null.
     * @param value The bytes to write.
     */
    public void put(Key key, byte[] value) {
        ValueLocation location = keyToLocationMap.get(key);
        if (location != null) {
            // this is a known key so we need to update
            location.file().update(location.slotIndex(), key, value);
        } else {
            // this is a new key to store a value for
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
                if (currentFileForWriting == null) {
                    // open new file
                    currentFileForWriting = new MemMapDataFile(keySize,valueSize,size,
                            storageDirectory.resolve(filePrefix+(files.size()+1)+"."+fileExtension).toFile());
                    files.add(currentFileForWriting);
                    try {
                        currentFileForWriting.open();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            // add data to file
            int slot = currentFileForWriting.add(key, value);
            // add to index map
            keyToLocationMap.put(key, new ValueLocation(currentFileForWriting, slot));
        }
    }

    public void delete(byte[] key) {
        ValueLocation location = keyToLocationMap.get(new Key(key));
        if(location != null) {
            location.file().delete(location.slotIndex());
        }
    }

    public void sync() {
        for (MemMapDataFile dataFile : files) {
            dataFile.sync();
        }
    }
}
