/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.event.generator;

import static com.swirlds.platform.test.event.EventUtils.staticDynamicValue;
import static com.swirlds.platform.test.event.EventUtils.weightedChoice;
import static com.swirlds.platform.test.event.RandomEventUtils.DEFAULT_FIRST_EVENT_TIME_CREATED;

import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.RandomAddressBookGenerator;
import com.swirlds.platform.test.event.DynamicValue;
import com.swirlds.platform.test.event.DynamicValueGenerator;
import com.swirlds.platform.test.event.IndexedEvent;
import com.swirlds.platform.test.event.source.EventSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A utility class for generating a graph of events.
 */
public class StandardGraphGenerator extends AbstractGraphGenerator<StandardGraphGenerator> {

    /**
     * A list of sources. There is one source per node that is being simulated.
     */
    private final List<EventSource<?>> sources;

    /**
     * Determines the probability that a node becomes the other parent of an event.
     */
    private DynamicValueGenerator<List<List<Double>>> affinityMatrix;

    /**
     * The address book representing the event sources.
     */
    private AddressBook addressBook;

    /**
     * The average difference in the timestamp between two adjacent events (in seconds).
     */
    private double eventPeriodMean = 0.000_1;

    /**
     * The standard deviation of the difference of the timestamp between two adjacent events (in seconds).
     */
    private double eventPeriodStandardDeviation = 0.000_01;

    /**
     * The probability, as a fraction of 1.0, that an event has the same timestamp as the proceeding event. If
     * the proceeding event has the same self parent then this is ignored and the events are not made to be
     * simultaneous.
     */
    private double simultaneousEventFraction = 0.01;

    /**
     * The timestamp of the previously emitted event.
     */
    private Instant previousTimestamp;

    /**
     * Construct a new StandardEventGenerator.
     *
     * Note: once an event source has been passed to this constructor it should not be modified by the outer context.
     *
     * @param seed
     * 		The random seed used to generate events.
     * @param eventSources
     * 		One or more event sources.
     */
    public StandardGraphGenerator(final long seed, final EventSource<?>... eventSources) {
        this(seed, new ArrayList<>(Arrays.asList(eventSources)));
    }

    /**
     * Construct a new StandardEventGenerator.
     *
     * @param seed
     * 		The random seed used to generate events.
     * @param eventSources
     * 		One or more event sources.
     */
    public StandardGraphGenerator(final long seed, final List<EventSource<?>> eventSources) {
        super(seed);

        this.sources = eventSources;
        if (eventSources.isEmpty()) {
            throw new IllegalArgumentException("At least one event source is required");
        }

        for (int index = 0; index < eventSources.size(); index++) {
            final EventSource<?> source = eventSources.get(index);
            source.setNodeId(index);
        }

        buildDefaultOtherParentAffinityMatrix();
        buildAddressBook(eventSources);
    }

    /**
     * Copy constructor.
     */
    private StandardGraphGenerator(final StandardGraphGenerator that) {
        this(that, that.getInitialSeed());
    }

    /**
     * Copy constructor, but with a different seed.
     */
    private StandardGraphGenerator(final StandardGraphGenerator that, final long seed) {
        super(seed);

        this.affinityMatrix = that.affinityMatrix.cleanCopy();
        this.sources = new ArrayList<>(that.sources.size());
        for (final EventSource<?> sourceToCopy : that.sources) {
            final EventSource<?> copy = sourceToCopy.copy();
            this.sources.add(copy);
        }
        this.eventPeriodMean = that.eventPeriodMean;
        this.eventPeriodStandardDeviation = that.eventPeriodStandardDeviation;
        this.simultaneousEventFraction = that.simultaneousEventFraction;
        buildAddressBook(this.sources);
    }

    private void buildAddressBook(final List<EventSource<?>> eventSources) {
        addressBook = new RandomAddressBookGenerator(getRandom())
                .setSize(sources.size())
                .setCustomStakeGenerator(id -> eventSources.get((int) id).getStake())
                .setHashStrategy(RandomAddressBookGenerator.HashStrategy.FAKE_HASH)
                .setSequentialIds(true)
                .build();
    }

    /**
     * Set the affinity of each node for choosing the parents of its events.
     *
     * @param affinityMatrix
     * 		An n by n matrix where n is the number of event sources. Each row defines the preference of a particular
     * 		node when choosing other parents. Node 0 is described by the first row, node 1 by the next, etc.
     * 		Each entry should be a weight. Weights of self (i.e. the weights on the diagonal) should be 0.
     */
    public void setOtherParentAffinity(final List<List<Double>> affinityMatrix) {
        setOtherParentAffinity(staticDynamicValue(affinityMatrix));
    }

    /**
     * Set the affinity of each node for choosing the parents of its events.
     *
     * @param affinityMatrix
     * 		A dynamic n by n matrix where n is the number of event sources. Each entry should be a weight.
     * 		Weights of self (i.e. the weights on the diagonal) should be 0.
     */
    public void setOtherParentAffinity(final DynamicValue<List<List<Double>>> affinityMatrix) {
        this.affinityMatrix = new DynamicValueGenerator<>(affinityMatrix);
    }

    /**
     * Get the affinity vector for a particular node.
     *
     * @param eventIndex
     * 		the current event index
     * @param nodeId
     * 		the node ID that is being requested
     */
    private List<Double> getOtherParentAffinityVector(final long eventIndex, final int nodeId) {
        return affinityMatrix.get(getRandom(), eventIndex).get(nodeId);
    }

