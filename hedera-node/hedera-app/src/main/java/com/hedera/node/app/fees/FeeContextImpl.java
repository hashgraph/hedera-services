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

package com.hedera.node.app.fees;

import static java.util.Collections.emptyList;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.fees.FeeCalculator;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
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
    private final AccountID payerId;
    private final FeeManager feeManager;
    private final ReadableStoreFactory storeFactory;
    private final Configuration configuration;
    private final Authorizer authorizer;
    private final int numSignatures;

    /**
     * Constructor of {@code FeeContextImpl}
     *
     * @param consensusTime the approximation of consensus time used during ingest
     * @param txInfo the {@link TransactionInfo} of the transaction
     * @param payerKey the {@link Key} of the payer
     * @param payerId the {@link AccountID} of the payer
     * @param feeManager the {@link FeeManager} to generate a {@link FeeCalculator}
     * @param storeFactory the {@link ReadableStoreFactory} to create readable stores
     * @param numSignatures the number of signatures in the transaction
     */
    public FeeContextImpl(
            @NonNull final Instant consensusTime,
            @NonNull final TransactionInfo txInfo,
            @NonNull final Key payerKey,
            @NonNull final AccountID payerId,
            @NonNull final FeeManager feeManager,
            @NonNull final ReadableStoreFactory storeFactory,
            @NonNull final Configuration configuration,
            @NonNull final Authorizer authorizer,
            final int numSignatures) {
        this.consensusTime = consensusTime;
        this.txInfo = txInfo;
        this.payerKey = payerKey;
        this.payerId = payerId;
        this.feeManager = feeManager;
        this.storeFactory = storeFactory;
        this.configuration = configuration;
        this.authorizer = authorizer;
        this.numSignatures = numSignatures;
    }

    @Override
    public @NonNull AccountID payer() {
        return payerId;
    }

    @NonNull
    @Override
    public TransactionBody body() {
        return txInfo.txBody();
    }

    @NonNull
    @Override
    public FeeCalculator feeCalculator(@NonNull SubType subType) {
        // For mono-service compatibility, we treat the sig map size as the number of verifications
        final var numVerifications =
                txInfo.signatureMap().sigPairOrElse(emptyList()).size();
        final var signatureMapSize = SignatureMap.PROTOBUF.measureRecord(txInfo.signatureMap());
        return feeManager.createFeeCalculator(
                txInfo.txBody(),
                payerKey,
                txInfo.functionality(),
                numVerifications,
                signatureMapSize,
                consensusTime,
                subType,
                false,
                storeFactory);
    }

    @NonNull
    @Override
    public <T> T readableStore(@NonNull Class<T> storeInterface) {
        return storeFactory.getStore(storeInterface);
    }

    @Override
    @NonNull
    public Configuration configuration() {
        return configuration;
    }

    @Override
    @NonNull
    public Authorizer authorizer() {
        return authorizer;
    }

    @Override
    public int numTxnSignatures() {
        return numSignatures;
    }
}
