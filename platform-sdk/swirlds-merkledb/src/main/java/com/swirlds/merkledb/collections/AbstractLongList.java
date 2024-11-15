/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

import static com.swirlds.base.units.UnitConstants.MEBIBYTES_TO_BYTES;
import static com.swirlds.merkledb.utilities.MerkleDbFileUtils.readFromFileChannel;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.toIntExact;
import static java.util.Objects.requireNonNull;

import com.swirlds.config.api.Configuration;
import com.swirlds.merkledb.utilities.MerkleDbFileUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.stream.LongStream;
import java.util.stream.StreamSupport;

/**
 * Common parent class for long list implementations. It takes care of loading a snapshot from disk,
 * chunk management and other common functionality.
 *
 * @param <C> a type that represents a chunk (byte buffer, array or long that represents an offset of the chunk)
 */
public abstract class AbstractLongList<C> implements LongList {

    public static final String MAX_CHUNKS_EXCEEDED_MSG = "The maximum number of memory chunks should not exceed %s. "
            + "Either increase numLongsPerChunk or decrease maxLongs";
    public static final String CHUNK_SIZE_EXCEEDED_MSG = "Cannot store %d per chunk (max is %d)";
    public static final String INVALID_RANGE_MSG = "Invalid range %d - %d";
    public static final String MAX_VALID_INDEX_LIMIT = "Max valid index %d must be less than max capacity %d";

    /** A suitable default for the maximum number of longs that may be stored (32GB of longs). */
    protected static final long DEFAULT_MAX_LONGS_TO_STORE = 4_000_000_000L;
    /** The maximum number of longs to store per chunk. */
    protected static final int MAX_NUM_LONGS_PER_CHUNK = toIntExact(16_000L * (MEBIBYTES_TO_BYTES / Long.BYTES));
    /** A suitable default for the number of longs to store per chunk. */
    protected static final int DEFAULT_NUM_LONGS_PER_CHUNK = toIntExact(8L * (MEBIBYTES_TO_BYTES / Long.BYTES));

    /** A suitable default for the reserved buffer length that the list should have before minimal
     * index in the list
     */
    public static final int DEFAULT_RESERVED_BUFFER_LENGTH = toIntExact(2L * MEBIBYTES_TO_BYTES / Long.BYTES);

    /** Maximum number of chunks allowed.*/
    public static final int MAX_NUM_CHUNKS = 2 << 14;

    /**Initial file format*/
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
     * The number of longs to store in each allocated buffer. Must be a positive integer. If the
     * value is small, then we will end up allocating a very large number of buffers. If the value
     * is large, then we will waste a lot of memory in the unfilled buffer.
     */
    protected final int numLongsPerChunk;
    /** Size in bytes for each memory chunk to allocate */
    protected final int memoryChunkSize;
    /** The number of longs that this list would contain if it was not optimized by {@link LongList#updateValidRange}.
     * Practically speaking, it defines the list's right boundary. */
    protected final AtomicLong size = new AtomicLong(0);
    /**
     * The maximum number of longs to ever store in this data structure. This is used as a safety
     * measure to make sure no bug causes an out of memory issue by causing us to allocate more
     * buffers than the system can handle.
     */
    protected final long maxLongs;

    /** Min valid index of the list. All indices to the left of this index have {@code IMPERMISSIBLE_VALUE}-s */
    protected final AtomicLong minValidIndex = new AtomicLong(-1);

    /** Max valid index of the list. All indices to the right of this index have {@code IMPERMISSIBLE_VALUE}-s */
    protected final AtomicLong maxValidIndex = new AtomicLong(-1);

    /** Atomic reference array of our memory chunks */
    protected final AtomicReferenceArray<C> chunkList;

    /**
     * A length of a buffer that is reserved to remain intact after memory optimization that is
     * happening in {@link LongList#updateValidRange}
     */
    protected final long reservedBufferLength;

    /** Platform configuration */
    protected Configuration configuration;