    private void buildDefaultOtherParentAffinityMatrix() {
        final List<List<Double>> matrix = new ArrayList<>(sources.size());

        for (int nodeId = 0; nodeId < sources.size(); nodeId++) {
            final List<Double> affinityVector = new ArrayList<>(sources.size());
            for (int index = 0; index < sources.size(); index++) {
                if (index == nodeId) {
                    affinityVector.add(0.0);
                } else {
                    affinityVector.add(1.0);
                }
            }
            matrix.add(affinityVector);
        }

        affinityMatrix = new DynamicValueGenerator<>(staticDynamicValue(matrix));
    }

    /**
     * Get the average difference in the timestamp between two adjacent events (in seconds).
     */
    public double getEventPeriodMean() {
        return eventPeriodMean;
    }

    /**
     * Set the average difference in the timestamp between two adjacent events (in seconds).
     *
     * @return this
     */
    public StandardGraphGenerator setEventPeriodMean(final double eventPeriodMean) {
        this.eventPeriodMean = eventPeriodMean;
        return this;
    }

    /**
     * Get the standard deviation of the difference of the timestamp between two adjacent events (in seconds).
     */
    public double getEventPeriodStandardDeviation() {
        return eventPeriodStandardDeviation;
    }

    /**
     * Set the standard deviation of the difference of the timestamp between two adjacent events (in seconds).
     *
     * @return this
     */
    public StandardGraphGenerator setEventPeriodStandardDeviation(final double eventPeriodStandardDeviation) {
        this.eventPeriodStandardDeviation = eventPeriodStandardDeviation;
        return this;
    }

    /**
     * Set the probability, as a fraction of 1.0, that an event has the same timestamp as the proceeding event. If
     * the proceeding event has the same self parent then this is ignored and the events are not made to be
     * simultaneous.
     */
    public double getSimultaneousEventFraction() {
        return simultaneousEventFraction;
    }

    /**
     * Get the probability, as a fraction of 1.0, that an event has the same timestamp as the proceeding event. If
     * the proceeding event has the same self parent then this is ignored and the events are not made to be
     * simultaneous.
     *
     * @return this
     */
    public StandardGraphGenerator setSimultaneousEventFraction(final double simultaneousEventFraction) {
        this.simultaneousEventFraction = simultaneousEventFraction;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StandardGraphGenerator cleanCopy() {
        return new StandardGraphGenerator(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNumberOfSources() {
        return sources.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EventSource<?> getSource(final int nodeID) {
        return sources.get(nodeID);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AddressBook getAddressBook() {
        return addressBook;
    }

    /**
     * Returns the weight of each source, used to determine the likelihood of that source producing the next event
     * compared to the other sources. Could be static or dynamic depending on how many events have already been
     * generated.
     *
     * @param eventIndex
     * 		the index of the event
     * @return list of new event weights
     */
    private List<Double> getSourceWeights(final long eventIndex) {
        final List<Double> sourceWeights = new ArrayList<>(sources.size());
        for (final EventSource<?> source : sources) {
            sourceWeights.add(source.getNewEventWeight(getRandom(), eventIndex));
        }

        return sourceWeights;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StandardGraphGenerator cleanCopy(final long newSeed) {
        return new StandardGraphGenerator(this, newSeed);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void resetInternalData() {
        for (final EventSource<?> source : sources) {
            source.reset();
        }
        previousTimestamp = null;
        buildAddressBook(sources);
    }

    /**
     * Get the next node that is creating an event.
     */
    private EventSource<?> getNextEventSource(final long eventIndex) {
        final int nodeID = weightedChoice(getRandom(), getSourceWeights(eventIndex));
        return sources.get(nodeID);
    }

    /**
     * Get the node that will be the other parent for the new event.
     *
     * @param source
     * 		The node that is creating the event.
     */
    private EventSource<?> getNextOtherParentSource(final long eventIndex, final EventSource<?> source) {
        final List<Double> affinityVector = getOtherParentAffinityVector(eventIndex, source.getNodeId());
        final int nodeID = weightedChoice(getRandom(), affinityVector);
        return sources.get(nodeID);
    }

    /**
     * Get the next timestamp for the next event.
     */
    private Instant getNextTimestamp(final EventSource<?> source) {
        if (previousTimestamp == null) {
            previousTimestamp = DEFAULT_FIRST_EVENT_TIME_CREATED;
            return previousTimestamp;
        }

        final IndexedEvent previousEvent = source.getLatestEvent(getRandom());
        final Instant previousTimestampForSource =
                previousEvent == null ? Instant.ofEpochSecond(0) : previousEvent.getTimeCreated();

        final boolean shouldRepeatTimestamp = getRandom().nextDouble() < simultaneousEventFraction;
        if (!previousTimestampForSource.equals(previousTimestamp) && shouldRepeatTimestamp) {
            return previousTimestamp;
        } else {
            final double delta = Math.max(
                    0.000_000_001,
                    eventPeriodMean + eventPeriodStandardDeviation * getRandom().nextGaussian());

            final long deltaSeconds = (int) delta;
            final long deltaNanoseconds = (int) ((delta - deltaSeconds) * 1_000_000_000);
            final Instant timestamp =
                    previousTimestamp.plusSeconds(deltaSeconds).plusNanos(deltaNanoseconds);
            previousTimestamp = timestamp;
            return timestamp;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IndexedEvent buildNextEvent(final long eventIndex) {
        final EventSource<?> source = getNextEventSource(eventIndex);
        final EventSource<?> otherParentSource = getNextOtherParentSource(eventIndex, source);

        final IndexedEvent next =
                source.generateEvent(getRandom(), eventIndex, otherParentSource, getNextTimestamp(source));
        next.setGeneratorIndex(eventIndex);

        return next;
    }

    @Override
    public void setPreviousTimestamp(final Instant previousTimestamp) {
        this.previousTimestamp = previousTimestamp;
    }
}
