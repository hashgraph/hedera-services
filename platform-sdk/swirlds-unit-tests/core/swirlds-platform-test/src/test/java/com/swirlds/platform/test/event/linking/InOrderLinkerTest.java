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

package com.swirlds.platform.test.event.linking;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.common.config.ConsensusConfig;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.events.BaseEventUnhashedData;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.linking.EventLinker;
import com.swirlds.platform.event.linking.InOrderLinker;
import com.swirlds.platform.event.linking.ParentFinder;
import com.swirlds.platform.sync.Generations;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class InOrderLinkerTest {
    @Test
    void testMissingParents() {
        final int ancient = 500;
        final int barelyAncient = 699;
        final int barelyNonAncient = 700;
        final int nonAncient = 800;

        testMissingParents(barelyNonAncient, ancient, false);
        testMissingParents(barelyAncient, ancient, true);
        testMissingParents(ancient, barelyNonAncient, false);
        testMissingParents(nonAncient, barelyNonAncient, false);
    }

    void testMissingParents(final int selfParentGen, final int otherParentGen, final boolean expectedValidity) {
        final GossipEvent event = mockEvent(selfParentGen, otherParentGen);
        final EventLinker linker = new InOrderLinker(
                ConfigurationHolder.getConfigData(ConsensusConfig.class), new ParentFinder(h -> null), l -> null);
        linker.updateGenerations(new Generations(100, 700, 800));
        linker.linkEvent(event);

        assertEquals(expectedValidity, linker.hasLinkedEvents(), "unexpected validity");
    }

    private GossipEvent mockEvent(final long selfParentGen, final long otherParentGen) {
        final BaseEventHashedData hashedData = Mockito.mock(BaseEventHashedData.class);
        Mockito.when(hashedData.getSelfParentGen()).thenReturn(selfParentGen);
        Mockito.when(hashedData.getOtherParentGen()).thenReturn(otherParentGen);

        return new GossipEvent(hashedData, new BaseEventUnhashedData());
    }
}
