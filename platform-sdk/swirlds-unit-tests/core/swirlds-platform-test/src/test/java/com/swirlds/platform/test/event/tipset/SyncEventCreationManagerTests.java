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

package com.swirlds.platform.test.event.tipset;

import static com.swirlds.common.test.fixtures.AssertionUtils.assertEventuallyTrue;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static com.swirlds.platform.event.creation.EventCreationStatus.RATE_LIMITED;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.creation.EventCreationStatus;
import com.swirlds.platform.event.creation.EventCreator;
import com.swirlds.platform.event.creation.SyncEventCreationManager;
import com.swirlds.platform.event.creation.rules.EventCreationRule;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SyncTipsetEventCreationManager Tests")
class SyncEventCreationManagerTests {

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

        final List<GossipEvent> createdEvents = new ArrayList<>();
        final Function<GossipEvent, Boolean> eventConsumer = e -> {
            createdEvents.add(e);
            return true;
        };

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        final SyncEventCreationManager manager =
                new SyncEventCreationManager(platformContext, Time.getCurrent(), creator, rule, eventConsumer);
        assertEquals(0, eventWasCreatedCount.get());
        assertEquals(0, createdEvents.size());

        manager.maybeCreateEvent();
        assertEquals(1, eventWasCreatedCount.get());
        assertEquals(1, createdEvents.size());
        assertSame(eventsToCreate.get(0), createdEvents.get(0));

        manager.maybeCreateEvent();
        assertEquals(2, eventWasCreatedCount.get());
        assertEquals(2, createdEvents.size());
        assertSame(eventsToCreate.get(1), createdEvents.get(1));

        manager.maybeCreateEvent();
        assertEquals(3, eventWasCreatedCount.get());
        assertEquals(3, createdEvents.size());
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

        final List<GossipEvent> createdEvents = new ArrayList<>();
        final Function<GossipEvent, Boolean> eventConsumer = e -> {
            createdEvents.add(e);
            return true;
        };

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        final SyncEventCreationManager manager =
                new SyncEventCreationManager(platformContext, Time.getCurrent(), creator, rule, eventConsumer);

        assertEquals(0, eventWasCreatedCount.get());
        assertEquals(0, createdEvents.size());

        // Event creation is not permitted
        for (int i = 0; i < 10; i++) {
            manager.maybeCreateEvent();
            assertEquals(0, eventWasCreatedCount.get());
            assertEquals(0, createdEvents.size());
        }

        // Event creation is permitted
        allowCreation.set(true);
        manager.maybeCreateEvent();
        assertEquals(1, eventWasCreatedCount.get());
        assertEquals(1, createdEvents.size());
        assertSame(eventsToCreate.get(0), createdEvents.get(0));

        // Event creation is not permitted
        allowCreation.set(false);
        for (int i = 0; i < 10; i++) {
            manager.maybeCreateEvent();
            assertEquals(1, eventWasCreatedCount.get());
            assertEquals(1, createdEvents.size());
        }

        // Event creation is permitted
        allowCreation.set(true);
        manager.maybeCreateEvent();
        assertEquals(2, eventWasCreatedCount.get());
        assertEquals(2, createdEvents.size());
        assertSame(eventsToCreate.get(1), createdEvents.get(1));

        // Event creation is not permitted
        allowCreation.set(false);
        for (int i = 0; i < 10; i++) {
            manager.maybeCreateEvent();
            assertEquals(2, eventWasCreatedCount.get());
            assertEquals(2, createdEvents.size());
        }

        // Event creation is permitted
        allowCreation.set(true);
        manager.maybeCreateEvent();
        assertEquals(3, eventWasCreatedCount.get());
        assertEquals(3, createdEvents.size());

