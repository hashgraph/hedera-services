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

package com.swirlds.platform.test.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.hapi.platform.event.GossipEvent;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.test.fixtures.Randotron;
import com.swirlds.common.test.fixtures.io.InputOutputStream;
import com.swirlds.platform.test.fixtures.event.TestingEventBuilder;
import java.io.IOException;
import org.junit.jupiter.api.Test;

public class GossipEventTest {

    /**
     * Tests the serialization of a {@link GossipEvent} object alonside legacy
     * {@link com.swirlds.common.io.SelfSerializable} objects.
     */
    @Test
    void pbjSerializationTest() throws IOException {
        final Randotron r = Randotron.create();
        final Hash serializable = r.nextHash();
        final GossipEvent original = new TestingEventBuilder(r)
                .setAppTransactionCount(2)
                .setSystemTransactionCount(1)
                .setSelfParent(new TestingEventBuilder(r).build())
                .setOtherParent(new TestingEventBuilder(r).build())
                .build()
                .getGossipEvent();

        try (final InputOutputStream io = new InputOutputStream()) {
            io.getOutput().writeSerializable(serializable, true);
            io.getOutput().writePbjRecord(original, GossipEvent.PROTOBUF);
            io.getOutput().writeSerializable(serializable, false);
            io.getOutput().writePbjRecord(original, GossipEvent.PROTOBUF);

            io.startReading();

            final Hash readSer1 = io.getInput().readSerializable(true, Hash::new);
            final GossipEvent deserialized1 = io.getInput().readPbjRecord(GossipEvent.PROTOBUF);
            final Hash readSer2 = io.getInput().readSerializable(false, Hash::new);
            final GossipEvent deserialized2 = io.getInput().readPbjRecord(GossipEvent.PROTOBUF);

            assertEquals(serializable, readSer1, "the serializable object should be the same as the one written");
            assertEquals(original, deserialized1, "the event should be the same as the one written");
            assertEquals(serializable, readSer2, "the serializable object should be the same as the one written");
            assertEquals(original, deserialized2, "the event should be the same as the one written");
        }
    }
}
