/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.test.fixtures.config.TestConfigBuilder;
import com.swirlds.common.test.io.SerializationUtils;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.test.utils.EqualsVerifier;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GossipEventTest {

    @BeforeAll
    public static void setup() throws FileNotFoundException, ConstructableRegistryException {
        new TestConfigBuilder().getOrCreateConfig();
    }

    @Test
    void test() throws IOException, ConstructableRegistryException {
        final IndexedEvent indexedEvent = RandomEventUtils.randomEvent(new Random(), 0, null, null);
        final GossipEvent gossipEvent =
                new GossipEvent(indexedEvent.getBaseEventHashedData(), indexedEvent.getBaseEventUnhashedData());
        ConstructableRegistry.getInstance().registerConstructables("com.swirlds");
        SettingsCommon.maxTransactionCountPerEvent = 1000;
        SettingsCommon.transactionMaxBytes = 1000;
        final GossipEvent copy = SerializationUtils.serializeDeserialize(gossipEvent);
        assertEquals(gossipEvent, copy, "deserialized version should be the same");
    }

    @Test
    void validateEqualsHashCode() {
        assertTrue(EqualsVerifier.verify(EqualsVerifier::randomGossipEvent));
    }
}
