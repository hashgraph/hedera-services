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

package com.swirlds.platform.eventhandling;

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.metrics.api.Metrics.INTERNAL_CATEGORY;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.state.nexus.SignedStateNexus;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.stats.AverageTimeStat;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Default implementation of the {@link TransactionPrehandler} interface
 */
public class DefaultTransactionPrehandler implements TransactionPrehandler {
    private static final Logger logger = LogManager.getLogger(DefaultTransactionPrehandler.class);

    /**
     * A source to get the latest immutable state
     */
    private final SignedStateNexus latestImmutableStateNexus;

    /**
     * Average time spent in to prehandle each individual transaction (in microseconds)
     */
    private final AverageTimeStat preHandleTime;

    private final Time time;

    /**
     * Constructs a new TransactionPrehandler
     *
     * @param platformContext           the platform context
     * @param latestImmutableStateNexus the latest immutable state nexus
     */
    public DefaultTransactionPrehandler(
            @NonNull final PlatformContext platformContext, @NonNull final SignedStateNexus latestImmutableStateNexus) {
        this.time = platformContext.getTime();
        this.latestImmutableStateNexus = Objects.requireNonNull(latestImmutableStateNexus);

        preHandleTime = new AverageTimeStat(
                platformContext.getMetrics(),
                ChronoUnit.MICROS,
                INTERNAL_CATEGORY,
                "preHandleMicros",
                "average time it takes to perform preHandle (in microseconds)");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void prehandleApplicationTransactions(@NonNull final GossipEvent event) {
        // FUTURE WORK: As a temporary workaround, convert to EventImpl. This workaround will be removed as part of the
        // event refactor
        final EventImpl eventImpl = new EventImpl(event, null, null);

        final long startTime = time.nanoTime();

        ReservedSignedState latestImmutableState = null;
        try {
            latestImmutableState = latestImmutableStateNexus.getState("transaction prehandle");
            while (latestImmutableState == null) {
                latestImmutableState = latestImmutableStateNexus.getState("transaction prehandle");
            }

            try {
                latestImmutableState.get().getSwirldState().preHandle(eventImpl);
            } catch (final Throwable t) {
                logger.error(
                        EXCEPTION.getMarker(),
                        "error invoking SwirldState.preHandle() for event {}",
                        eventImpl.toMediumString(),
                        t);
            }
        } finally {
            event.signalPrehandleCompletion();
            latestImmutableState.close();

            preHandleTime.update(startTime, time.nanoTime());
        }
    }
}
