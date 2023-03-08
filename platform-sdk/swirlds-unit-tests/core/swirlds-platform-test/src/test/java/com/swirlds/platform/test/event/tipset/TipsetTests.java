/*
 * Copyright 2016-2023 Hedera Hashgraph, LLC
 *
 * This software is the confidential and proprietary information of
 * Hedera Hashgraph, LLC. ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Hedera Hashgraph.
 *
 * HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
 * THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, OR NON-INFRINGEMENT. HEDERA HASHGRAPH SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
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
            assertEquals(expectedTipGenerations.get(nodeId), tipset.getTipGeneration(nodeId));
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
            assertEquals(initialTipset.getTipGeneration(creatorId), comparisonTipset.getTipGeneration(creatorId));
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
            if (initialTipset.getTipGeneration(i) < comparisonTipset.getTipGeneration(i)) {
                expectedAdvancementCount++;
            }
        }

        assertEquals(expectedAdvancementCount, initialTipset.getAdvancementCount(nodeId, comparisonTipset));
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
            assertEquals(initialTipset.getTipGeneration(creatorId), comparisonTipset.getTipGeneration(creatorId));
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
            if (initialTipset.getTipGeneration(i) < comparisonTipset.getTipGeneration(i)) {
                expectedAdvancementCount += weights.get((long) i);
            }
        }

        assertEquals(expectedAdvancementCount, initialTipset.getAdvancementCount(nodeId, comparisonTipset));
    }
}
