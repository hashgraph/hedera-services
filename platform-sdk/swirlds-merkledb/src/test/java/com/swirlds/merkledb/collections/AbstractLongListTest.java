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
import static com.swirlds.common.test.fixtures.RandomUtils.nextInt;
import static com.swirlds.merkledb.collections.AbstractLongList.FILE_HEADER_SIZE_V2;
import static com.swirlds.merkledb.collections.LongList.IMPERMISSIBLE_VALUE;
import static com.swirlds.merkledb.test.fixtures.MerkleDbTestUtils.CONFIGURATION;
import static com.swirlds.merkledb.test.fixtures.MerkleDbTestUtils.checkDirectMemoryIsCleanedUpToLessThanBaseUsage;
import static com.swirlds.merkledb.test.fixtures.MerkleDbTestUtils.getDirectMemoryUsedBytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.test.fixtures.io.ResourceLoader;
import com.swirlds.config.api.Configuration;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

// TODO: improve parametrized tests names
// TODO: improve temp file names
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
abstract class AbstractLongListTest<T extends AbstractLongList<?>> {

    protected static final int SAMPLE_SIZE = 1_000_000;
    protected static final int MAX_VALID_INDEX = SAMPLE_SIZE - 1;
    protected static final int HALF_SAMPLE_SIZE = SAMPLE_SIZE / 2;

    private static final int OUT_OF_SAMPLE_INDEX = 13_000_123;
    private static final long REPL_VALUE = 42;
    private static final long DEFAULT_VALUE = 0;

    private static AbstractLongList<?> longList;

    protected AbstractLongList<?> createLongList() {
        return new LongListHeap();
    }

    @SuppressWarnings("SameParameterValue")
    protected abstract T createLongListWithChunkSizeInMb(final int chunkSizeInMb);

    protected abstract T createFullyParameterizedLongListWith(final int numLongsPerChunk, final long maxLongs);

    protected abstract T createLongListFromFile(final Path file) throws IOException;

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

