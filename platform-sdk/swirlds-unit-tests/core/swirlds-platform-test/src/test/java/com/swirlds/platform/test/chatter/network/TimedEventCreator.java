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

import com.swirlds.common.time.Time;
import com.swirlds.platform.test.chatter.network.framework.SimulatedChatterEvent;
import com.swirlds.platform.test.chatter.network.framework.SimulatedEventCreator;
import com.swirlds.platform.test.simulated.config.NodeConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

public class TimedEventCreator<T extends SimulatedChatterEvent> implements SimulatedEventCreator<T> {

    private static final Duration DEFAULT_CREATION_INTERVAL = Duration.ofMillis(500);
    private final Time time;
    private Duration createEvery;
    private final Supplier<T> newEventSupplier;
    private Instant nextEventCreation;

    public TimedEventCreator(final Time time, final Supplier<T> newEventSupplier) {
        this(time, newEventSupplier, DEFAULT_CREATION_INTERVAL);
    }

    public TimedEventCreator(final Time time, final Supplier<T> newEventSupplier, final Duration createEvery) {
        this.time = time;
        this.createEvery = createEvery;
        this.newEventSupplier = newEventSupplier;
        nextEventCreation = time.now();
    }

    @Override
    public void applyNodeConfig(final NodeConfig nodeConfig) {
        createEvery = nodeConfig.createEventEvery();
    }

    @Override
    public T maybeCreateEvent() {
        if (createEvery.isZero() || time.now().isBefore(nextEventCreation)) {
            return null;
        }
        nextEventCreation = nextEventCreation.plus(createEvery);
        final T newEvent = newEventSupplier.get();
        newEvent.setTimeReceived(time.now());
        return newEvent;
    }
}
