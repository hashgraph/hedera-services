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

import static com.swirlds.common.metrics.Metrics.PLATFORM_CATEGORY;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.sequence.map.SequenceMap;
import com.swirlds.common.sequence.map.StandardSequenceMap;
import com.swirlds.common.sequence.set.SequenceSet;
import com.swirlds.common.sequence.set.StandardSequenceSet;
import com.swirlds.common.system.events.EventDescriptor;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.gossip.IntakeEventCounter;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Takes as input an unordered stream of {@link com.swirlds.platform.event.GossipEvent GossipEvent}s and emits a stream
 * of {@link com.swirlds.platform.event.GossipEvent GossipEvent}s in topological order.
 */
public class OrphanBuffer {
    /**
     * Initial capacity of {@link #eventsWithParents} and {@link #missingParentMap}.
     */
    private static final int INITIAL_CAPACITY = 1024;

    /**
     * Avoid the creation of lambdas for Map.computeIfAbsent() by reusing this lambda.
     */
    private static final Function<EventDescriptor, List<OrphanedEvent>> EMPTY_LIST = ignored -> new ArrayList<>();

    /**
     * Non-ancient events are passed to this method in topological order.
     */
    private final Consumer<GossipEvent> eventConsumer;

    /**
     * The current minimum generation required for an event to be non-ancient.
     */
    private long minimumGenerationNonAncient = 0;

    /**
     * The number of orphans currently in the buffer.
     */
    private int currentOrphanCount;

    /**
     * Keeps track of the number of events in the intake pipeline from each peer
     */
    private final IntakeEventCounter intakeEventCounter;

    /**
     * A set containing descriptors of all non-ancient events that have found their parents (or whose parents have
     * become ancient).
     */
    private final SequenceSet<EventDescriptor> eventsWithParents =
            new StandardSequenceSet<>(0, INITIAL_CAPACITY, true, EventDescriptor::getGeneration);

    /**
     * A map where the key is the descriptor of a missing parent, and the value is a list of orphans that are missing
     * that parent.
     */
    private final SequenceMap<EventDescriptor, List<OrphanedEvent>> missingParentMap =
            new StandardSequenceMap<>(0, INITIAL_CAPACITY, true, EventDescriptor::getGeneration);

    /**
     * Constructor
     *
     * @param platformContext    the platform context
     * @param eventConsumer      the consumer to which to emit the ordered stream of
     *                           {@link com.swirlds.platform.event.GossipEvent GossipEvent}s
     * @param intakeEventCounter keeps track of the number of events in the intake pipeline from each peer
     */
    public OrphanBuffer(
            @NonNull final PlatformContext platformContext,
            @NonNull final Consumer<GossipEvent> eventConsumer,
            @NonNull final IntakeEventCounter intakeEventCounter) {

        this.eventConsumer = Objects.requireNonNull(eventConsumer);
        this.intakeEventCounter = Objects.requireNonNull(intakeEventCounter);
        this.currentOrphanCount = 0;

        platformContext
                .getMetrics()
                .getOrCreate(new FunctionGauge.Config<>(
                                PLATFORM_CATEGORY, "orphanBufferSize", Integer.class, this::getCurrentOrphanCount)
                        .withDescription("number of orphaned events currently in the orphan buffer")
                        .withUnit("events"));
    }

