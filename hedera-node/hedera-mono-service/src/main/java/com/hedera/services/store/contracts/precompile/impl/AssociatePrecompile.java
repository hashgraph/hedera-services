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

import static com.hedera.services.contracts.ParsingConstants.ADDRESS_PAIR_RAW_TYPE;
import static com.hedera.services.contracts.ParsingConstants.INT;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertLeftPaddedAddressToAccountId;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.decodeFunctionCall;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.contracts.sources.EvmSigsVerifier;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.InfrastructureFactory;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.Association;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.function.UnaryOperator;
import javax.inject.Provider;
import org.apache.tuweni.bytes.Bytes;

public class AssociatePrecompile extends AbstractAssociatePrecompile {
    private static final Function ASSOCIATE_TOKEN_FUNCTION =
            new Function("associateToken(address,address)", INT);
    private static final Bytes ASSOCIATE_TOKEN_SELECTOR =
            Bytes.wrap(ASSOCIATE_TOKEN_FUNCTION.selector());
    private static final ABIType<Tuple> ASSOCIATE_TOKEN_DECODER =
            TypeFactory.create(ADDRESS_PAIR_RAW_TYPE);

    public AssociatePrecompile(
            final WorldLedgers ledgers,
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
        associateOp = decodeAssociation(input, aliasResolver);
        transactionBody = syntheticTxnFactory.createAssociate(associateOp);
        return transactionBody;
    }

    @Override
    public long getGasRequirement(long blockTimestamp) {
        return pricingUtils.computeGasRequirement(blockTimestamp, this, transactionBody);
    }

    public static Association decodeAssociation(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final Tuple decodedArguments =
                decodeFunctionCall(input, ASSOCIATE_TOKEN_SELECTOR, ASSOCIATE_TOKEN_DECODER);

        final var accountID =
                convertLeftPaddedAddressToAccountId(decodedArguments.get(0), aliasResolver);
        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(1));

        return Association.singleAssociation(accountID, tokenID);
    }
}
