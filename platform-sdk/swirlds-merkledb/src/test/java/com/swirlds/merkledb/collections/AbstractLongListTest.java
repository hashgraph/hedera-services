/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

import static com.swirlds.base.units.UnitConstants.BYTES_TO_MEBIBYTES;
import static com.swirlds.base.units.UnitConstants.MEBIBYTES_TO_BYTES;
import static com.swirlds.merkledb.collections.AbstractLongList.FILE_HEADER_SIZE_V2;
import static com.swirlds.merkledb.test.fixtures.MerkleDbTestUtils.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.config.api.Configuration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
abstract class AbstractLongListTest<T extends AbstractLongList<?>> {

    private static final int OUT_OF_SAMPLE_INDEX = 13_000_123;
    private static final long REPL_VALUE = 42;
    private static final long DEFAULT_VALUE = 0;

    private static AbstractLongList<?> longList;

    protected int getSampleSize() {
        return 1_000_000;
    }

    protected AbstractLongList<?> createLongList() {
        return new LongListHeap();
    }

    @SuppressWarnings("SameParameterValue")
    protected abstract T createLongListWithChunkSizeInMb(final int chunkSizeInMb);

    protected abstract T createFullyParameterizedLongListWith(final int numLongsPerChunk, final long maxLongs);

    protected abstract T createLongListFromFile(final Path file, final Configuration configuration) throws IOException;

    /**
     * Keep track of initial direct memory used already, so we can check if we leek over and above what we started with
     */
    private static long directMemoryUsedAtStart;

    @Test
    @Order(1)
    void createData() {
        directMemoryUsedAtStart = getDirectMemoryUsedBytes();
        longList = createLongList();
        final long capacity = longList.capacity();

        assertEquals(
                AbstractLongList.DEFAULT_MAX_LONGS_TO_STORE,
                longList.capacity(),
                "Capacity should be default it not given explicitly");
        assertEquals(
                AbstractLongList.DEFAULT_NUM_LONGS_PER_CHUNK,
                longList.getNumLongsPerChunk(),
                "Num longs per chunk should be default it not given explicitly");

        assertThrows(
                IllegalArgumentException.class,
                () -> longList.put(0, LongList.IMPERMISSIBLE_VALUE),
                "Should be illegal to put 0 in a LongList");
        assertThrows(
                IndexOutOfBoundsException.class, () -> longList.put(-1, -1), "Negative indices should be rejected");
        assertThrows(
                IndexOutOfBoundsException.class,
                () -> longList.put(capacity, 1),
                "Capacity should not be a valid index");

        assertThrows(
                IllegalArgumentException.class,
                () -> longList.putIfEqual(0, 1, LongList.IMPERMISSIBLE_VALUE),
                "Should be illegal to put 0 in a LongList");
        assertThrows(
                IndexOutOfBoundsException.class,
                () -> longList.putIfEqual(-1, 1, -1),
                "Negative indices should be rejected");
        assertThrows(
                IndexOutOfBoundsException.class,
                () -> longList.putIfEqual(capacity, 1, -1),
                "Capacity should not be a valid index");

        longList.updateValidRange(0, getSampleSize());
        for (int i = 1; i < getSampleSize(); i++) {
            longList.put(i, i);
        }
    }

    @Test
    @Order(2)
    void check() {
        checkRange();
    }

    @Test
    @Order(3)
    void writeToFileAndReadBack(@TempDir final Path tempDir) throws IOException {
        final int sampleSize = getSampleSize();
        final Path file = tempDir.resolve("LongListByteBufferTest.hl");
        // write longList data
        longList.writeToFile(file);
        // check file exists and contains some data
        assertTrue(Files.exists(file), "file does not exist");
        assertEquals(
                (FILE_HEADER_SIZE_V2 + (Long.BYTES * (long) sampleSize)),
                Files.size(file),
                "Expected file to contain all the data so its size [" + Files.size(file)
                        + "] should have been header plus longs data size ["
                        + (FILE_HEADER_SIZE_V2 + (Long.BYTES * (sampleSize)))
                        + "]");
        // check all data, to make sure it did not get messed up
        for (int i = 0; i < sampleSize; i++) {
            final long readValue = longList.get(i, 0);
            assertEquals(i, readValue, "Longs don't match for " + i + " got [" + readValue + "] should be [" + i + "]");
        }
        // now try and construct a new LongList reading from the file
        try (final LongList longList2 = createLongListFromFile(file, CONFIGURATION)) {
            // now check data and other attributes
            assertEquals(longList.capacity(), longList2.capacity(), "Unexpected value for longList2.capacity()");
            assertEquals(longList.size(), longList2.size(), "Unexpected value for longList2.size()");
            for (int i = 0; i < sampleSize; i++) {
                final long readValue = longList2.get(i, 0);
                assertEquals(
                        i, readValue, "Longs don't match for " + i + " got [" + readValue + "] should be [" + i + "]");
            }
        }
        // delete file as we are done with it
        Files.delete(file);
    }

