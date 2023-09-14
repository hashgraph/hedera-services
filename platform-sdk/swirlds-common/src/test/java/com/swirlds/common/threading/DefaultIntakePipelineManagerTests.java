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

package com.swirlds.common.threading;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.system.NodeId;
import com.swirlds.common.test.fixtures.RandomUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DefaultIntakePipelineManager Tests")
class DefaultIntakePipelineManagerTests {
    private IntakePipelineManager pipelineManager;
    private Random random;

    @BeforeEach
    void setup() {
        pipelineManager = new DefaultIntakePipelineManager();
        random = RandomUtils.getRandomPrintSeed();
    }

    /**
     * Generates a list of random node ids
     *
     * @param nodeCount the number of node ids to generate
     * @return the list of node ids
     */
    private List<NodeId> generateNodeIds(final int nodeCount) {
        final List<NodeId> nodeIds = new ArrayList<>();
        for (int i = 0; i < nodeCount; i++) {
            nodeIds.add(new NodeId(random.nextLong(Long.MAX_VALUE)));
        }

        return nodeIds;
    }

    /**
     * Generates a map to track the expected number of unprocessed events for each node id
     *
     * @param nodeIds the list of node ids
     * @return the map, with each entry initialized to 0
     */
    private Map<NodeId, Long> generateExpectedCountMap(@NonNull final List<NodeId> nodeIds) {
        final Map<NodeId, Long> expectedCounts = new HashMap<>();
        for (final NodeId nodeId : nodeIds) {
            if (nodeId != null) {
                expectedCounts.put(nodeId, 0L);
            }
        }

        return expectedCounts;
    }

    /**
     * Performs a number of random of operations on the intake pipeline manager
     *
     * @param nodeIds        the list of node ids
     * @param expectedCounts a structure to track the expected number of unprocessed events for each node id
     * @param operationCount the number of operations to perform
     */
    private void performRandomOperations(
            @NonNull final List<NodeId> nodeIds,
            @NonNull final Map<NodeId, Long> expectedCounts,
            final int operationCount) {

        for (int i = 0; i < operationCount; i++) {
            final NodeId nodeId = nodeIds.get(random.nextInt(nodeIds.size()));

            if (random.nextBoolean()) {
                // add an event to the intake pipeline
                pipelineManager.eventAddedToIntakePipeline(nodeId);

                if (nodeId != null) {
                    expectedCounts.put(nodeId, expectedCounts.get(nodeId) + 1);
                }
            } else {
                // remove an event from the intake pipeline
                if (nodeId != null && expectedCounts.get(nodeId) - 1 < 0) {
                    assertThrows(IllegalStateException.class, () -> pipelineManager.eventThroughIntakePipeline(nodeId));
                } else {
                    pipelineManager.eventThroughIntakePipeline(nodeId);

                    if (nodeId != null) {
                        expectedCounts.put(nodeId, expectedCounts.get(nodeId) - 1);
                    }
                }
            }
        }
    }

    /**
     * Asserts that the number of unprocessed events for each node id is as expected based on the given map
     *
     * @param nodeIds        the list of node ids
     * @param expectedCounts a map from node id to the expected number of unprocessed events
     */
    private void assertValidState(
            @NonNull final List<NodeId> nodeIds, @NonNull final Map<NodeId, Long> expectedCounts) {

        for (final NodeId nodeId : nodeIds) {
            if (nodeId != null) {
                if (expectedCounts.get(nodeId) == 0) {
                    assertFalse(pipelineManager.hasUnprocessedEvents(nodeId));
                } else {
                    assertTrue(pipelineManager.hasUnprocessedEvents(nodeId));
                }
            }
        }
    }

    @Test
    @DisplayName("Standard operation")
    void standardOperation() {
        final List<NodeId> nodeIds = generateNodeIds(50);
        final Map<NodeId, Long> expectedCounts = generateExpectedCountMap(nodeIds);

        // add a null node id to test that edge case
        nodeIds.add(null);

        performRandomOperations(nodeIds, expectedCounts, 10000);
        assertValidState(nodeIds, expectedCounts);
    }

    @Test
    @DisplayName("Check unprocessed events for uninitialized manager")
    void unprocessedEventsForUninitializedManager() {
        final List<NodeId> nodeIds = generateNodeIds(10);

        for (final NodeId nodeId : nodeIds) {
            assertFalse(pipelineManager.hasUnprocessedEvents(nodeId));
        }
    }

    @Test
    @DisplayName("Test reset behavior")
    void testReset() {
        final List<NodeId> nodeIds = generateNodeIds(50);
        final Map<NodeId, Long> expectedCounts = generateExpectedCountMap(nodeIds);

        performRandomOperations(nodeIds, expectedCounts, 100);

        assertValidState(nodeIds, expectedCounts);
        pipelineManager.reset();

        for (final NodeId nodeId : nodeIds) {
            assertFalse(pipelineManager.hasUnprocessedEvents(nodeId));
        }
    }
}
