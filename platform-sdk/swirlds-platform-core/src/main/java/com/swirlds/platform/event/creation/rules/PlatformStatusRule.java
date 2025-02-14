// SPDX-License-Identifier: Apache-2.0
package com.swirlds.platform.event.creation.rules;

import static org.hiero.event.creator.EventCreationStatus.PLATFORM_STATUS;

import com.swirlds.platform.pool.TransactionPoolNexus;
import com.swirlds.platform.system.status.PlatformStatus;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.function.Supplier;
import org.hiero.event.creator.EventCreationRule;
import org.hiero.event.creator.EventCreationStatus;

/**
 * Limits the creation of new events depending on the current platform status.
 */
public class PlatformStatusRule implements EventCreationRule {

    private final Supplier<PlatformStatus> platformStatusSupplier;
    private final TransactionPoolNexus transactionPoolNexus;

    /**
     * Constructor.
     *
     * @param platformStatusSupplier provides the current platform status
     * @param transactionPoolNexus   provides transactions to be added to new events
     */
    public PlatformStatusRule(
            @NonNull final Supplier<PlatformStatus> platformStatusSupplier,
            @NonNull final TransactionPoolNexus transactionPoolNexus) {

        this.platformStatusSupplier = Objects.requireNonNull(platformStatusSupplier);
        this.transactionPoolNexus = Objects.requireNonNull(transactionPoolNexus);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEventCreationPermitted() {
        final PlatformStatus currentStatus = platformStatusSupplier.get();

        if (currentStatus == PlatformStatus.FREEZING) {
            return transactionPoolNexus.hasBufferedSignatureTransactions();
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
