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

package com.swirlds.state.merkle.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.swirlds.state.test.fixtures.merkle.MerkleTestBase;
import org.junit.jupiter.api.Test;

class QueueNodeTest extends MerkleTestBase {
    @Test
    void usesQueueNodeIdFromMetadataIfAvailable() {
        final var node = new QueueNode<>(
                FIRST_SERVICE,
                FRUIT_STATE_KEY,
                queueNodeClassId(FRUIT_STATE_KEY),
                singletonClassId(FRUIT_STATE_KEY),
                STRING_CODEC);
        assertNotEquals(0x990FF87AD2691DCL, node.getClassId());
    }

    @Test
    void usesDefaultClassIdWithoutMetadata() {
        final var node = new QueueNode<>();
        assertEquals(0x990FF87AD2691DCL, node.getClassId());
    }
}
