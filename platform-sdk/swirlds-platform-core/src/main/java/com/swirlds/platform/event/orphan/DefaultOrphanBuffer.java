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

package com.swirlds.platform.event.orphan;

import static com.swirlds.metrics.api.Metrics.PLATFORM_CATEGORY;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.eventhandling.EventConfig;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.sequence.map.SequenceMap;
import com.swirlds.platform.sequence.map.StandardSequenceMap;
import com.swirlds.platform.sequence.set.SequenceSet;
import com.swirlds.platform.sequence.set.StandardSequenceSet;
import com.swirlds.platform.system.events.EventDescriptorWrapper;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Takes as input an unordered stream of {@link PlatformEvent}s and emits a stream
 * of {@link PlatformEvent}s in topological order.
 */
public class DefaultOrphanBuffer implements OrphanBuffer {
    /**
     * Initial capacity of {@link #eventsWithParents} and {@link #missingParentMap}.
     */
    private static final int INITIAL_CAPACITY = 1024;

    /**
     * Avoid the creation of lambdas for Map.computeIfAbsent() by reusing this lambda.
     */
    private static final Function<EventDescriptorWrapper, List<OrphanedEvent>> EMPTY_LIST =
            ignored -> new ArrayList<>();

    /**
     * The current event window.
     */
    private EventWindow eventWindow;

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
    private final SequenceSet<EventDescriptorWrapper> eventsWithParents;

    /**
     * A map where the key is the descriptor of a missing parent, and the value is a list of orphans that are missing
     * that parent.
     */
    private final SequenceMap<EventDescriptorWrapper, List<OrphanedEvent>> missingParentMap;

