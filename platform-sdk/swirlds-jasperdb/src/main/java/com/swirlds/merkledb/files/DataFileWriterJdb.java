/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static com.swirlds.merkledb.files.DataFileCommon.PAGE_SIZE;

import com.swirlds.merkledb.serialize.DataItemSerializer;
import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Arrays;

/**
 * Writer for creating a data file. A data file contains a number of data items. Each data item can
 * be variable or fixed size and is considered as a black box. All access to contents of the data
 * item is done via the DataItemSerializer.
 *
 * <p><b>This is designed to be used from a single thread.</b>
 *
 * <p>At the end of the file it is padded till a 4096 byte page boundary then a footer page is
 * written by DataFileMetadata.
 *
 * @param <D> Data item type
 */
// Future work: remove this class after JDB format support is no longer needed
// https://github.com/hashgraph/hedera-services/issues/8344
public final class DataFileWriterJdb<D> extends DataFileWriterPbj<D> {

    /** Mapped buffer size */
    private static final int MMAP_BUF_SIZE = PAGE_SIZE * 1024 * 4;

    /** File metadata, masks the field from super class */
    private final DataFileMetadataJdb metadata;

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
    public DataFileWriterJdb(
            final String filePrefix,
            final Path dataFileDir,
            final int index,
            final DataItemSerializer<D> dataItemSerializer,
            final Instant creationTime,
            final int compactionLevel)
            throws IOException {
        super(
                filePrefix,
                dataFileDir,
                index,
                dataItemSerializer,
                creationTime,
                compactionLevel,
                DataFileCommon.FILE_EXTENSION_JDB);
        metadata = new DataFileMetadataJdb(
                0, // data item count will be updated later in finishWriting()
                index,
                creationTime,
                dataItemSerializer.getCurrentDataVersion(),
                compactionLevel);
        moveMmapBuffer(0);
    }

    /**
     * Maps the writing byte buffer to the given position in the file. Byte buffer size is always
     * {@link #MMAP_BUF_SIZE}. Previous mapped byte buffer, if not null, is released.
     *
     * @param currentMmapPos new mapped byte buffer position in the file, in bytes
     * @throws IOException if I/O error(s) occurred
     */
    private void moveMmapBuffer(final int currentMmapPos) throws IOException {
        try (final FileChannel channel = FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            final MappedByteBuffer newMap =
                    channel.map(MapMode.READ_WRITE, mmapPositionInFile + currentMmapPos, MMAP_BUF_SIZE);
            // theoretically it's possible, we should check it
            if (newMap == null) {
                throw new IOException("Failed to map file channel to memory");
            }
            if (writingMmap != null) {
                DataFileCommon.closeMmapBuffer(writingMmap);
            }
            writingMmap = newMap;
            mmapPositionInFile += currentMmapPos;
        }
    }

    protected void writeHeader() {
        // no op
    }

    @Override
    public DataFileType getFileType() {
        return DataFileType.JDB;
    }

    /**
     * Get file metadata for the written file.
     *
     * @return data file metadata
     */
    public DataFileMetadataJdb getMetadata() {
        // return this.metadata, not super.metadata
        return metadata;
    }

    @Override
    public synchronized long writeCopiedDataItem(final Object dataItemData) throws IOException {
        if (!(dataItemData instanceof ByteBuffer jdbData)) {
            throw new IllegalArgumentException("Data item data buffer type mismatch");
        }
        // capture the current write position for beginning of data item
        final int currentWritingMmapPos = writingMmap.position();
        final long byteOffset = mmapPositionInFile + currentWritingMmapPos;
        // capture the current read position in the data item data buffer
        final int currentDataItemPos = jdbData.position();
        try {
            writingMmap.put(jdbData);
        } catch (final BufferOverflowException e) {
            // Buffer overflow indicates the current writing mapped byte buffer needs to be
            // mapped to a new location
            moveMmapBuffer(currentWritingMmapPos);
            // Reset dataItemData buffer position and retry
            jdbData.position(currentDataItemPos);
            try {
                writingMmap.put(jdbData);
            } catch (final BufferOverflowException t) {
                // If still a buffer overflow, it means the mapped buffer is smaller than even a single
                // data item
                throw new IOException(DataFileCommon.ERROR_DATAITEM_TOO_LARGE, e);
            }
        }
        dataItemCount++;
        // return the offset where we wrote the data
        return DataFileCommon.dataLocation(metadata.getIndex(), byteOffset);
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
                throw new IOException(DataFileCommon.ERROR_DATAITEM_TOO_LARGE, e);
            }
        }
        // increment data item counter
        dataItemCount++;
        // return the offset where we wrote the data
        return DataFileCommon.dataLocation(metadata.getIndex(), byteOffset);
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
        // release all the resources
        DataFileCommon.closeMmapBuffer(writingMmap);

        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            channel.truncate(totalFileSize);
            // after finishWriting(), mmapPositionInFile should be equal to the file size
            mmapPositionInFile = totalFileSize;
        }
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
