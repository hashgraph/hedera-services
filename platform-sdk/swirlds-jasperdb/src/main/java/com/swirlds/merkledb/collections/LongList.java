/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import com.swirlds.merkledb.files.DataFileCommon;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.LongStream;

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
 *
 */
public interface LongList extends CASableLongIndex, Closeable {
    /**
     * A LongList may not contain the non-existent data location, which is used as a sentinel for a
     * never-set index.
     */
    long IMPERMISSIBLE_VALUE = DataFileCommon.NON_EXISTENT_DATA_LOCATION;

    /**
     * Read current long value at index
     * @param index position, key, etc.
     * @param defaultValue default value to return if index is not set
     * @return read value
     */
    long get(long index, long defaultValue);

    /**
     * Stores a long at the given index.
     *
     * @param index the index to use
     * @param value the long to store
     * @throws IndexOutOfBoundsException if the index is negative or beyond the max capacity of the list
     * @throws IllegalArgumentException  if the value is zero
     */
    void put(long index, long value);

    /**
     * Stores a long at the given index, on the condition that the current long therein has a given
     * value.
     *
     * @param index    the index to use
     * @param oldValue the value that must currently obtain at the index
     * @param newValue the new value to store
     * @return whether the newValue was set
     * @throws IndexOutOfBoundsException if the index is negative or beyond the max capacity of the list
     * @throws IllegalArgumentException  if old value is zero (which could never be true)
     */
    @Override
    boolean putIfEqual(long index, long oldValue, long newValue);

    long capacity();

    long size();

    LongStream stream();

    void writeToFile(Path file) throws IOException;

    void updateMinValidIndex(long newMinValidIndex);

    @Override
    <T extends Throwable> void forEach(LongAction<T> action) throws InterruptedException, T;

    @Override
    void close();
}
