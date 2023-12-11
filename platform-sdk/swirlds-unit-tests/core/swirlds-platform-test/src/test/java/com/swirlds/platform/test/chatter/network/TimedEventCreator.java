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

package com.swirlds.platform.test.chatter.network;

import com.swirlds.base.time.Time;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.test.chatter.network.framework.SimulatedEventCreator;
import com.swirlds.platform.test.simulated.config.NodeConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Creates events at a set interval.
 *
 */
public class TimedEventCreator implements SimulatedEventCreator {

    private static final Duration DEFAULT_CREATION_INTERVAL = Duration.ofMillis(500);
    /** The instance of time used by the simulation */
    private final Time time;
    /** The amount of time to wait before creating the next event */
    private Duration createEvery;
    /** A supplier of a new event */
    private final Supplier<GossipEvent> newEventSupplier;
    /** The time at which the next event should be created */
    private Instant nextEventCreation;

    public TimedEventCreator(final Time time, final Supplier<GossipEvent> newEventSupplier) {
        this(time, newEventSupplier, DEFAULT_CREATION_INTERVAL);
    }

    public TimedEventCreator(
            final Time time, final Supplier<GossipEvent> newEventSupplier, final Duration createEvery) {
        this.time = time;
        this.createEvery = createEvery;
        this.newEventSupplier = newEventSupplier;
        nextEventCreation = time.now();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void applyNodeConfig(final NodeConfig nodeConfig) {
        createEvery = nodeConfig.createEventEvery();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GossipEvent maybeCreateEvent() {
        if (createEvery.isZero() || time.now().isBefore(nextEventCreation)) {
            return null;
        }
        nextEventCreation = nextEventCreation.plus(createEvery);
        final GossipEvent newEvent = newEventSupplier.get();
        newEvent.setTimeReceived(time.now());
        return newEvent;
    }
}
