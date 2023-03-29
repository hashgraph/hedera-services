/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.event.tipset;

import static com.swirlds.common.test.RandomUtils.getRandomPrintSeed;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.platform.event.tipset.Tipset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.IntToLongFunction;
import java.util.function.LongToIntFunction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Tipset Tests")
class TipsetTests {

    // TODO tests with non-consecutive node IDs

    private static final LongToIntFunction nodeIdToIndex = x -> (int) x;
    private static final IntToLongFunction indexToWeight = x -> 1;

    private static void validateTipset(final Tipset tipset, final Map<Long, Long> expectedTipGenerations) {
        for (final Long nodeId : expectedTipGenerations.keySet()) {
            assertEquals(expectedTipGenerations.get(nodeId), tipset.getTipGenerationForNodeId(nodeId));
        }
    }

    @Test
    @DisplayName("Advancement Test")
    void advancementTest() {
        final Random random = getRandomPrintSeed();

        final int nodeCount = 100;

        final Tipset tipset = new Tipset(nodeCount, nodeIdToIndex, indexToWeight);
        assertEquals(nodeCount, tipset.size());

        final Map<Long, Long> expected = new HashMap<>();

        for (int iteration = 0; iteration < 10; iteration++) {
            for (long creator = 0; creator < 100; creator++) {
                final long generation = random.nextLong(1, 100);

                tipset.advance(creator, generation);
                expected.put(creator, Math.max(generation, expected.getOrDefault(creator, 0L)));
                validateTipset(tipset, expected);
            }
        }
    }

    @Test
    @DisplayName("Merge Test")
    void mergeTest() {
        final Random random = getRandomPrintSeed();

        final int nodeCount = 100;

        for (int count = 0; count < 10; count++) {
            final List<Tipset> tipsets = new ArrayList<>();
            final Map<Long, Long> expected = new HashMap<>();

            for (int tipsetIndex = 0; tipsetIndex < 10; tipsetIndex++) {
                final Tipset tipset = new Tipset(nodeCount, nodeIdToIndex, indexToWeight);
                for (long creator = 0; creator < nodeCount; creator++) {
                    final long generation = random.nextLong(1, 100);
                    tipset.advance(creator, generation);
                    expected.put(creator, Math.max(generation, expected.getOrDefault(creator, 0L)));
                }
                tipsets.add(tipset);
            }

            final Tipset merged = Tipset.merge(tipsets);
            validateTipset(merged, expected);
        }
    }

    @Test
    @DisplayName("getAdvancementCount() Test")
    void getAdvancementCountTest() {
        final Random random = getRandomPrintSeed();
        final long nodeId = random.nextLong(100);

        final int nodeCount = 100;

        final Tipset initialTipset = new Tipset(nodeCount, nodeIdToIndex, indexToWeight);
        for (long creator = 0; creator < nodeCount; creator++) {
            final long generation = random.nextLong(1, 100);
            initialTipset.advance(creator, generation);
        }

        // Merging the tipset with itself will result in a copy
        final Tipset comparisonTipset = Tipset.merge(List.of(initialTipset));
        assertEquals(initialTipset.size(), comparisonTipset.size());
        for (int creatorId = 0; creatorId < 100; creatorId++) {
            assertEquals(
                    initialTipset.getTipGenerationForNodeId(creatorId),
                    comparisonTipset.getTipGenerationForNodeId(creatorId));
        }

        // Cause the comparison tipset to advance in a random way
        for (int entryIndex = 0; entryIndex < 100; entryIndex++) {
            final long creator = random.nextLong(100);
            final long generation = random.nextLong(1, 100);

            comparisonTipset.advance(creator, generation);
        }

        long expectedAdvancementCount = 0;
        for (int i = 0; i < 100; i++) {
            if (i == nodeId) {
                // Self advancements are not counted
                continue;
            }
            if (initialTipset.getTipGenerationForNodeId(i) < comparisonTipset.getTipGenerationForNodeId(i)) {
                expectedAdvancementCount++;
            }
        }

        assertEquals(expectedAdvancementCount, initialTipset.getWeightedAdvancementCount(nodeId, comparisonTipset));
    }

    @Test
    @DisplayName("Weighted getAdvancementCount() Test")
    void weightedGetAdvancementCountTest() {
        final Random random = getRandomPrintSeed();
        final int nodeCount = 100;
        final long nodeId = random.nextLong(nodeCount);

        final Map<Long, Long> weights = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            weights.put((long) i, random.nextLong(1_000_000));
        }

        final Tipset initialTipset = new Tipset(nodeCount, nodeIdToIndex, x -> weights.get((long) x));
        for (long creator = 0; creator < 100; creator++) {
            final long generation = random.nextLong(1, 100);
            initialTipset.advance(creator, generation);
        }

        // Merging the tipset with itself will result in a copy
        final Tipset comparisonTipset = Tipset.merge(List.of(initialTipset));
        assertEquals(initialTipset.size(), comparisonTipset.size());
        for (int creatorId = 0; creatorId < 100; creatorId++) {
            assertEquals(
                    initialTipset.getTipGenerationForNodeId(creatorId),
                    comparisonTipset.getTipGenerationForNodeId(creatorId));
        }

        // Cause the comparison tipset to advance in a random way
        for (long creator = 0; creator < 100; creator++) {
            final long generation = random.nextLong(1, 100);

            comparisonTipset.advance(creator, generation);
        }

        long expectedAdvancementCount = 0;
        for (int i = 0; i < 100; i++) {
            if (i == nodeId) {
                // Self advancements are not counted
                continue;
            }
            if (initialTipset.getTipGenerationForNodeId(i) < comparisonTipset.getTipGenerationForNodeId(i)) {
                expectedAdvancementCount += weights.get((long) i);
            }
        }

        assertEquals(expectedAdvancementCount, initialTipset.getWeightedAdvancementCount(nodeId, comparisonTipset));
    }
}
