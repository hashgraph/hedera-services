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

package com.hedera.node.app.blocks.impl;

import static com.hedera.hapi.block.stream.output.StateChange.ChangeOperationOneOfType.QUEUE_POP;
import static com.hedera.hapi.block.stream.output.StateChange.ChangeOperationOneOfType.QUEUE_PUSH;
import static com.hedera.hapi.block.stream.output.StateChange.ChangeOperationOneOfType.SINGLETON_UPDATE;
import static com.hedera.hapi.util.HapiUtils.asInstant;
import static com.hedera.node.app.blocks.impl.BlockImplUtils.stateIdFor;
import static com.swirlds.state.StateChangeListener.StateType.QUEUE;
import static com.swirlds.state.StateChangeListener.StateType.SINGLETON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.primitives.ProtoString;
import com.hedera.node.app.blocks.BlockStreamService;
import com.hedera.node.app.blocks.schemas.V0540BlockStreamSchema;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BoundaryStateChangeListenerTest {
    private static final int STATE_ID = 1;
    public static final ProtoString PROTO_STRING = new ProtoString("test");
    public static final ProtoBytes PROTO_BYTES = new ProtoBytes(Bytes.wrap(new byte[] {1, 2, 3}));

    private BoundaryStateChangeListener listener;

    @BeforeEach
    void setUp() {
        listener = new BoundaryStateChangeListener();
    }

    @Test
    void targetTypesAreSingletonAndQueue() {
        assertEquals(Set.of(SINGLETON, QUEUE), listener.stateTypes());
    }

    @Test
    void understandsStateIds() {
        final var service = BlockStreamService.NAME;
        final var stateKey = V0540BlockStreamSchema.BLOCK_STREAM_INFO_KEY;
        assertEquals(stateIdFor(service, stateKey), listener.stateIdFor(service, stateKey));
    }

    @Test
    void testFlushChanges() {
        listener.setLastUsedConsensusTime(Instant.now());
        listener.singletonUpdateChange(STATE_ID, PROTO_STRING);
        BlockItem blockItem = listener.flushChanges();

        assertNotNull(blockItem);
        assertTrue(listener.allStateChanges().isEmpty());
    }

    @Test
    void testAllStateChanges() {
        listener.singletonUpdateChange(STATE_ID, PROTO_STRING);
        listener.queuePushChange(STATE_ID, PROTO_BYTES);

        List<StateChange> stateChanges = listener.allStateChanges();
        assertEquals(2, stateChanges.size());
    }

    @Test
    void testEndOfBlockTimestamp() {
        Instant now = Instant.now();
        listener.setLastUsedConsensusTime(now);
        assertEquals(now.plusNanos(1), asInstant(listener.endOfBlockTimestamp()));
    }

    @Test
    void testQueuePushChange() {
        listener.queuePushChange(STATE_ID, PROTO_BYTES);

        StateChange stateChange = listener.allStateChanges().getFirst();
        assertEquals(QUEUE_PUSH, stateChange.changeOperation().kind());
        assertEquals(STATE_ID, stateChange.stateId());
        assertEquals(PROTO_BYTES.value(), stateChange.queuePush().protoBytesElement());
    }

    @Test
    void testQueuePopChange() {
        listener.queuePopChange(STATE_ID);

        StateChange stateChange = listener.allStateChanges().getFirst();
        assertEquals(QUEUE_POP, stateChange.changeOperation().kind());
        assertEquals(STATE_ID, stateChange.stateId());
    }

    @Test
    void testSingletonUpdateChange() {
        listener.singletonUpdateChange(STATE_ID, PROTO_STRING);

        StateChange stateChange = listener.allStateChanges().getFirst();
        assertEquals(SINGLETON_UPDATE, stateChange.changeOperation().kind());
        assertEquals(STATE_ID, stateChange.stateId());
        assertEquals(PROTO_STRING.value(), stateChange.singletonUpdate().stringValue());
    }
}
