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

package com.swirlds.platform.test.fixtures.event.generator;

import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.test.fixtures.event.DynamicValue;
import com.swirlds.platform.test.fixtures.event.source.EventSource;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates a hashgraph of events.
 *
 * @param <T>
 * 		the concrete type of this {@link GraphGenerator}
 */
public interface GraphGenerator<T extends GraphGenerator<T>> {

    /**
     * Get the next event.
     */
    EventImpl generateEvent();

    /**
     * Get the number of sources (i.e. nodes) contained by this generator.
     */
    int getNumberOfSources();

    /**
     * Get the event source for a particular node ID.
     */
    EventSource<?> getSource(@NonNull final NodeId nodeID);

    /**
     * Get an exact copy of this event generator in its current state. The events returned by this
     * new generator will be equivalent to the events returned by this generator, but these events
     * will be distinct objects that are not inter-connected in any way.
     *
     * Note: if this generator has emitted a large number of events, this method may be expensive. The copied
     * generator needs to skip all events already emitted.
     */
    default T copy() {
        final T generator = cleanCopy();
        generator.skip(getNumEventsGenerated());
        return generator;
    }

    /**
     * Get an exact copy of this event generator as it was when it was first created.
     */
    T cleanCopy();

    /**
     * Get a clean copy but with a different seed.
     *
     * @param seed
     * 		The new seed to use.
     */
    T cleanCopy(long seed);

    /**
     * Reset this generator to its original state. Does not undo settings changes, just the events that have been
     * emitted.
     */
    void reset();

    /**
     * Get the total number of events that have been created by this generator.
     */
    long getNumEventsGenerated();

    /**
     * Skip a number of events. These events will not be returned by any methods.
     *
     * @param numberToSkip
     * 		The number of events to skip.
     */
    default void skip(final long numberToSkip) {
        for (long i = 0; i < numberToSkip; i++) {
            generateEvent();
        }
    }

    /**
     * Get the next sequence of events.
     *
     * @param numberOfEvents
     * 		The number of events to get.
     */
    default List<EventImpl> generateEvents(final int numberOfEvents) {
        final List<EventImpl> events = new ArrayList<>(numberOfEvents);
        for (int i = 0; i < numberOfEvents; i++) {
            events.add(generateEvent());
        }
        return events;
    }

    /**
     * Get an address book that represents the collection of nodes that are generating the events.
     */
    @NonNull
    AddressBook getAddressBook();

    /**
     * Returns the maximum generation of this event generator.
     *
     * @param creatorId
     * 		the event creator
     * @return the maximum event generation for the supplied creator
     */
    long getMaxGeneration(@Nullable final NodeId creatorId);

    /**
     * Returns the maximum birth round of this event generator.
     *
     * @param creatorId
     * 		the event creator
     * @return the maximum event birth round for the supplied creator
     */
    long getMaxBirthRound(@Nullable final NodeId creatorId);

    /**
     * Returns the maximum generation of all events created by this generator
     */
    long getMaxGeneration();

    /**
     * Set the affinity of each node for choosing the parents of its events.
     *
     * @param affinityMatrix
     * 		An n by n matrix where n is the number of event sources. Each row defines the preference of a particular
     * 		node when choosing other parents. The node at index 0 in the address book is described by the first row,
     * 		the node at index 1 in the address book by the next row, etc. Each entry should be a weight. Weights of
     * 		self (i.e. the weights on the diagonal) should be 0.
     */
    void setOtherParentAffinity(final List<List<Double>> affinityMatrix);

    /**
     * Set the affinity of each node for choosing the parents of its events.
     *
     * @param affinityMatrix
     * 		A dynamic n by n matrix where n is the number of event sources. Each row defines the preference of a
     * 		particular node when choosing other parents. The node at index 0 in the address book is described by
     * 		the first row, the node at index 1 in the address book by the next row, etc. Each entry should be a weight.
     * 		Weights of self (i.e. the weights on the diagonal) should be 0.
     */
    void setOtherParentAffinity(final DynamicValue<List<List<Double>>> affinityMatrix);

    /**
     * Sets the timestamp of the last emitted event
     *
     * @param previousTimestamp the timestamp to set
     */
    void setPreviousTimestamp(final Instant previousTimestamp);
}
