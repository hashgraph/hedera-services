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

package com.swirlds.merkledb.collections;

import static com.swirlds.common.utility.Units.MEBIBYTES_TO_BYTES;
import static com.swirlds.merkledb.utilities.MerkleDbFileUtils.readFromFileChannel;

import com.swirlds.merkledb.files.DataFileCommon;
import com.swirlds.merkledb.utilities.MerkleDbFileUtils;
import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.LongStream;
import java.util.stream.StreamSupport;

/**
 * A simple, random access list of <b>non-zero</b> longs designed to allow lock-free concurrency
 * control. Unlike a {@link java.util.List}, the size of a {@link LongList} can exceed {@code
 * Integer.MAX_VALUE}.
 *
 * <p>Zero is treated as a sentinel value, marking indexes that have never been used with a {@code
 * put()} call.
 *
 * <p>Implementations should support both concurrent reads and writes. Writing to an index beyond
 * the current capacity of the list (but less than the max capacity) should <b>not</b> fail, but
 * instead trigger an automatic expansion of the list's capacity. Thus a {@link LongList} behaves
 * more like a long-to-long map than a traditional list.
 */
public abstract class LongList implements CASableLongIndex, Closeable {
    /** A suitable default for the maximum number of longs that may be stored (32GB of longs). */
    protected static final long DEFAULT_MAX_LONGS_TO_STORE = 4_000_000_000L;
    /** The maximum number of longs to store per chunk. */
    protected static final int MAX_NUM_LONGS_PER_CHUNK = Math.toIntExact(16_000L * (MEBIBYTES_TO_BYTES / Long.BYTES));
    /** A suitable default for the number of longs to store per chunk. */
    protected static final int DEFAULT_NUM_LONGS_PER_CHUNK = Math.toIntExact(8L * (MEBIBYTES_TO_BYTES / Long.BYTES));
    /** Initial file format*/
    private static final int INITIAL_VERSION = 1;
    /** File format that supports min valid index */
    private static final int MIN_VALID_INDEX_SUPPORT_VERSION = 2;
    /** The version number for format of current data files */
    private static final int CURRENT_FILE_FORMAT_VERSION = MIN_VALID_INDEX_SUPPORT_VERSION;
    /** The number of bytes required to store file version */
    protected static final int VERSION_METADATA_SIZE = Integer.BYTES;
    /** The number of bytes to read for format metadata, v1: <br>
     * - number of longs per chunk<br>
     * - max index that can be stored<br>
     * - max number of longs supported by the list<br>
     */
    protected static final int FORMAT_METADATA_SIZE_V1 = Integer.BYTES + Long.BYTES + Long.BYTES;
    /** The number of bytes to read for format metadata, v2:
     * - number of longs per chunk<br>
     * - max number of longs supported by the list<br>
     * - min valid index<br>
     */
    protected static final int FORMAT_METADATA_SIZE_V2 = Integer.BYTES + Long.BYTES + Long.BYTES;
    /** The number for bytes to read for file header, v1 */
    protected static final int FILE_HEADER_SIZE_V1 = VERSION_METADATA_SIZE + FORMAT_METADATA_SIZE_V1;
    /** The number for bytes to read for file header, v2 */
    protected static final int FILE_HEADER_SIZE_V2 = VERSION_METADATA_SIZE + FORMAT_METADATA_SIZE_V2;
    /** File header size for the latest format */
    protected final int currentFileHeaderSize;

