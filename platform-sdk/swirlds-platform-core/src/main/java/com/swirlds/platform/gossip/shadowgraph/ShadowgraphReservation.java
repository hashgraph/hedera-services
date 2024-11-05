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

import com.swirlds.common.event.AncientMode;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents zero or more reservations for an ancient indicator (i.e. a generation or a birth round, depending on
 * current {@link AncientMode andient mode}). It is used to determine when it is safe to
 * expire events in a given ancient indicator. Reservations are made by gossip threads inside {@link Shadowgraph}.
 * Ancient indicators that have at least one reservation may not have any of its events expired.
 */
public final class ShadowgraphReservation implements AutoCloseable {

    /**
     * The threshold that is being reserved. No event with an ancient indicator greater than or equal to this value may
     * be expired.
     */
    private final long reservedThreshold;

    /**
     * The number of reservations on this ancient indicator.
     */
    private final AtomicInteger reservationCount;

    /**
     * Constructor.
     *
     * @param reservedThreshold the ancient indicator that is being reserved, no event with an ancient indicator greater
     *                          than or equal to this value may be expired
     */
    public ShadowgraphReservation(final long reservedThreshold) {
        this.reservedThreshold = reservedThreshold;
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
     * Returns the ancient indicator that this instance tracks reservations for. The returned value is always zero or
     * greater.
     *
     * @return the ancient indicator that is reserved
     */
    public long getReservedThreshold() {
        return reservedThreshold;
    }
}
