/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

import com.swirlds.common.config.ConsensusConfig;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.sequence.map.SequenceMap;
import com.swirlds.common.sequence.map.StandardSequenceMap;
import com.swirlds.common.system.events.EventDescriptor;
import com.swirlds.logging.LogMarker;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.state.signed.SignedState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * An event linker which buffers out-of-order events until their parents are provided, or they become ancient. An event
 * received out of order is called an orphan. Once an event is no longer an orphan, it is returned by this linker.
 */
public class OrphanBufferingLinker extends AbstractEventLinker {
    private static final Logger logger = LogManager.getLogger(OrphanBufferingLinker.class);
    private final ParentFinder parentFinder;
    private final Queue<EventImpl> eventOutput;
    private final Queue<EventImpl> newlyLinkedEvents;
    private final SequenceMap<ParentDescriptor, Set<ChildEvent>> missingParents;
    private final SequenceMap<EventDescriptor, ChildEvent> orphanMap;

    /**
     * Keeps track of the number of events in the intake pipeline from each peer
     */
    private final IntakeEventCounter intakeEventCounter;

    /**
     * Create a new orphan buffer.
     *
     * @param config                consensus configuration
     * @param parentFinder          responsible for finding parents of an event
     * @param futureGenerationLimit the maximum number of future generations we are willing to store
     * @param intakeEventCounter    keeps track of the number of events in the intake pipeline from each peer
     */
    public OrphanBufferingLinker(
            final ConsensusConfig config,
            final ParentFinder parentFinder,
            final int futureGenerationLimit,
            @NonNull final IntakeEventCounter intakeEventCounter) {
        super(config);
        this.parentFinder = parentFinder;
        this.eventOutput = new ArrayDeque<>();
        this.newlyLinkedEvents = new ArrayDeque<>();
        this.orphanMap = new StandardSequenceMap<>(0, futureGenerationLimit, EventDescriptor::getGeneration);
        this.missingParents = new StandardSequenceMap<>(0, futureGenerationLimit, ParentDescriptor::generation);
        this.intakeEventCounter = Objects.requireNonNull(intakeEventCounter);
    }

    private static void parentNoLongerMissing(final ChildEvent child, final Hash parentHash, final EventImpl parent) {
        try {
            child.parentNoLongerMissing(parentHash, parent);
        } catch (final IllegalArgumentException e) {
            logger.error(
                    LogMarker.EXCEPTION.getMarker(),
                    "Error while reuniting a child with its parent :( child: {} parent hash: {}",
                    child,
                    parentHash,
                    e);
        }
    }

