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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.swirlds.common.test.fixtures.io.ResourceLoader;
import com.swirlds.config.api.Configuration;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.Spliterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.LongConsumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
abstract class AbstractLongListTest<T extends AbstractLongList<?>> {

    // Constants (used in ordered and some of the other tests)

    protected static final int SAMPLE_SIZE = 1_000_000;
    protected static final int MAX_VALID_INDEX = SAMPLE_SIZE - 1;
    protected static final int HALF_SAMPLE_SIZE = SAMPLE_SIZE / 2;

    private static final int OUT_OF_SAMPLE_INDEX = 13_000_123;
    private static final long REPL_VALUE = 42;
    private static final long DEFAULT_VALUE = 0;

    // Variables used in ordered tests

    private static AbstractLongList<?> longList;

    /**
     * Keep track of initial direct memory used already, so we can check if we leek over and above what we started with
     */
    private static long directMemoryUsedAtStart;

    // Factory methods for creating different configurations of LongList instances

    protected abstract AbstractLongList<?> createLongList();

    @SuppressWarnings("SameParameterValue")
    protected abstract T createLongListWithChunkSizeInMb(final int chunkSizeInMb);

    protected abstract T createFullyParameterizedLongListWith(final int numLongsPerChunk, final long maxLongs);

    protected abstract T createLongListFromFile(final Path file) throws IOException;

    @BeforeAll
    static void beforeAll() {
        directMemoryUsedAtStart = getDirectMemoryUsedBytes();
    }

