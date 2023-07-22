/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import static com.hedera.pbj.runtime.ProtoParserTools.TAG_FIELD_OFFSET;
import static com.swirlds.merkledb.files.DataFileCommon.FIELD_DATAFILE_ITEMS;
import static com.swirlds.merkledb.files.DataFileCommon.PAGE_SIZE;
import static com.swirlds.merkledb.utilities.ProtoUtils.WIRE_TYPE_DELIMITED;

import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.swirlds.merkledb.collections.IndexedObject;
import com.swirlds.merkledb.serialize.DataItemSerializer;
import com.swirlds.merkledb.utilities.ProtoUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * The aim for a DataFileReader is to facilitate fast highly concurrent random reading of items from
 * a data file. It is designed to be used concurrently from many threads.
 *
 * @param <D> Data item type
 */
@SuppressWarnings({"DuplicatedCode", "NullableProblems"})
// Future work: make it final again after DataFileReaderJdb is dropped
public class DataFileReaderPbj<D> implements DataFileReader<D> {

    private static final int MMAP_BUF_SIZE = PAGE_SIZE * 1024 * 16;
    private static final long MAX_FILE_SIZE = 32L * 1024 * 1024 * 1024;
    /** FileChannel's for each thread */
    private static final ThreadLocal<BufferedData> BUFFER_CACHE = new ThreadLocal<>();
    /**
     */
    private FileChannel fileChannel;

    private final AtomicReferenceArray<MappedByteBuffer> readingMmaps =
            new AtomicReferenceArray<>(Math.toIntExact(MAX_FILE_SIZE / MMAP_BUF_SIZE * 2));
    private final AtomicReferenceArray<BufferedData> readingBufs =
            new AtomicReferenceArray<>(Math.toIntExact(MAX_FILE_SIZE / MMAP_BUF_SIZE * 2));

    private final List<MappedByteBuffer> mmapsToClean = Collections.synchronizedList(new ArrayList<>());

    /** Indicates whether this file reader is open */
    private final AtomicBoolean open = new AtomicBoolean(true);
    /** The path to the file on disk */
    // Future work: make it back private
    protected final Path path;
    /** The metadata for this file read from the footer */
    // Future work: make it back private
    protected final DataFileMetadata metadata;
    /** Serializer for converting raw data to/from data items */
    // Future work: make it back private
    protected final DataItemSerializer<D> dataItemSerializer;
    /** A flag for if the underlying file is fully written and ready to be compacted. */
    private final AtomicBoolean fileCompleted = new AtomicBoolean(false);
    /**
     * The size of this file in bytes, cached as need it often. This size is updated in {@link
     * #setFileCompleted()}, which is called for existing files right after the reader is created,
     * and for newly created files right after they are fully written and available to compact.
     */
    private final AtomicLong fileSizeBytes = new AtomicLong(0);

    /**
     * Open an existing data file, reading the metadata from the file
     *
     * @param path the path to the data file
     * @param dataItemSerializer Serializer for converting raw data to/from data items
     */
    public DataFileReaderPbj(final Path path, final DataItemSerializer<D> dataItemSerializer) throws IOException {
        this(path, dataItemSerializer, new DataFileMetadata(path));
    }

    /**
     * Open an existing data file, using the provided metadata
     *
     * @param path the path to the data file
     * @param dataItemSerializer Serializer for converting raw data to/from data items
     * @param metadata the file's metadata to save loading from file
     */
    public DataFileReaderPbj(
            final Path path, final DataItemSerializer<D> dataItemSerializer, final DataFileMetadata metadata)
            throws IOException {
        if (!Files.exists(path)) {
            throw new IllegalArgumentException(
                    "Tried to open a non existent data file [" + path.toAbsolutePath() + "].");
        }
        this.path = path;
        this.metadata = metadata;
        this.dataItemSerializer = dataItemSerializer;
        openNewFileChannel();
    }

    @Override
    public DataFileType getFileType() {
        return DataFileType.PBJ;
    }

