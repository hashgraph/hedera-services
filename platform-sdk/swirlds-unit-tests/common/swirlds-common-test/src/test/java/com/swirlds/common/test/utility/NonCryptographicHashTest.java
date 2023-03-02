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

package com.swirlds.common.test.utility;

import static com.swirlds.common.test.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.utility.NonCryptographicHashing.hash32;
import static com.swirlds.common.utility.NonCryptographicHashing.hash64;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.swirlds.common.test.RandomUtils;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Non-Cryptographic Hash Test")
class NonCryptographicHashTest {

    /**
     * This test does not attempt to verify statistical properties of the hash functions.
     * Its purpose is to ensure that none of the methods cause a crash.
     */
    @DisplayName("Test hash32")
    @Test
    void testHash32() {
        final Random random = getRandomPrintSeed();

        assertDoesNotThrow(() -> {
            hash32(random.nextInt());
            hash32(random.nextInt(), random.nextInt());
            hash32(random.nextInt(), random.nextInt(), random.nextInt());
            hash32(random.nextInt(), random.nextInt(), random.nextInt(), random.nextInt());
            hash32(random.nextInt(), random.nextInt(), random.nextInt(), random.nextInt(), random.nextInt());
            hash32(
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt());
            hash32(
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt());
            hash32(
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt());
            hash32(
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt());
            hash32(
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt());
            hash32(
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt());
            hash32(
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt());

            hash32(random.nextLong());
            hash32(random.nextLong(), random.nextLong());
            hash32(random.nextLong(), random.nextLong(), random.nextLong());
            hash32(random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong());
            hash32(random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong());
            hash32(
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong());
            hash32(
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong());
            hash32(
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong());
            hash32(
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong());
            hash32(
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong());
            hash32(
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong());
            hash32(
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong());

            for (int i = 0; i < 100; i++) {
                final byte[] bytes = new byte[i];
                hash32(bytes);

                final String string = RandomUtils.randomString(random, i);
                hash32(string);
            }
        });
    }

    /**
     * This test does not attempt to verify statistical properties of the hash functions.
     * Its purpose is to ensure that none of the methods cause a crash.
     */
    @DisplayName("Test hash64")
    @Test
    void testHash64() {
        final Random random = getRandomPrintSeed();

        assertDoesNotThrow(() -> {
            hash64(random.nextInt());
            hash64(random.nextInt(), random.nextInt());
            hash64(random.nextInt(), random.nextInt(), random.nextInt());
            hash64(random.nextInt(), random.nextInt(), random.nextInt(), random.nextInt());
            hash64(random.nextInt(), random.nextInt(), random.nextInt(), random.nextInt(), random.nextInt());
            hash64(
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt());
            hash64(
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt());
            hash64(
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt());
            hash64(
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt());
            hash64(
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt());
            hash64(
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt());
            hash64(
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt(),
                    random.nextInt());

            hash64(random.nextLong());
            hash64(random.nextLong(), random.nextLong());
            hash64(random.nextLong(), random.nextLong(), random.nextLong());
            hash64(random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong());
            hash64(random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong());
            hash64(
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong());
            hash64(
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong());
            hash64(
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong());
            hash64(
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong());
            hash64(
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong());
            hash64(
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong());
            hash64(
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong());

            for (int i = 0; i < 100; i++) {
                final byte[] bytes = new byte[i];
                hash64(bytes);

                final String string = RandomUtils.randomString(random, i);
                hash64(string);
            }
        });
    }

