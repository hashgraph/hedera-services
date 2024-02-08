/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.gossip.shadowgraph;

import com.swirlds.platform.consensus.NonAncientEventWindow;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents zero or more reservations for an ancient indicator (i.e. a generation or a birth round, depending on
 * current {@link com.swirlds.platform.event.AncientMode andient mode}). It is used to determine when it is safe to
 * expire events in a given ancient indicator. Reservations are made by gossip threads inside {@link Shadowgraph}.
 * Ancient indicators that have at least one reservation may not have any of its events expired.
 */
public final class ShadowgraphReservation implements AutoCloseable {

    /**
     * The event window that this reservation is for.
     */
    private final NonAncientEventWindow eventWindow;

    /**
     * The number of reservations on this ancient indicator.
     */
    private final AtomicInteger reservationCount;

    /**
     * Constructor.
     *
     * @param eventWindow the event window that this reservation is for
     */
    public ShadowgraphReservation(@NonNull final NonAncientEventWindow eventWindow) {
        this.eventWindow = Objects.requireNonNull(eventWindow);
        reservationCount = new AtomicInteger(1);
    }

    /**
     * Increments the number of reservations on this ancient indicator.
     */
    public void incrementReservations() {
        reservationCount.incrementAndGet();
    }

    /**
     * Returns the number of current reservations for this ancient indicator.
     *
     * @return number of reservations
     */
    public int getReservationCount() {
        return reservationCount.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        reservationCount.decrementAndGet();
    }

    /**
     * Get the event window at the time of the reservation.
     *
     * @return the event window
     */
    @NonNull
    public NonAncientEventWindow getEventWindow() {
        return eventWindow;
    }

    /**
     * Returns the ancient indicator that this instance tracks reservations for. The returned value is always zero or
     * greater.
     *
     * @return the ancient indicator that is reserved
     */
    public long getReservedIndicator() {
        return eventWindow.getExpiredThreshold();
    }
}
