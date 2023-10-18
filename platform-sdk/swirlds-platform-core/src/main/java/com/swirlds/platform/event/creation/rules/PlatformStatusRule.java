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

package com.swirlds.platform.event.creation.rules;

import static com.swirlds.platform.event.creation.EventCreationStatus.PLATFORM_STATUS;

import com.swirlds.common.system.status.PlatformStatus;
import com.swirlds.platform.event.creation.EventCreationStatus;
import com.swirlds.platform.eventhandling.TransactionPool;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Limits the creation of new events depending on the current platform status.
 */
public class PlatformStatusRule implements EventCreationRule {

    private final Supplier<PlatformStatus> platformStatusSupplier;
    private final TransactionPool transactionPool;

    /**
     * Constructor.
     *
     * @param platformStatusSupplier    provides the current platform status
     * @param transactionPool           provides transactions to be added to new events
     */
    public PlatformStatusRule(
            @NonNull final Supplier<PlatformStatus> platformStatusSupplier,
            @NonNull final TransactionPool transactionPool) {

        this.platformStatusSupplier = Objects.requireNonNull(platformStatusSupplier);
        this.transactionPool = Objects.requireNonNull(transactionPool);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEventCreationPermitted() {
        final PlatformStatus currentStatus = platformStatusSupplier.get();

        if (currentStatus == PlatformStatus.FREEZING) {
            return transactionPool.hasBufferedSignatureTransactions();
        }

        if (currentStatus != PlatformStatus.ACTIVE && currentStatus != PlatformStatus.CHECKING) {
            return false;
        }

        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void eventWasCreated() {
        // no-op
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public EventCreationStatus getEventCreationStatus() {
        return PLATFORM_STATUS;
    }
}
