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

package com.swirlds.common.bloom;

import static com.swirlds.common.utility.Units.BITS_TO_BYTES;
import static com.swirlds.common.utility.Units.BYTES_PER_INT;
import static com.swirlds.common.utility.Units.BYTES_TO_BITS;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import java.util.Objects;

/**
 * <p>
 * An implementation of a bloom filter. A bloom filter is a data structure that has similarities to a set. Elements
 * can be added to the bloom filter, but not removed. When checked to see if the bloom filter contains an element,
 * it can respond "no, this element is not present" or "this element may be present". The probability of false
 * positives can be tuned depending on input configuration. A bloom filter can be advantageous when there is a
 * need for a set too large to fit into memory, as its memory footprint is SIGNIFICANTLY smaller than a standard
 * set of the same size.
 * </p>
 *
 * <p>
 * This data structure is not thread safe. External synchronization required if used in a multi-threaded environment.
 * </p>
 *
 * @param <T>
 * 		the type of the element contained by the bloom filter
 */
public class BloomFilter<T> implements SelfSerializable {

    private static final long CLASS_ID = 0xef14b2193cd4900eL;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    /**
     * The maximum array size is JVM dependant, and there is no good way to find out what
     * that limit is programmatically. This constant is chosen because it seems to work in tested environments.
     */
    public static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE / 2;

    /**
     * The number of bits in an integer.
     */
    private static final int BITS_PER_INT = BYTES_PER_INT * BYTES_TO_BITS;

    /**
     * The number of bits in an array of maximum size (as configured by {@link #MAX_ARRAY_SIZE}).
     */
    private static final long BITS_PER_ARRAY = ((long) BITS_PER_INT) * MAX_ARRAY_SIZE;

    /**
     * A constant used for bit shifting.
     */
    private static final int FIRST_BIT = 0b10000000_00000000_00000000_00000000;

    /**
     * The number of hashes computed for each element added to the bloom filter.
     */
    private int hashCount;

    /**
     * An object that is responsible for hashing elements.
     */
    private BloomHasher<T> hashProvider;

    /**
     * The total size of the bloom filter, in bits.
     */
    private long filterSizeInBits;

    /**
     * Contains the bloom filter's data. Split into an array of arrays to accommodate very large bloom filters
     * that do not fit into a single array.
     */
    private int[][] filter;

    /**
     * A buffer used to improve single threaded performance by reducing throw-away objects.
     */
    private long[] hashBuffer;

    /**
     * Zero arg constructor, required for serialization.
     */
    public BloomFilter() {}

    /**
     * Create a new bloom filter.
     *
     * @param hashCount
     * 		the number of hashes to use
     * @param hashProvider
     * 		an object that performs hashing
     * @param filterSizeInBits
     * 		the total size of the bloom filter, in bits
     */
    public BloomFilter(final int hashCount, final BloomHasher<T> hashProvider, final long filterSizeInBits) {

        if (hashCount <= 0) {
            throw new IllegalArgumentException("hash count must be greater than 0");
        }

        if (filterSizeInBits <= 0) {
            throw new IllegalArgumentException("filter size must be greater than 0");
        }

        this.hashCount = hashCount;
        this.hashProvider = Objects.requireNonNull(hashProvider, "null hash provider not supported");
        this.filterSizeInBits = filterSizeInBits;
        this.filter = createFilter();
        this.hashBuffer = new long[hashCount];
    }

    /**
     * Convenience method. Create a new buffer of the appropriate length to hold hashes.
     *
     * @return an appropriately sized hash buffer
     */
    public long[] buildNewBuffer() {
        return new long[hashCount];
    }

    /**
     * Allocate the memory for the bloom filter.
     *
     * @return the bloom filter array
     */
    private int[][] createFilter() {
        final double byteCount = Math.ceil(filterSizeInBits * BITS_TO_BYTES);
        final double intCount = Math.ceil(byteCount / BYTES_PER_INT);
        final int arrayCount = (int) Math.ceil(intCount / MAX_ARRAY_SIZE);

        final int[][] array = new int[arrayCount][];

        // All arrays except for the last one will have the maximum possible size
        for (int arrayIndex = 0; arrayIndex < arrayCount - 1; arrayIndex++) {
            array[arrayIndex] = new int[MAX_ARRAY_SIZE];
        }
        final int lastArraySize = (int) (((long) intCount) - (arrayCount - 1) * MAX_ARRAY_SIZE);
        array[arrayCount - 1] = new int[lastArraySize];

        return array;
    }

    /**
     * <p>
     * Add an element to the bloom filter.
     * </p>
     *
     * @param element
     * 		the element to add
     * @throws NullPointerException
     * 		if the hasher does not support null values but a null element is provided
     */
    public void add(final T element) {
        hash(element, hashBuffer);
        add(hashBuffer);
    }

    /**
     * Add an element using precomputed hashes. This method is useful for when an element's hashes are already known
     * (for example, if a contains operation was already performed).
     *
     * @param hashes
     * 		hashes corresponding ot an element
     * @throws NullPointerException
     * 		if a null array of hashes is provided
     */
    public void add(final long[] hashes) {
        Objects.requireNonNull(hashes, "null hash array not supported");
        for (final long hash : hashes) {
            setBit(hash);
        }
    }

    /**
     * <p>
     * Check if an element is in the filter, and then add it to the filter.
     * </p>
     *
     * <p>
     * This method uses the internal buffer, and so it is not thread safe.
     * </p>
     *
     * @param element
     * 		the element to check and then add
     * @return true if the element may have been in the filter before it was added
     */
    public boolean checkAndAdd(final T element) {
        return checkAndAdd(element, hashBuffer);
    }