    @DisplayName("Hashes Are Not Degenerate 32")
    @Test
    void hashesAreNonDegenerate32() {
        final Random random = getRandomPrintSeed();

        assertNotEquals(0, hash32(0));
        assertNotEquals(0, hash32(0, 0));
        assertNotEquals(0, hash32(0, 0, 0));
        assertNotEquals(0, hash32(0, 0, 0));
        assertNotEquals(0, hash32(0, 0, 0, 0));
        assertNotEquals(0, hash32(0, 0, 0, 0, 0));
        assertNotEquals(0, hash32(0, 0, 0, 0, 0, 0));
        assertNotEquals(0, hash32(0, 0, 0, 0, 0, 0, 0));
        assertNotEquals(0, hash32(0, 0, 0, 0, 0, 0, 0, 0));
        assertNotEquals(0, hash32(0, 0, 0, 0, 0, 0, 0, 0, 0));
        assertNotEquals(0, hash32(0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        assertNotEquals(0, hash32(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));

        assertNotEquals(0, hash32(random.nextLong()));
        assertNotEquals(0, hash32(random.nextLong(), random.nextLong()));
        assertNotEquals(0, hash32(random.nextLong(), random.nextLong(), random.nextLong()));
        assertNotEquals(0, hash32(random.nextLong(), random.nextLong(), random.nextLong()));
        assertNotEquals(0, hash32(random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong()));
        assertNotEquals(
                0,
                hash32(random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong()));
        assertNotEquals(
                0,
                hash32(
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong()));
        assertNotEquals(
                0,
                hash32(
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong()));
        assertNotEquals(
                0,
                hash32(
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong()));
        assertNotEquals(
                0,
                hash32(
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong()));
        assertNotEquals(
                0,
                hash32(
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong()));
        assertNotEquals(
                0,
                hash32(
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong()));

        for (int i = 0; i < 100; i++) {
            final byte[] bytes = new byte[i];
            assertNotEquals(0, hash32(bytes), "Hashes should be non-degenerate");

            final String string = RandomUtils.randomString(random, i);
            assertNotEquals(0, hash32(string), "Hashes should be non-degenerate");
        }
    }

    @DisplayName("Hashes Are Not Degenerate 64")
    @Test
    void hashesAreNonDegenerate64() {
        final Random random = getRandomPrintSeed();

        assertNotEquals(0, hash64(0));
        assertNotEquals(0, hash64(0, 0));
        assertNotEquals(0, hash64(0, 0, 0));
        assertNotEquals(0, hash64(0, 0, 0));
        assertNotEquals(0, hash64(0, 0, 0, 0));
        assertNotEquals(0, hash64(0, 0, 0, 0, 0));
        assertNotEquals(0, hash64(0, 0, 0, 0, 0, 0));
        assertNotEquals(0, hash64(0, 0, 0, 0, 0, 0, 0));
        assertNotEquals(0, hash64(0, 0, 0, 0, 0, 0, 0, 0));
        assertNotEquals(0, hash64(0, 0, 0, 0, 0, 0, 0, 0, 0));
        assertNotEquals(0, hash64(0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        assertNotEquals(0, hash64(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));

        assertNotEquals(0, hash64(random.nextLong()));
        assertNotEquals(0, hash64(random.nextLong(), random.nextLong()));
        assertNotEquals(0, hash64(random.nextLong(), random.nextLong(), random.nextLong()));
        assertNotEquals(0, hash64(random.nextLong(), random.nextLong(), random.nextLong()));
        assertNotEquals(0, hash64(random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong()));
        assertNotEquals(
                0,
                hash64(random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong()));
        assertNotEquals(
                0,
                hash64(
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong()));
        assertNotEquals(
                0,
                hash64(
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong()));
        assertNotEquals(
                0,
                hash64(
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong()));
        assertNotEquals(
                0,
                hash64(
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong()));
        assertNotEquals(
                0,
                hash64(
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong()));
        assertNotEquals(
                0,
                hash64(
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong()));

        for (int i = 0; i < 100; i++) {
            final byte[] bytes = new byte[i];
            assertNotEquals(0, hash64(bytes), "Hashes should be non-degenerate");

            final String string = RandomUtils.randomString(random, i);
            assertNotEquals(0, hash64(string), "Hashes should be non-degenerate");
        }
    }
}
