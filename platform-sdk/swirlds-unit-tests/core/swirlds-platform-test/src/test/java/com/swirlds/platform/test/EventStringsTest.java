/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.common.test.fixtures.junit.tags.TestComponentTags;
import com.swirlds.platform.EventStrings;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.system.events.BaseEventHashedData;
import com.swirlds.platform.test.fixtures.event.EventImplTestUtils;
import com.swirlds.platform.test.fixtures.event.TestingEventBuilder;
import java.time.Instant;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisplayName("Event Strings Test")
class EventStringsTest {
    @Test
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("Test event strings")
    void testCopy() {
        final Random random = RandomUtils.getRandomPrintSeed();

        printAssert(EventStrings.toShortString(null), "null", "should indicate that the object is null");
        printAssert(EventStrings.toShortString((GossipEvent) null), "null", "should indicate that the object is null");
        printAssert(EventStrings.toShortString(new EventImpl()), "null", "should indicate that the object is null");

        final long id = 1;
        final long opId = 2;
        final long spGen = 10;
        final long opGen = 10;

        final NodeId selfId = new NodeId(id);
        final NodeId otherId = new NodeId(opId);

        final EventImpl selfParent =
                EventImplTestUtils.createEventImpl(new TestingEventBuilder(random).setCreatorId(selfId), null, null);
        final EventImpl otherParent =
                EventImplTestUtils.createEventImpl(new TestingEventBuilder(random).setCreatorId(otherId), null, null);

        final TestingEventBuilder childBuilder = new TestingEventBuilder(random)
                .setCreatorId(selfId)
                .overrideSelfParentGeneration(spGen)
                .overrideOtherParentGeneration(opGen)
                .setTimeCreated(Instant.now());

        final EventImpl eventImplChild = EventImplTestUtils.createEventImpl(childBuilder, selfParent, otherParent);
        final GossipEvent gossipEventChild = childBuilder.build();

        printAssert(
                EventStrings.toShortString(eventImplChild),
                String.format("%d,%d", id, BaseEventHashedData.calculateGeneration(spGen, opGen)),
                "should have creator and generation");
        printAssert(
                EventStrings.toShortString(gossipEventChild),
                String.format("%d,%d", id, BaseEventHashedData.calculateGeneration(spGen, opGen)),
                "should have creator and generation");
        printAssert(
                EventStrings.toMediumString(eventImplChild),
                String.format("%d,%d", opId, opGen),
                "should have op creator and generation");
    }

    private static void printAssert(final String toCheck, final String contains, final String msg) {
        System.out.println(toCheck);
        assertTrue(toCheck.contains(contains), msg);
    }
}
