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

package com.hedera.node.app.fees;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.fees.FeeCalculator;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;

/**
 * Simple implementation of {@link FeeContext} without any addition functionality.
 *
 * <p>This class is intended to be used during ingest. In the handle-workflow we use
 * {@link com.hedera.node.app.workflows.handle.HandleContextImpl}, which also implements{@link FeeContext}
 */
public class FeeContextImpl implements FeeContext {

    private final Instant consensusTime;
    private final TransactionInfo txInfo;
    private final Key payerKey;
    private final FeeManager feeManager;
    private final ReadableStoreFactory storeFactory;
    private final Configuration configuration;

    /**
     * Constructor of {@code FeeContextImpl}
     *
     * @param consensusTime the approximation of consensus time used during ingest
     * @param txInfo the {@link TransactionInfo} of the transaction
     * @param payerKey the {@link Key} of the payer
     * @param feeManager the {@link FeeManager} to generate a {@link FeeCalculator}
     * @param storeFactory the {@link ReadableStoreFactory} to create readable stores
     */
    public FeeContextImpl(
            @NonNull final Instant consensusTime,
            @NonNull final TransactionInfo txInfo,
            @NonNull final Key payerKey,
            @NonNull final FeeManager feeManager,
            @NonNull final ReadableStoreFactory storeFactory,
            @Nullable final Configuration configuration) {
        this.consensusTime = consensusTime;
        this.txInfo = txInfo;
        this.payerKey = payerKey;
        this.feeManager = feeManager;
        this.storeFactory = storeFactory;
        this.configuration = configuration;
    }

    @NonNull
    @Override
    public TransactionBody body() {
        return txInfo.txBody();
    }

    @NonNull
    @Override
    public FeeCalculator feeCalculator(@NonNull SubType subType) {
        return feeManager.createFeeCalculator(txInfo, payerKey, 0, consensusTime, subType);
    }

    @NonNull
    @Override
    public <T> T readableStore(@NonNull Class<T> storeInterface) {
        return storeFactory.getStore(storeInterface);
    }

    @Override
    @Nullable
    public Configuration configuration() {
        return configuration;
    }
}
