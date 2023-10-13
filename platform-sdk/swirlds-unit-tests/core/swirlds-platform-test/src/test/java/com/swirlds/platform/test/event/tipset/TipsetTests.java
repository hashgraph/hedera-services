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

import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.fixtures.RandomAddressBookGenerator;
import com.swirlds.common.test.fixtures.RandomAddressBookGenerator.WeightDistributionStrategy;
import com.swirlds.platform.event.creation.tipset.Tipset;
import com.swirlds.platform.event.creation.tipset.TipsetAdvancementWeight;
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
            assertEquals(expectedTipGenerations.get(nodeId), tipset.getTipGenerationForNode(nodeId));
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

        final AddressBook addressBook = new RandomAddressBookGenerator(random)
                .setSize(nodeCount)
                .setAverageWeight(1)
                .setWeightDistributionStrategy(WeightDistributionStrategy.BALANCED)
                .build();

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
                    initialTipset.getTipGenerationForNode(creatorId),
                    comparisonTipset.getTipGenerationForNode(creatorId));
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
            if (initialTipset.getTipGenerationForNode(nodeId) < comparisonTipset.getTipGenerationForNode(nodeId)) {
                expectedAdvancementCount++;
            }
        }
        assertEquals(
                TipsetAdvancementWeight.of(expectedAdvancementCount, 0),
                initialTipset.getTipAdvancementWeight(selfId, comparisonTipset));
    }

    @Test
    @DisplayName("Weighted getAdvancementCount() Test")
    void weightedGetAdvancementCountTest() {
        final Random random = getRandomPrintSeed();
        final int nodeCount = 100;

        final AddressBook addressBook =
                new RandomAddressBookGenerator(random).setSize(nodeCount).build();

        final Map<NodeId, Long> weights = new HashMap<>();
        for (final Address address : addressBook) {
            weights.put(address.getNodeId(), address.getWeight());
        }

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
                    initialTipset.getTipGenerationForNode(creatorId),
                    comparisonTipset.getTipGenerationForNode(creatorId));
        }

        // Cause the comparison tipset to advance in a random way
        for (final Address address : addressBook) {
            final long generation = random.nextLong(1, 100);

            comparisonTipset.advance(address.getNodeId(), generation);
        }

        long expectedAdvancementCount = 0;
        for (final Address address : addressBook) {
            final NodeId nodeId = address.getNodeId();
            if (nodeId.equals(selfId)) {
                // Self advancements are not counted
                continue;
            }
            if (initialTipset.getTipGenerationForNode(nodeId) < comparisonTipset.getTipGenerationForNode(nodeId)) {
                expectedAdvancementCount += weights.get(nodeId);
            }
        }

        assertEquals(
                TipsetAdvancementWeight.of(expectedAdvancementCount, 0),
                initialTipset.getTipAdvancementWeight(selfId, comparisonTipset));
    }
}
