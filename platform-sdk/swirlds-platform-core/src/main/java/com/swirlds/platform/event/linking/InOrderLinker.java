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

package com.swirlds.platform.event.linking;

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.sequence.map.SequenceMap;
import com.swirlds.common.sequence.map.StandardSequenceMap;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.events.EventDescriptor;
import com.swirlds.platform.EventStrings;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Links events.
 * <p>
 * Expects events to be provided in topological order. If an out-of-order event is provided, it is logged and discarded.
 * <p>
 * Note: This class doesn't have a direct dependency on the {@link com.swirlds.platform.gossip.shadowgraph.ShadowGraph ShadowGraph},
 * but it is dependent in the sense that the Shadowgraph is currently responsible for eventually unlinking events.
 */
public class InOrderLinker {
    private static final Logger logger = LogManager.getLogger(InOrderLinker.class);

    /**
     * The initial capacity of the {@link #parentDescriptorMap} and {@link #parentHashMap}
     */
    private static final int INITIAL_CAPACITY = 1024;

    /**
     * A sequence map from event descriptor to event.
     * <p>
     * The window of this map is shifted when the minimum generation non-ancient is changed, so that only non-ancient
     * events are retained.
     */
    private final SequenceMap<EventDescriptor, EventImpl> parentDescriptorMap =
            new StandardSequenceMap<>(0, INITIAL_CAPACITY, true, EventDescriptor::getGeneration);

    /**
     * A map from event hash to event.
     * <p>
     * This map is needed in addition to the sequence map, since we need to be able to look up parent events based on
     * hash. Elements are removed from this map when the window of the sequence map is shifted.
     */
    private final Map<Hash, EventImpl> parentHashMap = new HashMap<>(INITIAL_CAPACITY);

    /**
     * Linked events are passed to this consumer.
     */
    private final Consumer<EventImpl> eventConsumer;

    /**
     * The current minimum generation required for an event to be non-ancient.
     */
    private long minimumGenerationNonAncient = 0;

    /**
     * Keeps track of the number of events in the intake pipeline from each peer
     */
    private final IntakeEventCounter intakeEventCounter;

    /**
     * Constructor
     *
     * @param eventConsumer      the consumer that successfully linked events are passed to
     * @param intakeEventCounter keeps track of the number of events in the intake pipeline from each peer
     */
    public InOrderLinker(
            @NonNull final Consumer<EventImpl> eventConsumer, @NonNull final IntakeEventCounter intakeEventCounter) {

        this.eventConsumer = Objects.requireNonNull(eventConsumer);
        this.intakeEventCounter = Objects.requireNonNull(intakeEventCounter);
    }

    /**
     * Find and link the parents of the given event.
     *
     * @param event the event to link
     */
    public void linkEvent(@NonNull final GossipEvent event) {
        if (event.getGeneration() < minimumGenerationNonAncient) {
            // This event is ancient, so we don't need to link it.
            this.intakeEventCounter.eventExitedIntakePipeline(event.getSenderId());
            return;
        }

        final BaseEventHashedData hashedData = event.getHashedData();

        final Hash selfParentHash = hashedData.getSelfParentHash();
        final long selfParentGen = hashedData.getSelfParentGen();
        final EventImpl selfParent;
        if (selfParentGen >= minimumGenerationNonAncient) {
            // self parent is non-ancient. we are guaranteed to have it, since events are received in topological order
            selfParent = parentHashMap.get(selfParentHash);
            if (selfParent == null) {
                logger.error(
                        EXCEPTION.getMarker(),
                        "Event has a missing self-parent. Child: {}, missing parent hash: {}, missing parent generation: {}. This should not be possible",
                        EventStrings.toMediumString(event),
                        selfParentHash,
                        selfParentGen);
                this.intakeEventCounter.eventExitedIntakePipeline(event.getSenderId());
                return;
            }
        } else {
            // ancient parents don't need to be linked
            selfParent = null;
        }

        final Hash otherParentHash = hashedData.getOtherParentHash();
        final long otherParentGen = hashedData.getOtherParentGen();
        final EventImpl otherParent;
        if (otherParentGen >= minimumGenerationNonAncient) {
            // other parent is non-ancient. we are guaranteed to have it, since events are received in topological order
            otherParent = parentHashMap.get(otherParentHash);
            if (otherParent == null) {
                logger.error(
                        EXCEPTION.getMarker(),
                        "Event has a missing other-parent. Child: {}, missing parent hash: {}, missing parent generation: {}. This should not be possible",
                        EventStrings.toMediumString(event),
                        otherParentHash,
                        otherParentGen);
                this.intakeEventCounter.eventExitedIntakePipeline(event.getSenderId());
                return;
            }
        } else {
            // ancient parents don't need to be linked
            otherParent = null;
        }

        final EventImpl linkedEvent = new EventImpl(event, selfParent, otherParent);

        final EventDescriptor eventDescriptor = event.getDescriptor();

        parentDescriptorMap.put(eventDescriptor, linkedEvent);
        parentHashMap.put(eventDescriptor.getHash(), linkedEvent);

        eventConsumer.accept(linkedEvent);
    }

    /**
     * Set the minimum generation required for an event to be non-ancient.
     *
     * @param minimumGenerationNonAncient the minimum generation required for an event to be non-ancient
     */
    public void setMinimumGenerationNonAncient(final long minimumGenerationNonAncient) {
        this.minimumGenerationNonAncient = minimumGenerationNonAncient;

        parentDescriptorMap.shiftWindow(
                minimumGenerationNonAncient, (descriptor, event) -> parentHashMap.remove(descriptor.getHash()));
    }
}
