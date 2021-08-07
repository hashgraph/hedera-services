package com.hedera.services.state.merkle.v3.files;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

import static com.hedera.services.state.merkle.v3.files.DataFileCommon.*;

/**
 * Writer for creating a data file. This is designed to be used from a single thread.
 *
 * A data files contains a number of data items. Each data item is written to the file like this:
 *
 * <ul>
 *     <li>integer - value data length in bytes (ONLY IF NOT IN FIXED SIZE MODE)</li>
 *     <li>long    - key data</li>
 *     <li>bytes   - value data</li>
 *     <li>bytes   - padding till end of nearest blockSize</li>
 * </ul>
 *
 * At the end of the file in last 4096 byte block is a footer created by DataFileMetadata.
 */
@SuppressWarnings("DuplicatedCode")
public final class DataFileWriter {
    private final BufferedOutputStream writingStream;
    private final boolean isMergeFile;
    private final int dataSize;
    private final boolean hasVariableDataSize;
    private final int index;
    private final Instant creationInstant;
    private final Path path;
    private final Path lockFilePath;
    private ByteBuffer tempWriteBuffer = null;
    private long writePosition = 0;
    private long dataItemCount = 0;

    /**
     * Create a new data file in the given directory, in append mode.
     *
     * @param filePrefix string prefix for all files, must not contain "_" chars
     * @param dataFileDir the path to directory to create the data file in
     * @param index the index number for this file
     * @param dataSize the size of data items, -1 if variable size
     * @param isMergeFile true if this is a merge file, false if it is a new data file that has not been merged
     */
    public DataFileWriter(String filePrefix, Path dataFileDir, int index, int dataSize, boolean isMergeFile) throws IOException {
        this.index = index;
        this.dataSize = dataSize;
        this.hasVariableDataSize = dataSize == VARIABLE_DATA_SIZE;
        this.isMergeFile = isMergeFile;
        this.creationInstant = Instant.now();
        this.path = createDataFilePath(filePrefix,dataFileDir,index, creationInstant);
        this.lockFilePath = getLockFilePath(path);
        if (Files.exists(lockFilePath)) throw new IOException("Tried to start writing to data file ["+path+"] when lock file already existed");
        writingStream = new BufferedOutputStream(Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND), 4*1024*1024);
        Files.createFile(lockFilePath);
    }

    /**
     * Get the number of bytes written so far plus footer size.
     */
    public long getFileSizeEstimate() {
        int paddingBytesNeeded = (int)(DataFileCommon.PAGE_SIZE - (writePosition % DataFileCommon.PAGE_SIZE));
        return writePosition + paddingBytesNeeded + FOOTER_SIZE;
    }

    /**
     * Get the path for the file being written
     */
    public Path getPath() {
        return path;
    }

    /**
     * Store data in provided ByteBuffer from its current position to its limit.
     *
     * @param key a long key
     * @param dataToStore buffer containing data to be written, must be rewound
     * @return the data location of written data in bytes
     * @throws IOException If there was a problem appending data to file
     */
    public synchronized long storeData(long key, ByteBuffer dataToStore) throws IOException {
        final int dataValueSize,dataSize;
        final ByteBuffer tempWriteBuffer;
        if (hasVariableDataSize) {
            dataValueSize = dataToStore.remaining();
            dataSize = Integer.BYTES + DataFileCommon.KEY_SIZE + dataValueSize;
            tempWriteBuffer = getTempWriteBuffer(dataSize);
            // write data size
            tempWriteBuffer.putInt(dataValueSize);
        } else {
            dataValueSize = this.dataSize;
            if (dataToStore.remaining() != dataValueSize) {
                throw new BufferTooSmallException(dataValueSize,dataToStore.remaining());
            }
            dataSize = DataFileCommon.KEY_SIZE + dataValueSize;
            tempWriteBuffer = getTempWriteBuffer(dataSize);
        }
        // find offset for the start of this new data item, we assume we always write data in a whole number of blocks
        final long byteOffset = writePosition;
        writePosition += dataSize;
        // write key and value
        tempWriteBuffer.putLong(key);
        tempWriteBuffer.put(dataToStore);
        // write temp buffer to file
        tempWriteBuffer.flip();
        writingStream.write(tempWriteBuffer.array(),tempWriteBuffer.position(), tempWriteBuffer.limit() -tempWriteBuffer.position());
        // increment data item counter
        dataItemCount ++;
        // return the offset where we wrote the data
        return DataFileCommon.dataLocation(index,byteOffset);
    }

    /**
     * Store data in provided ByteBuffer from its current position to its limit. If the file has variable size data then
     * the buffer should contain:
     *
     *  - Int value size
     *  - Long key
     *  - Data Value bytes
     *
     *  If it is not variable size then it should contain just the key and data value with correct fixed size.
     *
     * @param blockBuffer buffer containing whole data item to be written
     * @return the data location of written data in bytes
     * @throws IOException If there was a problem appending data to file
     */
    public synchronized long storeData(ByteBuffer blockBuffer) throws IOException {
        if (!hasVariableDataSize && (blockBuffer.remaining() != (dataSize+KEY_SIZE))) {
            throw new BufferTooSmallException(dataSize+KEY_SIZE,blockBuffer.remaining());
        }
        final int dataSize = hasVariableDataSize ? blockBuffer.remaining() : KEY_SIZE+this.dataSize;
        final long byteOffset = writePosition;
        writePosition += dataSize;
        // write temp buffer to file
        writingStream.write(blockBuffer.array(),blockBuffer.position(), blockBuffer.limit() -blockBuffer.position());
        // increment data item counter
        dataItemCount ++;
        // return the offset where we wrote the data
        return DataFileCommon.dataLocation(index,byteOffset);
    }

    /**
     * When you finished append to a new file, call this to seal the file and make it read only for reading.
     *
     * @throws IOException If there was a problem sealing file or opening again as read only
     */
    public synchronized DataFileMetadata finishWriting(long minimumValidKey, long maximumValidKey) throws IOException {
        // pad the end of file till we are a whole number of pages
        final ByteBuffer tempWriteBuffer = getTempWriteBuffer(DataFileCommon.PAGE_SIZE);
        int paddingBytesNeeded = (int)(DataFileCommon.PAGE_SIZE - (writePosition % DataFileCommon.PAGE_SIZE));
        tempWriteBuffer.position(0);
        for (int i = 0; i < paddingBytesNeeded; i++)  tempWriteBuffer.put((byte)0);
        tempWriteBuffer.flip();
        writingStream.write(tempWriteBuffer.array(),tempWriteBuffer.position(), paddingBytesNeeded);
        writePosition += paddingBytesNeeded;
        // write any metadata to end of file.
        DataFileMetadata metadataFooter = new DataFileMetadata(DataFileCommon.FILE_FORMAT_VERSION,dataSize,
                dataItemCount,index, creationInstant,minimumValidKey,maximumValidKey,isMergeFile);
        ByteBuffer footerData = metadataFooter.getFooterForWriting();
        // write footer to file
        writingStream.write(footerData.array(),footerData.position(), footerData.limit() -footerData.position());
        // close and reopen file as read only
        writingStream.flush();
        writingStream.close();
        // delete lock file
        Files.delete(lockFilePath);
        // return metadata
        return metadataFooter;
    }

    /**
     * Get a temporary byte buffer we can use for writing
     */
    private ByteBuffer getTempWriteBuffer(int size) {
        if (tempWriteBuffer == null || tempWriteBuffer.limit() < size) {
            tempWriteBuffer = ByteBuffer.allocate(size);
        } else {
            tempWriteBuffer.clear();
        }
        tempWriteBuffer.limit(size);
        return tempWriteBuffer;
    }
}
