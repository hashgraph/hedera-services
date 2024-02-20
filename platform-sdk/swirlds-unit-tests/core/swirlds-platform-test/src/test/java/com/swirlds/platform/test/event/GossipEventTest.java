/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.test.fixtures.io.SerializationUtils;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.test.fixtures.event.TestingEventBuilder;
import com.swirlds.platform.test.utils.EqualsVerifier;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GossipEventTest {

    @BeforeAll
    public static void setup() throws FileNotFoundException, ConstructableRegistryException {
        new TestConfigBuilder().getOrCreateConfig();
    }

    @Test
    @DisplayName("Serialize and deserialize event")
    void serializeDeserialize() throws IOException, ConstructableRegistryException {
        final GossipEvent gossipEvent = TestingEventBuilder.builder().buildGossipEvent();
        ConstructableRegistry.getInstance().registerConstructables("com.swirlds");
        final GossipEvent copy = SerializationUtils.serializeDeserialize(gossipEvent);
        assertEquals(gossipEvent, copy, "deserialized version should be the same");
    }

    @Test
    @DisplayName("Deserialize prior version of event")
    void deserializePriorVersion() throws IOException, ConstructableRegistryException {
        ConstructableRegistry.getInstance().registerConstructables("com.swirlds");
        final File file = new File("src/test/resources/eventFiles/eventSerializationV45/sampleGossipEvent.evts");
        final SerializableDataInputStream in = new SerializableDataInputStream(new FileInputStream(file));
        final GossipEvent gossipEvent = in.readSerializable(false, GossipEvent::new);
        assertEquals(3, gossipEvent.getHashedData().getVersion());
        assertEquals(1, gossipEvent.getUnhashedData().getVersion());
        final GossipEvent copy = SerializationUtils.serializeDeserialize(gossipEvent);
        assertEquals(gossipEvent, copy, "deserialized version should be the same");
        assertEquals(
                gossipEvent.getHashedData().getVersion(), copy.getHashedData().getVersion());

        final byte[] original = new FileInputStream(file).readAllBytes();
        final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        final SerializableDataOutputStream out = new SerializableDataOutputStream(outBytes);
        out.writeSerializable(gossipEvent, false);
        final byte[] serialized = outBytes.toByteArray();
        assertArrayEquals(original, serialized, "serialized bytes should be the same");
    }

    @Test
    void validateEqualsHashCode() {
        assertTrue(EqualsVerifier.verify(
                r -> TestingEventBuilder.builder().setRandom(r).buildGossipEvent()));
    }
}
