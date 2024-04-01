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

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.sequence.map.SequenceMap;
import com.swirlds.common.sequence.map.StandardSequenceMap;
import com.swirlds.common.utility.throttle.RateLimitedLogger;
import com.swirlds.platform.EventStrings;
import com.swirlds.platform.consensus.NonAncientEventWindow;
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.event.EventCounter;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.eventhandling.EventConfig;
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
     * Constructor
     *
     * @param platformContext the platform context
     */
    public InOrderLinker(@NonNull final PlatformContext platformContext) {
        this.missingParentLogger = new RateLimitedLogger(logger, platformContext.getTime(), MINIMUM_LOG_PERIOD);
        this.generationMismatchLogger = new RateLimitedLogger(logger, platformContext.getTime(), MINIMUM_LOG_PERIOD);
        this.birthRoundMismatchLogger = new RateLimitedLogger(logger, platformContext.getTime(), MINIMUM_LOG_PERIOD);
        this.timeCreatedMismatchLogger = new RateLimitedLogger(logger, platformContext.getTime(), MINIMUM_LOG_PERIOD);

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
     * Find and link the parents of the given event.
     *
     * @param event the event to link
     * @return the linked event, or null if the event is ancient
     */
    @Nullable
    public EventImpl linkEvent(@NonNull final GossipEvent event) {
        if (nonAncientEventWindow.isAncient(event)) {
            // This event is ancient, so we don't need to link it.
            ancientEventAdded(event);
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

        parentDescriptorMap.shiftWindow(nonAncientEventWindow.getAncientThreshold(), (descriptor, event) -> {
            parentHashMap.remove(descriptor.getHash());
            eventHasBecomeAncient(event);
        });
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

    /**
     * This method is called when a child event has a missing parent.
     *
     * @param child            the child event
     * @param parentDescriptor the descriptor of the missing parent
     */
    protected void childHasMissingParent(
            @NonNull final GossipEvent child, @NonNull final EventDescriptor parentDescriptor) {
        missingParentLogger.error(
                EXCEPTION.getMarker(),
                "Child has a missing parent. This should not be possible. " + "Child: {}, Parent EventDescriptor: {}",
                EventStrings.toMediumString(child),
                parentDescriptor);
    }

    /**
     * This method is called when a child event has a parent with a different generation than claimed.
     *
     * @param child            the child event
     * @param parentDescriptor the claimed descriptor of the parent
     * @param candidateParent  the parent event that we found in the parentHashMap
     */
    protected void parentHasIncorrectGeneration(
            @NonNull final GossipEvent child,
            @NonNull final EventDescriptor parentDescriptor,
            @NonNull final EventImpl candidateParent) {
        generationMismatchLogger.warn(
                EXCEPTION.getMarker(),
                "Event has a parent with a different generation than claimed. Child: {}, parent: {}, "
                        + "claimed generation: {}, actual generation: {}",
                EventStrings.toMediumString(child),
                EventStrings.toMediumString(candidateParent),
                parentDescriptor.getGeneration(),
                candidateParent.getGeneration());
    }

    /**
     * This method is called when a child event has a parent with a different birth round than claimed.
     *
     * @param child            the child event
     * @param parentDescriptor the claimed descriptor of the parent
     * @param candidateParent  the parent event that we found in the parentHashMap
     */
    protected void parentHasIncorrectBirthRound(
            @NonNull final GossipEvent child,
            @NonNull final EventDescriptor parentDescriptor,
            @NonNull final EventImpl candidateParent) {
        birthRoundMismatchLogger.warn(
                EXCEPTION.getMarker(),
                "Event has a parent with a different birth round than claimed. Child: {}, parent: {}, "
                        + "claimed birth round: {}, actual birth round: {}",
                EventStrings.toMediumString(child),
                EventStrings.toMediumString(candidateParent),
                parentDescriptor.getBirthRound(),
                candidateParent.getBirthRound());
    }

    /**
     * This method is called when a child event has a self parent with a time created that is not strictly before the
     * child's time created.
     *
     * @param child             the child event
     * @param candidateParent   the parent event that we found in the parentHashMap
     * @param parentTimeCreated the time created of the parent event
     * @param childTimeCreated  the time created of the child event
     */
    protected void parentChildTimeIsIncorrect(
            @NonNull final GossipEvent child,
            @NonNull final EventImpl candidateParent,
            @NonNull final Instant parentTimeCreated,
            @NonNull final Instant childTimeCreated) {
        timeCreatedMismatchLogger.error(
                EXCEPTION.getMarker(),
                "Child time created isn't strictly after self parent time created. "
                        + "Child: {}, parent: {}, child time created: {}, parent time created: {}",
                EventStrings.toMediumString(child),
                EventStrings.toMediumString(candidateParent),
                childTimeCreated,
                parentTimeCreated);
    }

    /**
     * This method is called when an event is discarded because it is ancient when it is first added.
     *
     * @param event the event that was discarded
     */
    protected void ancientEventAdded(@NonNull final GossipEvent event) {
        // Implement this if extra action is needed.
    }

    /**
     * This method is called when this data structure stops tracking an event because it has become ancient.
     * @param event the event that has become ancient
     */
    protected void eventHasBecomeAncient(@NonNull final EventImpl event) {
        // Implement this if extra action is needed.
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
            childHasMissingParent(child, parentDescriptor);
            return null;
        }

        if (candidateParent.getGeneration() != parentDescriptor.getGeneration()) {
            parentHasIncorrectGeneration(child, parentDescriptor, candidateParent);
            return null;
        }

        if (candidateParent.getBirthRound() != parentDescriptor.getBirthRound()) {
            parentHasIncorrectBirthRound(child, parentDescriptor, candidateParent);
            return null;
        }

        final Instant parentTimeCreated =
                candidateParent.getBaseEvent().getHashedData().getTimeCreated();
        final Instant childTimeCreated = child.getHashedData().getTimeCreated();

        // only do this check for self parent, since the event creator doesn't consider other parent creation time
        // when deciding on the event creation time
        if (parentDescriptor.getCreator().equals(child.getDescriptor().getCreator())
                && parentTimeCreated.compareTo(childTimeCreated) >= 0) {

            parentChildTimeIsIncorrect(child, candidateParent, parentTimeCreated, childTimeCreated);

            return null;
        }

        return candidateParent;
    }
}
