/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.merkledb.files;

import static com.swirlds.merkledb.files.DataFileCommon.FOOTER_SIZE;
import static com.swirlds.merkledb.files.DataFileCommon.PAGE_SIZE;
import static com.swirlds.merkledb.files.DataFileCommon.createDataFilePath;
import static com.swirlds.merkledb.files.DataFileCommon.getLockFilePath;

import com.swirlds.merkledb.serialize.DataItemSerializer;
import com.swirlds.merkledb.settings.MerkleDbSettings;
import com.swirlds.merkledb.settings.MerkleDbSettingsFactory;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Arrays;
import sun.misc.Unsafe;

/**
 * Writer for creating a data file. A data file contains a number of data items. Each data item can
 * be variable or fixed size and is considered as a black box. All access to contents of the data
 * item is done via the DataItemSerializer.
 *
 * <b>This is designed to be used from a single thread.</b>
 *
 * At the end of the file it is padded till a 4096 byte page boundary then a footer page is
 * written by DataFileMetadata.
 *
 * @param <D> Data item type
 */
public final class DataFileWriter<D> {
    /**
     * Since {@code com.swirlds.platform.Browser} populates settings, and it is loaded before any
     * application classes that might instantiate a data source, the {@link MerkleDbSettingsFactory}
     * holder will have been configured by the time this static initializer runs.
     */
    private static final MerkleDbSettings settings = MerkleDbSettingsFactory.get();

    /** Mapped buffer size */
    private static final int MMAP_BUF_SIZE = PAGE_SIZE * 1024 * 4;

    /**
     * The file channel we are writing to. The channel isn't used directly to write bytes, but to
     * create mapped byte buffers.
     */
    private FileChannel writingChannel;
    /**
     * The current mapped byte buffer used for writing. When overflowed, it is released, and another
     * buffer is mapped from the file channel.
     */
    private MappedByteBuffer writingMmap;
    /**
     * Offset, in bytes, of the current mapped byte buffer in the file channel. After the file is
     * completely written and closed, this field value is equal to the file size.
     */
    private long mmapPositionInFile = 0;
    /** Serializer for converting raw data to/from data items */
    private final DataItemSerializer<D> dataItemSerializer;
    /** file index number */
    private final int index;
    /**
     * The moment in time when the file should be considered as existing for. When we merge files,
     * we take the newest timestamp of the set of merge files and give it to this new file.
     */
    private final Instant creationInstant;
    /** The path to the data file we are writing */
    private final Path path;
    /** The path to the lock file for data file we are writing */
    private final Path lockFilePath;
    /** File metadata */
    private final DataFileMetadata metadata;
    /**
     * Count of the number of data items we have written so far. Ready to be stored in footer
     * metadata
     */
    private long dataItemCount = 0;

