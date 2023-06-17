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

import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.RandomAddressBookGenerator;
import com.swirlds.platform.event.tipset.Tipset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Tipset Tests")
class TipsetTests {

    private static void validateTipset(final Tipset tipset, final Map<NodeId, Long> expectedTipGenerations) {
        for (final NodeId nodeId : expectedTipGenerations.keySet()) {
            assertEquals(expectedTipGenerations.get(nodeId), tipset.getTipGenerationForNodeId(nodeId));
        }
    }

    @Test
    @DisplayName("Advancement Test")
    void advancementTest() {
        final Random random = getRandomPrintSeed();

        final int nodeCount = 100;

        final AddressBook addressBook =
                new RandomAddressBookGenerator(random).setSize(nodeCount).build();

        final Tipset tipset = new Tipset(addressBook);
        assertEquals(nodeCount, tipset.size());

        final Map<NodeId, Long> expected = new HashMap<>();

        for (int iteration = 0; iteration < 10; iteration++) {
            for (int creator = 0; creator < 100; creator++) {
                final NodeId creatorId = addressBook.getNodeId(creator);
                final long generation = random.nextLong(1, 100);

                tipset.advance(creatorId, generation);
                expected.put(creatorId, Math.max(generation, expected.getOrDefault(creatorId, 0L)));
                validateTipset(tipset, expected);
            }
        }
    }

    @Test
    @DisplayName("Merge Test")
    void mergeTest() {
        final Random random = getRandomPrintSeed();

        final int nodeCount = 100;

        final AddressBook addressBook =
                new RandomAddressBookGenerator(random).setSize(nodeCount).build();

        for (int count = 0; count < 10; count++) {
            final List<Tipset> tipsets = new ArrayList<>();
            final Map<NodeId, Long> expected = new HashMap<>();

            for (int tipsetIndex = 0; tipsetIndex < 10; tipsetIndex++) {
                final Tipset tipset = new Tipset(addressBook);
                for (int creator = 0; creator < nodeCount; creator++) {
                    final NodeId creatorId = addressBook.getNodeId(creator);
                    final long generation = random.nextLong(1, 100);
                    tipset.advance(creatorId, generation);
                    expected.put(creatorId, Math.max(generation, expected.getOrDefault(creatorId, 0L)));
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

        final int nodeCount = 100;

        final AddressBook addressBook =
                new RandomAddressBookGenerator(random).setSize(nodeCount).build();

        final NodeId selfId = addressBook.getNodeId(random.nextInt(nodeCount));

        final Tipset initialTipset = new Tipset(addressBook);
        for (long creator = 0; creator < nodeCount; creator++) {
            final NodeId creatorId = addressBook.getNodeId((int) creator);
            final long generation = random.nextLong(1, 100);
            initialTipset.advance(creatorId, generation);
        }

        // Merging the tipset with itself will result in a copy
        final Tipset comparisonTipset = Tipset.merge(List.of(initialTipset));
        assertEquals(initialTipset.size(), comparisonTipset.size());
        for (int creator = 0; creator < 100; creator++) {
            final NodeId creatorId = addressBook.getNodeId(creator);
            assertEquals(
                    initialTipset.getTipGenerationForNodeId(creatorId),
                    comparisonTipset.getTipGenerationForNodeId(creatorId));
        }

        // Cause the comparison tipset to advance in a random way
        for (int entryIndex = 0; entryIndex < 100; entryIndex++) {
            final long creator = random.nextLong(100);
            final NodeId creatorId = addressBook.getNodeId((int) creator);
            final long generation = random.nextLong(1, 100);

            comparisonTipset.advance(creatorId, generation);
        }

        long expectedAdvancementCount = 0;
        for (int i = 0; i < 100; i++) {
            final NodeId nodeId = addressBook.getNodeId(i);
            if (nodeId.equals(selfId)) {
                // Self advancements are not counted
                continue;
            }
            if (initialTipset.getTipGenerationForNodeId(nodeId) < comparisonTipset.getTipGenerationForNodeId(nodeId)) {
                expectedAdvancementCount++;
            }
        }
        assertEquals(expectedAdvancementCount, initialTipset.getWeightedAdvancementCount(selfId, comparisonTipset));
    }

    @Test
    @DisplayName("Weighted getAdvancementCount() Test")
    void weightedGetAdvancementCountTest() {
        final Random random = getRandomPrintSeed();
        final int nodeCount = 100;

        final Map<Long, Long> weights = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            weights.put((long) i, random.nextLong(1_000_000));
        }

        final AddressBook addressBook = new RandomAddressBookGenerator(random)
                .setSize(nodeCount)
                .setCustomWeightGenerator(weights::get) // TODO the weights map is from long to long
                .build();

        final NodeId selfId = addressBook.getNodeId(random.nextInt(nodeCount));

        final Tipset initialTipset = new Tipset(addressBook);
        for (long creator = 0; creator < 100; creator++) {
            final NodeId creatorId = addressBook.getNodeId((int) creator);
            final long generation = random.nextLong(1, 100);
            initialTipset.advance(creatorId, generation);
        }

        // Merging the tipset with itself will result in a copy
        final Tipset comparisonTipset = Tipset.merge(List.of(initialTipset));
        assertEquals(initialTipset.size(), comparisonTipset.size());
        for (int creator = 0; creator < 100; creator++) {
            final NodeId creatorId = addressBook.getNodeId(creator);
            assertEquals(
                    initialTipset.getTipGenerationForNodeId(creatorId),
                    comparisonTipset.getTipGenerationForNodeId(creatorId));
        }

        // Cause the comparison tipset to advance in a random way
        for (long creator = 0; creator < 100; creator++) {
            final long generation = random.nextLong(1, 100);

            comparisonTipset.advance(new NodeId(creator), generation);
        }

        long expectedAdvancementCount = 0;
        for (int i = 0; i < 100; i++) {
            final NodeId nodeId = addressBook.getNodeId(i);
            if (nodeId.equals(selfId)) {
                // Self advancements are not counted
                continue;
            }
            if (initialTipset.getTipGenerationForNodeId(nodeId) < comparisonTipset.getTipGenerationForNodeId(nodeId)) {
                expectedAdvancementCount += weights.get((long) i);
            }
        }

        assertEquals(expectedAdvancementCount, initialTipset.getWeightedAdvancementCount(selfId, comparisonTipset));
    }
}
