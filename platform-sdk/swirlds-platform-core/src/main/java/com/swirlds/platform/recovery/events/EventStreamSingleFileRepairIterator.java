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

package com.swirlds.platform.recovery.events;

import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.IOIterator;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.stream.RunningHashCalculatorForStream;
import com.swirlds.common.system.events.DetailedConsensusEvent;
import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * An iterator over events streams that will compute a running hash and add a final hash if it is missing from the event
 * stream.  The expected pattern for events streams is a Hash followed by one or more DetailedConsensusEvents followed
 * by a final hash.  The behavior of this class is indeterminate for any sequence of events that is not a prefix of the
 * expected pattern for event streams.
 */
public class EventStreamSingleFileRepairIterator implements Iterator<SelfSerializable> {

    /**
     * The logger to record information and errors.
     */
    private final Logger logger = LogManager.getLogger(EventStreamSingleFileRepairIterator.class);
    /**
     * The sequence of SelfSerializables that may need a final hash added.
     */
    private final IOIterator<SelfSerializable> eventStreamIterator;
    /**
     * The running hash calculator for computing the final hash.
     */
    private final RunningHashCalculatorForStream<DetailedConsensusEvent> runningHashCalculator;
    /**
     * The next event to return in the event stream.
     */
    private SelfSerializable next = null;
    /**
     * The most recent event returned in the event stream.
     */
    private SelfSerializable prev = null;
    /**
     * indicates the number of hashes we've seen through the event stream iterator.
     */
    private int hashCount = 0;
    /**
     * A boolean to indicate if we provided a new final hash for the event stream iterator.
     */
    private boolean finalHashAdded = false;
    /**
     * The count of events in the event stream.
     */
    private int eventCount = 0;

    /**
     * Constructs a repairing iterator for the given event stream iterator.
     *
     * @param eventStream The event stream iterator that may be missing a final hash.
     */
    public EventStreamSingleFileRepairIterator(final IOIterator<SelfSerializable> eventStream) {
        this.eventStreamIterator = Objects.requireNonNull(eventStream, "The eventStream iterator must not be null.");
        this.runningHashCalculator = new RunningHashCalculatorForStream<>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasNext() {
        return next != null || findNextWithRepair();
    }

    /**
     * Sets the next SelfSerializable from the event stream iterator, increments the count of hashes or events, and
     * updates the running hash.  If the event stream iterator does not have a next element or throws an exception, the
     * next variable is set to null.
     */
    private void setNextAndUpdateRunningHash() {
        try {
            if (eventStreamIterator.hasNext()) {
                next = eventStreamIterator.next();
                if (next instanceof Hash hash) {
                    hashCount++;
                    if (hashCount == 1) {
                        // seed running hash calculator with first hash.
                        runningHashCalculator.setRunningHash(hash);
                    }
                } else if (next instanceof DetailedConsensusEvent dce) {
                    eventCount++;
                    // update running hash calculator with event.
                    runningHashCalculator.addObject(dce);
                } else {
                    throw new IllegalStateException("Unexpected SelfSerializable Class: "
                            + next.getClass().getCanonicalName());
                }
            }
        } catch (final IOException e) {
            // No error, set next element to null.
            next = null;
        }
    }

    /**
     * Assumption: that there are no more elements in the event stream iterator. We repair the event stream sequence if
     * it is missing a final hash by setting the next to the running hash of the previous event returned.
     */
    private void determineFinalHash() {
        if (prev.getClassId() == Hash.CLASS_ID) {
            if (hashCount == 1 && eventCount == 0) {
                logger.error(EXCEPTION.getMarker(), "No events in sequence.");
            }
        } else if (prev instanceof DetailedConsensusEvent dce) {
            // the final hash is the running hash of the last event returned.
            try {
                next = dce.getRunningHash().getFutureHash().getAndRethrow();
                finalHashAdded = true;
            } catch (final InterruptedException ex) {
                logger.error(EXCEPTION.getMarker(), "Interrupted Exception while waiting for running hash.", ex);
                Thread.currentThread().interrupt();
            }
        } else {
            logger.warn(
                    "Unexpected event stream pattern in single file repair iterator, "
                            + "event count is {} and last event class is: {}",
                    eventCount,
                    prev.getClass().getCanonicalName());
        }
    }

    /**
     * Sets the next SelfSerializable from the event stream iterator if it exists and updates the running hash. If the
     * event stream iterator does not have a final hash, the sequence is repaired by setting the next SelfSerializable
     * to the running hash saved in the previous event.
     *
     * @return true if the next event is set, false otherwise.
     */
    private boolean findNextWithRepair() {
        setNextAndUpdateRunningHash();
        if (next == null && !finalHashAdded) {
            determineFinalHash();
        }
        return next != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SelfSerializable next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        prev = next;
        next = null;
        return prev;
    }

    /**
     * Indicates if the wrapped event stream did not have a final hash and the final hash was added to the resulting
     * event stream.
     *
     * @return false until and unless this iterator has added a final hash to the content of the original event stream,
     * then true is returned.
     */
    public boolean finalHashAdded() {
        return finalHashAdded;
    }

    /**
     * Indicates the number of DetailedConsensusEvents iterated over in the event stream.
     *
     * @return the number of events iterated over so far.
     */
    public int getEventCount() {
        return eventCount;
    }
}
