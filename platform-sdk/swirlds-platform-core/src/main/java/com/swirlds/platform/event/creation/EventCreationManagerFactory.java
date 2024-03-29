/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event.creation;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.stream.Signer;
import com.swirlds.platform.event.creation.rules.AggregateEventCreationRules;
import com.swirlds.platform.event.creation.rules.BackpressureRule;
import com.swirlds.platform.event.creation.rules.EventCreationRule;
import com.swirlds.platform.event.creation.rules.MaximumRateRule;
import com.swirlds.platform.event.creation.rules.PlatformStatusRule;
import com.swirlds.platform.event.creation.tipset.TipsetEventCreator;
import com.swirlds.platform.eventhandling.TransactionPool;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.status.PlatformStatus;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.Random;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * A factory for creating {@link DefaultEventCreationManager} instances.
 */
public final class EventCreationManagerFactory {

    private EventCreationManagerFactory() {}

    /**
     * Create a new event creation manager.
     *
     * @param platformContext        the platform's context
     * @param signer                 can sign with this node's key
     * @param addressBook            the current address book
     * @param selfId                 the ID of this node
     * @param appVersion             the current application version
     * @param transactionPool        provides transactions to be added to new events
     * @param getIntakeQueueSize     provides the size of the event intake queue
     * @param platformStatusSupplier provides the current platform status
     * @param latestReconnectRound   provides the latest reconnect round
     * @return a new event creation manager
     */
    @NonNull
    public static EventCreationManager buildEventCreationManager(
            @NonNull final PlatformContext platformContext,
            @NonNull final Signer signer,
            @NonNull final AddressBook addressBook,
            @NonNull final NodeId selfId,
            @NonNull final SoftwareVersion appVersion,
            @NonNull final TransactionPool transactionPool,
            @NonNull final LongSupplier getIntakeQueueSize,
            @NonNull final Supplier<PlatformStatus> platformStatusSupplier,
            @NonNull final Supplier<Long> latestReconnectRound) {

        Objects.requireNonNull(platformContext);
        Objects.requireNonNull(signer);
        Objects.requireNonNull(addressBook);
        Objects.requireNonNull(selfId);
        Objects.requireNonNull(appVersion);
        Objects.requireNonNull(transactionPool);
        Objects.requireNonNull(platformStatusSupplier);
        Objects.requireNonNull(latestReconnectRound);

        final EventCreator eventCreator = new TipsetEventCreator(
                platformContext,
                new Random() /* does not need to be cryptographically secure */,
                signer,
                addressBook,
                selfId,
                appVersion,
                transactionPool);

        final EventCreationRule eventCreationRules = AggregateEventCreationRules.of(
                new MaximumRateRule(platformContext),
                new BackpressureRule(platformContext, getIntakeQueueSize),
                new PlatformStatusRule(platformStatusSupplier, transactionPool));

        return new DefaultEventCreationManager(platformContext, eventCreator, eventCreationRules);
    }
}