    @Test
    @Order(3)
    void testOffEndExpand() {
        longList.updateValidRange(0, OUT_OF_SAMPLE_INDEX);
        longList.put(OUT_OF_SAMPLE_INDEX, OUT_OF_SAMPLE_INDEX);
        assertEquals(
                OUT_OF_SAMPLE_INDEX,
                longList.get(OUT_OF_SAMPLE_INDEX, 0),
                "Failed to save and get " + OUT_OF_SAMPLE_INDEX);
    }

    @Test
    @Order(4)
    void testPutIfEqual() {
        longList.put(OUT_OF_SAMPLE_INDEX, OUT_OF_SAMPLE_INDEX);

        longList.putIfEqual(OUT_OF_SAMPLE_INDEX, DEFAULT_VALUE, REPL_VALUE);
        assertNotEquals(
                REPL_VALUE, longList.get(OUT_OF_SAMPLE_INDEX, DEFAULT_VALUE), "putIfEqual put when it should have not");

        longList.putIfEqual(OUT_OF_SAMPLE_INDEX, DEFAULT_VALUE + 1, REPL_VALUE);
        assertNotEquals(
                REPL_VALUE, longList.get(OUT_OF_SAMPLE_INDEX, DEFAULT_VALUE), "putIfEqual put when it should have not");

        longList.putIfEqual(OUT_OF_SAMPLE_INDEX, OUT_OF_SAMPLE_INDEX - 1, REPL_VALUE);
        assertNotEquals(
                REPL_VALUE, longList.get(OUT_OF_SAMPLE_INDEX, DEFAULT_VALUE), "putIfEqual put when it should have not");

        longList.putIfEqual(OUT_OF_SAMPLE_INDEX, OUT_OF_SAMPLE_INDEX, REPL_VALUE);
        assertEquals(
                REPL_VALUE,
                longList.get(OUT_OF_SAMPLE_INDEX, DEFAULT_VALUE),
                "putIfEqual did not put when it should have");
    }

    @Test
    @Order(5)
    void chunkSizeFactoryWorks() {
        final int expectedNum = Math.toIntExact(2 * MEBIBYTES_TO_BYTES / Long.BYTES);

        final AbstractLongList<?> subject2mbChunks = createLongListWithChunkSizeInMb(2);

        checkNumLongsPerChunk(subject2mbChunks, expectedNum);
    }

    @SuppressWarnings("resource")
    @Test
    @Order(6)
    void constructorValidatesArgs() {
        assertThrows(
                IllegalArgumentException.class,
                () -> createFullyParameterizedLongListWith(1, -1),
                "Should not be able to create with a negative maxLongs");
        assertThrows(
                IllegalArgumentException.class,
                () -> createFullyParameterizedLongListWith(Integer.MAX_VALUE, 1000),
                "Should not be able to create with a more longs per chunk than maxLongs");
    }

    @Test
    @Order(6)
    void testClose() {
        // close
        if (longList != null) {
            longList.close();
        }
        // check all memory is freed after DB is closed
        assertTrue(
                checkDirectMemoryIsCleanedUpToLessThanBaseUsage(directMemoryUsedAtStart),
                "Direct Memory used is more than base usage even after 20 gc() calls. At start was "
                        + (directMemoryUsedAtStart * BYTES_TO_MEBIBYTES) + "MB and is now "
                        + (getDirectMemoryUsedBytes() * BYTES_TO_MEBIBYTES)
                        + "MB");
    }

    private void checkRange() {
        for (int i = 0; i < getSampleSize(); i++) {
            final long readValue = longList.get(i, 0);
            assertEquals(i, readValue, "Longs don't match for " + i + " got [" + readValue + "] should be [" + i + "]");
        }

        final AtomicInteger atomicI = new AtomicInteger(0);
        longList.stream().forEach(readValue -> {
            final int i = atomicI.getAndIncrement();
            assertEquals(i, readValue, "Longs don't match for " + i + " got [" + readValue + "] should be [" + i + "]");
        });

        assertEquals(
                getSampleSize(),
                longList.stream().parallel().summaryStatistics().getCount(),
                "Stream size should match initial sample size");
    }

    protected void checkNumLongsPerChunk(final AbstractLongList<?> subject, final int expected) {
        assertEquals(
                expected,
                subject.getNumLongsPerChunk(),
                "On-heap implementations should respect constructor parameter for numLongsPerChunk");
    }
}
