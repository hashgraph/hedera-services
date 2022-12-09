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
package com.hedera.node.app.service.mono.store.contracts.precompile.impl;

import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.ADDRESS_PAIR_RAW_TYPE;
import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.INT;
import static com.hedera.node.app.service.mono.exceptions.ValidationUtils.validateTrue;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.convertLeftPaddedAddressToAccountId;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.decodeFunctionCall;
import static com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.GRANT_KYC;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.GrantRevokeKycWrapper;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.contracts.sources.EvmSigsVerifier;
import com.hedera.node.app.service.mono.ledger.accounts.ContractAliases;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.contracts.precompile.InfrastructureFactory;
import com.hedera.node.app.service.mono.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hedera.node.app.service.mono.txns.token.GrantKycLogic;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class GrantKycPrecompile extends AbstractGrantRevokeKycPrecompile {
    private static final Function GRANT_TOKEN_KYC_FUNCTION =
            new Function("grantTokenKyc(address,address)", INT);
    private static final Bytes GRANT_TOKEN_KYC_FUNCTION_SELECTOR =
            Bytes.wrap(GRANT_TOKEN_KYC_FUNCTION.selector());
    private static final ABIType<Tuple> GRANT_TOKEN_KYC_FUNCTION_DECODER =
            TypeFactory.create(ADDRESS_PAIR_RAW_TYPE);

    public GrantKycPrecompile(
            final WorldLedgers ledgers,
            final ContractAliases aliases,
            final EvmSigsVerifier sigsVerifier,
            final SideEffectsTracker sideEffects,
            final SyntheticTxnFactory syntheticTxnFactory,
            final InfrastructureFactory infrastructureFactory,
            final PrecompilePricingUtils pricingUtils) {
        super(
                ledgers,
                aliases,
                sigsVerifier,
                sideEffects,
                syntheticTxnFactory,
                infrastructureFactory,
                pricingUtils);
    }

    @Override
    public void run(final MessageFrame frame) {
        initialise(frame);

        final var grantKycLogic = infrastructureFactory.newGrantKycLogic(accountStore, tokenStore);
        executeForGrant(grantKycLogic, tokenId, accountId);
    }

    @Override
    public TransactionBody.Builder body(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        grantRevokeOp = decodeGrantTokenKyc(input, aliasResolver);
        transactionBody = syntheticTxnFactory.createGrantKyc(grantRevokeOp);
        return transactionBody;
    }

    @Override
    public long getMinimumFeeInTinybars(final Timestamp consensusTime) {
        Objects.requireNonNull(grantRevokeOp);
        return pricingUtils.getMinimumPriceInTinybars(GRANT_KYC, consensusTime);
    }

    public static GrantRevokeKycWrapper<TokenID, AccountID> decodeGrantTokenKyc(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final Tuple decodedArguments =
                decodeFunctionCall(
                        input, GRANT_TOKEN_KYC_FUNCTION_SELECTOR, GRANT_TOKEN_KYC_FUNCTION_DECODER);

        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));
        final var accountID =
                convertLeftPaddedAddressToAccountId(decodedArguments.get(1), aliasResolver);

        return new GrantRevokeKycWrapper<>(tokenID, accountID);
    }

    private void executeForGrant(
            final GrantKycLogic grantKycLogic, final Id tokenId, final Id accountId) {
        validateLogic(grantKycLogic.validate(transactionBody.build()));
        grantKycLogic.grantKyc(tokenId, accountId);
    }

    private void validateLogic(final ResponseCodeEnum validity) {
        validateTrue(validity == OK, validity);
    }
}
