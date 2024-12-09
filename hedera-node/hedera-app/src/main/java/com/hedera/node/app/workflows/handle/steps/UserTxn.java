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

package com.hedera.node.app.workflows.handle.steps;

import static com.hedera.node.app.workflows.handle.HandleWorkflow.initializeBuilderInfo;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.node.app.spi.workflows.record.StreamBuilder;
import com.hedera.node.app.store.ReadableStoreFactory;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.handle.TransactionType;
import com.hedera.node.app.workflows.handle.record.TokenContextImpl;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.State;
import com.swirlds.state.lifecycle.info.NodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.function.Consumer;

public record UserTxn(
        @NonNull TransactionType type,
        @NonNull HederaFunctionality functionality,
        @NonNull Instant consensusNow,
        @NonNull State state,
        @NonNull TransactionInfo txnInfo,
        @NonNull TokenContextImpl tokenContextImpl,
        @NonNull SavepointStackImpl stack,
        @NonNull PreHandleResult preHandleResult,
        @NonNull ReadableStoreFactory readableStoreFactory,
        @NonNull Configuration config,
        @NonNull NodeInfo creatorInfo) {

    /**
     * Initializes and returns the base stream builder for this user transaction.
     * @param exchangeRates the exchange rates to use
     * @return the initialized stream builder
     */
    public StreamBuilder initBaseBuilder(@NonNull final ExchangeRateSet exchangeRates) {
        return initializeBuilderInfo(baseBuilder(), txnInfo, exchangeRates);
    }

    /**
     * Initializes and returns the base stream builder for this user transaction.
     *
     * @param exchangeRates the exchange rates to use
     * @param builderSpec the builder specification
     * @return the initialized stream builder
     */
    public <T extends StreamBuilder> T initBaseBuilder(
            @NonNull final ExchangeRateSet exchangeRates,
            @NonNull final Class<T> builderType,
            @NonNull final Consumer<T> builderSpec) {
        final var baseBuilder = builderType.cast(initializeBuilderInfo(baseBuilder(), txnInfo, exchangeRates));
        builderSpec.accept(baseBuilder);
        return baseBuilder;
    }

    /**
     * Returns the base stream builder for this user transaction.
     *
     * @return the base stream builder
     */
    public StreamBuilder baseBuilder() {
        return stack.getBaseBuilder(StreamBuilder.class);
    }
}
