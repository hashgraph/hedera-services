package com.hedera.services.state.jasperdb.files;

import com.hedera.services.state.jasperdb.collections.LongList;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Common static content for data files.
 */
public class DataFileCommon {
    /** Constant for 1 Mb of bytes */
    public static final long MB = 1024*1024;
    /** Data size constant used when the data size is variable */
    public static final int VARIABLE_DATA_SIZE = -1;
    /** This is the size of the header written for each variable size data item to store its size */
    public static final int SIZE_OF_DATA_ITEM_SIZE_IN_FILE = Integer.BYTES;
    /**
     * The data item byte offset is packed into lower 40 bits and file index upper 24 bits.
     * This allows for 16 million files 1 trillion bytes of data.
     * So at one file per minute we have 30 years of 1Tb files.
     *
     * TODO: At the moment there is no support for file indexes to wrap so if we write data more often than once a
     * TODO: minute or longer than 30 years this will have to be fixed.
     * TODO: Thinking maybe this should be 36 which would give us a max file size of 128Gb but give us 85 years of one file every 10 seconds.
     */
    static final int DATA_ITEM_OFFSET_BITS = 40;
    /** The maximum size a data file can be, 1Tb with DATA_ITEM_OFFSET_BITS = 40 */
    static final long MAX_ADDRESSABLE_DATA_FILE_SIZE_BYTES = 1L << DATA_ITEM_OFFSET_BITS;
    /** Bit mask to remove file index from data location long */
    static final long ITEM_OFFSET_MASK = MAX_ADDRESSABLE_DATA_FILE_SIZE_BYTES -1;
    /**
     * Nominal value to indicate a non-existent data location. This was carefully crafted to be 0 so that a new long
     * array of data location pointers will be initialized to be all non-existent.
     */
    public static long NON_EXISTENT_DATA_LOCATION = 0;
    /** Just a handy constant for one GB */
    static final long GB = 1024*1024*1024;
    /**
     * Chosen max size for a data file, this is a balance as fewer bigger files are faster to read from but large files
     * are extensive to merge. It must be less than MAX_ADDRESSABLE_DATA_FILE_SIZE_BYTES.
     *
     * It is also limited by the in RAM size of the moves list needed to merge a file of this size. If you take this
     * MAX_DATA_FILE_SIZE divided by (key+hash+value) size. That number should be less than Integer.MAX_VALUE/3 which
     * is needed by ThreeLongsList.
     */
    static final long MAX_DATA_FILE_SIZE = 64*GB;
    /** Size of keys in bytes, assumed to be a single long as all our use cases just needed a long */
    static final int KEY_SIZE = Long.BYTES;
    /** The current file format version, ready for if the file format needs to change */
    static final int FILE_FORMAT_VERSION = 1;
    /** Date formatter for dates used in data file names */
    static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd--HH-mm-ss-SSS").withZone(ZoneId.of("Z"));
    /** Extension to use for Jasper DB data files :-) */
    static final String FILE_EXTENSION = ".jdb";
    /** System page size used in calculations, could be read from system but for linux we are pretty safe assuming 4k */
    static final int PAGE_SIZE = 4096;
    /** Size of metadata footer written at end of file */
    static final int FOOTER_SIZE = PAGE_SIZE;
    /** Comparator for comparing DataFileReaders by file creation time */
    static final Comparator<DataFileReader> DATA_FILE_READER_CREATION_TIME_COMPARATOR =
            Comparator.comparing(o -> o.getMetadata().getCreationDate());

