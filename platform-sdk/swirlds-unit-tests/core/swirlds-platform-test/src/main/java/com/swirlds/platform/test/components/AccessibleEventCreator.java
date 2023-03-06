/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.components;

import com.swirlds.common.stream.Signer;
import com.swirlds.common.system.NodeId;
import com.swirlds.platform.components.EventCreationRules;
import com.swirlds.platform.components.EventCreator;
import com.swirlds.platform.components.EventHandler;
import com.swirlds.platform.components.EventMapper;
import com.swirlds.platform.components.TransactionPool;
import com.swirlds.platform.components.TransactionSupplier;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.internal.EventImpl;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * This class has identical behavior as {@link EventCreator} but makes a variety of methods public.
 */
public class AccessibleEventCreator extends EventCreator {

    public AccessibleEventCreator(
            final NodeId selfId,
            final EventMapper eventMapper,
            final Signer signer,
            final Supplier<GraphGenerations> graphGenerationsSupplier,
            final TransactionSupplier transactionSupplier,
            final EventHandler newEventHandler,
            final TransactionPool transactionPool,
            final BooleanSupplier isInFreeze,
            final EventCreationRules eventCreationRules) {

        super(
                selfId,
                signer,
                graphGenerationsSupplier,
                transactionSupplier,
                newEventHandler,
                eventMapper,
                eventMapper,
                transactionPool,
                isInFreeze,
                eventCreationRules);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void logEventCreation(final EventImpl event) {
        super.logEventCreation(event);
    }
}
