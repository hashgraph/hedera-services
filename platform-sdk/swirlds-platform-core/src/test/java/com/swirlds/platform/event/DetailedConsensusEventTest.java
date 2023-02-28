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

package com.swirlds.platform.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.events.BaseEventUnhashedData;
import com.swirlds.common.system.events.ConsensusData;
import com.swirlds.common.system.events.DetailedConsensusEvent;
import com.swirlds.common.test.io.InputOutputStream;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import java.io.IOException;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class DetailedConsensusEventTest {
    @BeforeAll
    public static void setUp() throws ConstructableRegistryException {
        final ConstructableRegistry registry = ConstructableRegistry.getInstance();
        registry.registerConstructables("com.swirlds.common");
        registry.registerConstructables("com.swirlds.common.events");
        SettingsCommon.maxTransactionCountPerEvent = 245760;
        SettingsCommon.maxTransactionBytesPerEvent = 245760;
        SettingsCommon.transactionMaxBytes = 6144;
        SettingsCommon.maxAddressSizeAllowed = 384;
    }

    @Test
    public void serializeAndDeserializeConsensusEvent() throws IOException {
        final DetailedConsensusEvent consensusEvent = generateConsensusEvent();
        try (final InputOutputStream io = new InputOutputStream()) {
            io.getOutput().writeSerializable(consensusEvent, true);
            io.startReading();

            final DetailedConsensusEvent deserialized = io.getInput().readSerializable();
            assertEquals(consensusEvent, deserialized);
        }
    }

    @Test
    public void EventImplGetHashTest() {
        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();
        final DetailedConsensusEvent consensusEvent = generateConsensusEvent();
        final EventImpl event = new EventImpl(
                consensusEvent.getBaseEventHashedData(),
                consensusEvent.getBaseEventUnhashedData(),
                consensusEvent.getConsensusData());
        platformContext.getCryptography().digestSync(consensusEvent);
        final Hash expectedHash = consensusEvent.getHash();
        platformContext.getCryptography().digestSync(event);
        assertEquals(expectedHash, event.getHash());
    }

    private DetailedConsensusEvent generateConsensusEvent() {
        final Random random = new Random(68651684861L);
        final BaseEventHashedData hashedData = DetGenerateUtils.generateBaseEventHashedData(random);
        final BaseEventUnhashedData unhashedData = DetGenerateUtils.generateBaseEventUnhashedData(random);
        final ConsensusData consensusData = DetGenerateUtils.generateConsensusEventData(random);
        return new DetailedConsensusEvent(hashedData, unhashedData, consensusData);
    }
}
