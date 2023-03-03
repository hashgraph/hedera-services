/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state.signed;

import com.swirlds.common.time.Time;
import com.swirlds.common.utility.StackTrace;
import java.time.Instant;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Tracks the usage of a signed state over time, storing stack traces that can be used at a later time for debugging.
 */
public class SignedStateHistory {

    /**
     * An operation performed on a signed state.
     */
    public enum SignedStateAction {
        /**
         * When the signed state constructor is called.
         */
        CREATION,
        /**
         * When {@link SignedState#incrementReservationCount(ReservedSignedState)} is called.
         */
        RESERVE,
        /**
         * When {@link SignedState#decrementReservationCount(ReservedSignedState)} is called.
         */
        RELEASE,
        /**
         * When a signed state is destroyed.
         */
        DESTROY
    }

    /**
     * A record of an action taking with a signed state.
     *
     * @param action       the action
     * @param reason       the reason for the action, may be null for actions that do not require a reason
     * @param uniqueId     a unique id for the action, may be null for actions that do not require a unique id
     * @param stackTrace   where the action was performed, may be null if stack traces are not enabled
     * @param timestamp    the timestamp of the action
     * @param reservations the reservation count prior to the action
     */
    public record SignedStateActionReport(
            SignedStateAction action,
            String reason,
            Long uniqueId,
            StackTrace stackTrace,
            Instant timestamp,
            int reservations) {

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {

            final StringBuilder sb = new StringBuilder();

            sb.append(action)
                    .append(" @ ")
                    .append(timestamp)
                    .append("\n")
                    .append("initial reservations: ")
                    .append(reservations);

            if (reason != null) {
                sb.append("\nreason: ").append(reason);
            }
            if (uniqueId != null) {
                sb.append("\nreservation ID: ").append(uniqueId);
            }
            if (stackTrace != null) {
                sb.append("\n").append(stackTrace);
            }

            return sb.toString();
        }
    }

    private final Queue<SignedStateActionReport> actions = new ConcurrentLinkedQueue<>();
    private final Time time;
    private final long round;
    private final boolean stackTracesEnabled;

    /**
     * Create a new object to track the history of a signed state.
     *
     * @param time used to access wall clock time
     * @param round the round number of the signed state
     * @param stackTracesEnabled whether stack traces should be recorded
     */
    public SignedStateHistory(final Time time, final long round, final boolean stackTracesEnabled) {
        this.time = time;
        this.round = round;
        this.stackTracesEnabled = stackTracesEnabled;
    }

    /**
     * Record an action on a signed state.
     *
     * @param action       the action
     * @param reservations the number of reservations before the action
     * @param reason       the reason for the action, may be null for actions that do not require a reason
     * @param uniqueId     a unique id for the action, may be null for actions that do not require a unique id
     */
    public void recordAction(
            final SignedStateAction action, int reservations, final String reason, final Long uniqueId) {
        actions.add(new SignedStateActionReport(
                action,
                reason,
                uniqueId,
                stackTracesEnabled ? StackTrace.getStackTrace() : null,
                time.now(),
                reservations));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("SignedState history for round ").append(round).append("\n");
        actions.forEach(report -> sb.append(report).append("\n"));
        return sb.toString();
    }
}
