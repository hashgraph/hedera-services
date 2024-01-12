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

package com.hedera.node.app.state.merkle.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.hedera.node.app.spi.fixtures.state.TestSchema;
import com.hedera.node.app.spi.state.StateDefinition;
import com.hedera.node.app.state.merkle.MerkleTestBase;
import com.hedera.node.app.state.merkle.StateMetadata;
import org.junit.jupiter.api.Test;

class QueueNodeTest extends MerkleTestBase {
    @Test
    void usesQueueNodeIdFromMetadataIfAvailable() {
        final var metadata = new StateMetadata<>(
                FIRST_SERVICE, new TestSchema(1), StateDefinition.queue(FRUIT_STATE_KEY, STRING_CODEC));
        final var node = new QueueNode<>(metadata);
        assertNotEquals(0x990FF87AD2691DCL, node.getClassId());
    }

    @Test
    void usesDefaultClassIdWithoutMetadata() {
        final var node = new QueueNode();
        assertEquals(0x990FF87AD2691DCL, node.getClassId());
    }
}