    /**
     * A LongList may not contain the non-existent data location, which is used as a sentinel for a
     * never-set index.
     */
    public static final long IMPERMISSIBLE_VALUE = DataFileCommon.NON_EXISTENT_DATA_LOCATION;
    /**
     * The number of longs to store in each allocated buffer. Must be a positive integer. If the
     * value is small, then we will end up allocating a very large number of buffers. If the value
     * is large, then we will waste a lot of memory in the unfilled buffer.
     */
    protected final int numLongsPerChunk;
    /** Size in bytes for each memory chunk to allocate */
    protected final int memoryChunkSize;
    /** The number of longs that this list would contain if it was not optimized by {@link LongList#updateMinValidIndex}.
     * Practically speaking, it defines the list's right boundary. */
    protected final AtomicLong size = new AtomicLong(0);
    /**
     * The maximum number of longs to ever store in this data structure. This is used as a safety
     * measure to make sure no bug causes an out of memory issue by causing us to allocate more
     * buffers than the system can handle.
     */
    protected final long maxLongs;
    /** The file channel for this LongList's data if it was loaded from a file. */
    protected FileChannel fileChannel;

    /** Min valid index of the list. All the indices to the left of this index have {@code IMPERMISSIBLE_VALUE}-s */
    protected final AtomicLong minValidIndex = new AtomicLong(0);

    /**
     * Construct a new LongList with the specified number of longs per chunk and maximum number of
     * longs.
     *
     * @param numLongsPerChunk number of longs to store in each chunk of memory allocated
     * @param maxLongs the maximum number of longs permissible for this LongList
     */
    protected LongList(final int numLongsPerChunk, final long maxLongs) {
        if (maxLongs < 0) {
            throw new IllegalArgumentException("The maximum number of longs must be non-negative, not " + maxLongs);
        }
        if (numLongsPerChunk > MAX_NUM_LONGS_PER_CHUNK) {
            throw new IllegalArgumentException(
                    "Cannot store " + numLongsPerChunk + " per chunk (max is " + MAX_NUM_LONGS_PER_CHUNK + ")");
        }
        this.numLongsPerChunk = numLongsPerChunk;
        // multiplyExact throws exception if we overflow and int
        memoryChunkSize = Math.multiplyExact(numLongsPerChunk, Long.BYTES);
        this.maxLongs = maxLongs;
        currentFileHeaderSize = FILE_HEADER_SIZE_V2;
    }

    /**
     * Read the file header from file channel, populating final fields. The file channel is stored
     * in protected field this.fileChannel so that it can be used and closed by the caller. This is
     * a little bit of an ugly hack but of the available other options like making the state fields
     * non-final or reading the file twice it seemed the least offencive. The FileChannel will be
     * positioned at the start of the data after the header at the end of this constructor.
     *
     * @param fileChannel File channel to read header from
     * @throws IOException If there was a problem reading the file
     */
    protected LongList(final FileChannel fileChannel) throws IOException {
        this.fileChannel = fileChannel;
        if (fileChannel.size() > 0) {
            // read header from existing file
            final ByteBuffer versionBuffer = readFromFileChannel(fileChannel, VERSION_METADATA_SIZE);
            final int formatVersion = versionBuffer.getInt();
            final int formatMetadataSize;
            if (formatVersion == INITIAL_VERSION) {
                formatMetadataSize = FORMAT_METADATA_SIZE_V1;
                currentFileHeaderSize = FILE_HEADER_SIZE_V1;
            } else if (formatVersion == MIN_VALID_INDEX_SUPPORT_VERSION) {
                formatMetadataSize = FORMAT_METADATA_SIZE_V2;
                currentFileHeaderSize = FILE_HEADER_SIZE_V2;
            } else {
                throw new IOException("File format version is not supported. File format version ["
                        + formatVersion
                        + "], the latest supported version is ["
                        + CURRENT_FILE_FORMAT_VERSION
                        + "].");
            }

            final ByteBuffer headerBuffer = readFromFileChannel(fileChannel, formatMetadataSize);
            numLongsPerChunk = headerBuffer.getInt();
            memoryChunkSize = numLongsPerChunk * Long.BYTES;
            // skip the maxIndexThatCanBeStored field as it's no longer used
            if (formatVersion == INITIAL_VERSION) headerBuffer.getLong();

            maxLongs = headerBuffer.getLong();
            if (formatVersion >= MIN_VALID_INDEX_SUPPORT_VERSION) {
                minValidIndex.set(headerBuffer.getLong());
                // "inflating" the size by number of indices that are to the left of the min valid index
                size.set(minValidIndex.get() + (fileChannel.size() - currentFileHeaderSize) / Long.BYTES);
            } else {
                size.set((fileChannel.size() - FILE_HEADER_SIZE_V1) / Long.BYTES);
            }
        } else {
            // opening a new file
            numLongsPerChunk = DEFAULT_NUM_LONGS_PER_CHUNK;
            memoryChunkSize = numLongsPerChunk * Long.BYTES;
            maxLongs = DEFAULT_MAX_LONGS_TO_STORE;
            currentFileHeaderSize = FILE_HEADER_SIZE_V2;
            writeHeader(fileChannel);
        }
    }