    /**
     * Returns if this file is completed and ready to be compacted.
     *
     * @return if true the file is completed (read only and ready to compact)
     */
    public boolean isFileCompleted() {
        return fileCompleted.get();
    }

    /**
     * Marks the reader as completed, so it can be included into future compactions. If the reader
     * is created for an existing file, it's usually marked as completed immediately. If the reader
     * is created for a new file, which is still being written in a different thread, it's marked as
     * completed right after the file is fully written and the writer is closed.
     */
    public void setFileCompleted() {
        try {
            fileSizeBytes.set(fileChannel.size());
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to update data file reader size", e);
        } finally {
            fileCompleted.set(true);
        }
    }

    /**
     * Get file index, the index is an ordered integer identifying the file in a set of files
     *
     * @return this file's index
     */
    @Override
    public int getIndex() {
        return metadata.getIndex();
    }

    /** Get the files metadata */
    public DataFileMetadata getMetadata() {
        return metadata;
    }

    /** Get the path to this data file */
    public Path getPath() {
        return path;
    }

    /**
     * Create an iterator to iterate over the data items in this data file. It opens its own file
     * handle so can be used in a separate thread. It must therefore be closed when you are finished
     * with it.
     *
     * @return new data item iterator
     * @throws IOException if there was a problem creating a new DataFileIterator
     */
    @Override
    public DataFileIterator createIterator() throws IOException {
        return new DataFileIteratorPbj(path, metadata, dataItemSerializer);
    }

    /**
     * Read data item bytes from file at dataLocation and deserialize them into the Java object, if
     * requested.
     *
     * @param dataLocation The file index combined with the offset for the starting block of the
     *     data in the file
     * @return Deserialized data item, or {@code null} if deserialization is not requested
     * @throws IOException If there was a problem reading from data file
     * @throws ClosedChannelException if the data file was closed
     */
    @Override
    public D readDataItem(final long dataLocation) throws IOException {
        final long byteOffset = DataFileCommon.byteOffsetFromDataLocation(dataLocation);
        final BufferedData data = read(byteOffset);
        return dataItemSerializer.deserialize(data);
    }

    @Override
    public Object readDataItemBytes(final long dataLocation) throws IOException {
        final long byteOffset = DataFileCommon.byteOffsetFromDataLocation(dataLocation);
        return read(byteOffset);
    }

    /**
     * Get the size of this file in bytes. This method should only be called for files available to
     * merging (compaction), i.e. after they are fully written.
     *
     * @return file size in bytes
     */
    public long getSize() {
        return fileSizeBytes.get();
    }