    /**
     * Create a filter to only return all new files that are smaller than given size
     *
     * @param sizeMB max file size to accept in MB
     * @return filter to filter list of files
     */
    public static Function<List<DataFileReader>, List<DataFileReader>> newestFilesSmallerThan(int sizeMB) {
        long sizeBytes = sizeMB * MB;
        return dataFileReaders -> {
            var filesNewestFirst = dataFileReaders.stream()
                    .sorted(DATA_FILE_READER_CREATION_TIME_COMPARATOR.reversed())
                    .collect(Collectors.toList());
            var smallEnoughFiles = new ArrayList<DataFileReader>(filesNewestFirst.size());
            for(var file: filesNewestFirst) {
                long size = Long.MAX_VALUE;
                try {
                    size = file.getSize();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (size < sizeBytes) {
                    smallEnoughFiles.add(file);
                } else {
                    break;
                }
            }
            return smallEnoughFiles;
        };
    }

    /**
     * Get path for file given prefix, index and parent directory. This standardizes out file naming convention.
     *
     * @param filePrefix the prefix for file name
     * @param dataFileDir the files parent directory
     * @param index the file index
     * @param creationInstant the date and time the file was created
     * @return path to file
     */
    static Path createDataFilePath(String filePrefix, Path dataFileDir, int index, Instant creationInstant) {
        return dataFileDir.resolve(filePrefix + "_" + StringUtils.leftPad(Integer.toString(index),5,'0') +
                "_" + DATE_FORMAT.format(creationInstant) + FILE_EXTENSION);
    }

    /**
     * Get the path for a lock file for a given data file path
     */
    static Path getLockFilePath(Path dataFilePath) {
        return dataFilePath.resolveSibling(dataFilePath.getFileName().toString()+".lock");
    }

    /**
     * Get the packed data location from file index and byte offset.
     *
     * @param fileIndex the index for the file
     * @param byteOffset the offset for the data within the file in bytes
     * @return packed data location
     */
    public static long dataLocation(int fileIndex, long byteOffset) {
        // we add 1 to file index so that 0 works for NON_EXISTENT_DATA_LOCATION
        final long indexShifted = (long) (fileIndex + 1) << DATA_ITEM_OFFSET_BITS;
        final long byteOffsetMasked = byteOffset & ITEM_OFFSET_MASK;
        return indexShifted | byteOffsetMasked;
    }

    public static String dataLocationToString(long dataLocation) {
        return "{"+fileIndexFromDataLocation(dataLocation)+","+byteOffsetFromDataLocation(dataLocation)+"}";
    }

    /**
     * Extract the file index from packed data location, this is the upper 24 bits. So in the range of 0 to 16 million.
     *
     * @param dataLocation packed data location
     * @return file index
     */
   public static int fileIndexFromDataLocation(long dataLocation) {
        // we subtract 1 from file index so that 0 works for NON_EXISTENT_DATA_LOCATION
        return (int)(dataLocation >> DATA_ITEM_OFFSET_BITS) - 1;
    }

    /**
     * Extract the data byte offset from packed data location, this is the lower 40 bits so in the 0 to 1 trillion range.
     *
     * @param dataLocation packed data location
     * @return data offset in bytes
     */
    public static long byteOffsetFromDataLocation(long dataLocation) {
        return dataLocation & ITEM_OFFSET_MASK;
    }

    /**
     * Check if a file at path, is a data file based on name. Also checks if there is an existing write lock file.
     *
     * @param filePrefix the prefix for the set of data files
     * @param path the path to the data file
     * @return true if the name starts with prefix and has right extension
     */
    static boolean isFullyWrittenDataFile(String filePrefix, Path path) {
        String fileName = path.getFileName().toString();
        boolean validFile =  fileName.startsWith(filePrefix) && fileName.endsWith(FILE_EXTENSION);
        if (!validFile) return false;
        return !Files.exists(getLockFilePath(path));
    }

    /**
     * print debug info showing if all links in index are still valid
     */
    public static void printDataLinkValidation(LongList index, List<DataFileReader> fileList) {
        System.out.println("DataFileCommon.printDataLinkValidation");
        SortedSet<Integer> validFileIds = new TreeSet<>();
        for(var file:fileList) validFileIds.add(file.getMetadata().getIndex());
        final Map<Integer,Integer> goodFileCounts = new HashMap<>();
        final Map<Integer,Integer> missingFileCounts = new HashMap<>();
        index.stream()
                .mapToInt(DataFileCommon::fileIndexFromDataLocation)
                .forEach(fileIndex -> {
                    Map<Integer,Integer> map = validFileIds.contains(fileIndex) ? goodFileCounts : missingFileCounts;
                    if (map.containsKey(fileIndex)) {
                        map.put(fileIndex,map.get(fileIndex) + 1);
                    } else {
                        map.put(fileIndex,1);
                    }
                });

        goodFileCounts.forEach((id,count)->System.out.println(      "       good    file "+id+" has "+count+" references"));
        missingFileCounts.forEach((id,count)->System.out.println(   "       missing file "+id+" has "+count+" references"));
    }

    // =================================================================================================================
    // BufferTooSmallException

    /**
     * Special exception for when we are asked to read data and given a buffer that is too small
     */
    static class BufferTooSmallException extends IOException {
        /**
         * Constructs an BufferTooSmallException
         */
        public BufferTooSmallException(int expectedSize, int actualSize) {
            super("BufferTooSmallException needed "+expectedSize+" bytes but only had "+actualSize+" bytes.");
        }
    }
}
