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
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.Objects;

/**
 * A bound on the event stream. This bound can be either a round or a timestamp but not both. The bound is inclusive of
 * exact matches to round and timestamp. If no round or timestamp is specified, then the bound is unbounded.
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
     * Create an event stream bound with the specified round or timestamp.  Throws an IllegalStateException if both the
     * round and timestamp are specified.
     *
     * @param round     the round
     * @param timestamp the timestamp
     * @throws IllegalStateException if both the round and timestamp are specified
     */
    public EventStreamBound(final long round, @Nullable final Instant timestamp) {
        this.round = round > NO_ROUND ? round : NO_ROUND;
        this.timestamp = Objects.requireNonNullElse(timestamp, NO_TIMESTAMP);
        if (hasRound() && hasTimestamp()) {
            throw new IllegalStateException("Cannot specify both round and timestamp");
        }
    }

    /**
     * Create an event stream bound with the specified round and no timestamp.
     *
     * @param round the round
     */
    public EventStreamBound(final long round) {
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
    @NonNull
    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * Creates an instance of a builder for a bound.
     *
     * @return the builder
     */
    @NonNull
    public static BoundBuilder create() {
        return new BoundBuilder();
    }

    /**
     * The string representation of the event stream bound.
     *
     * @return the string representation
     */
    @NonNull
    @Override
    public String toString() {
        return "EventStreamBound{" + "round=" + round + ", timestamp=" + timestamp + '}';
    }

    /**
     * Compares the detailed consensus event to this bound based on the consensus data in the event.
     *
     * @param event the detailed consensus event to compare to
     * @return a negative integer, zero, or a positive integer based on whether the consensus data is less, equal or
     * greater than the bound.
     */
    public int compareTo(@NonNull final DetailedConsensusEvent event) {
        return compareTo(event.getConsensusData());
    }

    /**
     * Compares the event implementation's consensus data to this bound.
     *
     * @param event the event implementation to compare to
     * @return a negative integer, zero, or a positive integer based on whether the consensus data is less, equal or
     * greater than the bound.
     */
    public int compareTo(@NonNull final EventImpl event) {
        return compareTo(event.getConsensusData());
    }

    /**
     * Compares the round received and consensus timestamp of the consensusData to the bound.  The comparison is made
     * with the frame of reference of the consensus data. If the data is higher, a value greater than 0 is returned, if
     * the data is lower, a valued less than 0 is returned, otherwise 0 is returned.
     * <p>
     * If hasRound() is true, the long value of the data's roundReceived() is compared to the round of the bound.
     * <p>
     * If hasTimestamp() is true, the consensusTimestamp() is compared to the timestamp of the bound.
     *
     * @param consensusData the consensus data to compare to
     * @return A positive value if the consensus data is greater than the bound, A negative value if the consensus data
     * is less than the bound, and 0 otherwise.
     */
    public int compareTo(@NonNull final ConsensusData consensusData) {
        Objects.requireNonNull(consensusData, "consensusData must not be null");
        if (isUnbounded()) {
            return 1;
        }
        if (round > NO_ROUND) {
            return Long.compare(consensusData.getRoundReceived(), round);
        } else {
            // the timestamp must be set, since the bound is not unbounded.
            return consensusData.getConsensusTimestamp().compareTo(timestamp);
        }
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
        @NonNull
        public BoundBuilder setRound(final long round) {
            if (round < NO_ROUND || round == 0L) {
                throw new IllegalStateException("Invalid round bound");
            }
            this.round = round;
            return this;
        }

        /**
         * Sets the timestamp of the bound.
         *
         * @param timestamp the timestamp
         * @return the builder
         */
        @NonNull
        public BoundBuilder setTimestamp(@Nullable final Instant timestamp) {
            this.timestamp = Objects.requireNonNullElse(timestamp, NO_TIMESTAMP);
            return this;
        }

        /**
         * Builds the event stream bound.
         *
         * @return the event stream bound
         */
        @NonNull
        public EventStreamBound build() {
            return new EventStreamBound(round, timestamp);
        }
    }
}
