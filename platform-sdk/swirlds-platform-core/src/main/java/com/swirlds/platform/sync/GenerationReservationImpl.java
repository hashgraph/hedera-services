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

package com.swirlds.platform.sync;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A simple implementation of {@link GenerationReservation}.
 */
public final class GenerationReservationImpl implements GenerationReservation {

    /**
     * The event generation that is reserved
     */
    private final long generation;

    /**
     * The number of reservations on this generation
     */
    private final AtomicInteger numReservations;

    public GenerationReservationImpl(final long generation) {
        this.generation = generation;
        numReservations = new AtomicInteger(1);
    }

    /**
     * Increments the number of reservations on this generation.
     */
    public void incrementReservations() {
        numReservations.incrementAndGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNumReservations() {
        return numReservations.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        numReservations.decrementAndGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getGeneration() {
        return generation;
    }
}
