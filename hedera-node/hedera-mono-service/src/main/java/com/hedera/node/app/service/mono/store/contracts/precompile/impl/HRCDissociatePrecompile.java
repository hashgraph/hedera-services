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

package com.hedera.node.app.service.mono.store.contracts.precompile.impl;

import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.contracts.sources.EvmSigsVerifier;
import com.hedera.node.app.service.mono.fees.FeeCalculator;
import com.hedera.node.app.service.mono.ledger.accounts.ContractAliases;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.contracts.precompile.InfrastructureFactory;
import com.hedera.node.app.service.mono.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.Dissociation;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hedera.node.app.service.mono.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody.Builder;
import java.util.Objects;
import java.util.function.UnaryOperator;
import javax.inject.Provider;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

/**
 * Precompile to handle the IHRC facade function for dissociate() function.
 */
public class HRCDissociatePrecompile extends AbstractDissociatePrecompile {

    private final TokenID tokenID;
    private final AccountID callerAccountID;

    public HRCDissociatePrecompile(
            final TokenID tokenID,
            final Address callerAccount,
            final WorldLedgers ledgers,
            final ContractAliases aliases,
            final EvmSigsVerifier sigsVerifier,
            final SideEffectsTracker sideEffects,
            final SyntheticTxnFactory syntheticTxnFactory,
            final InfrastructureFactory infrastructureFactory,
            final PrecompilePricingUtils pricingUtils,
            final Provider<FeeCalculator> feeCalculator) {
        super(
                ledgers,
                aliases,
                sigsVerifier,
                sideEffects,
                syntheticTxnFactory,
                infrastructureFactory,
                pricingUtils,
                feeCalculator);

        this.tokenID = tokenID;
        this.callerAccountID = EntityIdUtils.accountIdFromEvmAddress(Objects.requireNonNull(callerAccount));
    }

    @Override
    public Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver) {
        dissociateOp = Dissociation.singleDissociation(Objects.requireNonNull(callerAccountID), tokenID);
        accountId = Id.fromGrpcAccount(callerAccountID);
        transactionBody = syntheticTxnFactory.createDissociate(dissociateOp);
        return transactionBody;
    }

    @Override
    public long getGasRequirement(long blockTimestamp) {
        return pricingUtils.computeGasRequirement(blockTimestamp, this, transactionBody);
    }
}
