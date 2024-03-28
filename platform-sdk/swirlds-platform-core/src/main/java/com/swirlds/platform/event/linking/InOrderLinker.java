/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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
import static com.swirlds.metrics.api.Metrics.PLATFORM_CATEGORY;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.sequence.map.SequenceMap;
import com.swirlds.common.sequence.map.StandardSequenceMap;
import com.swirlds.common.utility.throttle.RateLimitedLogger;
import com.swirlds.metrics.api.LongAccumulator;
import com.swirlds.platform.EventStrings;
import com.swirlds.platform.consensus.NonAncientEventWindow;
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.event.EventCounter;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.eventhandling.EventConfig;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.gossip.shadowgraph.Shadowgraph;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.system.events.BaseEventHashedData;
import com.swirlds.platform.system.events.EventDescriptor;
import com.swirlds.platform.wiring.ClearTrigger;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Links events to their parents. Expects events to be provided in topological order.
 * <p>
 * Will not link events to parents in the following cases:
 * <ul>
 *     <li>The parent is ancient</li>
 *     <li>The parent's generation does not match the generation claimed by the child event</li>
 *     <li>The parent's time created is greater than or equal to the child's time created</li>
 * </ul>
 * Note: This class doesn't have a direct dependency on the {@link Shadowgraph ShadowGraph},
 * but it is dependent in the sense that the Shadowgraph is currently responsible for eventually unlinking events.
 */
public class InOrderLinker {
    private static final Logger logger = LogManager.getLogger(InOrderLinker.class);

    /**
     * The initial capacity of the {@link #parentDescriptorMap} and {@link #parentHashMap}
     */
    private static final int INITIAL_CAPACITY = 1024;

    /**
     * The minimum period between log messages for a specific mode of failure.
     */
    private static final Duration MINIMUM_LOG_PERIOD = Duration.ofMinutes(1);

    private final RateLimitedLogger missingParentLogger;
    private final RateLimitedLogger generationMismatchLogger;
    private final RateLimitedLogger birthRoundMismatchLogger;
    private final RateLimitedLogger timeCreatedMismatchLogger;

    // TODO don't report these metrics any more

    private final LongAccumulator missingParentAccumulator;
    private final LongAccumulator generationMismatchAccumulator;
    private final LongAccumulator birthRoundMismatchAccumulator;
    private final LongAccumulator timeCreatedMismatchAccumulator;

    /**
     * A sequence map from event descriptor to event.
     * <p>
     * The window of this map is shifted when the minimum non-ancient threshold is changed, so that only non-ancient
     * events are retained.
     */
    private final SequenceMap<EventDescriptor, EventImpl> parentDescriptorMap;

    /**
     * A map from event hash to event.
     * <p>
     * This map is needed in addition to the sequence map, since we need to be able to look up parent events based on
     * hash. Elements are removed from this map when the window of the sequence map is shifted.
     */
    private final Map<Hash, EventImpl> parentHashMap = new HashMap<>(INITIAL_CAPACITY);

    /**
     * The current non-ancient event window.
     */
    private NonAncientEventWindow nonAncientEventWindow;

    /**
     * Keeps track of the number of events in the intake pipeline from each peer
     */
    private final IntakeEventCounter intakeEventCounter;

