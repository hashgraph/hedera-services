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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.common.test.fixtures.io.SerializationUtils;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.event.GossipEvent;
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

class GossipEventTest {

    @BeforeAll
    public static void setup() throws FileNotFoundException, ConstructableRegistryException {
        new TestConfigBuilder().getOrCreateConfig();
        StaticSoftwareVersion.setSoftwareVersion(new BasicSoftwareVersion(1));
    }

    @AfterAll
    static void afterAll() {
        StaticSoftwareVersion.reset();
    }

    @Test
    @DisplayName("Serialize and deserialize event")
    void serializeDeserialize() throws IOException, ConstructableRegistryException {
        final Random random = RandomUtils.getRandomPrintSeed();

        final GossipEvent gossipEvent = TestingEventBuilder.builder(random).build();
        ConstructableRegistry.getInstance().registerConstructables("com.swirlds");
        final GossipEvent copy = SerializationUtils.serializeDeserialize(gossipEvent);
        assertEquals(gossipEvent, copy, "deserialized version should be the same");
    }

    @Test
    void validateEqualsHashCode() {
        assertTrue(EqualsVerifier.verify(
                random -> TestingEventBuilder.builder(random).build()));
    }
}
