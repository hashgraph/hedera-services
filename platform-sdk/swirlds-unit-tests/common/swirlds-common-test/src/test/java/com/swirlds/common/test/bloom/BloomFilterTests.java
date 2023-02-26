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

package com.swirlds.common.test.bloom;

import static com.swirlds.common.test.RandomUtils.getRandomPrintSeed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.bloom.BloomFilter;
import com.swirlds.common.bloom.BloomHasher;
import com.swirlds.common.bloom.hasher.IntBloomHasher;
import com.swirlds.common.bloom.hasher.LongBloomHasher;
import com.swirlds.common.bloom.hasher.SelfSerializableBloomHasher;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.utility.SerializableLong;
import com.swirlds.common.test.ResettableRandom;
import com.swirlds.test.framework.TestQualifierTags;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("BloomFilter Tests")
class BloomFilterTests {

    @BeforeAll
    static void beforeAll() throws ConstructableRegistryException {
        ConstructableRegistry.getInstance().registerConstructables("com.swirlds");
    }

    /**
     * Test the bloom filter using random data
     *
     * @param hashCount
     * 		the number of hashes
     * @param hasher
     * 		the bloom hasher for this data type
     * @param filterSize
     * 		the size of the bloom filter, in bits
     * @param count
     * 		the number of things to add to the bloom filter, and the number of times to check false for false positives
     * @param valueSupplier
     * 		a method that builds random values
     * @param maxFalsePositiveFraction
     * 		the maximum fraction of randomly chosen values that are expected to be identified as part of the set
     * @param serialize
     * 		if true then test serialization
     */
    private <T> void testRandomData(
            final int hashCount,
            final BloomHasher<T> hasher,
            final long filterSize,
            final long count,
            final Function<Random, T> valueSupplier,
            final double maxFalsePositiveFraction,
            final boolean serialize)
            throws IOException {

        final BloomFilter<T> filter = new BloomFilter<>(hashCount, hasher, filterSize);

        final ResettableRandom random = getRandomPrintSeed();

        // Insert a bunch of stuff into the bloom filter
        for (long i = 0; i < count; i++) {
            filter.add(valueSupplier.apply(random));
        }

        // Make sure all of the added values are contained by the set
        random.reset();
        for (long i = 0; i < count; i++) {
            assertTrue(filter.contains(valueSupplier.apply(random)), "bloom filter should contain value");
        }

        // Check a bunch of things that are (probably) not in the set to verify false positive rate
        long in = 0;
        long out = 0;
        for (long i = 0; i < count; i++) {
            if (filter.contains(valueSupplier.apply(random))) {
                in++;
            } else {
                out++;
            }
        }

        final double ratio = ((double) in) / (in + out);

        assertTrue(
                maxFalsePositiveFraction >= ratio,
                "false positive fraction of " + ratio + " exceeds expected max fraction of "
                        + maxFalsePositiveFraction);

        if (!serialize) {
            return;
        }

        final ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        final SerializableDataOutputStream outputStream = new SerializableDataOutputStream(byteOut);

        outputStream.writeSerializable(filter, true);

        final SerializableDataInputStream inputStream =
                new SerializableDataInputStream(new ByteArrayInputStream(byteOut.toByteArray()));

        final BloomFilter<T> deserializedFilter = inputStream.readSerializable();

        // Make sure all of the added values are contained by the deserialized set
        random.reset();
        for (long i = 0; i < count; i++) {
            assertTrue(deserializedFilter.contains(valueSupplier.apply(random)), "bloom filter should contain value");
        }

        // Check for values that should not be in the deserialized set
        long deserializedIn = 0;
        long deserializedOut = 0;
        for (long i = 0; i < count; i++) {
            if (deserializedFilter.contains(valueSupplier.apply(random))) {
                deserializedIn++;
            } else {
                deserializedOut++;
            }
        }

        assertEquals(in, deserializedIn, "number of things in the filter should match after deserialization");
        assertEquals(out, deserializedOut, "number of things not in the filter should match after deserialization");
    }

    /**
     * <p>
     * Test a bloom filter containing integers for small(ish) bloom filters.
     * </p>
     *
     * <p>
     * Parameters chosen with the aid of https://hur.st/bloomfilter/
     * </p>
     */
    @ParameterizedTest
    @CsvSource({
        "2,  10,       5,       1.0",
        "3,  100,      50,      1.0",
        "10, 150000,   10000,   0.002",
        "10, 1500000,  100000,  0.002",
        "10, 15000000, 1000000, 0.002"
    })
    @DisplayName("Small Bloom Filter Int Test")
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void smallBloomFilterIntTest(
            final int hashCount, final long filterSize, final long count, final double maxFalsePositiveFraction)
            throws IOException {
        testRandomData(
                hashCount, new IntBloomHasher(), filterSize, count, Random::nextInt, maxFalsePositiveFraction, true);
    }

