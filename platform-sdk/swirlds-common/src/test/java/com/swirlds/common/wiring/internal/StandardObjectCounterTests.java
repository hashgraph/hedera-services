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

package com.swirlds.common.wiring.internal;

import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.common.wiring.counters.ObjectCounter;
import com.swirlds.common.wiring.counters.StandardObjectCounter;
import java.time.Duration;
import java.util.Random;
import org.junit.jupiter.api.Test;

class StandardObjectCounterTests {

    @Test
    void basicOperationTest() throws InterruptedException {
        final Random random = getRandomPrintSeed();

        final ObjectCounter counter = new StandardObjectCounter(Duration.ofMillis(1));

        int count = 0;
        for (int i = 0; i < 1000; i++) {

            final boolean increment = count == 0 || random.nextBoolean();

            if (increment) {
                count++;

                // All of these methods are logically equivalent for this implementation.
                final int choice = random.nextInt(4);
                switch (choice) {
                    case 0 -> counter.onRamp();
                    case 1 -> counter.interruptableOnRamp();
                    case 2 -> counter.attemptOnRamp();
                    case 3 -> counter.forceOnRamp();
                    default -> throw new IllegalStateException("Unexpected value: " + choice);
                }

            } else {
                count--;
                counter.offRamp();
            }

            assertEquals(count, counter.getCount());
        }
    }

    // TODO test waitUntilEmpty()
}
