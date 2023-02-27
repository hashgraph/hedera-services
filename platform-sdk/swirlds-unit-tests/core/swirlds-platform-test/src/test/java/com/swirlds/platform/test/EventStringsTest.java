/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.events.BaseEventUnhashedData;
import com.swirlds.common.system.transaction.internal.ConsensusTransactionImpl;
import com.swirlds.platform.EventStrings;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestTypeTags;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisplayName("Event Strings Test")
class EventStringsTest {
    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("Test event strings")
    void testCopy() {
        printAssert(EventStrings.toShortString((EventImpl) null), "null", "should indicate that the object is null");
        printAssert(EventStrings.toShortString((GossipEvent) null), "null", "should indicate that the object is null");
        printAssert(EventStrings.toShortString(new EventImpl()), "null", "should indicate that the object is null");

        final long id = 1;
        final long opId = 2;
        final long spGen = 10;
        final long opGen = 10;

        BaseEventHashedData hashedData = new BaseEventHashedData(
                id, spGen, opGen, new Hash(), new Hash(), Instant.now(), new ConsensusTransactionImpl[0]);
        hashedData.setHash(new Hash());
        BaseEventUnhashedData unhashedData = new BaseEventUnhashedData(opId, new byte[0]);
        printAssert(
                EventStrings.toShortString(new EventImpl(hashedData, unhashedData)),
                String.format("%d,%d", id, BaseEventHashedData.calculateGeneration(spGen, opGen)),
                "should have creator and generation");
        printAssert(
                EventStrings.toShortString(new GossipEvent(hashedData, unhashedData)),
                String.format("%d,%d", id, BaseEventHashedData.calculateGeneration(spGen, opGen)),
                "should have creator and generation");
        printAssert(
                EventStrings.toMediumString(new EventImpl(hashedData, unhashedData)),
                String.format("%d,%d", opId, opGen),
                "should have op creator and generation");
    }

    private static void printAssert(final String toCheck, final String contains, final String msg) {
        System.out.println(toCheck);
        assertTrue(toCheck.contains(contains), msg);
    }
}