    /**
     * Construct a new LongList with the specified number of longs per chunk and maximum number of
     * longs.
     *
     * @param numLongsPerChunk number of longs to store in each chunk of memory allocated
     * @param maxLongs the maximum number of longs permissible for this LongList
     * @param reservedBufferLength reserved buffer length that the list should have before minimal index in the list
     */
    protected AbstractLongList(final int numLongsPerChunk, final long maxLongs, final long reservedBufferLength) {
        if (maxLongs < 0) {
            throw new IllegalArgumentException("The maximum number of longs must be non-negative, not " + maxLongs);
        }
        if (numLongsPerChunk > MAX_NUM_LONGS_PER_CHUNK) {
            throw new IllegalArgumentException(
                    CHUNK_SIZE_EXCEEDED_MSG.formatted(numLongsPerChunk, MAX_NUM_LONGS_PER_CHUNK));
        }
        this.maxLongs = maxLongs;
        this.numLongsPerChunk = numLongsPerChunk;
        final int chunkNum = calculateNumberOfChunks(maxLongs);
        if (chunkNum > MAX_NUM_CHUNKS) {
            throw new IllegalArgumentException(MAX_CHUNKS_EXCEEDED_MSG.formatted(MAX_NUM_CHUNKS));
        }
        currentFileHeaderSize = FILE_HEADER_SIZE_V2;
        chunkList = new AtomicReferenceArray<>(chunkNum);
        // multiplyExact throws exception if we overflow and int
        memoryChunkSize = Math.multiplyExact(numLongsPerChunk, Long.BYTES);
        this.reservedBufferLength = reservedBufferLength;
    }

    /**
     * Read the file header from file channel, populating final fields. The file channel is stored
     * in protected field this.fileChannel so that it can be used and closed by the caller. This is
     * a little bit of an ugly hack but of the available other options like making the state fields
     * non-final or reading the file twice it seemed the least offencive. The FileChannel will be
     * positioned at the start of the data after the header at the end of this constructor.
     *
     * @param path File to read header from
     * @param configuration platform configuration
     * @throws IOException If there was a problem reading the file
     */
    protected AbstractLongList(
            final Path path, final long reservedBufferLength, @NonNull final Configuration configuration)
            throws IOException {
        requireNonNull(configuration);
        this.configuration = configuration;
        final File file = path.toFile();
        this.reservedBufferLength = reservedBufferLength;
        if (!file.exists() || file.length() == 0) {
            // no existing content, initializing with default values
            numLongsPerChunk = DEFAULT_NUM_LONGS_PER_CHUNK;
            memoryChunkSize = numLongsPerChunk * Long.BYTES;
            maxLongs = DEFAULT_MAX_LONGS_TO_STORE;
            currentFileHeaderSize = FILE_HEADER_SIZE_V2;
            chunkList = new AtomicReferenceArray<>(calculateNumberOfChunks(maxLongs));
            onEmptyOrAbsentSourceFile(path);
        } else {
            try (final FileChannel fileChannel = FileChannel.open(path, StandardOpenOption.READ)) {
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
                if (formatVersion == INITIAL_VERSION) {
                    headerBuffer.getLong();
                }

                maxLongs = headerBuffer.getLong();
                if (formatVersion >= MIN_VALID_INDEX_SUPPORT_VERSION) {
                    minValidIndex.set(headerBuffer.getLong());
                    // "inflating" the size by number of indices that are to the left of the min valid index
                    size.set(minValidIndex.get() + (fileChannel.size() - currentFileHeaderSize) / Long.BYTES);
                } else {
                    minValidIndex.set(0);
                    size.set((fileChannel.size() - FILE_HEADER_SIZE_V1) / Long.BYTES);
                }
                maxValidIndex.set(size.get() - 1);
                chunkList = new AtomicReferenceArray<>(calculateNumberOfChunks(maxLongs));
                readBodyFromFileChannelOnInit(file.getName(), fileChannel);
            }
        }
    }

    /**
     * Initializes the list from the given file channel. At the moment of the call all the class metadata
     * is already initialized from the file header.
     * @param sourceFileName the name of the file from which the list is initialized
     * @param fileChannel the file channel to read the list body from
     * @throws IOException if there was a problem reading the file
     */
    protected abstract void readBodyFromFileChannelOnInit(String sourceFileName, FileChannel fileChannel)
            throws IOException;

    /**
     * Called when the list is initialized from an empty or absent source file.
     * @param path the path to the source file
     * @throws IOException if there was a problem reading the file
     */
    protected void onEmptyOrAbsentSourceFile(Path path) throws IOException {
        // do nothing
    }

