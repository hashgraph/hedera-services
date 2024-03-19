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

import com.swirlds.merkledb.config.MerkleDbConfig;
import com.swirlds.merkledb.serialize.DataItemHeader;
import com.swirlds.merkledb.serialize.DataItemSerializer;
import com.swirlds.merkledb.utilities.MerkleDbFileUtils;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

/**
 * The aim for a DataFileReader is to facilitate fast highly concurrent random reading of items from
 * a data file. It is designed to be used concurrently from many threads.
 *
 * @param <D> Data item type
 */
// Future work: drop this class after all files are migrated to protobuf format
// See https://github.com/hashgraph/hedera-services/issues/8344 for details
public class DataFileReaderJdb<D> extends DataFileReaderPbj<D> {

    private static final ThreadLocal<ByteBuffer> BUFFER_CACHE = new ThreadLocal<>();

    /**
     * Open an existing data file, reading the metadata from the file
     *
     * @param dbConfig MerkleDb config
     * @param path the path to the data file
     * @param dataItemSerializer Serializer for converting raw data to/from data items
     */
    public DataFileReaderJdb(
            final MerkleDbConfig dbConfig, final Path path, final DataItemSerializer<D> dataItemSerializer)
            throws IOException {
        this(dbConfig, path, dataItemSerializer, new DataFileMetadataJdb(path));
    }

    /**
     * Open an existing data file, using the provided metadata
     *
     * @param dbConfig MerkleDb config
     * @param path the path to the data file
     * @param dataItemSerializer Serializer for converting raw data to/from data items
     * @param metadata the file's metadata to save loading from file
     */
    public DataFileReaderJdb(
            final MerkleDbConfig dbConfig,
            final Path path,
            final DataItemSerializer<D> dataItemSerializer,
            DataFileMetadataJdb metadata)
            throws IOException {
        super(dbConfig, path, dataItemSerializer, metadata);
        openNewFileChannel(0);
    }

    @Override
    public DataFileType getFileType() {
        return DataFileType.JDB;
    }

    @Override
    public DataFileIterator<D> createIterator() throws IOException {
        return new DataFileIteratorJdb<>(dbConfig, path, metadata, dataItemSerializer);
    }

    @Override
    public D readDataItem(final long dataLocation) throws IOException {
        final ByteBuffer data = (ByteBuffer) readDataItemBytes(dataLocation);
        return dataItemSerializer.deserialize(data, metadata.getSerializationVersion());
    }

    @Override
    public Object readDataItemBytes(final long dataLocation) throws IOException {
        long serializationVersion = metadata.getSerializationVersion();
        final long byteOffset = DataFileCommon.byteOffsetFromDataLocation(dataLocation);
        final int bytesToRead;
        if (dataItemSerializer.isVariableSize()) {
            // read header to get size
            final ByteBuffer serializedHeader = read(byteOffset, dataItemSerializer.getHeaderSize());
            if (serializedHeader == null) {
                // File is closed during compaction in a parallel thread
                return null;
            }
            final DataItemHeader header = dataItemSerializer.deserializeHeader(serializedHeader);
            bytesToRead = header.getSizeBytes();
        } else {
            bytesToRead = dataItemSerializer.getSerializedSizeForVersion(serializationVersion);
        }
        return read(byteOffset, bytesToRead);
    }

    // =================================================================================================================
    // Private methods

    /**
     * Read bytesToRead bytes of data from the file starting at byteOffsetInFile unless we reach the
     * end of file. If we reach the end of file then returned buffer's limit will be set to the
     * number of bytes read and be less than bytesToRead.
     *
     * @param byteOffsetInFile Offset to start reading at
     * @param bytesToRead Number of bytes to read
     * @return ByteBuffer containing read data. This is a reused per thread buffer, so you can use
     *     it till your thread calls read again.
     * @throws IOException if there was a problem reading
     * @throws ClosedChannelException if the file was closed
     */
    private ByteBuffer read(final long byteOffsetInFile, final int bytesToRead) throws IOException {
        // get or create cached buffer
        ByteBuffer buffer = BUFFER_CACHE.get();
        if (buffer == null || bytesToRead > buffer.capacity()) {
            buffer = ByteBuffer.allocate(bytesToRead);
            BUFFER_CACHE.set(buffer);
        }
        // Try a few times. It's very unlikely (other than in tests) that a thread is
        // interrupted more than once in short period of time, so 3 retries should be enough
        for (int retries = 3; retries > 0; retries--) {
            final int fcIndex = leaseFileChannel();
            final FileChannel fileChannel = fileChannels.get(fcIndex);
            if (fileChannel == null) {
                // On rare occasions, if we have a race condition with compaction, the file channel
                // may be closed. We need to return null, so that the caller can retry with a new reader
                return null;
            }
            try {
                buffer.position(0);
                buffer.limit(bytesToRead);
                // read data
                MerkleDbFileUtils.completelyRead(fileChannel, buffer, byteOffsetInFile);
                buffer.flip();
                return buffer;
            } catch (final ClosedByInterruptException e) {
                // If the thread and the channel are interrupted, propagate it to the callers
                throw e;
            } catch (final ClosedChannelException e) {
                // This exception may be thrown, if the channel was closed, because a different
                // thread reading from the channel was interrupted. Re-create the file channel
                // and retry
                reopenFileChannel(fcIndex, fileChannel);
            } finally {
                releaseFileChannel();
            }
        }
        throw new IOException("Failed to read from file, file channels keep getting closed");
    }
}
