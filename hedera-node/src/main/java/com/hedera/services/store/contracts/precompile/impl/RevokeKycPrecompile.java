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
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertLeftPaddedAddressToAccountId;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.decodeFunctionCall;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.REVOKE_KYC;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.contracts.sources.EvmSigsVerifier;
import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.InfrastructureFactory;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.GrantRevokeKycWrapper;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.token.RevokeKycLogic;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class RevokeKycPrecompile extends AbstractGrantRevokeKycPrecompile {
    private static final Function REVOKE_TOKEN_KYC_FUNCTION =
            new Function("revokeTokenKyc(address,address)", INT);
    private static final Bytes REVOKE_TOKEN_KYC_FUNCTION_SELECTOR =
            Bytes.wrap(REVOKE_TOKEN_KYC_FUNCTION.selector());
    private static final ABIType<Tuple> REVOKE_TOKEN_KYC_FUNCTION_DECODER =
            TypeFactory.create(ADDRESS_PAIR_RAW_TYPE);

    public RevokeKycPrecompile(
            WorldLedgers ledgers,
            ContractAliases aliases,
            EvmSigsVerifier sigsVerifier,
            SideEffectsTracker sideEffects,
            SyntheticTxnFactory syntheticTxnFactory,
            InfrastructureFactory infrastructureFactory,
            PrecompilePricingUtils pricingUtils) {
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
    public void run(MessageFrame frame) {
        initialise(frame);

        final var revokeKycLogic =
                infrastructureFactory.newRevokeKycLogic(accountStore, tokenStore);
        executeForRevoke(revokeKycLogic, tokenId, accountId);
    }

    @Override
    public TransactionBody.Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver) {
        grantRevokeOp = decodeRevokeTokenKyc(input, aliasResolver);
        transactionBody = syntheticTxnFactory.createRevokeKyc(grantRevokeOp);
        return transactionBody;
    }

    @Override
    public long getMinimumFeeInTinybars(Timestamp consensusTime) {
        Objects.requireNonNull(grantRevokeOp);
        return pricingUtils.getMinimumPriceInTinybars(REVOKE_KYC, consensusTime);
    }

    public static GrantRevokeKycWrapper decodeRevokeTokenKyc(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final Tuple decodedArguments =
                decodeFunctionCall(
                        input,
                        REVOKE_TOKEN_KYC_FUNCTION_SELECTOR,
                        REVOKE_TOKEN_KYC_FUNCTION_DECODER);

        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));
        final var accountID =
                convertLeftPaddedAddressToAccountId(decodedArguments.get(1), aliasResolver);

        return new GrantRevokeKycWrapper(tokenID, accountID);
    }

    private void executeForRevoke(RevokeKycLogic revokeKycLogic, Id tokenId, Id accountId) {
        validateLogic(revokeKycLogic.validate(transactionBody.build()));
        revokeKycLogic.revokeKyc(tokenId, accountId);
    }

    private void validateLogic(ResponseCodeEnum validity) {
        validateTrue(validity == OK, validity);
    }
}