    /**
     * <p>
     * Test a bloom filter containing longs for small(ish) bloom filters.
     * </p>
     *
     * <p>
     * Parameters chosen with the aid of https://hur.st/bloomfilter/
     * </p>
     */
    @ParameterizedTest
    @CsvSource({
        "2,  10,       5,       1.0",
        "3,  100,      50,      1.0",
        "10, 150000,   10000,   0.002",
        "10, 1500000,  100000,  0.002",
        "10, 15000000, 1000000, 0.002"
    })
    @DisplayName("Small Bloom Filter Long Test")
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void smallBloomFilterLongTest(
            final int hashCount, final long filterSize, final long count, final double maxFalsePositiveFraction)
            throws IOException {
        testRandomData(
                hashCount, new LongBloomHasher(), filterSize, count, Random::nextLong, maxFalsePositiveFraction, true);
    }

    /**
     * <p>
     * Test a bloom filter containing self serializable objects for small(ish) bloom filters.
     * </p>
     *
     * <p>
     * Parameters chosen with the aid of https://hur.st/bloomfilter/
     * </p>
     */
    @ParameterizedTest
    @CsvSource({
        "2,  10,       5,       1.0",
        "3,  100,      50,      1.0",
        "10, 150000,   10000,   0.002",
        "10, 1500000,  100000,  0.002",
        "10, 15000000, 1000000, 0.002"
    })
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void smallBloomFilterSelfSerializableTest(
            final int hashCount, final long filterSize, final long count, final double maxFalsePositiveFraction)
            throws IOException {
        testRandomData(
                hashCount,
                new SelfSerializableBloomHasher<>(),
                filterSize,
                count,
                random -> new SerializableLong(random.nextLong()),
                maxFalsePositiveFraction,
                true);
    }

    /**
     * <p>
     * Test a bloom filter containing integers for super large bloom filters. A comparatively small number of
     * elements are inserted into each filter, as the number of insertions required to push bloom filters to
     * their limits at these extreme sizes could take a very long time.
     * </p>
     *
     * <p>
     * Parameters chosen with the aid of https://hur.st/bloomfilter/
     * </p>
     *
     * <p>
     * WARNING: these tests are likely to fail if run on a laptop due to memory limitations
     * </p>
     */
    @ParameterizedTest
    @CsvSource({
        "10, 1500000000,  1000000, 0.001",
        "10, 15000000000, 1000000, 0.001",
        "10, 50000000000, 1000000, 0.001" // This configuration will allocate an array at max size
    })
    @Tag(TestQualifierTags.TIME_CONSUMING)
    @DisplayName("Large Bloom Filter Int Test")
    void largeBloomFilterIntTest(
            final int hashCount, final long filterSize, final long count, final double maxFalsePositiveFraction)
            throws IOException {
        testRandomData(
                hashCount, new IntBloomHasher(), filterSize, count, Random::nextInt, maxFalsePositiveFraction, false);
    }

    /**
     * <p>
     * Test a bloom filter containing integers for super large bloom filters. A comparatively small number of
     * elements are inserted into each filter, as the number of insertions required to push bloom filters to
     * their limits at these extreme sizes could take a very long time.
     * </p>
     *
     * <p>
     * Parameters chosen with the aid of https://hur.st/bloomfilter/
     * </p>
     *
     * <p>
     * WARNING: these tests are likely to fail if run on a laptop due to memory limitations
     * </p>
     */
    @ParameterizedTest
    @CsvSource({
        "10, 1500000000,  1000000, 0.001",
        "10, 15000000000, 1000000, 0.001",
        "10, 50000000000, 1000000, 0.001" // This configuration will allocate an array at max size
    })
    @Tag(TestQualifierTags.TIME_CONSUMING)
    @DisplayName("Large Bloom Filter Int Test")
    void largeBloomFilterLongTest(
            final int hashCount, final long filterSize, final long count, final double maxFalsePositiveFraction)
            throws IOException {
        testRandomData(
                hashCount, new LongBloomHasher(), filterSize, count, Random::nextLong, maxFalsePositiveFraction, false);
    }

    @Test
    @DisplayName("checkAndAdd() test")
    void checkAndAddTest() {
        final BloomFilter<Integer> filter = new BloomFilter<>(10, new IntBloomHasher(), 1024);

        assertFalse(filter.contains(1234), "no objects should be in the filter");
        assertFalse(filter.checkAndAdd(1234), "no elements should be in the filter at check time");
        assertTrue(filter.contains(1234), "filter should now contain value");
        assertTrue(filter.checkAndAdd(1234), "checkAndAdd() should work even when element is present");
    }
}
