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

package com.swirlds.platform.event.deduplication;

import static com.swirlds.common.metrics.Metrics.PLATFORM_CATEGORY;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.metrics.LongAccumulator;
import com.swirlds.common.sequence.map.SequenceMap;
import com.swirlds.common.sequence.map.StandardSequenceMap;
import com.swirlds.common.system.events.EventDescriptor;
import com.swirlds.platform.event.GossipEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Deduplicates events.
 * <p>
 * A duplicate event is defined as an event with an identical descriptor, and identical signature to an event that has
 * already been observed.
 */
public class EventDeduplicator {
    /**
     * Initial capacity of {@link #observedEvents}.
     */
    private static final int INITIAL_CAPACITY = 1024;

    /**
     * Deduplicated events are passed to this consumer.
     */
    private final Consumer<GossipEvent> eventConsumer;

    /**
     * The current minimum generation required for an event to be non-ancient.
     */
    private long minimumGenerationNonAncient = 0;

    private static final LongAccumulator.Config DUPLICATE_EVENT_CONFIG = new LongAccumulator.Config(
                    PLATFORM_CATEGORY, "duplicateEvents")
            .withDescription("Events received that exactly match a previous event")
            .withUnit("events");
    private final LongAccumulator duplicateEventAccumulator;

    private static final LongAccumulator.Config DISPARATE_SIGNATURE_CONFIG = new LongAccumulator.Config(
                    PLATFORM_CATEGORY, "eventsWithDisparateSignature")
            .withDescription(
                    "Events received that match a descriptor of a previous event, but with a different signature")
            .withUnit("events");
    private final LongAccumulator disparateSignatureAccumulator;

    /**
     * A map from event descriptor to a set of signatures that have been received for that event.
     */
    private final SequenceMap<EventDescriptor, Set<byte[]>> observedEvents =
            new StandardSequenceMap<>(0, INITIAL_CAPACITY, true, EventDescriptor::getGeneration);

    /**
     * Constructor
     *
     * @param platformContext the platform context
     * @param eventConsumer   deduplicated events are passed to this consumer
     */
    public EventDeduplicator(
            @NonNull final PlatformContext platformContext, @NonNull final Consumer<GossipEvent> eventConsumer) {

        this.eventConsumer = Objects.requireNonNull(eventConsumer);

        this.duplicateEventAccumulator = platformContext.getMetrics().getOrCreate(DUPLICATE_EVENT_CONFIG);
        this.disparateSignatureAccumulator = platformContext.getMetrics().getOrCreate(DISPARATE_SIGNATURE_CONFIG);
    }

    /**
     * Handle a potentially duplicate event
     * <p>
     * Ancient events are ignored. If the input event has not already been observed by this deduplicator, it is passed
     * to the event consumer.
     *
     * @param event the event to handle
     */
    public void handleEvent(@NonNull final GossipEvent event) {
        if (event.getGeneration() < minimumGenerationNonAncient) {
            // Ancient events can be safely ignored.
            return;
        }

        final EventDescriptor eventDescriptor = event.getDescriptor();

        // a duplicate descriptor alone isn't sufficient to constitute a duplicate event
        // we still need to check the signature
        final boolean duplicateDescriptor = observedEvents.containsKey(eventDescriptor);

        if (!duplicateDescriptor) {
            observedEvents.put(eventDescriptor, new HashSet<>());
        }

        if (observedEvents.get(eventDescriptor).add(event.getUnhashedData().getSignature())) {
            if (duplicateDescriptor) {
                // the event is not a duplicate, but the descriptor matches that of a previously received event
                // keep track of this signature disparity, as receiving an event with a different signature
                // is indicative of malicious behavior
                disparateSignatureAccumulator.update(1);
            }

            eventConsumer.accept(event);
        } else {
            duplicateEventAccumulator.update(1);
        }
    }

    /**
     * Set the minimum generation required for an event to be non-ancient.
     *
     * @param minimumGenerationNonAncient the minimum generation required for an event to be non-ancient
     */
    public void setMinimumGenerationNonAncient(final long minimumGenerationNonAncient) {
        this.minimumGenerationNonAncient = minimumGenerationNonAncient;

        observedEvents.shiftWindow(minimumGenerationNonAncient);
    }
}
