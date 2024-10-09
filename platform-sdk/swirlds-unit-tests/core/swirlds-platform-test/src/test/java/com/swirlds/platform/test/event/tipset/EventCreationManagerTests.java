/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.event.tipset;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.event.creation.DefaultEventCreationManager;
import com.swirlds.platform.event.creation.EventCreationManager;
import com.swirlds.platform.event.creation.EventCreator;
import com.swirlds.platform.pool.TransactionPoolNexus;
import com.swirlds.platform.system.events.UnsignedEvent;
import com.swirlds.platform.system.status.PlatformStatus;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EventCreationManagerTests {
    private EventCreator creator;
    private List<UnsignedEvent> eventsToCreate;
    private FakeTime time;
    private EventCreationManager manager;

    @BeforeEach
    void setUp() {
        creator = mock(EventCreator.class);
        eventsToCreate = List.of(mock(UnsignedEvent.class), mock(UnsignedEvent.class), mock(UnsignedEvent.class));
        when(creator.maybeCreateEvent())
                .thenReturn(eventsToCreate.get(0), eventsToCreate.get(1), eventsToCreate.get(2));

        time = new FakeTime();
        final PlatformContext platformContext = TestPlatformContextBuilder.create()
                .withConfiguration(new TestConfigBuilder()
                        .withValue("event.creation.eventIntakeThrottle", 10)
                        .withValue("event.creation.eventCreationRate", 1)
                        .getOrCreateConfig())
                .withTime(time)
                .build();

        manager = new DefaultEventCreationManager(platformContext, mock(TransactionPoolNexus.class), creator);

        manager.updatePlatformStatus(PlatformStatus.ACTIVE);
    }

    @Test
    void basicBehaviorTest() {
        final UnsignedEvent e0 = manager.maybeCreateEvent();
        verify(creator, times(1)).maybeCreateEvent();
        assertNotNull(e0);
        assertSame(eventsToCreate.get(0), e0);

        time.tick(Duration.ofSeconds(1));

        final UnsignedEvent e1 = manager.maybeCreateEvent();
        verify(creator, times(2)).maybeCreateEvent();
        assertNotNull(e1);
        assertSame(eventsToCreate.get(1), e1);

        time.tick(Duration.ofSeconds(1));

        final UnsignedEvent e2 = manager.maybeCreateEvent();
        verify(creator, times(3)).maybeCreateEvent();
        assertNotNull(e2);
        assertSame(eventsToCreate.get(2), e2);
    }

    @Test
    void statusPreventsCreation() {
        final UnsignedEvent e0 = manager.maybeCreateEvent();
        verify(creator, times(1)).maybeCreateEvent();
        assertNotNull(e0);
        assertSame(eventsToCreate.get(0), e0);

        time.tick(Duration.ofSeconds(1));

        manager.updatePlatformStatus(PlatformStatus.BEHIND);
        assertNull(manager.maybeCreateEvent());
        verify(creator, times(1)).maybeCreateEvent();

        time.tick(Duration.ofSeconds(1));

        manager.updatePlatformStatus(PlatformStatus.ACTIVE);
        final UnsignedEvent e1 = manager.maybeCreateEvent();
        assertNotNull(e1);
        verify(creator, times(2)).maybeCreateEvent();
        assertSame(eventsToCreate.get(1), e1);
    }

    @Test
    void ratePreventsCreation() {
        final UnsignedEvent e0 = manager.maybeCreateEvent();
        verify(creator, times(1)).maybeCreateEvent();
        assertNotNull(e0);
        assertSame(eventsToCreate.get(0), e0);

        // no tick

        assertNull(manager.maybeCreateEvent());
        assertNull(manager.maybeCreateEvent());
        verify(creator, times(1)).maybeCreateEvent();

        time.tick(Duration.ofSeconds(1));

        final UnsignedEvent e1 = manager.maybeCreateEvent();
        verify(creator, times(2)).maybeCreateEvent();
        assertNotNull(e1);
        assertSame(eventsToCreate.get(1), e1);
    }

    @Test
    void unhealthyNodePreventsCreation() {
        final UnsignedEvent e0 = manager.maybeCreateEvent();
        verify(creator, times(1)).maybeCreateEvent();
        assertNotNull(e0);
        assertSame(eventsToCreate.get(0), e0);

        time.tick(Duration.ofSeconds(1));

        manager.reportUnhealthyDuration(Duration.ofSeconds(10));

        assertNull(manager.maybeCreateEvent());
        verify(creator, times(1)).maybeCreateEvent();

        time.tick(Duration.ofSeconds(1));

        manager.reportUnhealthyDuration(Duration.ZERO);

        final UnsignedEvent e1 = manager.maybeCreateEvent();
        assertNotNull(e1);
        verify(creator, times(2)).maybeCreateEvent();
        assertSame(eventsToCreate.get(1), e1);
    }
}