    @AfterAll
    static void afterAll() {
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

    // Ordered tests

    @Test
    @Order(1)
    void createData() {
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

        longList.updateValidRange(0, SAMPLE_SIZE - 1);
        for (int i = 0; i < SAMPLE_SIZE; i++) {
            longList.put(i, i + 100);
        }
    }

    @Test
    @Order(2)
    void checkData() {
        for (int i = 0; i < SAMPLE_SIZE; i++) {
            final long readValue = longList.get(i, 0);
            assertEquals(i + 100, readValue, "Longs don't match for " + i + " got [" + readValue + "] should be [" + i + 100 + "]");
        }

        final AtomicInteger atomicI = new AtomicInteger(0);
        longList.stream().forEach(readValue -> {
            final int i = atomicI.getAndIncrement();
            assertEquals(i + 100, readValue, "Longs don't match for " + i + " got [" + readValue + "] should be [" + i + 100 + "]");
        });

        assertEquals(
                SAMPLE_SIZE,
                longList.stream().parallel().summaryStatistics().getCount(),
                "Stream size should match initial sample size");
    }

    @Test
    @Order(3)
    void offEndExpand() {
        longList.updateValidRange(0, OUT_OF_SAMPLE_INDEX);
        longList.put(OUT_OF_SAMPLE_INDEX, OUT_OF_SAMPLE_INDEX);
        assertEquals(
                OUT_OF_SAMPLE_INDEX,
                longList.get(OUT_OF_SAMPLE_INDEX, 0),
                "Failed to save and get " + OUT_OF_SAMPLE_INDEX);
    }

    @Test
    @Order(4)
    void putIfEqual() {
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
    void close() {
        if (longList != null) {
            longList.close();
        }
    }

    // Tests without `@Order`

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
        assertThrows(
                ArithmeticException.class,
                () -> createFullyParameterizedLongListWith((Integer.MAX_VALUE / 8) + 1, Integer.MAX_VALUE),
                "Check that ArithmeticException of num longs per chuck is too big");
        assertThrows(
                IllegalArgumentException.class,
                () -> createFullyParameterizedLongListWith(Integer.MAX_VALUE - 1, Integer.MAX_VALUE),
                "Check that IllegalArgumentException of num longs per chuck is too big");
    }

    @Test
    void chunkSizeFactoryWorks() {
        final int expectedNum = Math.toIntExact(2 * MEBIBYTES_TO_BYTES / Long.BYTES);

        try (final AbstractLongList<?> longList = createLongListWithChunkSizeInMb(2)) {
            assertEquals(
                    expectedNum,
                    longList.getNumLongsPerChunk(),
                    "Long List implementations should respect constructor parameter for numLongsPerChunk");
        }
    }

    @Test
    void insertAtTheEndOfTheList() {
        try (final LongList longList = createLongList()) {
            longList.updateValidRange(0, DEFAULT_MAX_LONGS_TO_STORE - 1);
            assertDoesNotThrow(() -> longList.put(DEFAULT_MAX_LONGS_TO_STORE - 1, 1));
        }
    }

    @Test
    void insertAtTheEndOfTheListCustomConfigured() {
        final int MAX_LONGS = 10;
        try (final LongList longList = createFullyParameterizedLongListWith(10, MAX_LONGS)) {
            longList.updateValidRange(0, MAX_LONGS - 1);
            assertDoesNotThrow(() -> longList.put(MAX_LONGS - 1, 1));
        }
    }

    @Test
    void unsupportedVersion() throws URISyntaxException {
        final Path pathToList = ResourceLoader.getFile("test_data/LongList_unsupported_version.ll");
        assertThrows(IOException.class, () -> {
            //noinspection EmptyTryBlock
            try (final LongList ignored = createLongListFromFile(pathToList)) {
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
        final Path file = tempDir.resolve("testCloseAndRecreateLongListMultipleTimes.ll");
        if (Files.exists(file)) {
            Files.delete(file);
        }

        try (final LongList longList = createLongList()) {
            longList.updateValidRange(0, SAMPLE_SIZE);
            for (int i = 0; i <= SAMPLE_SIZE; i++) {
                longList.put(i, i + 100);
            }
            longList.writeToFile(file);
            assertTrue(Files.exists(file), "The file should exist after writing with the first list");
        }

        try (final LongList longListFromFile = createLongListFromFile(file)) {
            for (int i = 0; i <= SAMPLE_SIZE; i++) {
                assertEquals(i + 100, longListFromFile.get(i), "Data should match in the second list");
            }
        }

        try (final LongList anotherLongListFromFile = createLongListFromFile(file)) {
            for (int i = 0; i <= SAMPLE_SIZE; i++) {
                assertEquals(i + 100, anotherLongListFromFile.get(i), "Data should still match in the third list");
            }
        }
    }

    @Test
    void backwardCompatibilityHalfEmpty_10K() throws URISyntaxException, IOException {
        // SAMPLE_SIZE should be 10K for this test
        final int SAMPLE_SIZE = 10_000;
        final int HALF_SAMPLE_SIZE = SAMPLE_SIZE / 2;

        // Load a pre-existing file representing a half-empty LongList
        final Path longListFile = ResourceLoader.getFile("test_data/LongListHalfEmpty_10k_10pc_v1.ll");

        // Reconstruct the long list from the file and validate its content
        try (final LongList readerList = createLongListFromFile(longListFile)) {
            // Verify the first half of the list is empty
            checkEmptyUpToIndex(readerList, HALF_SAMPLE_SIZE);

            // Verify the second half of the list contains expected values
            for (int i = HALF_SAMPLE_SIZE; i < SAMPLE_SIZE; i++) {
                assertEquals(i, readerList.get(i), "Mismatch in value at index " + i);
            }
        }
    }

    @Test
    void reuseOfChunksMinValidIndex() throws IOException {
        // Create a LongList with the specified number of longs per chunk and max longs
        try (final LongList longList = createFullyParameterizedLongListWith(100, SAMPLE_SIZE * 2)) {
            // Populate the list with initial values and validate its contents
            populateList(longList);
            checkData(longList);

            // Save the original file size for comparison (for LongListDisk)
            // temporary file channel doesn't contain the header
            long originalChannelSize = 0;
            if (longList instanceof LongListDisk) {
                originalChannelSize = ((LongListDisk) longList).getCurrentFileChannel().size();
            }

            // Free up chunks below HALF_SAMPLE_SIZE by updating the minimum valid index
            longList.updateValidRange(HALF_SAMPLE_SIZE, SAMPLE_SIZE * 2 - 1);

            // Populate newly valid range using the previously freed-up chunks
            for (int i = SAMPLE_SIZE; i < SAMPLE_SIZE + HALF_SAMPLE_SIZE; i++) {
                longList.put(i, i + 100);
            }

            if (longList instanceof LongListDisk) {
                // Validate that the file size has not changed after reusing chunks
                assertEquals(originalChannelSize, ((LongListDisk) longList).getCurrentFileChannel().size());
            }

            // Verify that indices below HALF_SAMPLE_SIZE are cleared
            checkEmptyUpToIndex(longList, HALF_SAMPLE_SIZE);

            // Verify that all values in the newly valid range are correctly populated
            checkData(longList, HALF_SAMPLE_SIZE, SAMPLE_SIZE + HALF_SAMPLE_SIZE);
        }
    }

    @Test
    void reuseOfChunksMaxValidIndex() throws IOException {
        // Create a LongList with the specified number of longs per chunk and max longs
        try (final LongList longList = createFullyParameterizedLongListWith(100, SAMPLE_SIZE * 2)) {
            // Populate the list with initial values and validate its contents
            populateList(longList);
            checkData(longList);

            // Save the original file size for comparison (for LongListDisk)
            // temporary file channel doesn't contain the header
            long originalChannelSize = 0;
            if (longList instanceof LongListDisk) {
                originalChannelSize = ((LongListDisk) longList).getCurrentFileChannel().size();
            }

            // Free up chunks beyond HALF_SAMPLE_SIZE by updating the valid range
            longList.updateValidRange(0, HALF_SAMPLE_SIZE);

            // Extend the valid range to include the previously freed-up chunks
            longList.updateValidRange(0, SAMPLE_SIZE - 1);

            // Populate the newly valid range to reuse freed-up chunks
            for (int i = HALF_SAMPLE_SIZE; i < SAMPLE_SIZE; i++) {
                longList.put(i, i + 100);
            }

            if (longList instanceof LongListDisk) {
                // Validate that the file size has not changed after reusing chunks
                assertEquals(originalChannelSize, ((LongListDisk) longList).getCurrentFileChannel().size());
            }

            // Ensure all data, including reused chunk data, is correct
            checkData(longList);
        }
    }

    @ParameterizedTest(name = "[{index}] countDivider={0}")
    @ValueSource(ints = {2, 3, 4, 5, 10, 50})
    void minValidIndexRespectedInForEach_10K(final int countDivider) throws InterruptedException {
        // SAMPLE_SIZE is set to 10K for this test
        final int SAMPLE_SIZE = 10_000;

        // Create a LongList where each chunk holds 100 longs, resulting in 100 chunks
        try (final LongList longList = createFullyParameterizedLongListWith(
                SAMPLE_SIZE / 100,
                SAMPLE_SIZE + DEFAULT_NUM_LONGS_PER_CHUNK)) {

            // Populate the list with initial values and validate its contents
            populateList(longList, SAMPLE_SIZE);
            checkData(longList, 0, SAMPLE_SIZE);

            // Update the minimum valid index to exclude entries below SAMPLE_SIZE / countDivider
            final long minIndex = SAMPLE_SIZE / countDivider;
            longList.updateValidRange(minIndex, longList.size() - 1);

            // Count valid entries and collect their indices
            final AtomicLong count = new AtomicLong(0);
            final Set<Long> keysInForEach = new HashSet<>();
            longList.forEach((path, location) -> {
                count.incrementAndGet();
                keysInForEach.add(path);

                assertEquals(path + 100, location, "Mismatch in value for index " + path);
            });

            // Ensure the number of valid indices matches the expected range
            assertEquals(
                    SAMPLE_SIZE - minIndex,
                    count.get(),
                    "The number of valid index entries does not match expected count");
            assertEquals(
                    SAMPLE_SIZE - minIndex,
                    keysInForEach.size(),
                    "The size of valid index set does not match expected count");
        }
    }

    @Test
    void spliteratorEdgeCases() {
        final LongConsumer firstConsumer = mock(LongConsumer.class);
        final LongConsumer secondConsumer = mock(LongConsumer.class);

        try (final LongList longList = createFullyParameterizedLongListWith(32, 32)) {
            longList.updateValidRange(0, 3);
            for (int i = 1; i <= 3; i++) {
                longList.put(i, i);
            }

            final LongListSpliterator subject = new LongListSpliterator(longList);

            assertThrows(
                    IllegalStateException.class,
                    subject::getComparator,
                    "An unordered spliterator should not be asked to provide an ordering");

            final Spliterator.OfLong firstSplit = subject.trySplit();
            assertNotNull(firstSplit, "firstSplit should not be null");
            assertEquals(2, subject.estimateSize(), "Splitting 4 elements should yield 2");
            final Spliterator.OfLong secondSplit = subject.trySplit();
            assertNotNull(secondSplit, "secondSplit should not be null");
            assertEquals(1, subject.estimateSize(), "Splitting 2 elements should yield 1");
            assertNull(subject.trySplit(), "Splitting 1 element should yield null");

            assertTrue(firstSplit.tryAdvance(firstConsumer), "First split should yield 0 first");
            verify(firstConsumer).accept(0);
            assertTrue(firstSplit.tryAdvance(firstConsumer), "First split should yield 1 second");
            verify(firstConsumer).accept(1);
            assertFalse(firstSplit.tryAdvance(firstConsumer), "First split should be exhausted after 2 yields");

            secondSplit.forEachRemaining(secondConsumer);
            verify(secondConsumer).accept(2);
            verifyNoMoreInteractions(secondConsumer);
        }
    }

    // Parametrized tests to test cross compatibility between the Long List implementations

    /**
     * A named factory for producing new {@link AbstractLongList} instances, used primarily as a "writer"
     * in parameterized tests. The {@code name} field is for logging or display in test output, and
     * {@code createInstance} is the function that constructs a new {@link AbstractLongList}.
     */
    public record LongListWriterFactory(String name, Supplier<AbstractLongList<?>> createInstance) {
        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * A named factory for reconstructing {@link AbstractLongList} instances from a file, serving as a "reader"
     * in parameterized tests. The {@code name} field is for test output identification, and
     * {@code createFromFile} is a function that loads a {@link AbstractLongList} given a {@link Path}
     * and {@link Configuration}.
     */
    public record LongListReaderFactory(String name,
                                        BiFunction<Path, Configuration, AbstractLongList<?>> createFromFile) {
        @Override
        public String toString() {
            return name;
        }
    }

    /** Factories (named suppliers) for creating different {@link AbstractLongList} implementations. */
    static LongListWriterFactory heapWriterFactory = new LongListWriterFactory(
            LongListHeap.class.getSimpleName(),
            LongListHeap::new
    );
    static LongListWriterFactory offHeapWriterFactory = new LongListWriterFactory(
            LongListOffHeap.class.getSimpleName(),
            LongListOffHeap::new
    );
    static LongListWriterFactory diskWriterFactory = new LongListWriterFactory(
            LongListDisk.class.getSimpleName(),
            () -> new LongListDisk(CONFIGURATION)
    );

    /**
     * Factories (named BiFunctions) for reconstructing different {@link AbstractLongList}
     * implementations from files.
     */
    static LongListReaderFactory heapReaderFactory = new LongListReaderFactory(
            LongListHeap.class.getSimpleName(),
            (file, config) -> {
                try {
                    return new LongListHeap(file, config);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
    );
    static LongListReaderFactory offHeapReaderFactory = new LongListReaderFactory(
            LongListOffHeap.class.getSimpleName(),
            (file, config) -> {
                try {
                    return new LongListOffHeap(file, config);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
    );
    static LongListReaderFactory diskReaderFactory = new LongListReaderFactory(
            LongListDisk.class.getSimpleName(),
            (file, config) -> {
                try {
                    return new LongListDisk(file, config);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
    );

    /**
     * Generates a stream of writer-reader argument pairs for testing cross-compatibility
     * of different long list implementations. The writer implementation is supplied as
     * a parameter, and the method pairs it with three readers (heap, off-heap, and disk-based)
     * to test whether data written by one implementation can be correctly read by another.
     * <p>
     * This method is used internally to support the creation of specific writer-reader pairs
     * for different test configurations.
     *
     * @param writerFactory a supplier providing the writer long list implementation
     * @return a stream of arguments containing the writer and its corresponding readers
     */
    protected static Stream<Arguments> longListWriterBasedPairsProvider(final LongListWriterFactory writerFactory) {
        return Stream.of(
                Arguments.of(writerFactory, heapReaderFactory),
                Arguments.of(writerFactory, offHeapReaderFactory),
                Arguments.of(writerFactory, diskReaderFactory));
    }

    @ParameterizedTest(name = "[{index}] Writer={0}, Reader={1}")
    @MethodSource("longListWriterReaderPairsProvider")
    void writeAndReadBackEmptyList(
            final LongListWriterFactory writerFactory,
            final LongListReaderFactory readerFactory,
            @TempDir final Path tempDir)
            throws IOException {

        // Create a writer LongList
        try (final LongList writerList = writerFactory.createInstance().get()) {
            // Write the empty LongList to a file and verify its existence
            final String TEMP_FILE_NAME = String.format(
                    "testWriteAndReadBackEmptyList_write_%s_read_back_%s.ll",
                    writerFactory,
                    readerFactory);
            final Path longListFile = writeLongListToFileAndVerify(writerList, TEMP_FILE_NAME, tempDir);

            try (final LongList readerList = readerFactory.createFromFile().apply(longListFile, CONFIGURATION)) {
                // Validate the reconstructed list's attributes
                assertEquals(writerList.capacity(), readerList.capacity(), "Capacity mismatch in reconstructed list.");
                assertEquals(writerList.size(), readerList.size(), "Size mismatch in reconstructed list.");
            } finally {
                Files.delete(longListFile);
            }
        }
    }

    @ParameterizedTest(name = "[{index}] Writer={0}, Reader={1}")
    @MethodSource("longListWriterReaderPairsProvider")
    void writeAndReadBack(
            final LongListWriterFactory writerFactory,
            final LongListReaderFactory readerFactory,
            @TempDir final Path tempDir)
            throws IOException {

        // Create a writer LongList
        try (final LongList writerList = writerFactory.createInstance().get()) {
            // Populate the long list with initial values and validate its contents
            populateList(writerList);
            checkData(writerList);

            // Write the long list to a file and verify its existence
            final String TEMP_FILE_NAME = String.format(
                    "testWriteAndReadBack_write_%s_read_back_%s.ll",
                    writerFactory,
                    readerFactory);
            final Path longListFile = writeLongListToFileAndVerify(writerList, TEMP_FILE_NAME, tempDir);

            // Check that the file size matches the expected data size
            assertEquals(
                    (FILE_HEADER_SIZE_V2 + (Long.BYTES * (long) SAMPLE_SIZE)),
                    Files.size(longListFile),
                    "Expected file to contain all the data so its size [" + Files.size(longListFile)
                            + "] should have been header plus longs data size ["
                            + (FILE_HEADER_SIZE_V2 + (Long.BYTES * (SAMPLE_SIZE)))
                            + "]");

            // Reconstruct the LongList from the file
            try (final LongList readerList = readerFactory.createFromFile().apply(longListFile, CONFIGURATION)) {
                // Validate the reconstructed list's attributes
                assertEquals(writerList.capacity(), readerList.capacity(), "Capacity mismatch in reconstructed list.");
                assertEquals(writerList.size(), readerList.size(), "Size mismatch in reconstructed list.");
                checkData(readerList);
            } finally {
                // Clean up the temporary file
                Files.delete(longListFile);
            }
        }
    }

    @ParameterizedTest(name = "[{index}] Writer={0}, Reader={1}")
    @MethodSource("longListWriterReaderPairsProvider")
    void writeAndReadBackBigIndex(
            final LongListWriterFactory writerFactory,
            final LongListReaderFactory readerFactory,
            @TempDir final Path tempDir)
            throws IOException {

        // Create a writer LongList
        try (final LongList writerList = writerFactory.createInstance().get()) {
            // Use a large index to test beyond the typical Integer.MAX_VALUE range
            long bigIndex = Integer.MAX_VALUE + 1L;
            writerList.updateValidRange(bigIndex, bigIndex);
            writerList.put(bigIndex, 1);

            // Verify the value was written correctly
            assertEquals(1, writerList.get(bigIndex), "Value mismatch for the large index.");

            // Write the long list to a file and verify its existence
            final String TEMP_FILE_NAME = String.format(
                    "testWriteAndReadBackBigIndex_write_%s_read_back_%s.ll",
                    writerFactory,
                    readerFactory);
            final Path longListFile = writeLongListToFileAndVerify(writerList, TEMP_FILE_NAME, tempDir);

            // Reconstruct the LongList from the file
            try (final LongList readerList = readerFactory.createFromFile().apply(longListFile, CONFIGURATION)) {
                // Validate that the large index is correctly reconstructed
                assertEquals(1, readerList.get(bigIndex), "Value mismatch for the large index after reconstruction.");
            } finally {
                // Clean up the temporary file
                Files.delete(longListFile);
            }
        }
    }

    @ParameterizedTest(name = "[{index}] Writer={0}, Reader={1}")
    @MethodSource("longListWriterReaderPairsProvider")
    void customNumberOfLongs(
            final LongListWriterFactory writerFactory,
            final LongListReaderFactory readerFactory,
            @TempDir final Path tempDir)
            throws IOException {

        // Create a writer LongList
        try (final AbstractLongList<?> writerList = writerFactory.createInstance().get()) {
            // Populate the long list with initial values and validate its contents
            populateList(writerList);
            checkData(writerList);

            // Write the long list to a file and verify its existence
            final String TEMP_FILE_NAME = String.format(
                    "testCustomNumberOfLongs_write_%s_read_back_%s.ll",
                    writerFactory,
                    readerFactory);
            final Path longListFile = writeLongListToFileAndVerify(writerList, TEMP_FILE_NAME, tempDir);

            // Reconstruct the LongList from the file
            try (final AbstractLongList<?> readerList = readerFactory.createFromFile().apply(longListFile, CONFIGURATION)) {
                // Validate that the number of chunks matches between the writer and reader lists
                assertEquals(
                        writerList.dataCopy().size(),
                        readerList.dataCopy().size(),
                        "Mismatch in the number of chunks between writer and reader lists.");
            } finally {
                // Clean up the temporary file
                Files.delete(longListFile);
            }
        }
    }

    @ParameterizedTest(name = "[{index}] Writer={0}, Reader={1}")
    @MethodSource("longListWriterReaderPairsProvider")
    void shrinkListMinValidIndex_10K(
            final LongListWriterFactory writerFactory,
            final LongListReaderFactory readerFactory,
            @TempDir final Path tempDir)
            throws IOException {

        // SAMPLE_SIZE is set to 10K for this test
        final int SAMPLE_SIZE = 10_000;
        final int HALF_SAMPLE_SIZE = SAMPLE_SIZE / 2;

        // Create a writer LongList with the specified number of longs per chunk and max longs
        try (final AbstractLongList<?> writerList = createFullyParameterizedLongListWith(10, SAMPLE_SIZE * 2)) {
            // Populate the long list with initial values and validate its contents
            populateList(writerList, SAMPLE_SIZE);
            checkData(writerList, 0, SAMPLE_SIZE);

            // Save the original file size for comparison (for LongListDisk)
            long originalFileSize = 0;
            if (writerList instanceof LongListDisk) {
                originalFileSize = ((LongListDisk) writerList).getCurrentFileChannel().size()
                        + writerList.currentFileHeaderSize;
            }

            // Update the valid range to shrink the list by setting a new minimum valid index
            writerList.updateValidRange(HALF_SAMPLE_SIZE, SAMPLE_SIZE * 2 - 1);

            // Validate that the first half of the list is now empty
            checkEmptyUpToIndex(writerList, HALF_SAMPLE_SIZE);

            // Validate that the second half of the list retains its data
            checkData(writerList, HALF_SAMPLE_SIZE, SAMPLE_SIZE);

            // Write the modified long list to a file and verify its existence
            final String TEMP_FILE_NAME = String.format(
                    "testShrinkListMinValidIndex_write_%s_read_back_%s.ll",
                    writerFactory,
                    readerFactory);
            final Path longListFile = writeLongListToFileAndVerify(writerList, TEMP_FILE_NAME, tempDir);

            // If using LongListDisk, verify that the file size reflects the shrink operation
            if (writerList instanceof LongListDisk) {
                assertEquals(
                        HALF_SAMPLE_SIZE * Long.BYTES,
                        originalFileSize - Files.size(longListFile),
                        "File size after shrinking does not match expected reduction.");
            }

            // Reconstruct the LongList from the file
            try (final LongList readerList = readerFactory.createFromFile().apply(longListFile, CONFIGURATION)) {
                // Validate that all entries in the reconstructed list match the writer list
                for (int i = 0; i < SAMPLE_SIZE; i++) {
                    assertEquals(
                            writerList.get(i),
                            readerList.get(i),
                            "Mismatch in data for index " + i + " between writer and reader lists.");
                }
            } finally {
                // Clean up the temporary file
                Files.delete(longListFile);
            }
        }
    }

    @ParameterizedTest(name = "[{index}] Writer={0}, Reader={1}")
    @MethodSource("longListWriterReaderPairsProvider")
    void shrinkListMaxValidIndex_10K(
            final LongListWriterFactory writerFactory,
            final LongListReaderFactory readerFactory,
            @TempDir final Path tempDir)
            throws IOException {

        // SAMPLE_SIZE is set to 10K for this test
        final int SAMPLE_SIZE = 10_000;
        final int HALF_SAMPLE_SIZE = SAMPLE_SIZE / 2;

        // Create a writer LongList with the specified number of longs per chunk and max longs
        try (final AbstractLongList<?> writerList = createFullyParameterizedLongListWith(10, SAMPLE_SIZE * 2)) {
            // Populate the long list with initial values and validate its contents
            populateList(writerList, SAMPLE_SIZE);
            checkData(writerList, 0, SAMPLE_SIZE);

            // Save the original file size for comparison (for LongListDisk)
            // temporary file channel doesn't contain the header
            long originalFileSize = 0;
            if (writerList instanceof LongListDisk) {
                originalFileSize = ((LongListDisk) writerList).getCurrentFileChannel().size()
                        + writerList.currentFileHeaderSize;
            }

            // Update the valid range to shrink the list by setting a new maximum valid index
            writerList.updateValidRange(0, HALF_SAMPLE_SIZE - 1);

            // Validate that the second half of the list is now empty
            checkEmptyFromIndex(writerList, HALF_SAMPLE_SIZE, SAMPLE_SIZE);

            // Validate that the first half of the list retains its data
            checkData(writerList, 0, HALF_SAMPLE_SIZE);

            // Write the modified long list to a file and verify its existence
            final String TEMP_FILE_NAME = String.format(
                    "testShrinkListMaxValidIndex_write_%s_read_back_%s.ll",
                    writerFactory,
                    readerFactory);
            final Path longListFile = writeLongListToFileAndVerify(writerList, TEMP_FILE_NAME, tempDir);

            // If using LongListDisk, verify that the file size reflects the shrink operation
            if (writerList instanceof LongListDisk) {
                assertEquals(
                        HALF_SAMPLE_SIZE * Long.BYTES,
                        originalFileSize - Files.size(longListFile),
                        "File size after shrinking does not match expected reduction.");
            }

            // Reconstruct the LongList from the file
            try (final LongList readerList = readerFactory.createFromFile().apply(longListFile, CONFIGURATION)) {
                // Validate that all entries in the reconstructed list match the writer list
                for (int i = 0; i < SAMPLE_SIZE; i++) {
                    assertEquals(
                            writerList.get(i),
                            readerList.get(i),
                            "Mismatch in data for index " + i + " between writer and reader lists.");
                }
            } finally {
                // Clean up the temporary file
                Files.delete(longListFile);
            }
        }
    }

    /**
     * Takes a stream of (writerFactory, readerFactory) pairs,
     * and for each pair, returns multiple (writerFactory, readerFactory, secondReaderFactory) triples.
     *
     * @param writerReaderPairs a stream of (writerFactory, readerFactory) pairs
     * @return a stream of argument triples (writerFactory, readerFactory, secondReaderFactory)
     */
    protected static Stream<Arguments> longListWriterSecondReaderPairsProviderBase(
            final Stream<Arguments> writerReaderPairs) {
        // “Expand” each (writerFactory, readerFactory) into (writerFactory, readerFactory, secondReaderFactory).
        return writerReaderPairs.flatMap(pair -> {
            // The existing pair is [writerFactory, readerFactory].
            final Object writerFactory = pair.get()[0];
            final Object readerFactory   = pair.get()[1];

            // Now, produce multiple outputs, each with a different secondReader:
            return Stream.of(
                    Arguments.of(writerFactory, readerFactory, heapReaderFactory),
                    Arguments.of(writerFactory, readerFactory, offHeapReaderFactory),
                    Arguments.of(writerFactory, readerFactory, diskReaderFactory)
            );
        });
    }

    @ParameterizedTest(name = "[{index}] Writer={0}, First Reader={1}, Second Reader={2}")
    @MethodSource("longListWriterSecondReaderPairsProvider")
    void updateListCreatedFromSnapshotPersistAndVerify_10K(
            final LongListWriterFactory writerFactory,
            final LongListReaderFactory readerFactory,
            final LongListReaderFactory secondReaderFactory,
            @TempDir final Path tempDir)
            throws IOException {

        // SAMPLE_SIZE is set to 10K for this test
        final int SAMPLE_SIZE = 10_000;

        // Create a LongList where each chunk holds 100 longs, resulting in 100 chunks
        try (final LongList writerList = createFullyParameterizedLongListWith(
                SAMPLE_SIZE / 100,
                SAMPLE_SIZE + DEFAULT_NUM_LONGS_PER_CHUNK)) {

            // Populate the writer list with initial values and validate its contents
            populateList(writerList, SAMPLE_SIZE);
            checkData(writerList, 0, SAMPLE_SIZE);

            // Write the writer list to a file and verify its existence
            final String TEMP_FILE_NAME = String.format(
                    "testUpdateListCreatedFromSnapshotPersistAndVerify_write_%s_read_back_%s_read_again_%s.ll",
                    writerFactory,
                    readerFactory,
                    secondReaderFactory);
            final Path longListFile = writeLongListToFileAndVerify(writerList, TEMP_FILE_NAME, tempDir);

            // Reconstruct the list from the file using the first reader implementation
            try (final LongList longListFromFile = readerFactory.createFromFile().apply(longListFile, CONFIGURATION)) {

                // Validate that the reconstructed list matches the writer list
                for (int i = 0; i < longListFromFile.size(); i++) {
                    assertEquals(
                            writerList.get(i),
                            longListFromFile.get(i),
                            "Mismatch in data for index " + i + " between writer and first reader lists.");
                }

                // Rewrite the data from the first reconstructed list back to the file
                Files.delete(longListFile);
                longListFromFile.writeToFile(longListFile);

                // Reconstruct the list again using the second reader implementation
                try (final LongList longListFromFile2 = secondReaderFactory.createFromFile().apply(longListFile, CONFIGURATION)) {

                    // Validate that the second reconstruction matches the writer list
                    for (int i = 0; i < longListFromFile2.size(); i++) {
                        assertEquals(
                                writerList.get(i),
                                longListFromFile2.get(i),
                                "Mismatch in data for index " + i + " between writer and second reader lists.");
                    }
                }
            }
        }
    }

    @ParameterizedTest(name = "[{index}] Writer={0}, First Reader={1}, Second Reader={2}")
    @MethodSource("longListWriterSecondReaderPairsProvider")
    void updateMinToTheLowerEnd_10K(
            final LongListWriterFactory writerFactory,
            final LongListReaderFactory readerFactory,
            final LongListReaderFactory secondReaderFactory,
            @TempDir final Path tempDir)
            throws IOException {

        // Define constants for this test
        final int SAMPLE_SIZE = 10_000;
        final int MAX_VALID_INDEX = SAMPLE_SIZE - 1;
        final int HALF_SAMPLE_SIZE = SAMPLE_SIZE / 2;

        // Create a writer long list with 10 elements per chunk
        try (final LongList writerList = createFullyParameterizedLongListWith(10, SAMPLE_SIZE)) {
            // Populate the list and validate its initial data
            populateList(writerList, SAMPLE_SIZE);
            checkData(writerList, 0, SAMPLE_SIZE);

            // Update the minimum valid index to exclude the lower half of the list
            //noinspection UnnecessaryLocalVariable
            int newMinValidIndex = HALF_SAMPLE_SIZE;
            writerList.updateValidRange(newMinValidIndex, MAX_VALID_INDEX);

            // Write the updated list to a file and verify its existence
            final String TEMP_FILE_NAME = String.format(
                    "testUpdateMinToTheLowerEnd_write_%s_read_back_%s.ll",
                    writerFactory,
                    readerFactory);
            final Path longListFile = writeLongListToFileAndVerify(writerList, TEMP_FILE_NAME, tempDir);

            // Reconstruct the list from the file using the first reader
            try (final LongList halfEmptyList = readerFactory.createFromFile().apply(longListFile, CONFIGURATION)) {

                // Verify that long list is half-empty
                checkEmptyUpToIndex(halfEmptyList, newMinValidIndex);

                // Validate that indices above the new minimum are still intact
                checkData(halfEmptyList, newMinValidIndex, SAMPLE_SIZE);

                // Test behavior when attempting to update indices below the minimum valid index
                int belowMinValidIndex1 = newMinValidIndex - 1;
                int belowMinValidIndex2 = newMinValidIndex - 2;
                int belowMinIndexValue1 = nextInt();
                int belowMinIndexValue2 = nextInt();

                // Attempt to put values below the minimum valid index; expect errors
                assertThrows(AssertionError.class, () -> halfEmptyList.put(belowMinValidIndex1, belowMinIndexValue1));
                assertFalse(halfEmptyList.putIfEqual(belowMinValidIndex2, IMPERMISSIBLE_VALUE, belowMinIndexValue2));

                // Update the valid range to include all indices
                halfEmptyList.updateValidRange(0, MAX_VALID_INDEX);

                // Now, inserting at previously excluded indices should succeed
                halfEmptyList.put(belowMinValidIndex1, belowMinIndexValue1);
                assertEquals(belowMinIndexValue1, halfEmptyList.get(belowMinValidIndex1));
                assertTrue(halfEmptyList.putIfEqual(belowMinValidIndex2, IMPERMISSIBLE_VALUE, belowMinIndexValue2));
                assertEquals(belowMinIndexValue2, halfEmptyList.get(belowMinValidIndex2));

                // Force creation of an additional chunk
                final int INDEX_OFFSET = 10;
                halfEmptyList.put(belowMinValidIndex2 - INDEX_OFFSET, belowMinIndexValue2);

                // Write the updated list to a new file and verify its existence
                final String TEMP_FILE_NAME_2 = String.format(
                        "testUpdateMinToTheLowerEnd_2_write_%s_read_back_%s.ll",
                        readerFactory,
                        secondReaderFactory);
                final Path longListFile2 = writeLongListToFileAndVerify(halfEmptyList, TEMP_FILE_NAME_2, tempDir);

                // Reconstruct the list again using the second reader
                try (final LongList zeroMinValidIndexList = secondReaderFactory.createFromFile().apply(longListFile2, CONFIGURATION)) {
                    // Verify that indices up to the new offset are empty
                    checkEmptyUpToIndex(zeroMinValidIndexList, belowMinValidIndex2 - INDEX_OFFSET);

                    // Validate all data above the midpoint is intact
                    checkData(zeroMinValidIndexList, HALF_SAMPLE_SIZE, SAMPLE_SIZE);

                    // Verify all indices are correctly restored after updating values below the minimum
                    for (int i = 0; i < newMinValidIndex; i++) {
                        assertEquals(halfEmptyList.get(i), zeroMinValidIndexList.get(i), "Mismatch in reconstructed list data.");
                        zeroMinValidIndexList.put(i, i + 100); // Refill the list
                    }

                    // Validate the refilled list
                    checkData(zeroMinValidIndexList, 0, SAMPLE_SIZE);
                }
            } finally {
                // Clean up temporary files
                Files.deleteIfExists(longListFile);
            }
        }
    }

    /**
     * Combines writer-reader pairs with predefined range configurations for testing.
     *
     * @param writerReaderPairs a stream of writer-reader pairs
     * @return a stream of arguments combining writer-reader pairs with range parameters
     */
    protected static Stream<Arguments> longListWriterReaderRangePairsProviderBase(final Stream<Arguments> writerReaderPairs) {
        return writerReaderPairs.flatMap(pair -> {
            Object writerFactory = pair.get()[0];
            Object readerFactory = pair.get()[1];

            return Stream.of(
                    // writerFactory, readerFactory, startIndex, endIndex, numLongsPerChunk, maxLongs
                    Arguments.of(writerFactory, readerFactory, 1, 1, 100, 1000),
                    Arguments.of(writerFactory, readerFactory, 1, 5, 100, 1000),
                    Arguments.of(writerFactory, readerFactory, 150, 150, 100, 1000),
                    Arguments.of(writerFactory, readerFactory, 150, 155, 100, 1000));
        });
    }

    @ParameterizedTest(name = "[{index}] Writer={0}, Reader={1}, startIndex={2}, endIndex={3}, numLongsPerChunk={4}, maxLongs={5}")
    @MethodSource("longListWriterReaderRangePairsProvider")
    void writeReadRangeElement(
            final LongListWriterFactory writerFactory,
            final LongListReaderFactory readerFactory,
            final int startIndex,
            final int endIndex,
            final int numLongsPerChunk,
            final long maxLongs,
            @TempDir final Path tempDir)
            throws IOException {

        // Create a writer LongList with the specified number of longs per chunk and max longs
        try (final LongList writerList = createFullyParameterizedLongListWith(numLongsPerChunk, maxLongs)) {
            // Update the valid range to include only the specified range of indices
            writerList.updateValidRange(startIndex, endIndex);

            // Populate the range with values
            for (int i = startIndex; i <= endIndex; i++) {
                writerList.put(i, i + 100);
            }

            // Write the long list to a file and verify its existence
            final String TEMP_FILE_NAME = String.format(
                    "testWriteReadRangeElement-%d-%d-%d-%d_write_%s_read_back_%s.ll",
                    startIndex,
                    endIndex,
                    numLongsPerChunk,
                    maxLongs,
                    writerFactory,
                    readerFactory);
            final Path longListFile = writeLongListToFileAndVerify(writerList, TEMP_FILE_NAME, tempDir);

            // Reconstruct the long list from the file using the reader
            try (final LongList readerList = readerFactory.createFromFile().apply(longListFile, CONFIGURATION)) {
                // Validate that the reconstructed list has the same capacity and size
                assertEquals(writerList.capacity(), readerList.capacity(), "Capacity mismatch in reconstructed list.");
                assertEquals(writerList.size(), readerList.size(), "Size mismatch in reconstructed list.");

                // Verify that the data in the specified range is correctly restored
                for (int i = startIndex; i <= endIndex; i++) {
                    assertEquals(i + 100, readerList.get(i), "Mismatch in value for index " + i);
                }
            } finally {
                // Clean up the temporary file
                Files.deleteIfExists(longListFile);
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
            final Stream<Arguments> writerReaderPairs) {
        return writerReaderPairs.flatMap(pair -> {
            Object writerFactory = pair.get()[0];
            Object readerFactory = pair.get()[1];

            return Stream.of(
                    // writerFactory, readerFactory, chunkOffset
                    Arguments.of(writerFactory, readerFactory, 0),
                    Arguments.of(writerFactory, readerFactory, 5));
        });
    }

    @ParameterizedTest(name = "[{index}] Writer={0}, Reader={1}, chunkOffset={2}")
    @MethodSource("longListWriterReaderOffsetPairsProvider")
    void createHalfEmptyLongListInMemoryReadBack(
            final LongListWriterFactory writerFactory,
            final LongListReaderFactory readerFactory,
            final int chunkOffset,
            @TempDir final Path tempDir)
            throws IOException {

        // Create a writer LongList
        try (final LongList writerList = writerFactory.createInstance().get()) {
            // Populate the list with sample data and validate its initial state
            populateList(writerList);
            checkData(writerList);

            // Update the minimum valid index to simulate a "half-empty" list with the specified chunk offset
            int newMinValidIndex = HALF_SAMPLE_SIZE + chunkOffset;
            writerList.updateValidRange(newMinValidIndex, MAX_VALID_INDEX);

            // Write the modified "half-empty" list to a file and verify its existence
            final String TEMP_FILE_NAME = String.format(
                    "testCreateHalfEmptyLongListInMemoryReadBack_%d_write_%s_read_back_%s.ll",
                    chunkOffset,
                    writerFactory,
                    readerFactory);
            final Path longListFile = writeLongListToFileAndVerify(writerList, TEMP_FILE_NAME, tempDir);

            // Reconstruct the LongList from the file
            try (final LongList readerList = readerFactory.createFromFile().apply(longListFile, CONFIGURATION)) {
                // Verify the reconstructed list retains the expected capacity and size
                assertEquals(writerList.capacity(), readerList.capacity(), "Capacity mismatch in reconstructed list.");
                assertEquals(writerList.size(), readerList.size(), "Size mismatch in reconstructed list.");

                // Ensure all indices below the new minimum valid index are empty
                checkEmptyUpToIndex(readerList, newMinValidIndex);

                // Validate that data above the new minimum valid index matches the original values
                checkData(readerList, newMinValidIndex, SAMPLE_SIZE);
            } finally {
                // Clean up the temporary file after the test
                Files.deleteIfExists(longListFile);
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
            final Stream<Arguments> writerReaderPairs) {
        return writerReaderPairs.flatMap(pair -> {
            Object writerFactory = pair.get()[0];
            Object readerFactory = pair.get()[1];

            return Stream.of(
                    // writerFactory, readerFactory, chunkOffset
                    Arguments.of(writerFactory, readerFactory, 0),
                    Arguments.of(writerFactory, readerFactory, 1),
                    Arguments.of(writerFactory, readerFactory, 5_000),
                    Arguments.of(writerFactory, readerFactory, 9_999),
                    Arguments.of(writerFactory, readerFactory, 10_000));
        });
    }

    @ParameterizedTest(name = "[{index}] Writer={0}, Reader={1}, chunkOffset={2}")
    @MethodSource("longListWriterReaderOffsetPairs2Provider")
    void persistListWithNonZeroMinValidIndex_1M(
            final LongListWriterFactory writerFactory,
            final LongListReaderFactory readerFactory,
            final int chunkOffset,
            @TempDir final Path tempDir)
            throws IOException {

        // Define the sample size for this test
        final int SAMPLE_SIZE = 1_000_000;

        // Create a writer LongList where each chunk holds 10K longs, resulting in 100 chunks
        try (final LongList writerList = createFullyParameterizedLongListWith(
                SAMPLE_SIZE / 100,
                SAMPLE_SIZE)) {

            // Populate the list with sample data and validate its initial state
            populateList(writerList, SAMPLE_SIZE);
            checkData(writerList, 0, SAMPLE_SIZE);

            // Update the minimum valid index to simulate a "half-empty" list with the specified chunk offset
            writerList.updateValidRange(SAMPLE_SIZE / 2 + chunkOffset, writerList.size() - 1);

            // Write the modified list to a file and verify its existence
            final String TEMP_FILE_NAME = String.format(
                    "testPersistListWithNonZeroMinValidIndex_%d_write_%s_read_back_%s.ll",
                    chunkOffset,
                    writerFactory,
                    readerFactory);
            final Path longListFile = writeLongListToFileAndVerify(writerList, TEMP_FILE_NAME, tempDir);

            // Reconstruct the LongList from the file
            try (final LongList readerList = readerFactory.createFromFile().apply(longListFile, CONFIGURATION)) {
                // Validate the reconstructed list's capacity and size
                assertEquals(writerList.capacity(), readerList.capacity(), "Capacity mismatch in reconstructed list.");
                assertEquals(writerList.size(), readerList.size(), "Size mismatch in reconstructed list.");

                // Ensure the data matches between the original and reconstructed lists
                for (int i = 0; i < readerList.size(); i++) {
                    assertEquals(
                            writerList.get(i),
                            readerList.get(i),
                            "Unexpected value in a loaded readerList, index=" + i);
                }
            } finally {
                // Clean up the temporary file after the test
                Files.deleteIfExists(longListFile);
            }
        }
    }

    @ParameterizedTest(name = "[{index}] Writer={0}, Reader={1}, chunkOffset={2}")
    @MethodSource("longListWriterReaderOffsetPairs2Provider")
    void persistShrunkList_1M(
            final LongListWriterFactory writerFactory,
            final LongListReaderFactory readerFactory,
            final int chunkOffset,
            @TempDir final Path tempDir)
            throws IOException {

        // Define the sample size for this test
        final int SAMPLE_SIZE = 1_000_000;

        // Create a writer LongList where each chunk holds 10K longs, resulting in 100 chunks
        try (final LongList writerList = createFullyParameterizedLongListWith(
                SAMPLE_SIZE / 100,
                SAMPLE_SIZE)) {

            // Populate the list with sample data and validate its initial state
            populateList(writerList, SAMPLE_SIZE);
            checkData(writerList, 0, SAMPLE_SIZE);

            // Shrink the valid range of the list to simulate partial truncation
            writerList.updateValidRange(0, SAMPLE_SIZE / 2 + chunkOffset);

            // Write the modified list to a file and verify its existence
            final String TEMP_FILE_NAME = String.format(
                    "testPersistShrunkList_%d_write_%s_read_back_%s.ll",
                    chunkOffset,
                    writerFactory,
                    readerFactory);
            final Path longListFile = writeLongListToFileAndVerify(writerList, TEMP_FILE_NAME, tempDir);

            // Reconstruct the LongList from the file
            try (final LongList readerList = readerFactory.createFromFile().apply(longListFile, CONFIGURATION)) {
                // Validate the reconstructed list's capacity and size
                assertEquals(writerList.capacity(), readerList.capacity(), "Capacity mismatch in reconstructed list.");
                assertEquals(writerList.size(), readerList.size(), "Size mismatch in reconstructed list.");

                // Ensure the data matches between the original and reconstructed lists
                for (int i = 0; i < readerList.size(); i++) {
                    assertEquals(
                            writerList.get(i),
                            readerList.get(i),
                            "Unexpected value in a loaded readerList, index=" + i);
                }
            } finally {
                // Clean up the temporary file after the test
                Files.deleteIfExists(longListFile);
            }
        }
    }


    // Utility methods

    @SuppressWarnings("UnusedReturnValue")
    static <T extends LongList> T populateList(T longList) {
        return populateList(longList, SAMPLE_SIZE);
    }

    static <T extends LongList> T populateList(T longList, int sampleSize) {
        longList.updateValidRange(0, sampleSize - 1);
        for (int i = 0; i < sampleSize; i++) {
            longList.put(i, i + 100);
        }
        return longList;
    }

    static void checkData(final LongList longList) {
        checkData(longList, 0, SAMPLE_SIZE);
    }

    static void checkData(final LongList longList, final int startIndex, final int endIndex) {
        for (int i = startIndex; i < endIndex; i++) {
            final long readValue = longList.get(i, 0);
            assertEquals(i + 100, readValue, "Longs don't match for " + i + " got [" + readValue + "] should be [" + i + 100 + "]");
        }
    }

    static void checkEmptyUpToIndex(LongList longList, int index) {
        for (int i = 0; i < index; i++) {
            final long readValue = longList.get(i, 0);
            assertEquals(0, readValue, "Longs don't match for " + i + " got [" + readValue + "] should be [" + 0 + "]");
        }
    }

    @SuppressWarnings("SameParameterValue")
    static void checkEmptyFromIndex(LongList longList, int fromIndex, int toIndex) {
        for (int i = fromIndex; i < toIndex; i++) {
            final long readValue = longList.get(i, 0);
            assertEquals(0, readValue, "Longs don't match for " + i + " got [" + readValue + "] should be [" + 0 + "]");
        }
    }

    /**
     * Writes all longs in LongList instance to a temporary file and verifies its existence.
     *
     * @param longList   the LongList instance to be written to the file
     * @param fileName   the name of the file to write
     * @param tempDir    the directory where the temporary file will be created
     * @return the path to the created file
     * @throws IOException if an I/O error occurs
     */
    static Path writeLongListToFileAndVerify(final LongList longList, final String fileName, final Path tempDir) throws IOException {
        final Path file = tempDir.resolve(fileName);

        if (Files.exists(file)) {
            Files.delete(file);
        }

        longList.writeToFile(file);

        assertTrue(Files.exists(file),
                String.format("File '%s' does not exist after writing longs.", file.toAbsolutePath()));

        return file;
    }
}