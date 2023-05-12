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

package com.swirlds.merkledb.files.hashmap;

import static com.swirlds.common.test.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.utility.Units.BYTES_TO_BITS;
import static com.swirlds.common.utility.Units.MEBIBYTES_TO_BYTES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.merkledb.ExampleLongKeyFixedSize;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("HalfDiskVirtualKeySet Test")
class HalfDiskVirtualKeySetTest {

    @Test
    void randomElementTest() {

        final int count = 1_000_000;
        final int maxKey = count * 10;
        final Random random = getRandomPrintSeed();

        // Used for verifying membership
        final Set<ExampleLongKeyFixedSize> referenceSet = new HashSet<>();

        // The data structure being tested
        // Bloom filter configuration should give ~1% false positive rate
        // according to https://hur.st/bloomfilter/?n=1000000&p=1.0E-3&m=&k=
        final HalfDiskVirtualKeySet<ExampleLongKeyFixedSize> keySet = new HalfDiskVirtualKeySet<>(
                new ExampleLongKeyFixedSize.Serializer(),
                10,
                2L * MEBIBYTES_TO_BYTES * BYTES_TO_BITS,
                1_000_000,
                10_000);

        for (int i = 0; i < count; i++) {

            // Add an element.
            final ExampleLongKeyFixedSize elementToAdd = new ExampleLongKeyFixedSize(random.nextLong(maxKey));
            referenceSet.add(elementToAdd);
            keySet.add(elementToAdd);

            assertTrue(keySet.contains(elementToAdd), "element should be present immediately after being added");

            // Test a random element.
            final ExampleLongKeyFixedSize elementToTest = new ExampleLongKeyFixedSize(random.nextLong(maxKey));
            assertEquals(
                    referenceSet.contains(elementToTest),
                    keySet.contains(elementToTest),
                    "set should agree with reference set");
        }

        keySet.close();
    }
}