    /** Equals for use when comparing in collections, based on matching file paths */
    @SuppressWarnings("rawtypes")
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DataFileReader that = (DataFileReader) o;
        return path.equals(that.getPath());
    }

    /** hashCode for use when comparing in collections, based on file path */
    @Override
    public int hashCode() {
        return path.hashCode();
    }

    /** Compares this Data File to another based on creation date and index */
    @Override
    public int compareTo(final DataFileReader o) {
        Objects.requireNonNull(o);
        if (this == o) {
            return 0;
        }
        final int res = metadata.getCreationDate().compareTo(o.getMetadata().getCreationDate());
        return (res != 0)
                ? res
                : Integer.compare(metadata.getIndex(), o.getMetadata().getIndex());
    }

    /** ToString for debugging */
    @Override
    public String toString() {
        return Integer.toString(metadata.getIndex());
    }

    /**
     * Get if the DataFile is open for reading.
     *
     * @return True if file is open for reading
     */
    public boolean isOpen() {
        return open.get();
    }

    /** Close this data file, it can not be used once closed. */
    public void close() throws IOException {
        open.set(false);
        fileChannel.close();
        for (final MappedByteBuffer mmap : mmapsToClean) {
            DataFileCommon.closeMmapBuffer(mmap);
        }
    }

    // =================================================================================================================
    // Private methods

    private BufferedData getReadBuffer(final int index) throws IOException {
        final long bufOffset = (long) index * (MMAP_BUF_SIZE / 2);
        final long bufSize = Math.min(MMAP_BUF_SIZE, fileChannel.size() - bufOffset);
        MappedByteBuffer buf = readingMmaps.get(index);
        if ((buf == null) || (buf.capacity() < bufSize)) {
            final MappedByteBuffer newBuf = fileChannel.map(MapMode.READ_ONLY, bufOffset, bufSize);
            if (readingMmaps.compareAndSet(index, buf, newBuf)) {
                mmapsToClean.add(newBuf);
                buf = newBuf;
            } else {
                DataFileCommon.closeMmapBuffer(newBuf);
                buf = readingMmaps.get(index);
            }
        }
        BufferedData readBuf = readingBufs.get(index);
        if ((readBuf == null) || (readBuf.capacity() != buf.capacity())) {
            final BufferedData newReadBuf = BufferedData.wrap(buf);
            if (readingBufs.compareAndSet(index, readBuf, newReadBuf)) {
                readBuf = newReadBuf;
            } else {
                readBuf = readingBufs.get(index);
            }
        }
        return readBuf;
    }

    private void openNewFileChannel() throws IOException {
        assert (fileChannel == null) || !fileChannel.isOpen();
        fileChannel = FileChannel.open(path, StandardOpenOption.READ);
    }

    /**
     * Read bytesToRead bytes of data from the file starting at byteOffsetInFile unless we reach the
     * end of file. If we reach the end of file then returned buffer's limit will be set to the
     * number of bytes read and be less than bytesToRead.
     *
     * @param byteOffsetInFile Offset to start reading at
     * @return ByteBuffer containing read data. This is a reused per thread buffer, so you can use
     *     it till your thread calls read again.
     * @throws IOException if there was a problem reading
     * @throws ClosedChannelException if the file was closed
     */
    private BufferedData read(final long byteOffsetInFile) throws IOException {
        final int bufIndex = Math.toIntExact(byteOffsetInFile / (MMAP_BUF_SIZE / 2));
        // Try a few times. It's very unlikely (other than in tests) that a thread is
        // interrupted more than once in short period of time, so 2 retries should be enough
        final long mmapOffset = (long) bufIndex * (MMAP_BUF_SIZE / 2);
        for (int retries = 2; retries > 0; retries--) {
            try {
                final BufferedData in = getReadBuffer(bufIndex);
                final int tag = in.getVarInt(byteOffsetInFile - mmapOffset, false); // tag
                assert tag == ((FIELD_DATAFILE_ITEMS.number() << TAG_FIELD_OFFSET) | WIRE_TYPE_DELIMITED);
                final int sizeOfTag = ProtoUtils.sizeOfUnsignedVarInt32(tag);
                final int size = in.getVarInt(byteOffsetInFile + sizeOfTag - mmapOffset, false);
                if (size >= MMAP_BUF_SIZE / 2) {
                    throw new UnsupportedOperationException("Data item is too large");
                }
                final int sizeOfSize = ProtoUtils.sizeOfUnsignedVarInt32(size);
                BufferedData buf = BUFFER_CACHE.get();
                if ((buf == null) || (buf.capacity() < size)) {
                    buf = BufferedData.allocate(size);
                    BUFFER_CACHE.set(buf);
                }
                buf.position(0);
                buf.limit(size);
                in.getBytes(byteOffsetInFile + sizeOfTag + sizeOfSize - mmapOffset, buf);
//                buf.flip(); // TODO: why isn't buf position updated?
                return buf;
            } catch (final ClosedByInterruptException e) {
                // If the thread and the channel are interrupted, propagate it to the callers
                throw e;
            } catch (final ClosedChannelException e) {
                // This exception may be thrown, if the channel was closed, because a different
                // thread reading from the channel was interrupted. Re-create the file channel
                // and retry
                openNewFileChannel();
            }
        }
        throw new IOException("Failed to read from file, file channel keeps getting closed");
    }
}
