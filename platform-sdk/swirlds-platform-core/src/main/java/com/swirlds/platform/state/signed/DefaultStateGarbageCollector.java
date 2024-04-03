/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

import static com.swirlds.common.units.TimeUnit.UNIT_MICROSECONDS;
import static com.swirlds.common.units.TimeUnit.UNIT_NANOSECONDS;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.metrics.RunningAverageMetric;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * This class is responsible for the deletion of signed states. In case signed state deletion is expensive, we never
 * want to delete a signed state on the last thread that releases it.
 */
public class DefaultStateGarbageCollector implements StateGarbageCollector {

    private final List<SignedState> states = new LinkedList<>();

    private static final RunningAverageMetric.Config UNDELETABLE_STATES_CONFIG = new RunningAverageMetric.Config(
                    "platform", "undeletableStates")
            .withDescription("Average number of undeletable states in the state garbage collector")
            .withUnit("count");
    private final RunningAverageMetric undeletableStates;

    private static final RunningAverageMetric.Config TIME_TO_DELETE_STATE_CONFIG = new RunningAverageMetric.Config(
                    "platform", "timeToDeleteState")
            .withDescription("Average time to delete a state in the state garbage collector")
            .withUnit("microseconds");
    private final RunningAverageMetric timeToDeleteState;

    /**
     * Create a new instance of the garbage collector.
     *
     * @param platformContext the platform context
     */
    public DefaultStateGarbageCollector(@NonNull final PlatformContext platformContext) {
        undeletableStates = platformContext.getMetrics().getOrCreate(UNDELETABLE_STATES_CONFIG);
        timeToDeleteState = platformContext.getMetrics().getOrCreate(TIME_TO_DELETE_STATE_CONFIG);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerState(@NonNull final ReservedSignedState state) {

        // TODO log error for states not configured to be deleted on background thread

        try (state) {
            // Intentionally hold a java reference without a signed state reference count.
            // This is the only place in the codebase that is allowed to do this.
            states.add(state.get());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void heartbeat(@NonNull final Instant now) {
        final Iterator<SignedState> iterator = states.iterator();
        while (iterator.hasNext()) {
            final SignedState signedState = iterator.next();
            if (signedState.isEligibleForDeletion()) {

                final Instant start = Instant.now();
                signedState.delete();
                final Instant end = Instant.now();
                final Duration duration = Duration.between(start, end);
                timeToDeleteState.update(UNIT_NANOSECONDS.convertTo(duration.toNanos(), UNIT_MICROSECONDS));

                iterator.remove();
            }
        }

        undeletableStates.update(states.size());
    }
}