    /**
     * Constructor
     *
     * @param platformContext    the platform context
     * @param time               a source of time
     * @param intakeEventCounter keeps track of the number of events in the intake pipeline from each peer
     */
    public InOrderLinker(
            @NonNull final PlatformContext platformContext,
            @NonNull final Time time,
            @NonNull final IntakeEventCounter intakeEventCounter) {

        this.intakeEventCounter = Objects.requireNonNull(intakeEventCounter);
        this.missingParentLogger = new RateLimitedLogger(logger, time, MINIMUM_LOG_PERIOD);
        this.generationMismatchLogger = new RateLimitedLogger(logger, time, MINIMUM_LOG_PERIOD);
        this.birthRoundMismatchLogger = new RateLimitedLogger(logger, time, MINIMUM_LOG_PERIOD);
        this.timeCreatedMismatchLogger = new RateLimitedLogger(logger, time, MINIMUM_LOG_PERIOD);

        this.missingParentAccumulator = platformContext
                .getMetrics()
                .getOrCreate(new LongAccumulator.Config(PLATFORM_CATEGORY, "missingParents")
                        .withDescription("Parent child relationships where a parent was missing"));
        this.generationMismatchAccumulator = platformContext
                .getMetrics()
                .getOrCreate(
                        new LongAccumulator.Config(PLATFORM_CATEGORY, "parentGenerationMismatch")
                                .withDescription(
                                        "Parent child relationships where claimed parent generation did not match actual parent generation"));
        this.birthRoundMismatchAccumulator = platformContext
                .getMetrics()
                .getOrCreate(
                        new LongAccumulator.Config(PLATFORM_CATEGORY, "parentBirthRoundMismatch")
                                .withDescription(
                                        "Parent child relationships where claimed parent birth round did not match actual parent birth round"));
        this.timeCreatedMismatchAccumulator = platformContext
                .getMetrics()
                .getOrCreate(
                        new LongAccumulator.Config(PLATFORM_CATEGORY, "timeCreatedMismatch")
                                .withDescription(
                                        "Parent child relationships where child time created wasn't strictly after parent time created"));

        final AncientMode ancientMode = platformContext
                .getConfiguration()
                .getConfigData(EventConfig.class)
                .getAncientMode();
        this.nonAncientEventWindow = NonAncientEventWindow.getGenesisNonAncientEventWindow(ancientMode);
        if (ancientMode == AncientMode.BIRTH_ROUND_THRESHOLD) {
            this.parentDescriptorMap =
                    new StandardSequenceMap<>(0, INITIAL_CAPACITY, true, EventDescriptor::getBirthRound);
        } else {
            this.parentDescriptorMap =
                    new StandardSequenceMap<>(0, INITIAL_CAPACITY, true, EventDescriptor::getGeneration);
        }
    }