    /**
     * Check if an element is in the filter, and then add it to the filter.
     *
     * @param element
     * 		the element to check and then add
     * @return true if the element may have been in the filter before it was added
     */
    public boolean checkAndAdd(final T element, final long[] hashes) {
        hash(element, hashes);
        final boolean isContained = contains(hashes);
        add(hashes);
        return isContained;
    }

    /**
     * <p>
     * Check if an element is contained by the bloom filter. May return false positives.
     * </p>
     *
     * <p>
     * Even though this is a read only operation (from the perspective of the caller), internally this method
     * uses a shared buffer to store hash data. If multi-threaded read access is needed, each thread must provide
     * it's own buffer and use ({@link #hash(Object)} or {@link #hash(Object, long[])}) and {@link #contains(long[])}.
     * </p>
     *
     * @param element
     * 		the element in question
     * @return if false, then the element is guaranteed not to be contained within the bloom filter. If true, then
     * 		the element may or may not be contained by the bloom filter.
     * @throws NullPointerException
     * 		if the hasher does not support null values but a null element is provided
     */
    public boolean contains(final T element) {
        return contains(hash(element));
    }

    /**
     * <p>
     * Check if an element is contained by the bloom filter using the hashes of the element. This method is useful for
     * when an element's hashes are already known.
     * </p>
     *
     * <p>
     * This method is thread safe as long as not called concurrently with additions to the bloom filter.
     * </p>
     *
     * @param hashes
     * 		an array of hashes
     * @return if false, then the element is guaranteed not to be contained within the bloom filter. If true, then
     * 		the element may or may not be contained by the bloom filter.
     * @throws NullPointerException
     * 		if a null array of hashes is provided
     */
    public boolean contains(final long[] hashes) {
        Objects.requireNonNull(hashes, "null hash array not supported");
        for (final long hash : hashes) {
            if (!isBitSet(hash)) {
                return false;
            }
        }

        return true;
    }

    /**
     * <p>
     * Hash an element.
     * </p>
     *
     * <p>
     * This method is thread safe.
     * </p>
     *
     * @param element
     * 		the element to be hashed
     * @return an array of hashes
     * @throws NullPointerException
     * 		if the hasher does not support null values but a null element is provided
     */
    public long[] hash(final T element) {
        final long[] hashes = new long[hashCount];
        hash(element, hashes);
        return hashes;
    }

    /**
     * <p>
     * Hash an element into an existing array.
     * </p>
     *
     * <p>
     * This method is thread safe.
     * </p>
     *
     * @param element
     * 		the element to be hashed
     * @param hashes
     * 		the array where the hashes will be stored
     * @throws NullPointerException
     * 		if a null array of hashes is provided,
     * 		or if the hasher does not support null values but a null element is provided
     */
    public void hash(final T element, final long[] hashes) {
        Objects.requireNonNull(hashes, "null hash array not supported");
        hashProvider.hash(element, filterSizeInBits, hashes);
    }

    /**
     * Given a bit index with respect to the entire bloom filter, return the index of the array that contains the bit
     *
     * @param index
     * 		the bit index with respect to the entire bloom filter
     * @return the index of the array that contains the bit
     */
    private static int getArrayIndex(final long index) {
        return (int) (index / BITS_PER_ARRAY);
    }

    /**
     * Given a bit index with respect to the entire bloom filter, return the index of the integer that contains the bit
     *
     * @param index
     * 		the bit index with respect to the entire bloom filter
     * @return the index of the int that contains the bit
     */
    private static int getIntIndex(final long index) {
        return (int) ((index % BITS_PER_ARRAY) / BITS_PER_INT);
    }

    /**
     * Given a bit index with respect to the entire bloom filter, return the index of the bit within the integer
     *
     * @param index
     * 		the bit index with respect to the entire bloom filter
     * @return the index of the bit within the integer
     */
    private static int getBitIndex(final long index) {
        return (int) (index % BITS_PER_INT);
    }

    /**
     * Check if a bit is set.
     *
     * @param index
     * 		the index of the bit
     * @return true if the bit is set, false if it is unset
     */
    private boolean isBitSet(final long index) {
        final int[] array = filter[getArrayIndex(index)];
        final int integer = array[getIntIndex(index)];
        return ((FIRST_BIT >>> getBitIndex(index)) & integer) != 0;
    }

    /**
     * Set a bit in the set.
     *
     * @param index
     * 		the index of the bit to set
     */
    private void setBit(final long index) {
        final int[] array = filter[getArrayIndex(index)];
        final int intIndex = getIntIndex(index);
        final int integer = array[intIndex];
        array[intIndex] = (FIRST_BIT >>> getBitIndex(index)) | integer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeSerializable(hashProvider, true);
        out.writeInt(hashCount);
        out.writeLong(filterSizeInBits);
        for (final int[] array : filter) {
            out.writeIntArray(array);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        hashProvider = in.readSerializable();
        hashCount = in.readInt();
        filterSizeInBits = in.readLong();

        final double byteCount = Math.ceil(filterSizeInBits * BITS_TO_BYTES);
        final double intCount = Math.ceil(byteCount / BYTES_PER_INT);
        final int arrayCount = (int) Math.ceil(intCount / MAX_ARRAY_SIZE);
        final int lastArraySize = (int) (((long) intCount) - (arrayCount - 1) * MAX_ARRAY_SIZE);

        filter = new int[arrayCount][];

        for (int arrayIndex = 0; arrayIndex < arrayCount - 1; arrayIndex++) {
            filter[arrayIndex] = in.readIntArray(MAX_ARRAY_SIZE);
        }
        filter[arrayCount - 1] = in.readIntArray(lastArraySize);

        hashBuffer = new long[hashCount];
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getVersion() {
        return ClassVersion.ORIGINAL;
    }
}
