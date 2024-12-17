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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.common.test.fixtures.Randotron;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.event.EventSerializationUtils;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.StaticSoftwareVersion;
import com.swirlds.platform.test.fixtures.event.TestingEventBuilder;
import com.swirlds.platform.test.utils.EqualsVerifier;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Random;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlatformEventTest {

    @BeforeAll
    public static void setup() throws FileNotFoundException, ConstructableRegistryException {
        new TestConfigBuilder().getOrCreateConfig();
        StaticSoftwareVersion.setSoftwareVersion(new BasicSoftwareVersion(1));
        ConstructableRegistry.getInstance().registerConstructables("com.swirlds");
    }

    @AfterAll
    static void afterAll() {
        StaticSoftwareVersion.reset();
    }

    @Test
    @DisplayName("Serialize and deserialize event with 2 app payloads")
    void serializeDeserializeAppPayloads() throws IOException, ConstructableRegistryException {
        final Random random = RandomUtils.getRandomPrintSeed();

        final PlatformEvent platformEvent = new TestingEventBuilder(random).build();
        final PlatformEvent copy = EventSerializationUtils.serializeDeserializePlatformEvent(platformEvent);
        assertEquals(platformEvent, copy, "deserialized version should be the same");
    }

    @Test
    @DisplayName("Serialize and deserialize event with no payloads")
    void serializeDeserializeNoPayloads() throws IOException, ConstructableRegistryException {
        final Random random = RandomUtils.getRandomPrintSeed();

        final PlatformEvent platformEvent = new TestingEventBuilder(random)
                .setSystemTransactionCount(0)
                .setAppTransactionCount(0)
                .build();
        final PlatformEvent copy = EventSerializationUtils.serializeDeserializePlatformEvent(platformEvent);
        assertEquals(platformEvent, copy, "deserialized version should be the same");
    }

    @Test
    @DisplayName("Serialize and deserialize event with 2 system payloads")
    void serializeDeserializeSystemPayloads() throws IOException, ConstructableRegistryException {
        final Random random = RandomUtils.getRandomPrintSeed();

        final PlatformEvent platformEvent = new TestingEventBuilder(random)
                .setAppTransactionCount(0)
                .setSystemTransactionCount(2)
                .build();
        final PlatformEvent copy = EventSerializationUtils.serializeDeserializePlatformEvent(platformEvent);
        assertEquals(platformEvent, copy, "deserialized version should be the same");
    }

    @Test
    @DisplayName("Serialize and deserialize event with 2 system payloads and 2 app payloads")
    void serializeDeserializeAppAndSystemPayloads() throws IOException, ConstructableRegistryException {
        final Random random = RandomUtils.getRandomPrintSeed();

        final PlatformEvent platformEvent = new TestingEventBuilder(random)
                .setAppTransactionCount(2)
                .setSystemTransactionCount(2)
                .build();
        final PlatformEvent copy = EventSerializationUtils.serializeDeserializePlatformEvent(platformEvent);
        assertEquals(platformEvent, copy, "deserialized version should be the same");
    }

    @Test
    void validateEqualsHashCode() {
        assertTrue(EqualsVerifier.verify(random -> new TestingEventBuilder(random).build()));
    }

    @Test
    void validateDescriptor() {
        final Randotron r = Randotron.create();
        final PlatformEvent event = new TestingEventBuilder(r).build();
        event.invalidateHash();
        assertThrows(
                IllegalStateException.class,
                event::getDescriptor,
                "When the descriptor is not set, an exception should be thrown");
        event.setHash(r.nextHash());
        assertNotNull(event.getDescriptor(), "When the hash is set, the descriptor should be returned");
    }
}
