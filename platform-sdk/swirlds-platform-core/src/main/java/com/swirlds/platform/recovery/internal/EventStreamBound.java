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

package com.swirlds.platform.recovery.internal;

import com.swirlds.common.system.events.ConsensusData;
import com.swirlds.common.system.events.DetailedConsensusEvent;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;

/**
 * A bound on the event stream. This bound can be either a round or a timestamp or both.  It can be compared as an upper
 * bound or a lower bound to an event.  The bound is inclusive. If no round or timestamp is specified, then the bound is
 * unbounded.
 */
public class EventStreamBound {

    /** A constant for unspecified round. */
    public static final long NO_ROUND = -1L;
    /** A constant for unspecified timestamp. */
    public static final Instant NO_TIMESTAMP = Instant.MIN;
    /** A constant for an unbounded bound. */
    public static final EventStreamBound UNBOUNDED = new EventStreamBound(NO_ROUND, NO_TIMESTAMP);
    /** the round of the bound */
    private final long round;
    /** the timestamp of the bound */
    private final Instant timestamp;

    /**
     * Create an event stream bound with the specified round and timestamp.
     *
     * @param round     the round
     * @param timestamp the timestamp
     */
    public EventStreamBound(@NonNull final long round, @NonNull final Instant timestamp) {
        this.round = round;
        this.timestamp = timestamp;
    }

    /**
     * Create an event stream bound with the specified round and no timestamp.
     *
     * @param round the round
     */
    public EventStreamBound(@NonNull final long round) {
        this(round, NO_TIMESTAMP);
    }

    /**
     * Create an event stream bound with the specified timestamp and no round.
     *
     * @param timestamp the timestamp
     */
    public EventStreamBound(@NonNull final Instant timestamp) {
        this(NO_ROUND, timestamp);
    }

    /**
     * Indicates if the event stream bound has a round specified.
     *
     * @return true if the bound has a round
     */
    public boolean hasRound() {
        return round != NO_ROUND;
    }

    /**
     * Indicates if the event stream bound has a timestamp specified.
     *
     * @return true if the bound has a timestamp
     */
    public boolean hasTimestamp() {
        return !timestamp.equals(NO_TIMESTAMP);
    }

    /**
     * Gets the round of the bound.
     *
     * @return the round
     */
    public long getRound() {
        return round;
    }

    /**
     * Gets the timestamp of the event stream bound.
     *
     * @return the timestamp
     */
    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * Creates an instance of a builder for a bound.
     *
     * @return the builder
     */
    public static BoundBuilder create() {
        return new BoundBuilder();
    }

    /**
     * The string representation of the event stream bound.
     *
     * @return the string representation
     */
    @Override
    public String toString() {
        return "EventStreamBound{" + "round=" + round + ", timestamp=" + timestamp + '}';
    }

    /**
     * Compares the detailed consensus event to this bound based on the consensus data in the event.
     *
     * @param event     the detailed consensus event to compare to
     * @param boundType the type of bound to compare as
     * @return a negative integer, zero, or a positive integer based on whether the consensus data is less, equal or
     * greater than the bound.
     */
    public Integer compareTo(DetailedConsensusEvent event, BoundType boundType) {
        return compareTo(event.getConsensusData(), boundType);
    }

    /**
     * Compares the event implementation's consensus data to this bound.
     *
     * @param event     the event implementation to compare to
     * @param boundType the type of bound to compare as
     * @return a negative integer, zero, or a positive integer based on whether the consensus data is less, equal or
     * greater than the bound.
     */
    public Integer compareTo(EventImpl event, BoundType boundType) {
        return compareTo(event.getConsensusData(), boundType);
    }