    /**
     * Loads the long at the given index.
     *
     * @param index the index of the long
     * @param defaultValue The value to return if nothing is stored for the long
     * @return the loaded long
     * @throws IndexOutOfBoundsException if the index is negative or beyond current capacity of the
     *     list
     */
    public long get(final long index, final long defaultValue) {
        if (index < 0 || index >= maxLongs) {
            throw new IndexOutOfBoundsException();
        }
        if (index >= size.get()) {
            return defaultValue;
        }
        final long dataIndex = index / numLongsPerChunk;
        final long subIndex = index % numLongsPerChunk;
        final long presentValue = lookupInChunk(dataIndex, subIndex);
        return presentValue == IMPERMISSIBLE_VALUE ? defaultValue : presentValue;
    }

    /**
     * Implements CASable.get(index)
     *
     * @param index position, key, etc.
     * @return read value
     */
    @Override
    public long get(final long index) {
        return get(index, IMPERMISSIBLE_VALUE);
    }

    /**
     * Stores a long at the given index.
     *
     * @param index the index to use
     * @param value the long to store
     * @throws IndexOutOfBoundsException if the index is negative or beyond the max capacity of the
     *     list
     * @throws IllegalArgumentException if the value is zero
     */
    public abstract void put(final long index, final long value);

    /**
     * Stores a long at the given index, on the condition that the current long therein has a given
     * value.
     *
     * @param index the index to use
     * @param oldValue the value that must currently obtain at the index
     * @param newValue the new value to store
     * @return whether the newValue was set
     * @throws IndexOutOfBoundsException if the index is negative or beyond the max capacity of the
     *     list
     * @throws IllegalArgumentException if old value is zero (which could never be true)
     */
    @Override
    public abstract boolean putIfEqual(final long index, final long oldValue, final long newValue);

    /**
     * Get the maximum capacity of this LongList; that is, one greater than the maximum legal value
     * of an {@code index} parameter used in a {@code put()} call.
     */
    public final long capacity() {
        return maxLongs;
    }

    /**
     * Get the maximum number of indices in this LongList that may be non-zero. (That is, one more
     * than the largest {@code index} used in a call to {@code put()}.
     *
     * <p>Bounded above by {@link LongList#capacity()}.
     */
    public final long size() {
        return size.get();
    }

    /**
     * Get the number of longs in each check of allocated memory.
     *
     * @return Size in longs of memory allocation chunk
     */
    public final int getNumLongsPerChunk() {
        return numLongsPerChunk;
    }

    /**
     * Create a stream over the data in this LongList. This is designed for testing and may be
     * inconsistent under current modifications.
     */
    public LongStream stream() {
        return StreamSupport.longStream(new LongListSpliterator(this), false);
    }

    /**
     * Write all longs in this LongList into a file
     * <p>
     * <b> It is not guaranteed what version of data will be written if the LongList is changed
     * via put methods while this LongList is being written to a file. If you need consistency while
     * calling put concurrently then use a BufferedLongListWrapper. </b>
     *
     * @param file The file to write into, it should not exist but its parent directory should exist
     *     and be writable.
     * @throws IOException If there was a problem creating or writing to the file.
     */
    public void writeToFile(final Path file) throws IOException {
        try (final FileChannel fc = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            // write header
            writeHeader(fc);
            // write data
            writeLongsData(fc);
        }
    }

