/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link LinkedEventValidator}
 */
class LinkedEventValidatorTests {
    private AtomicInteger consumedEventCount;
    private AtomicLong exitedIntakePipelineCount;
    private Time time;
    private LinkedEventValidator validator;

    @BeforeEach
    void setup() {
        time = new FakeTime();

        exitedIntakePipelineCount = new AtomicLong(0);
        final IntakeEventCounter intakeEventCounter = mock(IntakeEventCounter.class);
        doAnswer(invocation -> {
                    exitedIntakePipelineCount.incrementAndGet();
                    return null;
                })
                .when(intakeEventCounter)
                .eventExitedIntakePipeline(any());

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        consumedEventCount = new AtomicInteger(0);
        final Consumer<EventImpl> eventConsumer = event -> consumedEventCount.incrementAndGet();

        validator = new LinkedEventValidator(platformContext, time, eventConsumer, intakeEventCounter);
    }

    /**
     * Generate a mock event with the given parameters
     *
     * @param timeChildCreated  the time the child was created
     * @param timeParentCreated the time the parent was created
     * @return the mock event
     */
    private EventImpl generateEvent(
            @NonNull final Instant timeChildCreated, @Nullable final Instant timeParentCreated) {

        final BaseEventHashedData hashedData = mock(BaseEventHashedData.class);
        final GossipEvent baseEvent = mock(GossipEvent.class);
        final EventImpl selfParent = mock(EventImpl.class);
        when(selfParent.getTimeCreated()).thenReturn(timeParentCreated);

        final EventImpl event = mock(EventImpl.class);
        when(event.getHashedData()).thenReturn(hashedData);
        when(event.getTimeCreated()).thenReturn(timeChildCreated);
        when(event.getSelfParent()).thenReturn(selfParent);
        when(event.getBaseEvent()).thenReturn(baseEvent);

        return event;
    }

    @Test
    @DisplayName("A child created after its self parent should pass validation")
    void childCreatedAfterParent() {
        final Instant now = time.now();
        final EventImpl child = generateEvent(now.plusMillis(1000), now);

        validator.handleEvent(child);

        assertEquals(1, consumedEventCount.get());
        assertEquals(0, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("Child created at the same time as parent")
    void childCreatedAtSameTimeAsParent() {
        final Instant now = time.now();
        final EventImpl child = generateEvent(now, now);

        validator.handleEvent(child);

        assertEquals(0, consumedEventCount.get());
        assertEquals(1, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("Child created before parent")
    void childCreatedBeforeParent() {
        final Instant now = time.now();
        final EventImpl child = generateEvent(now, now.plusMillis(1000));

        validator.handleEvent(child);

        assertEquals(0, consumedEventCount.get());
        assertEquals(1, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("Child has no self parent")
    void childHasNoSelfParent() {
        final EventImpl child = generateEvent(time.now(), null);
        when(child.getSelfParent()).thenReturn(null);

        validator.handleEvent(child);

        assertEquals(1, consumedEventCount.get());
        assertEquals(0, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("Ancient events should be discarded")
    void ancientEvent() {
        final Instant now = time.now();
        final EventImpl child = generateEvent(now.plusMillis(1000), now);

        validator.setMinimumGenerationNonAncient(100L);

        validator.handleEvent(child);

        assertEquals(0, consumedEventCount.get());
        assertEquals(1, exitedIntakePipelineCount.get());
    }
}
