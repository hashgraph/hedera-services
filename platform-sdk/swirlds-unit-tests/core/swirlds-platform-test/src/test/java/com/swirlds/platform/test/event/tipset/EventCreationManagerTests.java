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

import static com.swirlds.platform.event.creation.EventCreationStatus.RATE_LIMITED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.creation.DefaultEventCreationManager;
import com.swirlds.platform.event.creation.EventCreationManager;
import com.swirlds.platform.event.creation.EventCreationStatus;
import com.swirlds.platform.event.creation.EventCreator;
import com.swirlds.platform.event.creation.rules.EventCreationRule;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EventCreationManager Tests")
class EventCreationManagerTests {

    @Test
    @DisplayName("Basic Behavior Test")
    void basicBehaviorTest() {
        final EventCreator creator = mock(EventCreator.class);
        final List<GossipEvent> eventsToCreate =
                List.of(mock(GossipEvent.class), mock(GossipEvent.class), mock(GossipEvent.class));
        when(creator.maybeCreateEvent())
                .thenReturn(eventsToCreate.get(0), eventsToCreate.get(1), eventsToCreate.get(2));

        final AtomicInteger eventWasCreatedCount = new AtomicInteger(0);
        final EventCreationRule rule = new EventCreationRule() {
            @Override
            public boolean isEventCreationPermitted() {
                return true;
            }

            @Override
            public void eventWasCreated() {
                eventWasCreatedCount.getAndIncrement();
            }

            @NonNull
            @Override
            public EventCreationStatus getEventCreationStatus() {
                return RATE_LIMITED;
            }
        };

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        final EventCreationManager manager = new DefaultEventCreationManager(platformContext, creator, rule);
        assertEquals(0, eventWasCreatedCount.get());

        final GossipEvent e0 = manager.maybeCreateEvent();
        assertNotNull(e0);
        assertEquals(1, eventWasCreatedCount.get());
        assertSame(eventsToCreate.get(0), e0);

        final GossipEvent e1 = manager.maybeCreateEvent();
        assertEquals(2, eventWasCreatedCount.get());
        assertNotNull(e1);
        assertSame(eventsToCreate.get(1), e1);

        final GossipEvent e2 = manager.maybeCreateEvent();
        assertNotNull(e2);
        assertEquals(3, eventWasCreatedCount.get());
        assertSame(eventsToCreate.get(2), e2);
    }

    @Test
    @DisplayName("Rules Prevent Creation Test")
    void rulesPreventCreationTest() {
        final EventCreator creator = mock(EventCreator.class);
        final List<GossipEvent> eventsToCreate =
                List.of(mock(GossipEvent.class), mock(GossipEvent.class), mock(GossipEvent.class));
        when(creator.maybeCreateEvent())
                .thenReturn(eventsToCreate.get(0), eventsToCreate.get(1), eventsToCreate.get(2));

        final AtomicInteger eventWasCreatedCount = new AtomicInteger(0);
        final AtomicBoolean allowCreation = new AtomicBoolean(false);
        final EventCreationRule rule = new EventCreationRule() {
            @Override
            public boolean isEventCreationPermitted() {
                return allowCreation.get();
            }

            @Override
            public void eventWasCreated() {
                eventWasCreatedCount.getAndIncrement();
            }

            @NonNull
            @Override
            public EventCreationStatus getEventCreationStatus() {
                return RATE_LIMITED;
            }
        };

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        final EventCreationManager manager = new DefaultEventCreationManager(platformContext, creator, rule);

        assertEquals(0, eventWasCreatedCount.get());

        // Event creation is not permitted
        for (int i = 0; i < 10; i++) {
            assertNull(manager.maybeCreateEvent());
            assertEquals(0, eventWasCreatedCount.get());
        }

        // Event creation is permitted
        allowCreation.set(true);
        final GossipEvent e0 = manager.maybeCreateEvent();
        assertEquals(1, eventWasCreatedCount.get());
        assertSame(eventsToCreate.get(0), e0);

        // Event creation is not permitted
        allowCreation.set(false);
        for (int i = 0; i < 10; i++) {
            assertNull(manager.maybeCreateEvent());
            assertEquals(1, eventWasCreatedCount.get());
        }

        // Event creation is permitted
        allowCreation.set(true);
        final GossipEvent e1 = manager.maybeCreateEvent();
        assertEquals(2, eventWasCreatedCount.get());
        assertSame(eventsToCreate.get(1), e1);

        // Event creation is not permitted
        allowCreation.set(false);
        for (int i = 0; i < 10; i++) {
            assertNull(manager.maybeCreateEvent());
            assertEquals(2, eventWasCreatedCount.get());
        }

        // Event creation is permitted
        allowCreation.set(true);
        final GossipEvent e2 = manager.maybeCreateEvent();
        assertEquals(3, eventWasCreatedCount.get());
        assertSame(eventsToCreate.get(2), e2);

        // Event creation is not permitted
        allowCreation.set(false);
        for (int i = 0; i < 10; i++) {
            assertNull(manager.maybeCreateEvent());
            assertEquals(3, eventWasCreatedCount.get());
        }
    }
}