    private void orphanPurged(final EventDescriptor key, final ChildEvent orphan) {
        // this should never happen. an events parents should become ancient and at that point it will no longer be an
        // orphan
        if (orphan == null) {
            logger.error(LogMarker.EXCEPTION.getMarker(), "Null orphan, descriptor: {}", key);
            return;
        }

        orphan.orphanForever();
        intakeEventCounter.eventExitedIntakePipeline(
                orphan.getChild().getBaseEvent().getSenderId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void linkEvent(final GossipEvent event) {
        final ChildEvent childEvent = parentFinder.findParents(event, getMinGenerationNonAncient());

        if (!childEvent.isOrphan()) {
            eventLinked(childEvent.getChild());
            processNewlyLinkedEvents();
            return;
        }

        if (orphanMap.put(event.getDescriptor(), childEvent) != null) {
            // this should never happen
            logger.error(LogMarker.INVALID_EVENT_ERROR.getMarker(), "duplicate orphan: {}", event);
        }

        if (childEvent.isMissingSelfParent()) {
            addMissingParent(childEvent, true);
        }
        if (childEvent.isMissingOtherParent()) {
            addMissingParent(childEvent, false);
        }
    }

    private void addMissingParent(final ChildEvent childEvent, final boolean missingSelfParent) {
        final ParentDescriptor parentDescriptor =
                missingSelfParent ? childEvent.buildSelfParentDescriptor() : childEvent.buildOtherParentDescriptor();
        final Set<ChildEvent> childSet = missingParents.computeIfAbsent(parentDescriptor, d -> new HashSet<>());
        if (childSet == null) {
            logger.error(
                    LogMarker.INVALID_EVENT_ERROR.getMarker(),
                    "Orphan event {} is missing {} parent outside of missing parent window ({}-{})",
                    () -> childEvent.getChild().toMediumString(),
                    () -> missingSelfParent ? "self" : "other",
                    missingParents::getFirstSequenceNumberInWindow,
                    () -> missingParents.getFirstSequenceNumberInWindow() + missingParents.getSequenceNumberCapacity());
        } else {
            childSet.add(childEvent);
        }
    }

    private void processNewlyLinkedEvents() {
        while (!newlyLinkedEvents.isEmpty()) {
            final EventImpl newlyLinked = newlyLinkedEvents.poll();
            final Set<ChildEvent> orphans =
                    missingParents.remove(new ParentDescriptor(newlyLinked.getGeneration(), newlyLinked.getBaseHash()));
            if (orphans == null || orphans.isEmpty()) {
                continue;
            }
            for (final Iterator<ChildEvent> orphanIterator = orphans.iterator(); orphanIterator.hasNext(); ) {
                final ChildEvent child = orphanIterator.next();
                parentNoLongerMissing(child, newlyLinked.getBaseHash(), newlyLinked);
                if (!child.isOrphan()) {
                    eventNoLongerOrphan(child);
                    orphanIterator.remove();
                }
            }
        }
    }

    private void eventLinked(final EventImpl event) {
        eventOutput.add(event);
        newlyLinkedEvents.add(event);
    }

    private void eventNoLongerOrphan(final ChildEvent event) {
        eventLinked(event.getChild());
        orphanMap.remove(event.getChild().getBaseEvent().getDescriptor());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateGenerations(final GraphGenerations generations) {
        // very rarely, after a restart, generations can go in reverse, so we need this safeguard
        if (generations.getMinGenerationNonAncient() < getMinGenerationNonAncient()) {
            return;
        }
        super.updateGenerations(generations);
        missingParents.shiftWindow(generations.getMinGenerationNonAncient(), this::parentPurged);
        // if an orphan becomes ancient, we don't need it anymore
        orphanMap.shiftWindow(generations.getMinGenerationNonAncient(), this::orphanPurged);
        processNewlyLinkedEvents();
    }

    @Override
    public void loadFromSignedState(final SignedState signedState) {
        super.loadFromSignedState(signedState);
        missingParents.shiftWindow(getMinGenerationNonAncient());
        orphanMap.shiftWindow(getMinGenerationNonAncient());
    }

    private void parentPurged(final ParentDescriptor purgedParent, final Set<ChildEvent> orphans) {
        if (orphans == null) {
            return;
        }
        for (final ChildEvent child : orphans) {
            parentNoLongerMissing(child, purgedParent.hash(), null);
            if (!child.isOrphan()) {
                eventNoLongerOrphan(child);
            }
        }
    }

    /**
     * Is the event described an orphan we are keeping in the buffer
     *
     * @param descriptor the event descriptor
     * @return true if the event is an orphan this linker is buffering
     */
    public boolean isOrphan(final EventDescriptor descriptor) {
        return orphanMap.get(descriptor) != null;
    }

    /**
     * @return the number of orphans in the buffer
     */
    public int getNumOrphans() {
        return orphanMap.getSize();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasLinkedEvents() {
        return !eventOutput.isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EventImpl pollLinkedEvent() {
        return eventOutput.poll();
    }

    @Override
    public void clear() {
        super.clear();
        for (final EventImpl event : eventOutput) {
            event.clear();
        }
        eventOutput.clear();
        for (final EventImpl event : newlyLinkedEvents) {
            event.clear();
        }
        newlyLinkedEvents.clear();
        missingParents.clear();
        orphanMap.clear();
    }
}