    /**
     * Add a new event to the buffer if it is an orphan.
     * <p>
     * Events that are ancient are ignored, and events that don't have any missing parents are
     * immediately passed to the {@link #eventConsumer}.
     *
     * @param event the event to handle
     */
    public void handleEvent(@NonNull final GossipEvent event) {
        if (event.getGeneration() < minimumGenerationNonAncient) {
            // Ancient events can be safely ignored.
            intakeEventCounter.eventExitedIntakePipeline(event.getSenderId());
            return;
        }

        currentOrphanCount++;

        final List<EventDescriptor> missingParents = getMissingParents(event);
        if (missingParents.isEmpty()) {
            eventIsNotAnOrphan(event);
        } else {
            final OrphanedEvent orphanedEvent = new OrphanedEvent(event, missingParents);
            for (final EventDescriptor missingParent : missingParents) {
                this.missingParentMap.computeIfAbsent(missingParent, EMPTY_LIST).add(orphanedEvent);
            }
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
        final List<ParentAndOrphans> ancientParents = new ArrayList<>();
        missingParentMap.shiftWindow(
                minimumGenerationNonAncient,
                (parent, orphans) -> ancientParents.add(new ParentAndOrphans(parent, orphans)));

        ancientParents.forEach(this::missingParentBecameAncient);
    }

    /**
     * Called when a parent becomes ancient.
     * <p>
     * Accounts for events potentially becoming un-orphaned as a result of the parent becoming ancient.
     *
     * @param parentAndOrphans the parent that became ancient, along with its orphans
     */
    private void missingParentBecameAncient(@NonNull final ParentAndOrphans parentAndOrphans) {
        final EventDescriptor parentDescriptor = parentAndOrphans.parent();

        for (final OrphanedEvent orphan : parentAndOrphans.orphans()) {
            orphan.missingParents().remove(parentDescriptor);

            if (orphan.missingParents().isEmpty()) {
                eventIsNotAnOrphan(orphan.orphan());
            }
        }
    }

    /**
     * Get the parents of an event that are currently missing.
     *
     * @param event the event whose missing parents to find
     * @return the list of missing parents, empty if no parents are missing
     */
    @NonNull
    private List<EventDescriptor> getMissingParents(@NonNull final GossipEvent event) {
        final List<EventDescriptor> missingParents = new ArrayList<>();

        final Iterator<EventDescriptor> parentIterator = new ParentIterator(event);
        while (parentIterator.hasNext()) {
            final EventDescriptor parent = parentIterator.next();
            if (!eventsWithParents.contains(parent) && parent.getGeneration() >= minimumGenerationNonAncient) {
                missingParents.add(parent);
            }
        }

        return missingParents;
    }

    /**
     * Signal that an event is not an orphan.
     * <p>
     * Accounts for events potentially becoming un-orphaned as a result of this event not being an orphan.
     *
     * @param event the event that is not an orphan
     */
    private void eventIsNotAnOrphan(@NonNull final GossipEvent event) {
        final Deque<GossipEvent> nonOrphanStack = new LinkedList<>();
        nonOrphanStack.push(event);

        // When a missing parent is found, there may be many descendants of that parent who end up
        // being un-orphaned. This loop frees all such orphans non-recursively (recursion yields pretty
        // code but can thrash the stack).
        while (!nonOrphanStack.isEmpty()) {
            currentOrphanCount--;

            final GossipEvent nonOrphan = nonOrphanStack.pop();
            final EventDescriptor nonOrphanDescriptor = nonOrphan.getDescriptor();

            if (nonOrphan.getGeneration() < minimumGenerationNonAncient) {
                // Although it doesn't cause harm to pass along ancient events, it is unnecessary to do so.
                intakeEventCounter.eventExitedIntakePipeline(event.getSenderId());
                continue;
            }

            eventConsumer.accept(nonOrphan);
            eventsWithParents.add(nonOrphanDescriptor);

            // since this event is no longer an orphan, we need to recheck all of its children to see if any might
            // not be orphans anymore
            final List<OrphanedEvent> children = missingParentMap.remove(nonOrphanDescriptor);
            if (children == null) {
                continue;
            }

            for (final OrphanedEvent child : children) {
                child.missingParents().remove(nonOrphanDescriptor);
                if (child.missingParents().isEmpty()) {
                    nonOrphanStack.push(child.orphan());
                }
            }
        }
    }

    /**
     * Gets the number of orphans currently in the buffer.
     *
     * @return the number of orphans currently in the buffer
     */
    @NonNull
    public Integer getCurrentOrphanCount() {
        return currentOrphanCount;
    }
}
