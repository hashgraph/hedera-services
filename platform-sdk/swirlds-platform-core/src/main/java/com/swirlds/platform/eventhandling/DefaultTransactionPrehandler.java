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

import com.hedera.hapi.platform.event.StateSignatureTransaction;
import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.platform.components.transaction.system.ScopedSystemTransaction;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.stats.AverageTimeStat;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Default implementation of the {@link TransactionPrehandler} interface
 */
public class DefaultTransactionPrehandler implements TransactionPrehandler {
    private static final Logger logger = LogManager.getLogger(DefaultTransactionPrehandler.class);

    public static final Consumer<List<ScopedSystemTransaction<StateSignatureTransaction>>> NO_OP_CONSUMER =
            systemTransactions -> {};

    /**
     * A source to get the latest immutable state
     */
    private final Supplier<ReservedSignedState> latestStateSupplier;

    /**
     * Average time spent in to prehandle each individual transaction (in microseconds)
     */
    private final AverageTimeStat preHandleTime;

    private final Time time;

    /**
     * Constructs a new TransactionPrehandler
     *
     * @param platformContext     the platform context
     * @param latestStateSupplier provides access to the latest immutable state, may return null (implementation detail
     *                            of locking mechanism within the supplier)
     */
    public DefaultTransactionPrehandler(
            @NonNull final PlatformContext platformContext,
            @NonNull final Supplier<ReservedSignedState> latestStateSupplier) {
        this.time = platformContext.getTime();
        this.latestStateSupplier = Objects.requireNonNull(latestStateSupplier);

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
    public List<ScopedSystemTransaction<StateSignatureTransaction>> prehandleApplicationTransactions(
            @NonNull final PlatformEvent event) {
        final long startTime = time.nanoTime();

        ReservedSignedState latestImmutableState = null;
        try {
            latestImmutableState = latestStateSupplier.get();
            while (latestImmutableState == null) {
                latestImmutableState = latestStateSupplier.get();
            }

            try {
                latestImmutableState.get().getSwirldState().preHandle(event, NO_OP_CONSUMER);
            } catch (final Throwable t) {
                logger.error(EXCEPTION.getMarker(), "error invoking SwirldState.preHandle() for event {}", event, t);
            }
        } finally {
            event.signalPrehandleCompletion();
            latestImmutableState.close();

            preHandleTime.update(startTime, time.nanoTime());
        }

        // TODO temporarily return no transactions until SwirldState.preHandle() is updated with callback
        return new ArrayList<>();
    }
}