    /**
     * Find the correct parent to link to a child. If a parent should not be linked, null is returned.
     * <p>
     * A parent should not be linked if any of the following are true:
     * <ul>
     *     <li>The parent is ancient</li>
     *     <li>The parent's generation does not match the generation claimed by the child event</li>
     *     <li>The parent's birthRound does not match the claimed birthRound by the child event</li>
     *     <li>The parent's time created is greater than or equal to the child's time created</li>
     * </ul>
     *
     * @param child            the child event
     * @param parentDescriptor the event descriptor for the claimed parent
     * @return the parent to link, or null if no parent should be linked
     */
    @Nullable
    private EventImpl getParentToLink(
            @NonNull final GossipEvent child, @Nullable final EventDescriptor parentDescriptor) {

        if (parentDescriptor == null) {
            // There is no claimed parent for linking.
            return null;
        }

        if (nonAncientEventWindow.isAncient(parentDescriptor)) {
            // ancient parents don't need to be linked
            return null;
        }

        final EventImpl candidateParent = parentHashMap.get(parentDescriptor.getHash());
        if (candidateParent == null) {
            missingParentAccumulator.update(1);
            missingParentLogger.error(
                    EXCEPTION.getMarker(),
                    "Child has a missing parent. This should not be possible. "
                            + "Child: {}, Parent EventDescriptor: {}",
                    EventStrings.toMediumString(child),
                    parentDescriptor);

            return null;
        }

        if (candidateParent.getGeneration() != parentDescriptor.getGeneration()) {
            generationMismatchAccumulator.update(1);
            generationMismatchLogger.warn(
                    EXCEPTION.getMarker(),
                    "Event has a parent with a different generation than claimed. Child: {}, parent: {}, "
                            + "claimed generation: {}, actual generation: {}",
                    EventStrings.toMediumString(child),
                    EventStrings.toMediumString(candidateParent),
                    parentDescriptor.getGeneration(),
                    candidateParent.getGeneration());

            return null;
        }

        if (candidateParent.getBirthRound() != parentDescriptor.getBirthRound()) {
            birthRoundMismatchAccumulator.update(1);
            birthRoundMismatchLogger.warn(
                    EXCEPTION.getMarker(),
                    "Event has a parent with a different birth round than claimed. Child: {}, parent: {}, "
                            + "claimed birth round: {}, actual birth round: {}",
                    EventStrings.toMediumString(child),
                    EventStrings.toMediumString(candidateParent),
                    parentDescriptor.getBirthRound(),
                    candidateParent.getBirthRound());

            return null;
        }

        final Instant parentTimeCreated =
                candidateParent.getBaseEvent().getHashedData().getTimeCreated();
        final Instant childTimeCreated = child.getHashedData().getTimeCreated();

        // only do this check for self parent, since the event creator doesn't consider other parent creation time
        // when deciding on the event creation time
        if (parentDescriptor.getCreator().equals(child.getDescriptor().getCreator())
                && parentTimeCreated.compareTo(childTimeCreated) >= 0) {

            timeCreatedMismatchAccumulator.update(1);
            timeCreatedMismatchLogger.error(
                    EXCEPTION.getMarker(),
                    "Child time created isn't strictly after self parent time created. "
                            + "Child: {}, parent: {}, child time created: {}, parent time created: {}",
                    EventStrings.toMediumString(child),
                    EventStrings.toMediumString(candidateParent),
                    childTimeCreated,
                    parentTimeCreated);

            return null;
        }

        return candidateParent;
    }

    /**
     * Find and link the parents of the given event.
     *
     * @param event the event to link
     * @return the linked event, or null if the event is ancient
     */
    @Nullable
    public EventImpl linkEvent(@NonNull final GossipEvent event) {
        if (nonAncientEventWindow.isAncient(event)) {
            // This event is ancient, so we don't need to link it.
            this.intakeEventCounter.eventExitedIntakePipeline(event.getSenderId());
            return null;
        }

        final BaseEventHashedData hashedData = event.getHashedData();
        final EventImpl selfParent = getParentToLink(event, hashedData.getSelfParent());

        // FUTURE WORK: Extend other parent linking to support multiple other parents.
        // Until then, take the first parent in the list.
        final List<EventDescriptor> otherParents = hashedData.getOtherParents();
        final EventImpl otherParent = otherParents.isEmpty() ? null : getParentToLink(event, otherParents.get(0));

        final EventImpl linkedEvent = new EventImpl(event, selfParent, otherParent);
        EventCounter.incrementLinkedEventCount();

        final EventDescriptor eventDescriptor = event.getDescriptor();
        parentDescriptorMap.put(eventDescriptor, linkedEvent);
        parentHashMap.put(eventDescriptor.getHash(), linkedEvent);

        return linkedEvent;
    }

    /**
     * Set the non-ancient event window, defining the minimum non-ancient threshold.
     *
     * @param nonAncientEventWindow the non-ancient event window
     */
    public void setNonAncientEventWindow(@NonNull final NonAncientEventWindow nonAncientEventWindow) {
        this.nonAncientEventWindow = Objects.requireNonNull(nonAncientEventWindow);

        parentDescriptorMap.shiftWindow(
                nonAncientEventWindow.getAncientThreshold(),
                (descriptor, event) -> parentHashMap.remove(descriptor.getHash()));
    }

    /**
     * Clear the internal state of this linker.
     *
     * @param ignored ignored trigger object
     */
    public void clear(@NonNull final ClearTrigger ignored) {
        parentDescriptorMap.clear();
        parentHashMap.clear();
    }
}