    /**
     * Constructor
     *
     * @param platformContext    the platform context
     * @param intakeEventCounter keeps track of the number of events in the intake pipeline from each peer
     */
    public DefaultOrphanBuffer(
            @NonNull final PlatformContext platformContext, @NonNull final IntakeEventCounter intakeEventCounter) {

        this.intakeEventCounter = Objects.requireNonNull(intakeEventCounter);
        this.currentOrphanCount = 0;

        platformContext
                .getMetrics()
                .getOrCreate(new FunctionGauge.Config<>(
                                PLATFORM_CATEGORY, "orphanBufferSize", Integer.class, this::getCurrentOrphanCount)
                        .withDescription("number of orphaned events currently in the orphan buffer")
                        .withUnit("events"));

        final AncientMode ancientMode = platformContext
                .getConfiguration()
                .getConfigData(EventConfig.class)
                .getAncientMode();
        this.eventWindow = EventWindow.getGenesisEventWindow(ancientMode);
        if (ancientMode == AncientMode.BIRTH_ROUND_THRESHOLD) {
            missingParentMap = new StandardSequenceMap<>(
                    0, INITIAL_CAPACITY, true, ed -> ed.eventDescriptor().birthRound());
            eventsWithParents = new StandardSequenceSet<>(
                    0, INITIAL_CAPACITY, true, ed -> ed.eventDescriptor().birthRound());
        } else {
            missingParentMap = new StandardSequenceMap<>(
                    0, INITIAL_CAPACITY, true, ed -> ed.eventDescriptor().generation());
            eventsWithParents = new StandardSequenceSet<>(
                    0, INITIAL_CAPACITY, true, ed -> ed.eventDescriptor().generation());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public List<PlatformEvent> handleEvent(@NonNull final PlatformEvent event) {
        if (eventWindow.isAncient(event)) {
            // Ancient events can be safely ignored.
            intakeEventCounter.eventExitedIntakePipeline(event.getSenderId());
            return List.of();
        }

        currentOrphanCount++;

        final List<EventDescriptorWrapper> missingParents = getMissingParents(event);
        if (missingParents.isEmpty()) {
            return eventIsNotAnOrphan(event);
        } else {
            final OrphanedEvent orphanedEvent = new OrphanedEvent(event, missingParents);
            for (final EventDescriptorWrapper missingParent : missingParents) {
                this.missingParentMap.computeIfAbsent(missingParent, EMPTY_LIST).add(orphanedEvent);
            }

            return List.of();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public List<PlatformEvent> setEventWindow(@NonNull final EventWindow eventWindow) {
        this.eventWindow = Objects.requireNonNull(eventWindow);

        eventsWithParents.shiftWindow(eventWindow.getAncientThreshold());

        // As the map is cleared out, we need to gather the ancient parents and their orphans. We can't
        // modify the data structure as the window is being shifted, so we collect that data and act on
        // it once the window has finished shifting.
        final List<ParentAndOrphans> ancientParents = new ArrayList<>();
        missingParentMap.shiftWindow(
                eventWindow.getAncientThreshold(),
                (parent, orphans) -> ancientParents.add(new ParentAndOrphans(parent, orphans)));

        final List<PlatformEvent> unorphanedEvents = new ArrayList<>();
        ancientParents.forEach(
                parentAndOrphans -> unorphanedEvents.addAll(missingParentBecameAncient(parentAndOrphans)));

        return unorphanedEvents;
    }

    /**
     * Called when a parent becomes ancient.
     * <p>
     * Accounts for events potentially becoming un-orphaned as a result of the parent becoming ancient.
     *
     * @param parentAndOrphans the parent that became ancient, along with its orphans
     * @return the list of events that are no longer orphans as a result of this parent becoming ancient
     */
    @NonNull
    private List<PlatformEvent> missingParentBecameAncient(@NonNull final ParentAndOrphans parentAndOrphans) {
        final List<PlatformEvent> unorphanedEvents = new ArrayList<>();

        final EventDescriptorWrapper parentDescriptor = parentAndOrphans.parent();

        for (final OrphanedEvent orphan : parentAndOrphans.orphans()) {
            orphan.missingParents().remove(parentDescriptor);

            if (orphan.missingParents().isEmpty()) {
                unorphanedEvents.addAll(eventIsNotAnOrphan(orphan.orphan()));
            }
        }

        return unorphanedEvents;
    }

    /**
     * Get the parents of an event that are currently missing.
     *
     * @param event the event whose missing parents to find
     * @return the list of missing parents, empty if no parents are missing
     */
    @NonNull
    private List<EventDescriptorWrapper> getMissingParents(@NonNull final PlatformEvent event) {
        final List<EventDescriptorWrapper> missingParents = new ArrayList<>();

        for (final EventDescriptorWrapper parent : event.getAllParents()) {
            if (!eventsWithParents.contains(parent) && !eventWindow.isAncient(parent)) {
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
     * @return the list of events that are no longer orphans as a result of this event not being an orphan
     */
    @NonNull
    private List<PlatformEvent> eventIsNotAnOrphan(@NonNull final PlatformEvent event) {
        final List<PlatformEvent> unorphanedEvents = new ArrayList<>();

        final Deque<PlatformEvent> nonOrphanStack = new LinkedList<>();
        nonOrphanStack.push(event);

        // When a missing parent is found, there may be many descendants of that parent who end up
        // being un-orphaned. This loop frees all such orphans non-recursively (recursion yields pretty
        // code but can thrash the stack).
        while (!nonOrphanStack.isEmpty()) {
            currentOrphanCount--;

            final PlatformEvent nonOrphan = nonOrphanStack.pop();
            final EventDescriptorWrapper nonOrphanDescriptor = nonOrphan.getDescriptor();

            if (eventWindow.isAncient(nonOrphan)) {
                // Although it doesn't cause harm to pass along ancient events, it is unnecessary to do so.
                intakeEventCounter.eventExitedIntakePipeline(event.getSenderId());
                continue;
            }

            unorphanedEvents.add(nonOrphan);
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

        return unorphanedEvents;
    }

    /**
     * Gets the number of orphans currently in the buffer. Exposed for testing.
     *
     * @return the number of orphans currently in the buffer
     */
    @NonNull
    Integer getCurrentOrphanCount() {
        return currentOrphanCount;
    }

    /**
     * Clears the orphan buffer.
     */
    public void clear() {
        eventsWithParents.clear();

        // clearing this map here is safe, under the assumption that the intake event counter will be reset
        // before gossip starts back up
        missingParentMap.clear();
        currentOrphanCount = 0;
    }
}