    /**
     * Loads the long at the given index.
     *
     * @param index        the index of the long
     * @param defaultValue The value to return if nothing is stored for the long
     * @return the loaded long
     * @throws IndexOutOfBoundsException if the index is negative or beyond current capacity of the list
     */
    @Override
    public long get(final long index, final long defaultValue) {
        if (index < 0 || index >= maxLongs) {
            throw new IndexOutOfBoundsException(index);
        }
        if (index >= size.get()) {
            return defaultValue;
        }
        final int chunkIndex = toIntExact(index / numLongsPerChunk);
        final long subIndex = index % numLongsPerChunk;
        final C chunk = chunkList.get(chunkIndex);
        if (chunk == null) {
            return defaultValue;
        }
        final long presentValue = lookupInChunk(chunk, subIndex);
        return presentValue == IMPERMISSIBLE_VALUE ? defaultValue : presentValue;
    }

    /**
     * Stores a long in the list at the given index.
     *
     * @param index the index to use
     * @param value the long to store
     * @throws IndexOutOfBoundsException if the index is negative or beyond the max capacity of the list
     * @throws IllegalArgumentException if old value is zero (which could never be true)
     */
    @Override
    public final void put(long index, long value) {
        checkIndex(index);
        checkValue(value);
        putImpl(index, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void remove(final long index) {
        checkIndex(index);
        putImpl(index, IMPERMISSIBLE_VALUE);
    }

    private void putImpl(final long index, final long value) {
        assert index >= minValidIndex.get()
                : String.format("Index %d is less than min valid index %d", index, minValidIndex.get());
        assert index <= maxValidIndex.get()
                : String.format("Index %d is greater than max valid index %d", index, maxValidIndex.get());
        final C chunk = createOrGetChunk(index);
        final int subIndex = toIntExact(index % numLongsPerChunk);
        putToChunk(chunk, subIndex, value);
    }

    /**
     * Stores a long in the list at the given chunk at subIndex.
     * @param chunk the chunk to use
     * @param subIndex the subIndex to use
     * @param value the long to store
     */
    protected abstract void putToChunk(C chunk, int subIndex, long value);

    /**
     * Stores a long at the given index, on the condition that the current long therein has a given
     * value.
     *
     * @param index the index to use
     * @param oldValue the value that must currently obtain at the index
     * @param newValue the new value to store
     * @return whether the newValue was set
     * @throws IndexOutOfBoundsException if the index is negative or beyond the max capacity of the list
     * @throws IllegalArgumentException if old value is zero (which could never be true)
     */
    @Override
    public final boolean putIfEqual(long index, long oldValue, long newValue) {
        checkIndex(index);
        checkValue(newValue);
        final int chunkIndex = toIntExact(index / numLongsPerChunk);
        final C chunk = chunkList.get(chunkIndex);
        if (chunk == null) {
            // quick optimization: we can quit early without creating new memory blocks
            // unnecessarily
            return false;
        }
        final int subIndex = toIntExact(index % numLongsPerChunk);
        boolean result = putIfEqual(chunk, subIndex, oldValue, newValue);
        if (result) {
            // update the size if necessary
            size.getAndUpdate(oldSize -> index >= oldSize ? (index + 1) : oldSize);
        }
        return result;
    }

    /**
     * Stores a long in a given chunk at a given sub index, on the condition that the current long therein has a given
     * value.
     *
     * @param chunk offset of the chunk to use
     * @param subIndex the index within the chunk to use
     * @param oldValue the value that must currently obtain at the index
     * @param newValue the new value to store
     * @return whether the newValue was set
     */
    protected abstract boolean putIfEqual(C chunk, int subIndex, long oldValue, long newValue);

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

    /** {@inheritDoc} */
    @Override
    public final long capacity() {
        return maxLongs;
    }

    /** {@inheritDoc} */
    @Override
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
    @Override
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
     *             and be writable.
     * @throws IOException If there was a problem creating or writing to the file.
     */
    @Override
    public void writeToFile(final Path file) throws IOException {
        try (final FileChannel fc = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            // write header
            writeHeader(fc);
            // write data
            writeLongsData(fc);
            fc.force(true);
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
        // maxValidIndex is not written. On loading, it will be set automatically based on the size
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
     * @param chunk chunk to lookup in
     * @param subIndex   The sub index of the long in that chunk
     * @return The stored long value at given index
     */
    protected abstract long lookupInChunk(@NonNull final C chunk, final long subIndex);

    /** {@inheritDoc} */
    @Override
    public final void updateValidRange(final long newMinValidIndex, final long newMaxValidIndex) {
        if ((newMinValidIndex < -1) || (newMinValidIndex > newMaxValidIndex)) {
            throw new IndexOutOfBoundsException(INVALID_RANGE_MSG.formatted(newMinValidIndex, newMaxValidIndex));
        }
        if (newMaxValidIndex > maxLongs - 1) {
            throw new IndexOutOfBoundsException(MAX_VALID_INDEX_LIMIT.formatted(newMaxValidIndex, maxLongs));
        }

        minValidIndex.set(newMinValidIndex);
        final long oldMaxValidIndex = maxValidIndex.getAndSet(newMaxValidIndex);
        size.updateAndGet(v -> min(v, newMaxValidIndex + 1));

        shrinkLeftSideIfNeeded(newMinValidIndex);
        shrinkRightSideIfNeeded(oldMaxValidIndex, newMaxValidIndex);
        // everything to the right of the newMaxValidIndex is going to be discarded, adjust the size accordingly
    }

    @Override
    public long getMinValidIndex() {
        return minValidIndex.get();
    }

    @Override
    public long getMaxValidIndex() {
        return maxValidIndex.get();
    }

    /**
     * Expand the available data storage if needed to allow storage of an item at newIndex
     *
     * @param newIndex the index of the new item we would like to add to storage
     */
    protected C createOrGetChunk(final long newIndex) {
        size.getAndUpdate(oldSize -> newIndex >= oldSize ? (newIndex + 1) : oldSize);
        final int chunkIndex = toIntExact(newIndex / numLongsPerChunk);
        final C result = chunkList.get(chunkIndex);
        if (result == null) {
            final C newChunk = createChunk();
            // set new chunk if it's not created yet, if it is - release the chunk immediately
            // and use the one from the list
            final C oldChunk = chunkList.compareAndExchange(chunkIndex, null, newChunk);
            if (oldChunk == null) {
                return newChunk;
            } else {
                closeChunk(newChunk);
                return oldChunk;
            }
        } else {
            return result;
        }
    }

    /**
     * Deletes values up to {@code newMinValidIndex} and releases memory chunks reserved for these values.
     * @param newMinValidIndex new minimal valid index, left boundary of the list
     */
    private void shrinkLeftSideIfNeeded(final long newMinValidIndex) {
        final int firstValidChunkWithBuffer =
                toIntExact(max((newMinValidIndex - reservedBufferLength) / numLongsPerChunk, 0));
        final int firstChunkIndexToDelete = firstValidChunkWithBuffer - 1;
        for (int i = firstChunkIndexToDelete; i >= 0; i--) {
            final C chunk = chunkList.get(i);
            if (chunk != null && chunkList.compareAndSet(i, chunk, null)) {
                closeChunk(chunk);
            }
        }

        // clean up a chunk with data
        final int firstChunkWithDataIndex = toIntExact((newMinValidIndex / numLongsPerChunk));
        final long numberOfElementsToCleanUp = (newMinValidIndex % numLongsPerChunk);
        C chunk = chunkList.get(firstChunkWithDataIndex);
        if (chunk != null && numberOfElementsToCleanUp > 0) {
            partialChunkCleanup(chunk, true, numberOfElementsToCleanUp);
        }

        // clean up chunk(s) reserved for buffer
        for (int i = firstValidChunkWithBuffer; i < firstChunkWithDataIndex; i++) {
            chunk = chunkList.get(i);
            if (chunk != null) {
                partialChunkCleanup(chunk, true, numLongsPerChunk);
            }
        }
    }

    /**
     * Deletes values from {@code newMaxValidIndex} to the end of the list and releases memory chunks reserved for these values.
     *
     * @param oldMaxValidIndex old maximal valid index, former right boundary of the list
     * @param newMaxValidIndex new maximal valid index, new right boundary of the list
     */
    private void shrinkRightSideIfNeeded(long oldMaxValidIndex, long newMaxValidIndex) {
        final int listLength = chunkList.length();
        final int lastValidChunkWithBufferIndex =
                toIntExact(min((newMaxValidIndex + reservedBufferLength) / numLongsPerChunk, listLength - 1));
        final int firstChunkIndexToDelete = lastValidChunkWithBufferIndex + 1;
        final int numberOfChunks = calculateNumberOfChunks(oldMaxValidIndex);

        for (int i = firstChunkIndexToDelete; i < numberOfChunks; i++) {
            final C chunk = chunkList.get(i);
            if (chunk != null && chunkList.compareAndSet(i, chunk, null)) {
                closeChunk(chunk);
            }
        }

        // clean up a chunk with data
        final int firstChunkWithDataIndex = toIntExact(newMaxValidIndex / numLongsPerChunk);
        final long numberOfEntriesToCleanUp = numLongsPerChunk - (newMaxValidIndex % numLongsPerChunk) - 1;
        C chunk = chunkList.get(firstChunkWithDataIndex);
        if (chunk != null && numberOfEntriesToCleanUp > 0) {
            partialChunkCleanup(chunk, false, numberOfEntriesToCleanUp);
        }

        // clean up chunk(s) reserved for buffer
        for (int i = firstChunkWithDataIndex + 1; i <= lastValidChunkWithBufferIndex; i++) {
            chunk = chunkList.get(i);
            if (chunk != null) {
                partialChunkCleanup(chunk, false, numLongsPerChunk);
            }
        }
    }

    /**
     * Zeroes out a part of a chunk.
     * @param chunk index of the chunk to clean up
     * @param leftSide         if true, cleans up {@code entriesToCleanUp} on the left side of the chunk,
     *                         if false, cleans up {@code entriesToCleanUp} on the right side of the chunk
     * @param entriesToCleanUp number of entries to clean up
     */
    protected abstract void partialChunkCleanup(
            @NonNull final C chunk, final boolean leftSide, final long entriesToCleanUp);

    /**
     * Allocates a new chunk of data.
     *
     * @return a new chunk
     */
    protected abstract C createChunk();

    /**
     * Releases a chunk. This method is called for every chunk when this list is closed. It's
     * also used to delete chunks, when they are no longer in use because of min/max valid
     * index updated.
     *
     * @param chunk the chunk to clean up
     */
    protected void closeChunk(@NonNull final C chunk) {
        // to be overridden
    }

    /**
     * @param totalNumberOfElements total number of elements in the list
     * @return number of memory chunks that this list may have
     */
    protected int calculateNumberOfChunks(final long totalNumberOfElements) {
        return toIntExact((totalNumberOfElements - 1) / numLongsPerChunk + 1);
    }

    /**
     * Checks if a value may be put into a LongList at a certain index, given a max capacity.
     *
     * @param index the index to check
     * @throws IndexOutOfBoundsException if the index is out-of-bounds
     */
    private void checkIndex(final long index) {
        if (index < 0 || index >= maxLongs) {
            throw new IndexOutOfBoundsException("Index " + index + " is out-of-bounds given capacity " + maxLongs);
        }
    }

    /**
     * Checks if the value may be put into a LongList.
     *
     * @param value the value to check
     * @throws IllegalArgumentException  if the value is impermissible
     */
    private void checkValue(final long value) {
        if (value == IMPERMISSIBLE_VALUE) {
            throw new IllegalArgumentException("Cannot put " + IMPERMISSIBLE_VALUE + " into a LongList");
        }
    }

    /** {@inheritDoc} */
    @Override
    public <T extends Throwable> void forEach(final LongAction<T> action) throws InterruptedException, T {
        final long max = maxValidIndex.get();
        if (max < 0) {
            // Empty list, nothing to do
            return;
        }
        for (long i = minValidIndex.get(); i <= max; i++) {
            final long value = get(i);
            if (value != IMPERMISSIBLE_VALUE) {
                action.handle(i, value);
            }
        }
    }

    /**
     * This method returns a snapshot of the current data. FOR TEST PURPOSES ONLY. NOT
     * THREAD SAFE
     *
     * @return a copy of data.
     */
    List<C> dataCopy() {
        final ArrayList<C> result = new ArrayList<>();
        for (int i = 0; i < chunkList.length(); i++) {
            result.add(chunkList.get(i));
        }
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public void close() {
        size.set(0);
        for (int i = 0; i < chunkList.length(); i++) {
            final C chunk = chunkList.getAndSet(i, null);
            if (chunk != null) {
                closeChunk(chunk);
            }
        }
    }
}
