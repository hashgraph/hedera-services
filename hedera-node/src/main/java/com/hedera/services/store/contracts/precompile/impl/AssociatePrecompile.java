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
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.contracts.sources.EvmSigsVerifier;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.InfrastructureFactory;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.function.UnaryOperator;
import javax.inject.Provider;
import org.apache.tuweni.bytes.Bytes;

public class AssociatePrecompile extends AbstractAssociatePrecompile {
    public AssociatePrecompile(
            final WorldLedgers ledgers,
            final DecodingFacade decoder,
            final ContractAliases aliases,
            final EvmSigsVerifier sigsVerifier,
            final SideEffectsTracker sideEffects,
            final SyntheticTxnFactory syntheticTxnFactory,
            final InfrastructureFactory infrastructureFactory,
            final PrecompilePricingUtils pricingUtils,
            final Provider<FeeCalculator> feeCalculator,
            final StateView currentView) {
        super(
                ledgers,
                decoder,
                aliases,
                sigsVerifier,
                sideEffects,
                syntheticTxnFactory,
                infrastructureFactory,
                pricingUtils,
                feeCalculator,
                currentView);
    }

    @Override
    public TransactionBody.Builder body(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        associateOp = decoder.decodeAssociation(input, aliasResolver);
        transactionBody = syntheticTxnFactory.createAssociate(associateOp);
        return transactionBody;
    }

    @Override
    public long getGasRequirement(long blockTimestamp) {
        return pricingUtils.computeGasRequirement(blockTimestamp, this, transactionBody);
    }
}
