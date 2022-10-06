/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.store.contracts.precompile.impl;

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.InfrastructureFactory;
import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hederahashgraph.api.proto.java.TransactionBody;

public abstract class AbstractWritePrecompile implements Precompile {
    protected static final String FAILURE_MESSAGE = "Invalid full prefix for %s precompile!";
    protected final WorldLedgers ledgers;
    protected final SideEffectsTracker sideEffects;
    protected final SyntheticTxnFactory syntheticTxnFactory;
    protected final InfrastructureFactory infrastructureFactory;
    protected final PrecompilePricingUtils pricingUtils;
    protected TransactionBody.Builder transactionBody;

    protected AbstractWritePrecompile(
            final WorldLedgers ledgers,
            final SideEffectsTracker sideEffects,
            final SyntheticTxnFactory syntheticTxnFactory,
            final InfrastructureFactory infrastructureFactory,
            final PrecompilePricingUtils pricingUtils) {
        this.ledgers = ledgers;
        this.sideEffects = sideEffects;
        this.syntheticTxnFactory = syntheticTxnFactory;
        this.infrastructureFactory = infrastructureFactory;
        this.pricingUtils = pricingUtils;
    }

    @Override
    public long getGasRequirement(long blockTimestamp) {
        return pricingUtils.computeGasRequirement(blockTimestamp, this, transactionBody);
    }
}
