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

package com.swirlds.platform.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.platform.event.EventConsensusData;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.test.fixtures.Randotron;
import com.swirlds.common.test.fixtures.io.InputOutputStream;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.StaticSoftwareVersion;
import com.swirlds.platform.system.events.DetailedConsensusEvent;
import com.swirlds.platform.test.fixtures.event.TestingEventBuilder;
import java.io.IOException;
import java.time.Instant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class DetailedConsensusEventTest {
    @BeforeAll
    public static void setUp() throws ConstructableRegistryException {
        final ConstructableRegistry registry = ConstructableRegistry.getInstance();
        registry.registerConstructables("com.swirlds.platform");
        StaticSoftwareVersion.setSoftwareVersion(new BasicSoftwareVersion(1));
    }

    @AfterAll
    static void afterAll() {
        StaticSoftwareVersion.reset();
    }

    @Test
    public void serializeAndDeserializeConsensusEvent() throws IOException {
        DetailedConsensusEvent consensusEvent = generateConsensusEvent();
        try (final InputOutputStream io = new InputOutputStream()) {
            io.getOutput().writeSerializable(consensusEvent, true);
            io.startReading();

            final DetailedConsensusEvent deserialized = io.getInput().readSerializable();
            assertEquals(consensusEvent, deserialized);
        }
    }

    @Test
    public void EventImplGetHashTest() {
        DetailedConsensusEvent consensusEvent = generateConsensusEvent();
        EventImpl event = new EventImpl(consensusEvent);
        CryptographyHolder.get().digestSync(consensusEvent);
        Hash expectedHash = consensusEvent.getHash();
        CryptographyHolder.get().digestSync(event);
        assertEquals(expectedHash, event.getHash());
    }

    private DetailedConsensusEvent generateConsensusEvent() {
        final Randotron random = Randotron.create(68651684861L);
        final Instant consensusTimestamp = random.nextInstant();
        final GossipEvent gossipEvent = new TestingEventBuilder(random).build();
        final EventConsensusData eventConsensusData = EventConsensusData.newBuilder()
                .consensusTimestamp( // TODO use hapiutils
                        Timestamp.newBuilder()
                                .seconds(consensusTimestamp.getEpochSecond())
                                .nanos(consensusTimestamp.getNano())
                                .build())
                .consensusOrder(random.nextLong(0, Long.MAX_VALUE))
                .build();
        gossipEvent.setConsensusData(eventConsensusData);
        return new DetailedConsensusEvent(gossipEvent);
    }
}