    /**
     * Compares the round received and consensus timestamp of the consensusData to the bound.  The comparison is made
     * with the frame of reference of the consensus data. If the data is higher, a value greater than 0 is returned, if
     * the data is lower, a valued less than 0 is returned, otherwise 0 is returned.
     * <p>
     * If the bound is a LOWER bound, if both the round and the timestamp are present in the bound then the consensus
     * data is greater than bound if both the bound's round and the timestamp are lower than the consensus data round
     * received and consensus timestamp, the consensus data is less than the bound if either the bound's round or the
     * timestamp are strictly greater, and equal otherwise.   If only one of the timestamp or round is present, the
     * normal comparison rules apply. If neither the timestamp nor round is present, the bound is an unbounded lower
     * bound and the event is always greater than it.
     * <p>
     * If the bound is an UPPER bound, if both the round and the timestamp are present then the consensus data is less
     * than the bound if both the bound's round and the timestamp are greater than the consensus data round received and
     * consensus timestamp, the consensus data is greater than the bound if either bound's the round or the timestamp
     * are strictly less, and equal otherwise.   If only one of the timestamp or round is present, the normal comparison
     * rules apply. If neither the timestamp nor round is present, the bound is an unbounded upper bound and the event
     * is always less than it.
     *
     * @param consensusData the consensus data to compare to
     * @param boundType     the type of bound
     * @return A positive value if the consensus data is greater than the bound, A negative value if the consensus data
     * is less than the bound, and 0 otherwise.
     */
    public int compareTo(@NonNull final ConsensusData consensusData, BoundType boundType) {
        if (isUnbounded()) {
            return boundType == BoundType.LOWER ? 1 : -1;
        }
        if (round == NO_ROUND) {
            return compareTimestamp(consensusData);
        }
        if (timestamp == NO_TIMESTAMP) {
            return compareRound(consensusData);
        }

        int roundCompare = compareRound(consensusData);
        int timestampCompare = compareTimestamp(consensusData);

        if (boundType == BoundType.LOWER) {
            if (roundCompare > 0 && timestampCompare > 0) {
                return 1;
            }
            if (roundCompare < 0 || timestampCompare < 0) {
                return -1;
            }
        } else {
            if (roundCompare > 0 || timestampCompare > 0) {
                return 1;
            }
            if (roundCompare < 0 && timestampCompare < 0) {
                return -1;
            }
        }
        return 0;
    }

    /**
     * Compares the round received of the consensus data to the round of the bound.
     *
     * @param consensusData the consensus data to compare to
     * @return a negative integer, zero, or a positive integer based on whether the consensus data is less, equal or greater than the bound.
     */
    private int compareRound(@NonNull final ConsensusData consensusData) {
        return Long.compare(consensusData.getRoundReceived(), round);
    }

    /**
     * Compares the consensus timestamp of the consensus data to the timestamp of the bound.
     *
     * @param consensusData the consensus data to compare to
     * @return a negative integer, zero, or a positive integer based on whether the consensus data is less, equal or greater than the bound.
     */
    private int compareTimestamp(@NonNull final ConsensusData consensusData) {
        return consensusData.getConsensusTimestamp().compareTo(timestamp);
    }

    /**
     * Checks if the bound is unbounded.
     *
     * @return true if the bound is unbounded, false otherwise
     */
    public boolean isUnbounded() {
        return round == NO_ROUND && timestamp == NO_TIMESTAMP;
    }

    /**
     * The type of bound.
     */
    public enum BoundType {
        LOWER,
        UPPER
    }

    /**
     * A builder for an event stream bound.
     */
    public static class BoundBuilder {
        /** The round of the bound. */
        private long round = NO_ROUND;
        /** The timestamp of the bound. */
        private Instant timestamp = NO_TIMESTAMP;

        /**
         * Sets the round of the bound.
         *
         * @param round the round
         * @return the builder
         */
        public BoundBuilder setRound(final long round) {
            this.round = round;
            return this;
        }

        /**
         * Sets the timestamp of the bound.
         *
         * @param timestamp the timestamp
         * @return the builder
         */
        public BoundBuilder setTimestamp(final Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        /**
         * Builds the event stream bound.
         *
         * @return the event stream bound
         */
        public EventStreamBound build() {
            if (round < NO_ROUND || round == 0L) {
                throw new IllegalStateException("Invalid round bound");
            }
            return new EventStreamBound(round, timestamp);
        }
    }
}
