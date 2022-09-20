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

import static com.hedera.services.contracts.ParsingConstants.BYTES32;
import static com.hedera.services.contracts.ParsingConstants.EXPIRY;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.EXPIRY_DECODER;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.decodeFunctionCall;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.decodeTokenExpiry;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.removeBrackets;
import static com.hedera.services.store.contracts.precompile.impl.AbstractTokenUpdatePrecompile.UpdateType.UPDATE_TOKEN_EXPIRY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;

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
import com.hedera.services.store.contracts.precompile.codec.TokenUpdateExpiryInfoWrapper;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.TransactionBody.Builder;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class UpdateTokenExpiryInfoPrecompile extends AbstractTokenUpdatePrecompile {
    private static final Function TOKEN_UPDATE_EXPIRY_INFO_FUNCTION =
            new Function("updateTokenExpiryInfo(address," + EXPIRY + ")");
    private static final Bytes TOKEN_UPDATE_EXPIRY_INFO_SELECTOR =
            Bytes.wrap(TOKEN_UPDATE_EXPIRY_INFO_FUNCTION.selector());
    private static final ABIType<Tuple> TOKEN_UPDATE_EXPIRY_INFO_DECODER =
            TypeFactory.create("(" + removeBrackets(BYTES32) + "," + EXPIRY_DECODER + ")");
    private TokenUpdateExpiryInfoWrapper updateExpiryInfoOp;

    public UpdateTokenExpiryInfoPrecompile(
            WorldLedgers ledgers,
            ContractAliases aliases,
            EvmSigsVerifier sigsVerifier,
            SideEffectsTracker sideEffectsTracker,
            SyntheticTxnFactory syntheticTxnFactory,
            InfrastructureFactory infrastructureFactory,
            PrecompilePricingUtils precompilePricingUtils) {
        super(
                ledgers,
                aliases,
                sigsVerifier,
                sideEffectsTracker,
                syntheticTxnFactory,
                infrastructureFactory,
                precompilePricingUtils);
    }

    @Override
    public Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver) {
        updateExpiryInfoOp = decodeUpdateTokenExpiryInfo(input, aliasResolver);
        transactionBody = syntheticTxnFactory.createTokenUpdateExpiryInfo(updateExpiryInfoOp);
        return transactionBody;
    }

    @Override
    public void run(MessageFrame frame) {
        Objects.requireNonNull(updateExpiryInfoOp);
        validateTrue(updateExpiryInfoOp.tokenID() != null, INVALID_TOKEN_ID);
        tokenId = Id.fromGrpcToken(updateExpiryInfoOp.tokenID());
        type = UPDATE_TOKEN_EXPIRY;
        super.run(frame);
    }

    public static TokenUpdateExpiryInfoWrapper decodeUpdateTokenExpiryInfo(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final Tuple decodedArguments =
                decodeFunctionCall(
                        input, TOKEN_UPDATE_EXPIRY_INFO_SELECTOR, TOKEN_UPDATE_EXPIRY_INFO_DECODER);

        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));
        final Tuple tokenExpiryStruct = decodedArguments.get(1);
        final var tokenExpiry = decodeTokenExpiry(tokenExpiryStruct, aliasResolver);
        return new TokenUpdateExpiryInfoWrapper(tokenID, tokenExpiry);
    }
}
