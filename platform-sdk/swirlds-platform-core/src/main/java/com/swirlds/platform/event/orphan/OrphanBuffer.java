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

package com.swirlds.platform.event.orphan;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.sequence.map.SequenceMap;
import com.swirlds.common.sequence.map.StandardSequenceMap;
import com.swirlds.common.sequence.set.SequenceSet;
import com.swirlds.common.sequence.set.StandardSequenceSet;
import com.swirlds.platform.event.EventDescriptor;
import com.swirlds.platform.event.GossipEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Takes as input an unordered stream of {@link com.swirlds.platform.event.GossipEvent GossipEvent}s and emits a stream
 * of {@link com.swirlds.platform.event.GossipEvent GossipEvent}s in topological order.
 */
public class OrphanBuffer {

    private static final int INITIAL_CAPACITY = 1024;

    /**
     * Avoid the creation of lambdas for Map.computeIfAbsent() by reusing this lambda.
     */
    private static final Function<EventDescriptor, List<OrphanedEvent>> BUILD_LIST = ignored -> new ArrayList<>();

    /**
     * Non-ancient events are passed to this method in topological order.
     */
    private final Consumer<GossipEvent> eventConsumer;

    /**
     * The current minimum generation required to be non-ancient.
     */
    private long minimumGenerationNonAncient = 0;

    /**
     * A set containing descriptors of all non-ancient events that have found their parents (or whose parents have
     * become ancient).
     */
    private final SequenceSet<EventDescriptor> eventsWithParents =
            new StandardSequenceSet<>(0, INITIAL_CAPACITY, true, EventDescriptor::getGeneration);

    /**
     * A map of missing parents a list of orphans that are missing the parent.
     */
    private final SequenceMap<EventDescriptor, List<OrphanedEvent>> parentsOfOrphans =
            new StandardSequenceMap<>(0, INITIAL_CAPACITY, true, EventDescriptor::getGeneration);

    /**
     * Constructor.
     *
     * @param platformContext the platform context
     * @param eventConsumer   the consumer to which to emit the ordered stream of
     *                        {@link com.swirlds.platform.event.GossipEvent GossipEvent}s
     */
    public OrphanBuffer(
            @NonNull final PlatformContext platformContext, @NonNull final Consumer<GossipEvent> eventConsumer) {

        this.eventConsumer = Objects.requireNonNull(eventConsumer);
    }

    /**
     * Add a new event.
     *
     * @param event the event to add
     */
    public void addEvent(@NonNull final GossipEvent event) {
        if (event.getGeneration() < minimumGenerationNonAncient) {
            // Ancient events can be safely ignored.
            return;
        }

        final Set<EventDescriptor> missingParents = getMissingParents(event);
        if (missingParents.isEmpty()) {
            eventIsNotAnOrphan(event);
            return;
        }

        final OrphanedEvent orphanedEvent = new OrphanedEvent(event, missingParents);
        for (final EventDescriptor missingParent : missingParents) {
            parentsOfOrphans.computeIfAbsent(missingParent, BUILD_LIST).add(orphanedEvent);
        }
    }

    /**
     * Set the minimum generation of non-ancient events to keep in the buffer.
     *
     * @param minimumGenerationNonAncient the minimum generation of non-ancient events to keep in the buffer
     */
    public void setMinimumGenerationNonAncient(final long minimumGenerationNonAncient) {
        this.minimumGenerationNonAncient = minimumGenerationNonAncient;

        eventsWithParents.shiftWindow(minimumGenerationNonAncient);

        // As the map is cleared out, we need to gather the ancient parents and their orphans. We can't
        // modify the data structure as the window is being shifted, so we collect that data and act on
        // it once the window has finished shifting.
        final List<ParentAndOrphans> ancientEvents = new ArrayList<>();
        parentsOfOrphans.shiftWindow(
                minimumGenerationNonAncient,
                (parent, orphans) -> ancientEvents.add(new ParentAndOrphans(parent, orphans)));

        for (final ParentAndOrphans parentAndOrphans : ancientEvents) {
            missingParentFound(parentAndOrphans.parent(), parentAndOrphans.orphans());
        }
    }

    /**
     * Get the parents of an event that are currently missing.
     *
     * @param event the event whose missing parents to find
     * @return the set of missing parents, empty if no parents are missing
     */
    @NonNull
    private Set<EventDescriptor> getMissingParents(@NonNull final GossipEvent event) {
        final Iterator<EventDescriptor> parentIterator = new ParentIterator(event);
        final Set<EventDescriptor> missingParents = new HashSet<>();
        while (parentIterator.hasNext()) {
            final EventDescriptor parent = parentIterator.next();
            if (!eventsWithParents.contains(parent)) {
                missingParents.add(parent);
            }
        }
        return missingParents;
    }

    /**
     * Signal that an event is not an orphan.
     *
     * @param event the event that is not an orphan
     */
    private void eventIsNotAnOrphan(@NonNull final GossipEvent event) {
        if (event.getGeneration() < minimumGenerationNonAncient) {
            // Although it doesn't cause harm to pass along ancient events, it is unnecessary to do so.
            return;
        }

        eventConsumer.accept(event);

        final EventDescriptor descriptor = event.getDescriptor();
        eventsWithParents.add(descriptor);

        final List<OrphanedEvent> orphans = parentsOfOrphans.remove(descriptor);
        if (orphans != null) {
            missingParentFound(event.getDescriptor(), orphans);
        }
    }

    /**
     * Signal that a missing parent has been found. A parent is considered to be found once it no longer prevents its
     * children from being emitted. A parent can be "found" by actually being received or by becoming ancient. Either
     * way, we no longer need to wait on the parent.
     *
     * @param event           the event that is no longer missing
     * @param orphansOfParent the orphans that are missing the parent
     */
    private void missingParentFound(
            @NonNull final EventDescriptor event, @NonNull final List<OrphanedEvent> orphansOfParent) {

        for (final OrphanedEvent orphan : orphansOfParent) {
            orphan.missingParents().remove(event);
            if (orphan.missingParents().isEmpty()) {
                eventIsNotAnOrphan(orphan.orphan()); // TODO recursion is evil
            }
        }
    }
}
