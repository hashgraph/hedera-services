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

package com.swirlds.platform.test.event.creation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.swirlds.common.system.EventCreationRule;
import com.swirlds.common.system.EventCreationRuleResponse;
import com.swirlds.common.system.events.BaseEvent;
import com.swirlds.common.system.events.PlatformEvent;
import com.swirlds.platform.chatter.protocol.peer.CommunicationState;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.creation.AncientParentsRule;
import com.swirlds.platform.event.creation.BelowIntCreationRule;
import com.swirlds.platform.event.creation.ChatteringRule;
import com.swirlds.platform.event.creation.OtherParentTracker;
import com.swirlds.platform.test.event.EventBuilder;
import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestTypeTags;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class RulesTests {

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("ChatteringRule Test")
    void chatteringRuleTest() {
        final int numPeers = 10;
        final double threshold = 0.5;

        final List<CommunicationState> states =
                Stream.generate(CommunicationState::new).limit(numPeers).toList();
        final EventCreationRule rule = new ChatteringRule(threshold, states);

        Assertions.assertEquals(
                EventCreationRuleResponse.DONT_CREATE,
                rule.shouldCreateEvent(),
                "at start, we are not chattering with anyone, so dont create events");
        states.subList(0, 4).forEach(CommunicationState::chatterStarted);
        Assertions.assertEquals(
                EventCreationRuleResponse.DONT_CREATE,
                rule.shouldCreateEvent(),
                "4 out of 10 is below the limit, so dont create");
        states.get(4).chatterStarted();
        Assertions.assertEquals(
                EventCreationRuleResponse.PASS,
                rule.shouldCreateEvent(),
                "5 out of 10 is should be exactly at the threshold, so dont throttle");
        states.subList(5, 10).forEach(CommunicationState::chatterStarted);
        Assertions.assertEquals(
                EventCreationRuleResponse.PASS, rule.shouldCreateEvent(), "chattering to all peers, so dont throttle");
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("OtherParentTracker Test")
    void otherParentTrackerTest() {
        final OtherParentTracker tracker = new OtherParentTracker();

        assertEquals(EventCreationRuleResponse.PASS, tracker.shouldCreateEvent(null, null), "null should always pass");

        final EventBuilder myBuilder = EventBuilder.builder().setCreatorId(1);
        final EventBuilder otherBuilder = EventBuilder.builder().setCreatorId(2);

        final GossipEvent other1 = otherBuilder.buildGossipEvent();
        final GossipEvent self1 = otherBuilder.buildGossipEvent();

        assertEquals(
                EventCreationRuleResponse.PASS,
                tracker.shouldCreateEvent(self1, other1),
                "initially, any event should pass");

        tracker.track(self1);
        assertEquals(
                EventCreationRuleResponse.PASS,
                tracker.shouldCreateEvent(self1, other1),
                "self1 had no parents, so it should not affect anything");

        final GossipEvent self2 = otherBuilder.setOtherParent(other1).buildGossipEvent();
        // track self2 which has an other parent of other1
        tracker.track(self2);

        assertEquals(
                EventCreationRuleResponse.DONT_CREATE,
                tracker.shouldCreateEvent(self2, other1),
                "since other1 is a parent of self2, we should not use it again");
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("BelowIntCreationRule Test")
    void belowIntTest() {
        final int threshold = 10;
        final AtomicInteger value = new AtomicInteger();

        final EventCreationRule rule = new BelowIntCreationRule(value::get, threshold);

        value.set(threshold - 1);
        assertEquals(
                EventCreationRuleResponse.PASS, rule.shouldCreateEvent(), "it should not throttle below the threshold");
        value.set(threshold);
        assertEquals(
                EventCreationRuleResponse.PASS,
                rule.shouldCreateEvent(),
                "it should not throttle when equal to the threshold");
        value.set(threshold + 1);
        assertEquals(
                EventCreationRuleResponse.DONT_CREATE,
                rule.shouldCreateEvent(),
                "it should throttle when above the threshold");
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("AncientParentsRule Test")
    void ancientParentsTest() {

        final Set<BaseEvent> ancientEvents = new HashSet<>();
        final AtomicBoolean areAnyEventsAncient = new AtomicBoolean(false);
        final GraphGenerations graphGenerations = new GraphGenerations() {
            @Override
            public long getMaxRoundGeneration() {
                return FIRST_GENERATION;
            }

            @Override
            public long getMinGenerationNonAncient() {
                return FIRST_GENERATION;
            }

            @Override
            public long getMinRoundGeneration() {
                return FIRST_GENERATION;
            }

            @Override
            public boolean areAnyEventsAncient() {
                return areAnyEventsAncient.get();
            }

            @Override
            public boolean isAncient(final PlatformEvent event) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isAncient(final BaseEvent event) {
                return ancientEvents.contains(event);
            }
        };

        final AncientParentsRule ancientParentsCheck = new AncientParentsRule(() -> graphGenerations);

        final BaseEvent oldSelfParent = mock(BaseEvent.class);
        ancientEvents.add(oldSelfParent);
        final BaseEvent oldOtherParent = mock(BaseEvent.class);
        ancientEvents.add(oldOtherParent);

        final BaseEvent youngSelfParent = mock(BaseEvent.class);
        final BaseEvent youngOtherParent = mock(BaseEvent.class);

        final List<BaseEvent> otherParents = new LinkedList<>();
        otherParents.add(oldOtherParent);
        otherParents.add(youngOtherParent);
        otherParents.add(null);

        final List<BaseEvent> selfParents = new LinkedList<>();
        selfParents.add(oldSelfParent);
        selfParents.add(youngSelfParent);
        selfParents.add(null);

        for (final BaseEvent otherParent : otherParents) {
            for (final BaseEvent selfParent : selfParents) {
                assertEquals(
                        EventCreationRuleResponse.PASS,
                        ancientParentsCheck.shouldCreateEvent(selfParent, otherParent),
                        "there should be no ancient events yet");
            }
        }
        areAnyEventsAncient.set(true);
        for (final BaseEvent otherParent : otherParents) {
            for (final BaseEvent selfParent : selfParents) {
                if (selfParent == youngSelfParent || otherParent == youngOtherParent) {
                    assertEquals(
                            EventCreationRuleResponse.PASS,
                            ancientParentsCheck.shouldCreateEvent(selfParent, otherParent),
                            "if either parent is non-ancient, then we should create an event");
                } else {
                    assertEquals(
                            EventCreationRuleResponse.DONT_CREATE,
                            ancientParentsCheck.shouldCreateEvent(selfParent, otherParent),
                            "if neither parent is non-ancient, then we should NOT create an event");
                }
            }
        }
    }
}
