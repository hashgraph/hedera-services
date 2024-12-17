/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.util;

import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Random;
import org.junit.jupiter.api.Test;

class RandomBuilderTest {

    @Test
    void randomSeedShouldBeRandomTest() {
        final RandomBuilder randomBuilderA = new RandomBuilder();
        final RandomBuilder randomBuilderB = new RandomBuilder();

        assertNotEquals(
                randomBuilderA.buildNonCryptographicRandom().nextLong(),
                randomBuilderB.buildNonCryptographicRandom().nextLong());
    }

    @Test
    void specifiedSeedShouldProduceTheSameResultsTest() {
        final Random random = getRandomPrintSeed();

        final long seed = random.nextLong();
        final RandomBuilder randomBuilderA = new RandomBuilder(seed);
        final RandomBuilder randomBuilderB = new RandomBuilder(seed);

        Random randomA = randomBuilderA.buildNonCryptographicRandom();
        Random randomB = randomBuilderB.buildNonCryptographicRandom();
        for (int i = 0; i < 1000; i++) {
            if (i % 7 == 0) {
                randomA = randomBuilderA.buildNonCryptographicRandom();
                randomB = randomBuilderB.buildNonCryptographicRandom();
            }
            assertEquals(randomA.nextLong(), randomB.nextLong());
        }
    }
}