        longList.updateValidRange(0, SAMPLE_SIZE);
        for (int i = 1; i < SAMPLE_SIZE; i++) {
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

    // TESTS WITHOUT ORDER

    // SIMPLE ONES

    @Test
    void chunkSizeFactoryWorks() {
        final int expectedNum = Math.toIntExact(2 * MEBIBYTES_TO_BYTES / Long.BYTES);

        final AbstractLongList<?> subject2mbChunks = createLongListWithChunkSizeInMb(2);

        checkNumLongsPerChunk(subject2mbChunks, expectedNum);
    }

    @SuppressWarnings("resource")
    @Test
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
    void closeAndRecreateLongListMultipleTimes(@TempDir final Path tempDir) throws IOException {
        final Path file = tempDir.resolve("closeAndRecreateLongListMultipleTimes.ll");

        try (final AbstractLongList<?> list = createLongList()) {
            list.updateValidRange(0, SAMPLE_SIZE);
            for (int i = 0; i <= SAMPLE_SIZE; i++) {
                list.put(i, i + 100);
            }
            list.writeToFile(file);
            assertTrue(Files.exists(file), "The file should exist after writing with the first list");
        }

        try (final LongList listFromFile = createLongListFromFile(file)) {
            for (int i = 0; i <= SAMPLE_SIZE; i++) {
                assertEquals(i + 100, listFromFile.get(i), "Data should match in the second list");
            }
        }

        try (final LongList anotherListFromFile = createLongListFromFile(file)) {
            for (int i = 0; i <= SAMPLE_SIZE; i++) {
                assertEquals(i + 100, anotherListFromFile.get(i), "Data should still match in the third list");
            }
        }
    }

    // FOLLOWING TESTS NEED TO BE PARAMETRIZED TO TEST COMPATIBILITY BETWEEN DIFFERENT LONG LIST IMPLS

    /**
     * Generates a stream of writer-reader argument pairs for testing cross-compatibility
     * of different long list implementations. The writer implementation is supplied as
     * a parameter, and the method pairs it with three readers (heap, off-heap, and disk-based)
     * to test whether data written by one implementation can be correctly read by another.
     * <p>
     * This method is used internally to support the creation of specific writer-reader pairs
     * for different test configurations.
     *
     * @param writerSupplier a supplier providing the writer long list implementation
     * @return a stream of arguments containing the writer and its corresponding readers
     */
    protected static Stream<Arguments> longListWriterBasedPairsProvider(Supplier<AbstractLongList<?>> writerSupplier) {
        BiFunction<Path, Configuration, AbstractLongList<?>> heapReader = (file, config) -> {
            try {
                return new LongListHeap(file, config);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };

        BiFunction<Path, Configuration, AbstractLongList<?>> offHeapReader = (file, config) -> {
            try {
                return new LongListOffHeap(file, config);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };

        BiFunction<Path, Configuration, AbstractLongList<?>> diskReader = (file, config) -> {
            try {
                return new LongListDisk(file, config);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };

        return Stream.of(
                Arguments.of(writerSupplier, heapReader),
                Arguments.of(writerSupplier, offHeapReader),
                Arguments.of(writerSupplier, diskReader));
    }

    @ParameterizedTest
    @MethodSource("longListWriterReaderPairsProvider")
    @Disabled // fix related bug and enable
    void writeAndReadBackEmptyList(
            Supplier<AbstractLongList<?>> writerSupplier,
            BiFunction<Path, Configuration, AbstractLongList<?>> readerFunction,
            @TempDir final Path tempDir)
            throws IOException {

        try (final AbstractLongList<?> writerList = writerSupplier.get()) {
            final Path file = tempDir.resolve("writeAndReadBackEmptyList.ll");
            if (Files.exists(file)) {
                Files.delete(file);
            }

            writerList.writeToFile(file);
            assertTrue(Files.exists(file), "file does not exist");

            try (final LongList readerList = readerFunction.apply(file, CONFIGURATION)) {
                assertEquals(writerList.capacity(), readerList.capacity(), "Unexpected value for capacity()");
                assertEquals(writerList.size(), readerList.size(), "Unexpected value for size()");
            }

            Files.delete(file);
        }
    }

    @ParameterizedTest
    @MethodSource("longListWriterReaderPairsProvider")
    void writeAndReadBack(
            Supplier<AbstractLongList<?>> writerSupplier,
            BiFunction<Path, Configuration, AbstractLongList<?>> readerFunction,
            @TempDir final Path tempDir)
            throws IOException {

        // Create a long list using the writer
        try (final AbstractLongList<?> writerList = writerSupplier.get()) {
            populateList(writerList);
            checkData(writerList);

            // Prepare a temporary file for writing the list data
            final String TEMP_FILE_NAME = "writeAndReadBack.ll";
            final Path file = tempDir.resolve(TEMP_FILE_NAME);
            if (Files.exists(file)) {
                Files.delete(file); // Ensure no leftover file from previous tests
            }

            writerList.writeToFile(file);
            assertTrue(Files.exists(file), TEMP_FILE_NAME + " file does not exist.");

            // Check that the file size matches the expected data size
            assertEquals(
                    (FILE_HEADER_SIZE_V2 + (Long.BYTES * (long) SAMPLE_SIZE)),
                    Files.size(file),
                    "Expected file to contain all the data so its size [" + Files.size(file)
                            + "] should have been header plus longs data size ["
                            + (FILE_HEADER_SIZE_V2 + (Long.BYTES * (SAMPLE_SIZE)))
                            + "]");

            // Create reader long list, i.e. reconstruct the long list from file
            try (final LongList readerList = readerFunction.apply(file, CONFIGURATION)) {
                // Validate the reconstructed list's attributes
                assertEquals(writerList.capacity(), readerList.capacity(), "Capacity mismatch in reconstructed list.");
                assertEquals(writerList.size(), readerList.size(), "Size mismatch in reconstructed list.");
                checkData(readerList);
            } finally {
                // Clean up the temporary file
                Files.delete(file);
            }
        }
    }

    // TODO: add third long list, so there will be even more permutations?
    @ParameterizedTest
    @MethodSource("longListWriterReaderPairsProvider")
    @Disabled // need to debug
    void updateMinToTheLowerEnd(
            Supplier<AbstractLongList<?>> writerSupplier,
            BiFunction<Path, Configuration, AbstractLongList<?>> readerFunction,
            @TempDir final Path tempDir)
            throws IOException {

        // Create a long list using the writer
        try (final AbstractLongList<?> writerList = writerSupplier.get()) {
            populateList(writerList);
            checkData(writerList);

            int newMinValidIndex = HALF_SAMPLE_SIZE;
            writerList.updateValidRange(newMinValidIndex, MAX_VALID_INDEX);

            // Prepare a temporary file for writing the list data
            final String TEMP_FILE_NAME = "LongListDiskTest_half_empty.ll";
            final Path file = tempDir.resolve(TEMP_FILE_NAME);
            if (Files.exists(file)) {
                Files.delete(file); // Ensure no leftover file from previous tests
            }

            writerList.writeToFile(file);
            assertTrue(Files.exists(file), TEMP_FILE_NAME + " file does not exist.");

            // Create reader long list, i.e. reconstruct the long list from file
            try (final LongList halfEmptyList = readerFunction.apply(file, CONFIGURATION)) {
                final int INDEX_OFFSET = 10;

                // check that it's half-empty indeed
                checkEmptyUpToIndex(halfEmptyList, newMinValidIndex);
                // and half-full
                checkData(halfEmptyList, HALF_SAMPLE_SIZE, SAMPLE_SIZE);

                // if we try to put a value below min valid index, the operation should fail with AssertionError
                int belowMinValidIndex1 = newMinValidIndex - 1;
                int belowMinValidIndex2 = newMinValidIndex - 2;
                int belowMinIndexValue1 = nextInt();
                int belowMinIndexValue2 = nextInt();
                assertThrows(AssertionError.class, () -> halfEmptyList.put(belowMinValidIndex1, belowMinIndexValue1));
                // doesn't throw an AssertionError, but returns false
                assertFalse(halfEmptyList.putIfEqual(belowMinValidIndex2, IMPERMISSIBLE_VALUE, belowMinIndexValue2));

                // however, once we update min valid index, we should be able to put values below it
                halfEmptyList.updateValidRange(0, MAX_VALID_INDEX);
                halfEmptyList.put(belowMinValidIndex1, belowMinIndexValue1);
                assertEquals(belowMinIndexValue1, halfEmptyList.get(belowMinValidIndex1));

                assertTrue(halfEmptyList.putIfEqual(belowMinValidIndex2, IMPERMISSIBLE_VALUE, belowMinIndexValue2));
                assertEquals(belowMinIndexValue2, halfEmptyList.get(belowMinValidIndex2));

                // forcing to create one more chunk
                halfEmptyList.put(belowMinValidIndex2 - INDEX_OFFSET, belowMinIndexValue2);

                // check that it still works after restoring from a file
                final Path zeroMinValidIndex = tempDir.resolve("LongListDiskTest_zero_min_valid_index.ll");
                if (Files.exists(zeroMinValidIndex)) {
                    Files.delete(zeroMinValidIndex);
                }
                halfEmptyList.writeToFile(zeroMinValidIndex);

                try (final LongList zeroMinValidIndexList = readerFunction.apply(zeroMinValidIndex, CONFIGURATION)) {
                    checkEmptyUpToIndex(zeroMinValidIndexList, belowMinValidIndex2 - INDEX_OFFSET);
                    checkData(zeroMinValidIndexList, HALF_SAMPLE_SIZE, SAMPLE_SIZE);

                    for (int i = 0; i < newMinValidIndex; i++) {
                        // assert the content is the same
                        assertEquals(halfEmptyList.get(i), zeroMinValidIndexList.get(i));

                        // refill the list
                        zeroMinValidIndexList.put(i, i + 100);
                    }

                    // make sure that the refilled list works as expected
                    checkData(zeroMinValidIndexList);
                }

            } finally {
                // Clean up the temporary file
                Files.delete(file);
            }
        }
    }

    // should not run 9 times
    @ParameterizedTest
    @MethodSource("longListWriterReaderPairsProvider")
    @Disabled // revisit
    void testBackwardCompatibility_halfEmpty(
            Supplier<AbstractLongList<?>> writerSupplier,
            BiFunction<Path, Configuration, AbstractLongList<?>> readerFunction)
            throws URISyntaxException, IOException {
        final Path file = ResourceLoader.getFile("test_data/LongListOffHeapHalfEmpty_10k_10pc_v1.ll");

        // Create reader long list, i.e. reconstruct the long list from file
        try (final LongList readerList = readerFunction.apply(file, CONFIGURATION)) {
            // half-empty
            checkEmptyUpToIndex(readerList, HALF_SAMPLE_SIZE);
            // half-full
            for (int i = HALF_SAMPLE_SIZE; i < SAMPLE_SIZE; i++) {
                assertEquals(i, readerList.get(i));
            }
        } finally {
            // Clean up the temporary file
            Files.delete(file);
        }
    }

    /**
     * Combines writer-reader pairs with predefined range configurations for testing.
     *
     * @param writerReaderPairs a stream of writer-reader pairs
     * @return a stream of arguments combining writer-reader pairs with range parameters
     */
    protected static Stream<Arguments> longListWriterReaderRangePairsProviderBase(Stream<Arguments> writerReaderPairs) {
        return writerReaderPairs.flatMap(pair -> {
            Object writerSupplier = pair.get()[0];
            Object readerFunction = pair.get()[1];

            return Stream.of(
                    // writerSupplier, readerFunction, startIndex, endIndex, numLongsPerChunk, maxLongs
                    Arguments.of(writerSupplier, readerFunction, 1, 1, 100, 1000),
                    Arguments.of(writerSupplier, readerFunction, 1, 5, 100, 1000),
                    Arguments.of(writerSupplier, readerFunction, 150, 150, 100, 1000),
                    Arguments.of(writerSupplier, readerFunction, 150, 155, 100, 1000));
        });
    }

    @ParameterizedTest(name = "writeReadRangeElement [startIndex={2},endIndex={3},numLongsPerChunk={4},maxLongs={5}]")
    @MethodSource("longListWriterReaderRangePairsProvider")
    void writeReadRangeElement(
            Supplier<AbstractLongList<?>> writerSupplier,
            BiFunction<Path, Configuration, AbstractLongList<?>> readerFunction,
            final int startIndex,
            final int endIndex,
            final int numLongsPerChunk,
            final long maxLongs,
            @TempDir final Path tempDir)
            throws IOException {

        // Create a writer long list
        try (final AbstractLongList<?> writerList = writerSupplier.get()) {
            populateList(writerList);
            checkData(writerList);

            // Prepare a temporary file for writing the list data
            final String TEMP_FILE_NAME = "writeReadRangeElement-" + startIndex + "-" + endIndex + "-chunk"
                    + numLongsPerChunk + "-max" + maxLongs + ".ll";
            final Path file = tempDir.resolve(TEMP_FILE_NAME);
            if (Files.exists(file)) {
                Files.delete(file); // Ensure no leftover file from previous tests
            }

            writerList.writeToFile(file);
            assertTrue(Files.exists(file), TEMP_FILE_NAME + " file does not exist.");

            // Create reader long list, i.e., reconstruct the long list from the file
            try (final AbstractLongList<?> readerList = readerFunction.apply(file, CONFIGURATION)) {
                // Validate the reconstructed list
                assertEquals(writerList.capacity(), readerList.capacity(), "Capacity mismatch in reconstructed list.");
                assertEquals(writerList.size(), readerList.size(), "Size mismatch in reconstructed list.");
                checkData(readerList);
            } finally {
                // Clean up the temporary file
                Files.delete(file);
            }
        }
    }

    /**
     * Combines writer-reader pairs with predefined chunk offset configurations for testing.
     *
     * @param writerReaderPairs a stream of writer-reader pairs
     * @return a stream of arguments combining writer-reader pairs with chunk offset parameters
     */
    protected static Stream<Arguments> longListWriterReaderOffsetPairsProviderBase(
            Stream<Arguments> writerReaderPairs) {
        return writerReaderPairs.flatMap(pair -> {
            Object writerSupplier = pair.get()[0];
            Object readerFunction = pair.get()[1];

            return Stream.of(
                    // writerSupplier, readerFunction, chunkOffset
                    Arguments.of(writerSupplier, readerFunction, 0), Arguments.of(writerSupplier, readerFunction, 5));
        });
    }

    @ParameterizedTest
    @MethodSource("longListWriterReaderOffsetPairsProvider")
    void createHalfEmptyLongListInMemoryReadBack(
            Supplier<AbstractLongList<?>> writerSupplier,
            BiFunction<Path, Configuration, AbstractLongList<?>> readerFunction,
            final int chunkOffset,
            @TempDir final Path tempDir)
            throws IOException {

        // Create a writer long list
        try (final AbstractLongList<?> writerList = writerSupplier.get()) {
            populateList(writerList);
            checkData(writerList);

            int newMinValidIndex = HALF_SAMPLE_SIZE + chunkOffset;
            writerList.updateValidRange(newMinValidIndex, MAX_VALID_INDEX);

            // Prepare a temporary file for writing the list data
            final String TEMP_FILE_NAME = String.format(
                    "LongListDiskTest_half_empty_%s_%d.ll",
                    writerList.getClass().getSimpleName(), chunkOffset);
            final Path file = tempDir.resolve(TEMP_FILE_NAME);
            if (Files.exists(file)) {
                Files.delete(file); // Ensure no leftover file from previous tests
            }

            writerList.writeToFile(file);
            assertTrue(Files.exists(file), TEMP_FILE_NAME + " file does not exist.");

            // Create reader long list, i.e., reconstruct the long list from the file
            try (final AbstractLongList<?> readerList = readerFunction.apply(file, CONFIGURATION)) {
                // Validate the reconstructed list
                assertEquals(writerList.capacity(), readerList.capacity(), "Capacity mismatch in reconstructed list.");
                assertEquals(writerList.size(), readerList.size(), "Size mismatch in reconstructed list.");
                checkEmptyUpToIndex(readerList, newMinValidIndex);
                checkData(readerList, newMinValidIndex, SAMPLE_SIZE);
            } finally {
                // Clean up the temporary file
                Files.delete(file);
            }
        }
    }

    // UTIL METHODS

    private static <T extends LongList> T populateList(T longList) {
        return populateList(longList, SAMPLE_SIZE);
    }

    private static <T extends LongList> T populateList(T longList, int sampleSize) {
        longList.updateValidRange(0, sampleSize - 1);
        for (int i = 0; i < sampleSize; i++) {
            longList.put(i, i + 100);
        }
        return longList;
    }

    private static void checkData(final LongList longList) {
        checkData(longList, 0, SAMPLE_SIZE);
    }

    private static void checkData(final LongList longList, final int startIndex, final int endIndex) {
        for (int i = startIndex; i < endIndex; i++) {
            assertEquals(i + 100, longList.get(i, -1), "Unexpected value from longList.get(" + i + ")");
        }
    }

    private static void checkEmptyUpToIndex(LongList longList, int index) {
        for (int i = 0; i < index; i++) {
            assertEquals(0, longList.get(i), "Unexpected value for index " + i);
        }
    }

    private static void checkEmptyFromIndex(LongList longList, int index) {
        for (int i = index; i < SAMPLE_SIZE; i++) {
            assertEquals(0, longList.get(i), "Unexpected value for index " + i);
        }
    }

    private void checkRange() {
        for (int i = 0; i < SAMPLE_SIZE; i++) {
            final long readValue = longList.get(i, 0);
            assertEquals(i, readValue, "Longs don't match for " + i + " got [" + readValue + "] should be [" + i + "]");
        }

        final AtomicInteger atomicI = new AtomicInteger(0);
        longList.stream().forEach(readValue -> {
            final int i = atomicI.getAndIncrement();
            assertEquals(i, readValue, "Longs don't match for " + i + " got [" + readValue + "] should be [" + i + "]");
        });

        assertEquals(
                SAMPLE_SIZE,
                longList.stream().parallel().summaryStatistics().getCount(),
                "Stream size should match initial sample size");
    }

    protected void checkNumLongsPerChunk(final AbstractLongList<?> subject, final int expected) {
        assertEquals(
                expected,
                subject.getNumLongsPerChunk(),
                // TODO: double check this comment
                "On-heap implementations should respect constructor parameter for numLongsPerChunk");
    }
}
