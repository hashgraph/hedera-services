package com.hedera.services.state.merkle.virtual.persistence.fcmmap;

import sun.misc.Unsafe;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

public final class FCSlotIndexUsingMemMapFile<K> implements FCSlotIndex<K> {

    /**
     * We assume try and get the page size and us default of 4k if we fail as that is standard on linux
     */
    private static final int PAGE_SIZE_BYTES;
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
    }
    /** The path of the directory to store storage files */
    private final Path storageDirectory;

    private final String name;

    private final int numOfBins;
    private final int numOfFiles;
    private final int numOfBinsPerFile;
//    private final int numOfBinsPerPage;
//    private final int numOfPagesPerFile;

    //==================================================================================================================
    // State

    /** Array of BinFiles we are using */
    private final BinFile[] files;

    /** If this virtual map data store has been released, once released it can no longer be used */
    private boolean isReleased = false;

    /** If this data store is immutable(read only) */
    private boolean isImmutable = false;

    /** Monotonically increasing version number that is incremented every time copy() is called on the mutable copy. */
    private long version;


    //==================================================================================================================
    // Constructors

    public FCSlotIndexUsingMemMapFile(Path storageDirectory, String name, int numOfBins, int numOfFiles) throws IOException {
        if (!positivePowerOfTwo(numOfFiles)) throw new IllegalArgumentException("numOfFiles["+numOfFiles+"] must be a positive power of two.");
        if (!positivePowerOfTwo(numOfBins)) throw new IllegalArgumentException("numOfBins["+numOfBins+"] must be a positive power of two.");
        if (numOfBins > (2*numOfFiles)) throw new IllegalArgumentException("numOfBins["+numOfBins+"] must be at least twice the size of numOfFiles["+numOfFiles+"].");
        this.storageDirectory = storageDirectory;
        this.name = name;
        this.numOfBins = numOfBins;
        this.numOfFiles = numOfFiles;
        this.numOfBinsPerFile = numOfBins/numOfFiles;
        // create storage directory if it doesn't exist
        if (Files.exists(storageDirectory)) {
            Files.createDirectories(storageDirectory);
        } else {
            // check that storage directory is a directory
            if (!Files.isDirectory(storageDirectory)) {
                throw new IllegalArgumentException("storageDirectory must be a directory. ["+storageDirectory.toFile().getAbsolutePath()+"] is not a directory.");
            }
        }
        // create files
        files = new BinFile[numOfFiles];
        for (int i = 0; i < numOfFiles; i++) {
            files[i] = new BinFile(storageDirectory.resolve(name+"_"+i+".index"));
        }
    }

    /**
     * Construct a new FCFileMap as a fast copy of another FCFileMap
     *
     * @param toCopy The FCFileMap to copy to this new version leaving it immutable.
     */
    private FCSlotIndexUsingMemMapFile(FCSlotIndexUsingMemMapFile<K> toCopy) {
        // the copy that we are copying from becomes immutable as only the newest copy can be mutable
        toCopy.isImmutable = true;
        // set our incremental version
        version = toCopy.version + 1;
        // copy config
        this.storageDirectory = toCopy.storageDirectory;
        this.name = toCopy.name;
        this.numOfBins = toCopy.numOfBins;
        this.numOfFiles = toCopy.numOfFiles;
        this.numOfBinsPerFile = toCopy.numOfBinsPerFile;
        // state
        this.files = toCopy.files;
        this.files[0].referenceCount ++; // add this class as a new reference
    }


    //==================================================================================================================
    // FastCopy Implementation

    @Override
    public FCSlotIndexUsingMemMapFile copy() {
        this.throwIfImmutable();
        this.throwIfReleased();
        return new FCSlotIndexUsingMemMapFile(this);
    }

    @Override
    public boolean isImmutable() {
        return isImmutable;
    }


    //==================================================================================================================
    // Releasable Implementation

    @Override
    public void release() {
        isReleased = true;
        // TODO what else here
        // TODO need to remove all mutations of this version and clean up
        // remove reference from files
        this.files[0].referenceCount --;
        // if we were the last reference then close all the files
        if (this.files[0].referenceCount <= 0){
            for (int i = 0; i < files.length; i++) {
                BinFile file = files[i];
                try {
                    file.close();
                } catch (IOException e) {
                    e.printStackTrace(); // TODO is there a better option here
                }
            }
        }
    }

    @Override
    public boolean isReleased() {
        return isReleased;
    }

    //==================================================================================================================
    // FCSlotIndex Implementation

    @Override
    public void setKeySizeBytes(int size) {

    }

    @Override
    public long getSlot(K key) {
        return 0;
    }

    @Override
    public void putSlot(K key, long slot) {

    }

    @Override
    public long removeSlot(K key) {
        return 0;
    }

    @Override
    public int keyCount() {
        return 0;
    }

    //==================================================================================================================
    // Util functions

    /** Simple way to check of a integer is a power of two */
    private static boolean positivePowerOfTwo(int n){
        return n > 0 && (n & n-1)==0;
    }

    //==================================================================================================================
    // BinFile inner class

    /**
     * A BinFile contains a number of pages. Each page contains a number of bins. The number of pages per file is
     * ceil(numOfBinsPerFile/numOfBinsPerPage)
     * Each bin contains array of stored values
     * [int hash][bytes key][versions]
     */
    private static class BinFile {
        private final Path file;
        /** how many FCFileMaps are using this BinFile */
        private int referenceCount = 1;

        public BinFile(Path file) {
            this.file = file;
            // work out file size
            // mmap file
        }

//        public long getSlotLocation(int hash, )

        public void close() throws IOException {

        }
    }
}

