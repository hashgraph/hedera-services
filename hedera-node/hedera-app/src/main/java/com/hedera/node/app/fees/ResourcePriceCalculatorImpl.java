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

package com.hedera.node.app.fees;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.SubType;
import com.hedera.node.app.spi.fees.ResourcePriceCalculator;
import com.hedera.node.app.spi.workflows.FunctionalityResourcePrices;
import com.hedera.node.app.store.ReadableStoreFactory;
import com.hedera.node.app.workflows.TransactionInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import javax.inject.Inject;

/**
 * The default implementation of {@link ResourcePriceCalculator}.
 */
public class ResourcePriceCalculatorImpl implements ResourcePriceCalculator {

    private final Instant consensusNow;
    private final TransactionInfo txnInfo;
    private final FeeManager feeManager;
    private final ReadableStoreFactory readableStoreFactory;

    @Inject
    public ResourcePriceCalculatorImpl(
            @NonNull final Instant consensusNow,
            @NonNull final TransactionInfo txnInfo,
            @NonNull final FeeManager feeManager,
            @NonNull final ReadableStoreFactory readableStoreFactory) {
        this.consensusNow = requireNonNull(consensusNow);
        this.txnInfo = requireNonNull(txnInfo);
        this.feeManager = requireNonNull(feeManager);
        this.readableStoreFactory = requireNonNull(readableStoreFactory);
    }

    @NonNull
    @Override
    public FunctionalityResourcePrices resourcePricesFor(
            @NonNull HederaFunctionality functionality, @NonNull SubType subType) {
        return new FunctionalityResourcePrices(
                requireNonNull(feeManager.getFeeData(functionality, consensusNow, subType)),
                feeManager.congestionMultiplierFor(txnInfo.txBody(), functionality, readableStoreFactory));
    }
}