    /**
     * Write or rewrite header in file
     *
     * @param fc File channel on the file to write to
     * @throws IOException If there was a problem writing header
     */
    protected final void writeHeader(final FileChannel fc) throws IOException {
        final ByteBuffer headerBuffer = ByteBuffer.allocate(currentFileHeaderSize);
        headerBuffer.rewind();
        headerBuffer.putInt(CURRENT_FILE_FORMAT_VERSION);
        headerBuffer.putInt(getNumLongsPerChunk());
        headerBuffer.putLong(maxLongs);
        headerBuffer.putLong(minValidIndex.get());
        headerBuffer.flip();
        // always write at start of file
        MerkleDbFileUtils.completelyWrite(fc, headerBuffer, 0);
        fc.position(currentFileHeaderSize);
    }

    /**
     * Write the long data to file, This it is expected to be in one simple block of raw longs.
     *
     * @param fc The file channel to write to
     * @throws IOException if there was a problem writing longs
     */
    protected abstract void writeLongsData(final FileChannel fc) throws IOException;

    /**
     * Lookup a long in data
     *
     * @param chunkIndex the index of the chunk the long is contained in
     * @param subIndex The sub index of the long in that chunk
     * @return The stored long value at given index
     */
    protected abstract long lookupInChunk(final long chunkIndex, final long subIndex);

    /**
     * Calls implementation specific {@link LongList#onUpdateMinValidIndex(long)}
     * and then updates {@code minValidIndex} parameter
     *
     * @param newMinValidIndex minimal valid index of the list
     */
    public final void updateMinValidIndex(final long newMinValidIndex) {
        onUpdateMinValidIndex(newMinValidIndex);
        minValidIndex.set(newMinValidIndex);
    }

    /**
     * An action that has to be taken before update of the min valid index
     *
     * @param newMinValidIndex min valid index
     */
    protected void onUpdateMinValidIndex(final long newMinValidIndex) {
        // no op
    }

    /**
     * @return number of memory chunks that this list may have
     */
    protected int calculateNumberOfChunks(final long rightBoundary) {
        return (int) ((rightBoundary - 1) / numLongsPerChunk + 1);
    }

    /**
     * Checks if the value may be put into a LongList at a certain index, given a max capacity.
     *
     * @param value the value to check
     * @param index the index to check
     * @throws IllegalArgumentException if the value is impermissible
     * @throws IndexOutOfBoundsException if the index is out-of-bounds
     */
    protected final void checkValueAndIndex(final long value, final long index) {
        if (index < 0 || index >= maxLongs) {
            throw new IndexOutOfBoundsException("Index " + index + " is out-of-bounds given capacity " + maxLongs);
        }
        if (value == IMPERMISSIBLE_VALUE) {
            throw new IllegalArgumentException("Cannot put " + IMPERMISSIBLE_VALUE + " into a LongList");
        }
    }

    /**
     * Current max valid index in the list. By default, it's equal to the size of the list, but some
     * implementations may provide more fine-grained values.
     *
     * @return max valid index
     */
    protected long getCurrentMax() {
        return size();
    }

    /** {@inheritDoc} */
    @Override
    public <T extends Throwable> void forEach(final LongAction<T> action) throws InterruptedException, T {
        final long max = getCurrentMax();
        for (long i = getMinValidIndexInEffect(); i < max; i++) {
            final long value = get(i);
            if (value != IMPERMISSIBLE_VALUE) {
                action.handle(i, value);
            }
        }
    }

    /**
     * Returns currently applicable min valid index. For in-memory implementations it's equal to the {@link #minValidIndex},
     * however for file based implementations it may be different.
     * @return min valid index
     */
    protected long getMinValidIndexInEffect() {
        return minValidIndex.get();
    }
}