        // Event creation is not permitted
        allowCreation.set(false);
        for (int i = 0; i < 10; i++) {
            manager.maybeCreateEvent();
            assertEquals(3, eventWasCreatedCount.get());
            assertEquals(3, createdEvents.size());
        }
    }

    @Test
    @DisplayName("Buffered Event Test")
    void bufferedEventTest() {
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

        final List<GossipEvent> createdEvents = new ArrayList<>();
        final AtomicBoolean acceptNewEvents = new AtomicBoolean(false);
        final Function<GossipEvent, Boolean> eventConsumer = e -> {
            if (!acceptNewEvents.get()) {
                return false;
            }
            createdEvents.add(e);
            return true;
        };

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        final SyncEventCreationManager manager =
                new SyncEventCreationManager(platformContext, Time.getCurrent(), creator, rule, eventConsumer);

        assertEquals(0, eventWasCreatedCount.get());
        assertEquals(0, createdEvents.size());

        // Create an event. We are not accepting new events. We will be able to create an event, but it will not be
        // forwarded to the consumer.
        manager.maybeCreateEvent();
        assertEquals(1, eventWasCreatedCount.get());
        assertEquals(0, createdEvents.size());

        // Attempt to create more events. No new events will be created until the consumer accepts the previous event.
        for (int i = 0; i < 10; i++) {
            manager.maybeCreateEvent();
            assertEquals(1, eventWasCreatedCount.get());
            assertEquals(0, createdEvents.size());
        }

        // Accept new events. The buffered event will be forwarded to the consumer, and we will create a new event
        // and forward it while we are at it.
        acceptNewEvents.set(true);
        manager.maybeCreateEvent();
        assertEquals(2, eventWasCreatedCount.get());
        assertEquals(2, createdEvents.size());
        assertSame(eventsToCreate.get(0), createdEvents.get(0));
        assertSame(eventsToCreate.get(1), createdEvents.get(1));

        // Stop accepting events again. We will now be able to create another event and store it in the buffer.
        acceptNewEvents.set(false);
        manager.maybeCreateEvent();
        assertEquals(3, eventWasCreatedCount.get());
        assertEquals(2, createdEvents.size());

        for (int i = 0; i < 10; i++) {
            manager.maybeCreateEvent();
            assertEquals(3, eventWasCreatedCount.get());
            assertEquals(2, createdEvents.size());
        }
    }

    @Test
    @DisplayName("Paused Halts Creation Test")
    void pauseHaltsCreationTest() {
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

        final List<GossipEvent> createdEvents = new ArrayList<>();
        final Function<GossipEvent, Boolean> eventConsumer = e -> {
            createdEvents.add(e);
            return true;
        };

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        final SyncEventCreationManager manager =
                new SyncEventCreationManager(platformContext, Time.getCurrent(), creator, rule, eventConsumer);

        assertEquals(0, eventWasCreatedCount.get());
        assertEquals(0, createdEvents.size());

        // We are unpaused, so we can create events.
        manager.maybeCreateEvent();
        assertEquals(1, eventWasCreatedCount.get());
        assertEquals(1, createdEvents.size());
        assertSame(eventsToCreate.get(0), createdEvents.get(0));

        // While paused we should not be able to create events.
        manager.pauseEventCreation();
        for (int i = 0; i < 10; i++) {
            manager.maybeCreateEvent();
            assertEquals(1, eventWasCreatedCount.get());
            assertEquals(1, createdEvents.size());
        }

        // Once unpaused we should be able to create events again.
        manager.resumeEventCreation();
        manager.maybeCreateEvent();
        assertEquals(2, eventWasCreatedCount.get());
        assertEquals(2, createdEvents.size());
        assertSame(eventsToCreate.get(1), createdEvents.get(1));

        // While paused we should not be able to create events.
        manager.pauseEventCreation();
        for (int i = 0; i < 10; i++) {
            manager.maybeCreateEvent();
            assertEquals(2, eventWasCreatedCount.get());
            assertEquals(2, createdEvents.size());
        }

        // Once unpaused we should be able to create events again.
        manager.resumeEventCreation();
        manager.maybeCreateEvent();
        assertEquals(3, eventWasCreatedCount.get());
    }

    @Test
    @DisplayName("Pause Flushes Buffer Test")
    void pauseFlushesBufferTest() throws InterruptedException {
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

        final List<GossipEvent> createdEvents = new ArrayList<>();
        final AtomicBoolean acceptNewEvents = new AtomicBoolean(false);
        final AtomicLong rejections = new AtomicLong(0);
        final Function<GossipEvent, Boolean> eventConsumer = e -> {
            if (!acceptNewEvents.get()) {
                rejections.getAndIncrement();
                return false;
            }
            createdEvents.add(e);
            return true;
        };

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        final SyncEventCreationManager manager =
                new SyncEventCreationManager(platformContext, Time.getCurrent(), creator, rule, eventConsumer);

        assertEquals(0, eventWasCreatedCount.get());
        assertEquals(0, createdEvents.size());
        assertEquals(0, rejections.get());

        // Create an event. We are not accepting new events. We will be able to create an event, but it will not be
        // forwarded to the consumer.
        manager.maybeCreateEvent();
        assertEquals(1, eventWasCreatedCount.get());
        assertEquals(0, createdEvents.size());
        assertEquals(1, rejections.get());

        // Pausing event creation will force a flush of the buffer, and will block until we begin accepting new events.
        final AtomicBoolean pauseCompleted = new AtomicBoolean(false);
        new ThreadConfiguration(getStaticThreadManager())
                .setRunnable(() -> {
                    manager.pauseEventCreation();
                    pauseCompleted.set(true);
                })
                .build(true);

        // Wait until we see at least one rejection. This means that the thread has started and pause is attempting
        // to flush the buffer.
        assertEventuallyTrue(() -> rejections.get() > 1, Duration.ofSeconds(1), "pause did not begin");

        // Sleep for a short amount of time, giving the thread time to do bad things if it wants to.
        MILLISECONDS.sleep(100);

        assertEquals(1, eventWasCreatedCount.get());
        assertEquals(0, createdEvents.size());

        // Accept new events. The buffered event will be forwarded to the consumer.
        acceptNewEvents.set(true);
        assertEventuallyTrue(pauseCompleted::get, Duration.ofSeconds(1), "pause did not complete");
        assertEquals(1, createdEvents.size());
        assertEquals(1, eventWasCreatedCount.get());
        assertSame(eventsToCreate.get(0), createdEvents.get(0));
    }
}
