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

package com.swirlds.platform.event.deduplication;

import static com.swirlds.common.metrics.Metrics.PLATFORM_CATEGORY;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.metrics.LongAccumulator;
import com.swirlds.common.sequence.map.SequenceMap;
import com.swirlds.common.sequence.map.StandardSequenceMap;
import com.swirlds.platform.consensus.NonAncientEventWindow;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.metrics.EventIntakeMetrics;
import com.swirlds.platform.system.events.EventDescriptor;
import com.swirlds.platform.wiring.ClearTrigger;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Deduplicates events.
 * <p>
 * A duplicate event is defined as an event with an identical descriptor and identical signature to an event that has
 * already been observed.
 * <p>
 * It is necessary to consider the signature bytes when determining if an event is a duplicate, not just the descriptor
 * or hash. This guards against a malicious node gossiping the same event with different signatures, or a node gossiping
 * another node's event with a modified signature. If we went only off the descriptor or hash, we might discard the
 * correct version of an event as a duplicate, because a malicious version has already been received.
 * Instead, the deduplicator lets all versions of the event through that have a unique descriptor/signature pair, and
 * the signature validator further along the pipeline will handle discarding bad versions.
 */
public class EventDeduplicator {
    /**
     * Avoid the creation of lambdas for Map.computeIfAbsent() by reusing this lambda.
     */
    private static final Function<EventDescriptor, Set<ByteBuffer>> NEW_HASH_SET = ignored -> new HashSet<>();

    /**
     * Initial capacity of {@link #observedEvents}.
     */
    private static final int INITIAL_CAPACITY = 1024;

    /**
     * The current non-ancient event window.
     */
    private NonAncientEventWindow nonAncientEventWindow = NonAncientEventWindow.INITIAL_EVENT_WINDOW;

    /**
     * Keeps track of the number of events in the intake pipeline from each peer
     */
    private final IntakeEventCounter intakeEventCounter;

    /**
     * A map from event descriptor to a set of signatures that have been received for that event.
     */
    private final SequenceMap<EventDescriptor, Set<ByteBuffer>> observedEvents =
            new StandardSequenceMap<>(0, INITIAL_CAPACITY, true, EventDescriptor::getGeneration);

    private static final LongAccumulator.Config DISPARATE_SIGNATURE_CONFIG = new LongAccumulator.Config(
                    PLATFORM_CATEGORY, "eventsWithDisparateSignature")
            .withDescription(
                    "Events received that match a descriptor of a previous event, but with a different signature")
            .withUnit("events");
    private final LongAccumulator disparateSignatureAccumulator;

    /**
     * Keeps track of the number of events that are duplicates
     * <p>
     * Future work: Duplicate event metrics should be created and managed by this class directly once the intake
     * monolith is dismantled.
     */
    private final EventIntakeMetrics eventIntakeMetrics;

    /**
     * Constructor
     *
     * @param platformContext    the platform context
     * @param intakeEventCounter keeps track of the number of events in the intake pipeline from each peer
     * @param eventIntakeMetrics keeps track of the number of events that are duplicates
     */
    public EventDeduplicator(
            @NonNull final PlatformContext platformContext,
            @NonNull final IntakeEventCounter intakeEventCounter,
            @NonNull final EventIntakeMetrics eventIntakeMetrics) {

        this.intakeEventCounter = Objects.requireNonNull(intakeEventCounter);
        this.eventIntakeMetrics = Objects.requireNonNull(eventIntakeMetrics);

        this.disparateSignatureAccumulator = platformContext.getMetrics().getOrCreate(DISPARATE_SIGNATURE_CONFIG);
    }

    /**
     * Handle a potentially duplicate event
     * <p>
     * Ancient events are ignored. If the input event has not already been observed by this deduplicator, it is returned.
     *
     * @param event the event to handle
     * @return the event if it is not a duplicate, or null if it is a duplicate
     */
    @Nullable
    public GossipEvent handleEvent(@NonNull final GossipEvent event) {
        if (nonAncientEventWindow.isAncient(event)) {
            // Ancient events can be safely ignored.
            intakeEventCounter.eventExitedIntakePipeline(event.getSenderId());
            return null;
        }

        final Set<ByteBuffer> signatures = observedEvents.computeIfAbsent(event.getDescriptor(), NEW_HASH_SET);
        if (signatures.add(ByteBuffer.wrap(event.getUnhashedData().getSignature()))) {
            if (signatures.size() != 1) {
                // signature is unique, but descriptor is not
                disparateSignatureAccumulator.update(1);
            }

            eventIntakeMetrics.nonDuplicateEvent();

            return event;
        } else {
            // duplicate descriptor and signature
            eventIntakeMetrics.duplicateEvent();
            intakeEventCounter.eventExitedIntakePipeline(event.getSenderId());

            return null;
        }
    }

    /**
     * Set the NonAncientEventWindow, defines the minimum threshold for an event to be non-ancient.
     *
     * @param nonAncientEventWindow the non-ancient event window
     */
    public void setNonAncientEventWindow(@NonNull final NonAncientEventWindow nonAncientEventWindow) {
        this.nonAncientEventWindow = Objects.requireNonNull(nonAncientEventWindow);

        observedEvents.shiftWindow(nonAncientEventWindow.getLowerBound());
    }

    /**
     * Clear the internal state of this deduplicator.
     *
     * @param ignored ignored trigger object
     */
    public void clear(@NonNull final ClearTrigger ignored) {
        observedEvents.clear();
    }
}