    /** Access to sun.misc.Unsafe required for atomic compareAndSwapLong on off-heap memory */
    private static final Unsafe UNSAFE;

    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (Unsafe) f.get(null);
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
            throw new InternalError(e);
        }
    }

    /**
     * Create a new data file in the given directory, in append mode. Puts the object into "writing"
     * mode (i.e. creates a lock file. So you'd better start writing data and be sure to finish it
     * off).
     *
     * @param filePrefix string prefix for all files, must not contain "_" chars
     * @param dataFileDir the path to directory to create the data file in
     * @param index the index number for this file
     * @param dataItemSerializer Serializer for converting raw data to/from data items
     * @param creationTime the time stamp for the creation time for this file
     */
    public DataFileWriter(
            final String filePrefix,
            final Path dataFileDir,
            final int index,
            final DataItemSerializer<D> dataItemSerializer,
            final Instant creationTime)
            throws IOException {
        this.index = index;
        this.dataItemSerializer = dataItemSerializer;
        this.creationInstant = creationTime;
        this.path = createDataFilePath(filePrefix, dataFileDir, index, creationInstant);
        this.lockFilePath = getLockFilePath(path);
        if (Files.exists(lockFilePath)) {
            throw new IOException("Tried to start writing to data file [" + path + "] when lock file already existed");
        }
        metadata = new DataFileMetadata(
                DataFileCommon.FILE_FORMAT_VERSION,
                dataItemSerializer.getSerializedSize(),
                0, // data item count will be updated later in finishWriting()
                index,
                creationInstant,
                dataItemSerializer.getCurrentDataVersion());
        writingChannel = FileChannel.open(
                path, StandardOpenOption.CREATE_NEW, StandardOpenOption.READ, StandardOpenOption.WRITE);
        moveMmapBuffer(0);
        Files.createFile(lockFilePath);
    }

    /**
     * Maps the writing byte buffer to the given position in the file. Byte buffer size is always
     * {@link #MMAP_BUF_SIZE}. Previous mapped byte buffer, if not null, is released.
     *
     * @param currentMmapPos new mapped byte buffer position in the file, in bytes
     * @throws IOException if I/O error(s) occurred
     */
    private void moveMmapBuffer(final int currentMmapPos) throws IOException {
        mmapPositionInFile += currentMmapPos;
        closeMmapBuffer();
        writingMmap = writingChannel.map(MapMode.READ_WRITE, mmapPositionInFile, MMAP_BUF_SIZE);
    }

    /** Closes (unmaps) the current mapped byte buffer used to write bytes, if not null. */
    private void closeMmapBuffer() {
        if (writingMmap != null) {
            UNSAFE.invokeCleaner(writingMmap);
            writingMmap = null;
        }
    }

    /**
     * Get the number of bytes written so far plus footer size. Tells you what the size of the file
     * would be at this moment in time if you were to close it now.
     *
     * If this method is called after {@link #finishWriting()}, it returns the total file size.
     */
    public long getFileSizeEstimate() {
        if (writingMmap == null) {
            // Done with writing, return mmapPositionInFile, which is equal to the file size
            return mmapPositionInFile;
        }
        // Current mmap offset + position in mmap buffer + padding + footer
        return mmapPositionInFile + writingMmap.position() + computePaddingLength() + FOOTER_SIZE;
    }

    /** Get the path for the file being written. Useful when needing to get a reader to the file. */
    public Path getPath() {
        return path;
    }

    /**
     * Get file metadata for the written file.
     *
     * @return data file metadata
     */
    public DataFileMetadata getMetadata() {
        return metadata;
    }

    /**
     * Write a data item copied from another file like during merge. The data item serializer
     * copyItem() method will be called to give it a chance to pass the data for or upgrade the
     * serialization as needed.
     *
     * @param serializedVersion the serialization version the item was written with
     * @param dataItemData ByteBuffer containing the item's data, it is assumed
     *     dataItemData.remaining() is the amount of data to write.
     * @return New data location in this file where it was written
     * @throws IOException If there was a problem writing the data item
     */
    public synchronized long writeCopiedDataItem(final long serializedVersion, final ByteBuffer dataItemData)
            throws IOException {
        // capture the current write position for beginning of data item
        final int currentWritingMmapPos = writingMmap.position();
        final long byteOffset = mmapPositionInFile + currentWritingMmapPos;
        // capture the current read position in the data item data buffer
        final int currentDataItemPos = dataItemData.position();
        try {
            dataItemSerializer.copyItem(serializedVersion, dataItemData.remaining(), dataItemData, writingMmap);
        } catch (final BufferOverflowException e) {
            // Buffer overflow indicates the current writing mapped byte buffer needs to be
            // mapped to a new location
            moveMmapBuffer(currentWritingMmapPos);
            // Reset dataItemData buffer position and retry
            dataItemData.position(currentDataItemPos);
            try {
                dataItemSerializer.copyItem(serializedVersion, dataItemData.remaining(), dataItemData, writingMmap);
            } catch (final BufferOverflowException t) {
                // If still a buffer overflow, it means the mapped buffer is smaller than even a single
                // data item
                throw new IOException(
                        "Data item is too large to write to a data file. Increase data file"
                                + "mapped byte buffer size",
                        e);
            }
        }
        dataItemCount++;
        // return the offset where we wrote the data
        return DataFileCommon.dataLocation(index, byteOffset);
    }

    /**
     * Store data item in file returning location it was stored at.
     *
     * @param dataItem the data item to write
     * @return the data location of written data in bytes
     * @throws IOException if there was a problem appending data to file
     */
    public synchronized long storeDataItem(final D dataItem) throws IOException {
        // find offset for the start of this new data item, we assume we always write data in a
        // whole number of blocks
        final int currentWritingMmapPos = writingMmap.position();
        final long byteOffset = mmapPositionInFile + currentWritingMmapPos;
        // write serialized data
        try {
            dataItemSerializer.serialize(dataItem, writingMmap);
        } catch (final BufferOverflowException e) {
            // Buffer overflow indicates the current writing mapped byte buffer needs to be
            // mapped to a new location and retry
            moveMmapBuffer(currentWritingMmapPos);
            try {
                dataItemSerializer.serialize(dataItem, writingMmap);
            } catch (final BufferOverflowException t) {
                // If still a buffer overflow, it means the mapped buffer is smaller than even a single
                // data item
                throw new IOException(
                        "Data item is too large to write to a data file. Increase data file"
                                + "mapped byte buffer size",
                        e);
            }
        }
        // increment data item counter
        dataItemCount++;
        // return the offset where we wrote the data
        return DataFileCommon.dataLocation(index, byteOffset);
    }

    /** A helper method to write a byte buffer to the file. */
    private void writeBytes(final ByteBuffer data) throws IOException {
        final int needToWrite = data.remaining();
        if (needToWrite > writingMmap.remaining()) {
            final int currentWritingMmapPos = writingMmap.position();
            moveMmapBuffer(currentWritingMmapPos);
        }
        // Assuming the data is small enough to fit into the mmaped writing buffer
        writingMmap.put(data);
    }

    /**
     * When you finished append to a new file, call this to seal the file and make it read only for
     * reading.
     *
     * @throws IOException if there was a problem sealing file or opening again as read only
     */
    public synchronized void finishWriting() throws IOException {
        // pad the end of file till we are a whole number of pages
        int paddingBytesNeeded = computePaddingLength();
        final ByteBuffer paddingBuf = ByteBuffer.allocate(paddingBytesNeeded);
        Arrays.fill(paddingBuf.array(), (byte) 0);
        writeBytes(paddingBuf);
        // update data item count in the metadata
        metadata.setDataItemCount(dataItemCount);
        // write any metadata to end of file.
        final ByteBuffer footerData = metadata.getFooterForWriting();
        writeBytes(footerData);
        // truncate to the right size
        final long totalFileSize = mmapPositionInFile + writingMmap.position();
        writingChannel.truncate(totalFileSize);
        // after finishWriting(), mmapPositionInFile should be equal to the file size
        mmapPositionInFile = totalFileSize;
        // release all the resources
        closeMmapBuffer();
        writingChannel.force(true);
        writingChannel.close();
        writingChannel = null;
        // delete lock file
        Files.delete(lockFilePath);
    }

    /**
     * Compute the amount of padding needed to append at the end of file to push the metadata footer
     * so that it sits on a page boundary for fast random access reading later.
     */
    private int computePaddingLength() {
        final long writePosition = mmapPositionInFile + writingMmap.position();
        return (int) (PAGE_SIZE - (writePosition % PAGE_SIZE)) % PAGE_SIZE;
    }
}
