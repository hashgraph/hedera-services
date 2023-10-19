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

package com.swirlds.common.wiring.transformers;

import static com.swirlds.common.test.fixtures.AssertionUtils.assertEventuallyEquals;
import static com.swirlds.common.utility.NonCryptographicHashing.hash32;

import com.swirlds.common.wiring.Wire;
import com.swirlds.common.wiring.WireChannel;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class WireTransformersTests {

    // TODO
    //  - WireCollectionSplitter
    //  - WireFilter
    //  - WireTransformer
    //  - soldering to a lambda
    //  - injection soldering

    // TODO consider changing "withType()" methods into "cast()" methods

    @Test
    void wireListSplitterTest() {

        // Component A produces lists of integers. It passes data to B, C, and D.
        // Components B and C want individual integers. Component D wants the full list of integers.

        final Wire<List<Integer>> wireA = Wire.builder("A").build().cast();
        final WireChannel<Integer, List<Integer>> wireAIn = wireA.buildChannel();

        final Wire<Void> wireB = Wire.builder("B").build().cast();
        final WireChannel<Integer, Void> wireBIn = wireB.buildChannel();

        final Wire<Void> wireC = Wire.builder("C").build().cast();
        final WireChannel<Integer, Void> wireCIn = wireC.buildChannel();

        final Wire<Void> wireD = Wire.builder("D").build().cast();
        final WireChannel<List<Integer>, Void> wireDIn = wireD.buildChannel();

        wireA.buildSplitter(Integer.class).solderTo(wireBIn).solderTo(wireCIn);
        wireA.solderTo(wireDIn);

        wireAIn.bind(x -> {
            return List.of(x, x, x);
        });

        final AtomicInteger countB = new AtomicInteger(0);
        wireBIn.bind(x -> {
            countB.set(hash32(countB.get(), x));
        });

        final AtomicInteger countC = new AtomicInteger(0);
        wireCIn.bind(x -> {
            countC.set(hash32(countC.get(), -x));
        });

        final AtomicInteger countD = new AtomicInteger(0);
        wireDIn.bind(x -> {
            int product = 1;
            for (final int i : x) {
                product *= i;
            }
            countD.set(hash32(countD.get(), product));
        });

        int expectedCountB = 0;
        int expectedCountC = 0;
        int expectedCountD = 0;

        for (int i = 0; i < 100; i++) {
            wireAIn.put(i);

            for (int j = 0; j < 3; j++) {
                expectedCountB = hash32(expectedCountB, i);
                expectedCountC = hash32(expectedCountC, -i);
            }

            expectedCountD = hash32(expectedCountD, i * i * i);
        }

        assertEventuallyEquals(expectedCountB, countB::get, Duration.ofSeconds(1), "B did not receive all data");
        assertEventuallyEquals(expectedCountC, countC::get, Duration.ofSeconds(1), "C did not receive all data");
        assertEventuallyEquals(expectedCountD, countD::get, Duration.ofSeconds(1), "D did not receive all data");
    }
}
