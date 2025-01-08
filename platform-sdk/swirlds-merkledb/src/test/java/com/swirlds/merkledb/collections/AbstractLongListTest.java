/*
 * Copyright (C) 2016-2025 Hedera Hashgraph, LLC
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
import static com.swirlds.merkledb.collections.AbstractLongList.DEFAULT_MAX_LONGS_TO_STORE;
import static com.swirlds.merkledb.collections.AbstractLongList.DEFAULT_NUM_LONGS_PER_CHUNK;
import static com.swirlds.merkledb.collections.AbstractLongList.FILE_HEADER_SIZE_V2;
import static com.swirlds.merkledb.collections.LongList.IMPERMISSIBLE_VALUE;
import static com.swirlds.merkledb.test.fixtures.MerkleDbTestUtils.CONFIGURATION;
import static com.swirlds.merkledb.test.fixtures.MerkleDbTestUtils.checkDirectMemoryIsCleanedUpToLessThanBaseUsage;
import static com.swirlds.merkledb.test.fixtures.MerkleDbTestUtils.getDirectMemoryUsedBytes;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

// it takes longer for this suite to run after adding cross compatibility
// TODO: resolve constraint where some test depend on the certain sample size and other parameters
// TODO: improve parametrized tests names
// TODO: improve temp file names
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
abstract class AbstractLongListTest<T extends AbstractLongList<?>> {

    protected static final int SAMPLE_SIZE = 10_000;
    protected static final int MAX_VALID_INDEX = SAMPLE_SIZE - 1;
    protected static final int HALF_SAMPLE_SIZE = SAMPLE_SIZE / 2;

    private static final int OUT_OF_SAMPLE_INDEX = 13_000_123;
    private static final long REPL_VALUE = 42;
    private static final long DEFAULT_VALUE = 0;

    private static AbstractLongList<?> longList;

    protected abstract AbstractLongList<?> createLongList();

    // making those static might help de-duplicate the code in future
    @SuppressWarnings("SameParameterValue")
    protected abstract T createLongListWithChunkSizeInMb(final int chunkSizeInMb);

    protected abstract T createFullyParameterizedLongListWith(final int numLongsPerChunk, final long maxLongs);

    protected abstract T createLongListFromFile(final Path file) throws IOException;

    private static final BiFunction<Path, Configuration, AbstractLongList<?>> HEAP_READER = (file, config) -> {
        try {
            return new LongListHeap(file, config);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    };

    private static final BiFunction<Path, Configuration, AbstractLongList<?>> OFF_HEAP_READER = (file, config) -> {
        try {
            return new LongListOffHeap(file, config);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    };

    private static final BiFunction<Path, Configuration, AbstractLongList<?>> DISK_READER = (file, config) -> {
        try {
            return new LongListDisk(file, config);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    };

    // TODO: can validate this using beforeall and afterall
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
        for (int i = 0; i < SAMPLE_SIZE; i++) {
            longList.put(i, i + 100);
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
        // Close the longList
        if (longList != null) {
            longList.close();
        }
        // Check all memory is freed after DB is closed, but skip for LongListDisk
        // as LongListDisk use file-based operations (FileChannel#write in LongListDisk#closeChunk)
        // that don't immediately free memory due to OS-level caching
        if (!(longList instanceof LongListDisk)) {
            assertTrue(
                    checkDirectMemoryIsCleanedUpToLessThanBaseUsage(directMemoryUsedAtStart),
                    "Direct Memory used is more than base usage even after 20 gc() calls. At start was "
                            + (directMemoryUsedAtStart * BYTES_TO_MEBIBYTES) + "MB and is now "
                            + (getDirectMemoryUsedBytes() * BYTES_TO_MEBIBYTES)
                            + "MB");
        }
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
    void testInsertAtTheEndOfTheList() {
        try (AbstractLongList<?> list = createLongList()) {
            list.updateValidRange(0, DEFAULT_MAX_LONGS_TO_STORE - 1);
            assertDoesNotThrow(() -> list.put(DEFAULT_MAX_LONGS_TO_STORE - 1, 1));
        }
    }

    @Test
    void testInsertAtTheEndOfTheListCustomConfigured() {
        final int maxLongs = 10;
        try (AbstractLongList<?> list = createFullyParameterizedLongListWith(10, maxLongs)) {
            list.updateValidRange(0, maxLongs - 1);
            assertDoesNotThrow(() -> list.put(maxLongs - 1, 1));
        }
    }

    @Test
    void testUnsupportedVersion() throws URISyntaxException {
        final Path pathToList = ResourceLoader.getFile("test_data/LongList_unsupported_version.ll");
        assertThrows(IOException.class, () -> {
            //noinspection EmptyTryBlock
            try (final AbstractLongList<?> ignored = createLongListFromFile(pathToList)) {
                // no op
            }
        });
    }

    /**
     * Validate that AbstractLongList#close() and it's overrides does not create any negative side effects
     * for the future long list instances.
     *
     * @param tempDir temporary directory for storing test files, automatically cleaned up after the test.
     * @throws IOException if file operations fail.
     */
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

    // SAMPLE_SIZE should be 10K for this test, otherwise it will fail
    // questionable test
    @Test
    void testBackwardCompatibility_halfEmpty()
            throws URISyntaxException, IOException {
        final Path file = ResourceLoader.getFile("test_data/LongListOffHeapHalfEmpty_10k_10pc_v1.ll");

        // Create reader long list, i.e. reconstruct the long list from file
        try (final LongList readerList = createLongListFromFile(file)) {
            // half-empty
            checkEmptyUpToIndex(readerList, HALF_SAMPLE_SIZE);
            // half-full
            for (int i = HALF_SAMPLE_SIZE; i < SAMPLE_SIZE; i++) {
                assertEquals(i, readerList.get(i));
            }
        }
    }

    @Test
    void testReuseOfChunks_minValidIndex()
            throws IOException {
        final int NUM_LONGS_PER_CHUNK = 100;

        // Create a long list
        try (final AbstractLongList<?> longList = createFullyParameterizedLongListWith(NUM_LONGS_PER_CHUNK, SAMPLE_SIZE * 2)) {
            populateList(longList);
            checkData(longList, 0, SAMPLE_SIZE);

            long originalChannelSize = 0;
            if (longList instanceof LongListDisk) {
                // temporary file channel doesn't contain the header
                originalChannelSize = ((LongListDisk) longList).getCurrentFileChannel().size();
            }

            // freeing up some chunks
            longList.updateValidRange(HALF_SAMPLE_SIZE, SAMPLE_SIZE * 2 - 1);

            // using the freed up chunks
            for (int i = SAMPLE_SIZE; i < SAMPLE_SIZE + HALF_SAMPLE_SIZE; i++) {
                longList.put(i, i + 100);
            }

            if (longList instanceof LongListDisk) {
                // a longListDisk should have the same size as before because it has the same number of entries
                assertEquals(originalChannelSize, ((LongListDisk) longList).getCurrentFileChannel().size());
            }

            checkEmptyUpToIndex(longList, HALF_SAMPLE_SIZE);
            checkData(longList, HALF_SAMPLE_SIZE, SAMPLE_SIZE + HALF_SAMPLE_SIZE);
        }
    }

    @Test
    void testReuseOfChunks_maxValidIndex()
            throws IOException {
        final int NUM_LONGS_PER_CHUNK = 100;

        // Create a long list
        try (final AbstractLongList<?> longList = createFullyParameterizedLongListWith(NUM_LONGS_PER_CHUNK, SAMPLE_SIZE * 2)) {
            populateList(longList);
            checkData(longList, 0, SAMPLE_SIZE);

            long originalChannelSize = 0;
            if (longList instanceof LongListDisk) {
                // temporary file channel doesn't contain the header
                originalChannelSize = ((LongListDisk) longList).getCurrentFileChannel().size();
            }

            // freeing up some chunks
            longList.updateValidRange(0, HALF_SAMPLE_SIZE);

            // using the freed up chunks
            longList.updateValidRange(0, SAMPLE_SIZE - 1);
            for (int i = HALF_SAMPLE_SIZE; i < SAMPLE_SIZE; i++) {
                longList.put(i, i + 100);
            }

            if (longList instanceof LongListDisk) {
                // a longListDisk should have the same size as before because it has the same number of entries
                assertEquals(originalChannelSize, ((LongListDisk) longList).getCurrentFileChannel().size());
            }

            checkData(longList, 0, SAMPLE_SIZE);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5, 10, 50})
    void minValidIndexRespectedInForEachTest(final int countDivider) throws InterruptedException {
        final int sampleSize = SAMPLE_SIZE;
        try (final AbstractLongList<?> list = createFullyParameterizedLongListWith(
                sampleSize / 100, // 100 chunks, 100 longs each
                sampleSize + DEFAULT_NUM_LONGS_PER_CHUNK)) {
            list.updateValidRange(0, SAMPLE_SIZE - 1);
            for (int i = 1; i < SAMPLE_SIZE; i++) {
                list.put(i, i + 1);
            }
            final long minIndex = sampleSize / countDivider;
            list.updateValidRange(minIndex, list.size() - 1);
            final AtomicLong count = new AtomicLong(0);
            final Set<Long> keysInForEach = new HashSet<>();
            list.forEach((path, location) -> {
                count.incrementAndGet();
                keysInForEach.add(path);
                assertEquals(path + 1, location);
            });
            assertEquals(sampleSize - minIndex, count.get(), "Wrong number of valid index entries");
            assertEquals(sampleSize - minIndex, keysInForEach.size(), "Wrong number of valid index entries");
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
        return Stream.of(
                Arguments.of(writerSupplier, HEAP_READER),
                Arguments.of(writerSupplier, OFF_HEAP_READER),
                Arguments.of(writerSupplier, DISK_READER));
    }

    @ParameterizedTest
    @MethodSource("longListWriterReaderPairsProvider")
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

    @ParameterizedTest
    @MethodSource("longListWriterReaderPairsProvider")
    void writeAndReadBackBigIndex(
            Supplier<AbstractLongList<?>> writerSupplier,
            BiFunction<Path, Configuration, AbstractLongList<?>> readerFunction,
            @TempDir final Path tempDir)
            throws IOException {

        try (final AbstractLongList<?> writerList = writerSupplier.get()) {

            long bigIndex = Integer.MAX_VALUE + 1L;
            writerList.updateValidRange(bigIndex, bigIndex);
            writerList.put(bigIndex, 1);

            assertEquals(1, writerList.get(bigIndex));
            final Path file = tempDir.resolve("LongListLargeIndex.ll");
            if (Files.exists(file)) {
                Files.delete(file);
            }
            writerList.writeToFile(file);

            try (final LongList readerList = readerFunction.apply(file, CONFIGURATION)) {
                assertEquals(1, readerList.get(bigIndex));
            } finally {
                Files.delete(file);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("longListWriterReaderPairsProvider")
    void testCustomNumberOfLongs(
            Supplier<AbstractLongList<?>> writerSupplier,
            BiFunction<Path, Configuration, AbstractLongList<?>> readerFunction,
            @TempDir final Path tempDir)
            throws IOException {

        try (final AbstractLongList<?> writerList = writerSupplier.get()) {

            writerList.updateValidRange(0, SAMPLE_SIZE - 1);
            for (int i = 0; i < SAMPLE_SIZE; i++) {
                writerList.put(i, i + 1);
            }
            final Path file = tempDir.resolve("LongListOffHeapCustomLongCount.ll");
            // write longList data
            if (Files.exists(file)) {
                Files.delete(file);
            }
            writerList.writeToFile(file);

            try (final AbstractLongList<?> readerList = readerFunction.apply(file, CONFIGURATION)) {
                assertEquals(writerList.dataCopy().size(), readerList.dataCopy().size());
            } finally {
                Files.delete(file);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("longListWriterReaderPairsProvider")
    void testShrinkList_minValidIndex(
            Supplier<AbstractLongList<?>> writerSupplier,
            BiFunction<Path, Configuration, AbstractLongList<?>> readerFunction,
            @TempDir final Path tempDir)
            throws IOException {
        final int NUM_LONGS_PER_CHUNK = 10;

        // Create a long list
        try (final AbstractLongList<?> writerList = createFullyParameterizedLongListWith(NUM_LONGS_PER_CHUNK, SAMPLE_SIZE * 2)) {
            populateList(writerList);
            checkData(writerList, 0, SAMPLE_SIZE);

            long originalFileSize = 0;
            if (writerList instanceof LongListDisk) {
                // temporary file channel doesn't contain the header
                originalFileSize = ((LongListDisk) writerList).getCurrentFileChannel().size() + writerList.currentFileHeaderSize;
            }

            writerList.updateValidRange(HALF_SAMPLE_SIZE, SAMPLE_SIZE * 2 - 1);

            // half-empty
            checkEmptyUpToIndex(writerList, HALF_SAMPLE_SIZE);
            // half-full
            checkData(writerList, HALF_SAMPLE_SIZE, SAMPLE_SIZE);

            final Path shrunkListFile = tempDir.resolve("testShrinkList_minValidIndex.ll");
            if (Files.exists(shrunkListFile)) {
                Files.delete(shrunkListFile);
            }
            // if we write to the same file, it doesn't shrink after the min valid index update
            writerList.writeToFile(shrunkListFile);

            if (writerList instanceof LongListDisk) {
                assertEquals(HALF_SAMPLE_SIZE * Long.BYTES, originalFileSize - Files.size(shrunkListFile));
            }

            // Create reader long list, i.e. reconstruct the long list from file
            try (final LongList readerList = readerFunction.apply(shrunkListFile, CONFIGURATION)) {
                for (int i = 0; i < SAMPLE_SIZE; i++) {
                    assertEquals(
                            writerList.get(i),
                            readerList.get(i),
                            "Unexpected value in a loaded longListDisk, index=" + i);
                }
            } finally {
                // Clean up the temporary file
                Files.delete(shrunkListFile);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("longListWriterReaderPairsProvider")
    void testShrinkList_maxValidIndex(
            Supplier<AbstractLongList<?>> writerSupplier,
            BiFunction<Path, Configuration, AbstractLongList<?>> readerFunction,
            @TempDir final Path tempDir)
            throws IOException {
        final int NUM_LONGS_PER_CHUNK = 10;

        // Create a long list
        try (final AbstractLongList<?> writerList = createFullyParameterizedLongListWith(NUM_LONGS_PER_CHUNK, SAMPLE_SIZE * 2)) {
            populateList(writerList);
            checkData(writerList, 0, SAMPLE_SIZE);

            long originalFileSize = 0;
            if (writerList instanceof LongListDisk) {
                // temporary file channel doesn't contain the header
                originalFileSize = ((LongListDisk) writerList).getCurrentFileChannel().size() + writerList.currentFileHeaderSize;
            }

            writerList.updateValidRange(0, HALF_SAMPLE_SIZE - 1);

            // half-empty
            checkEmptyFromIndex(writerList, HALF_SAMPLE_SIZE);
            // half-full
            checkData(writerList, 0, HALF_SAMPLE_SIZE - 1);

            final Path shrunkListFile = tempDir.resolve("testShrinkList_maxValidIndex.ll");
            if (Files.exists(shrunkListFile)) {
                Files.delete(shrunkListFile);
            }
            // if we write to the same file, it doesn't shrink after the min valid index update
            writerList.writeToFile(shrunkListFile);

            if (writerList instanceof LongListDisk) {
                assertEquals(HALF_SAMPLE_SIZE * Long.BYTES, originalFileSize - Files.size(shrunkListFile));
            }

            // Create reader long list, i.e. reconstruct the long list from file
            try (final LongList readerList = readerFunction.apply(shrunkListFile, CONFIGURATION)) {
                for (int i = 0; i < SAMPLE_SIZE; i++) {
                    assertEquals(
                            writerList.get(i),
                            readerList.get(i),
                            "Unexpected value in a loaded longListDisk, index=" + i);
                }
            } finally {
                // Clean up the temporary file
                Files.delete(shrunkListFile);
            }
        }
    }

    /**
     * Takes a stream of (writerSupplier, firstReader) pairs,
     * and for each pair, returns multiple (writerSupplier, firstReader, secondReader) triples.
     *
     * @param writerReaderPairs a stream of (writerSupplier, firstReader) pairs
     * @return a stream of argument triples (writerSupplier, firstReader, secondReader)
     */
    protected static Stream<Arguments> longListWriterSecondReaderPairsProviderBase(
            final Stream<Arguments> writerReaderPairs) {
        // “Expand” each (writerSupplier, firstReader) into (writerSupplier, firstReader, secondReader).
        return writerReaderPairs.flatMap(pair -> {
            // The existing pair is [writerSupplier, firstReader].
            final Object writerSupplier = pair.get()[0];
            final Object firstReader   = pair.get()[1];

            // Now, produce multiple outputs, each with a different secondReader:
            return Stream.of(
                    Arguments.of(writerSupplier, firstReader, HEAP_READER),
                    Arguments.of(writerSupplier, firstReader, OFF_HEAP_READER),
                    Arguments.of(writerSupplier, firstReader, DISK_READER)
            );
        });
    }

    @ParameterizedTest
    @MethodSource("longListWriterSecondReaderPairsProvider")
    void updateListCreatedFromSnapshotPersistAndVerify(
            Supplier<AbstractLongList<?>> writerSupplier,
            BiFunction<Path, Configuration, AbstractLongList<?>> readerFunction,
            BiFunction<Path, Configuration, AbstractLongList<?>> secondReaderFunction,
            @TempDir final Path tempDir)
            throws IOException {

        final int sampleSize = SAMPLE_SIZE;

        try (final AbstractLongList<?> list = createFullyParameterizedLongListWith(
                sampleSize / 100, // 100 chunks, 100 longs each
                sampleSize + DEFAULT_NUM_LONGS_PER_CHUNK)) {
            list.updateValidRange(0, SAMPLE_SIZE - 1);
            for (int i = 0; i < SAMPLE_SIZE; i++) {
                list.put(i, i + 1);
            }
            final Path file = tempDir.resolve("LongListOffHeap.ll");
            if (Files.exists(file)) {
                Files.delete(file);
            }
            // write longList data
            list.writeToFile(file);

            // Create reader long list, i.e. reconstruct the long list from file
            try (final LongList longListFromFile = readerFunction.apply(file, CONFIGURATION)) {

                for (int i = 0; i < longListFromFile.size(); i++) {
                    assertEquals(list.get(i), longListFromFile.get(i));
                }
                // write longList data again
                Files.delete(file);
                longListFromFile.writeToFile(file);

                // restoring the list from the file again
                try (final LongList longListFromFile2 = secondReaderFunction.apply(file, CONFIGURATION)) {
                    for (int i = 0; i < longListFromFile2.size(); i++) {
                        assertEquals(longListFromFile.get(i), longListFromFile2.get(i));
                    }
                }
            }
        }
    }

    // SAMPLE_SIZE should be 10K for this test, otherwise it will fail
    @ParameterizedTest
    @MethodSource("longListWriterSecondReaderPairsProvider")
    void updateMinToTheLowerEnd(
            Supplier<AbstractLongList<?>> writerSupplier,
            BiFunction<Path, Configuration, AbstractLongList<?>> readerFunction,
            BiFunction<Path, Configuration, AbstractLongList<?>> secondReaderFunction,
            @TempDir final Path tempDir)
            throws IOException {

        final int NUM_LONGS_PER_CHUNK = 10;

        // Create a long list
        try (final AbstractLongList<?> writerList = createFullyParameterizedLongListWith(NUM_LONGS_PER_CHUNK, SAMPLE_SIZE)) {
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

                try (final LongList zeroMinValidIndexList = secondReaderFunction.apply(zeroMinValidIndex, CONFIGURATION)) {
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
        try (final AbstractLongList<?> writerList = createFullyParameterizedLongListWith(numLongsPerChunk, maxLongs)) {
            writerList.updateValidRange(startIndex, endIndex);

            for (int i = startIndex; i <= endIndex; i++) {
                writerList.put(i, i + 100);
            }

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
                System.out.println("Writer: " + writerList.getClass() + " Reader: " + readerList.getClass());
                // Validate the reconstructed list
                assertEquals(writerList.capacity(), readerList.capacity(), "Capacity mismatch in reconstructed list.");
                assertEquals(writerList.size(), readerList.size(), "Size mismatch in reconstructed list.");

                for (int i = startIndex; i <= endIndex; i++) {
                    assertEquals(i + 100, readerList.get(i));
                }
            } finally {
                // Clean up the temporary file
                Files.delete(file);
            }
        }
    }

    /**
     * Combines writer-reader pairs with predefined chunk offset configurations (first set) for testing.
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
                    Arguments.of(writerSupplier, readerFunction, 0),
                    Arguments.of(writerSupplier, readerFunction, 5));
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

    /**
     * Combines writer-reader pairs with predefined chunk offset configurations (second set) for testing.
     *
     * @param writerReaderPairs a stream of writer-reader pairs
     * @return a stream of arguments combining writer-reader pairs with chunk offset parameters
     */
    protected static Stream<Arguments> longListWriterReaderOffsetPairs2ProviderBase(
            Stream<Arguments> writerReaderPairs) {
        return writerReaderPairs.flatMap(pair -> {
            Object writerSupplier = pair.get()[0];
            Object readerFunction = pair.get()[1];

            return Stream.of(
                    // writerSupplier, readerFunction, chunkOffset
                    Arguments.of(writerSupplier, readerFunction, 0),
                    Arguments.of(writerSupplier, readerFunction, 1),
                    Arguments.of(writerSupplier, readerFunction, 5_000),
                    Arguments.of(writerSupplier, readerFunction, 9_999),
                    Arguments.of(writerSupplier, readerFunction, 10_000));
        });
    }

    // SAMPLE_SIZE should be 1M for this test, otherwise it will fail
    @ParameterizedTest
    @MethodSource("longListWriterReaderOffsetPairs2Provider") // chunk size is 10K longs
    void testPersistListWithNonZeroMinValidIndex(
            Supplier<AbstractLongList<?>> writerSupplier,
            BiFunction<Path, Configuration, AbstractLongList<?>> readerFunction,
            final int chunkOffset,
            @TempDir final Path tempDir)
            throws IOException {

        final int SAMPLE_SIZE = 1_000_000;

        // Create a writer long list
        try (final AbstractLongList<?> writerList = createFullyParameterizedLongListWith(
                SAMPLE_SIZE / 100, // 100 chunks
                SAMPLE_SIZE)) {

            writerList.updateValidRange(0, SAMPLE_SIZE - 1);
            for (int i = 1; i < SAMPLE_SIZE; i++) {
                writerList.put(i, i);
            }

            writerList.updateValidRange(SAMPLE_SIZE / 2 + chunkOffset, writerList.size() - 1);

            final Path file = tempDir.resolve("LongListOffHeapHalfEmpty.ll");
            // write longList data
            if (Files.exists(file)) {
                Files.delete(file);
            }
            writerList.writeToFile(file);

            // Create reader long list, i.e., reconstruct the long list from the file
            try (final AbstractLongList<?> readerList = readerFunction.apply(file, CONFIGURATION)) {
                for (int i = 0; i < readerList.size(); i++) {
                    assertEquals(writerList.get(i), readerList.get(i));
                }
            } finally {
                // Clean up the temporary file
                Files.delete(file);
            }
        }
    }

    // SAMPLE_SIZE should be 1M for this test, otherwise it will fail
    @ParameterizedTest
    @MethodSource("longListWriterReaderOffsetPairs2Provider") // chunk size is 10K longs
    void testPersistShrunkList(
            Supplier<AbstractLongList<?>> writerSupplier,
            BiFunction<Path, Configuration, AbstractLongList<?>> readerFunction,
            final int chunkOffset,
            @TempDir final Path tempDir)
            throws IOException {

        final int SAMPLE_SIZE = 1_000_000;

        // Create a writer long list
        try (final AbstractLongList<?> writerList = createFullyParameterizedLongListWith(
                SAMPLE_SIZE / 100, // 100 chunks
                SAMPLE_SIZE)) {

            writerList.updateValidRange(0, SAMPLE_SIZE - 1);
            for (int i = 1; i < SAMPLE_SIZE; i++) {
                writerList.put(i, i);
            }

            writerList.updateValidRange(0, SAMPLE_SIZE / 2 + chunkOffset);

            final Path file = tempDir.resolve("LongListOffHeapHalfEmpty.ll");
            // write longList data
            if (Files.exists(file)) {
                Files.delete(file);
            }
            writerList.writeToFile(file);

            // Create reader long list, i.e., reconstruct the long list from the file
            try (final AbstractLongList<?> readerList = readerFunction.apply(file, CONFIGURATION)) {
                for (int i = 0; i < readerList.size(); i++) {
                    assertEquals(writerList.get(i), readerList.get(i));
                }
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
            assertEquals(i + 100, readValue, "Longs don't match for " + i + " got [" + readValue + "] should be [" + i + "]");
        }

        final AtomicInteger atomicI = new AtomicInteger(0);
        longList.stream().forEach(readValue -> {
            final int i = atomicI.getAndIncrement();
            assertEquals(i + 100, readValue, "Longs don't match for " + i + " got [" + readValue + "] should be [" + i + "]");
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
                "Long List implementations should respect constructor parameter for numLongsPerChunk");
    }
}
