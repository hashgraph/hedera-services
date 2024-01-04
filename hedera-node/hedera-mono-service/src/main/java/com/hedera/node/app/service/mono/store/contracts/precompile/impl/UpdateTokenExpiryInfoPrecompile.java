/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.decodeFunctionCall;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.decodeTokenExpiry;
import static com.hedera.node.app.service.mono.store.contracts.precompile.impl.AbstractTokenUpdatePrecompile.UpdateType.UPDATE_TOKEN_EXPIRY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.contracts.sources.EvmSigsVerifier;
import com.hedera.node.app.service.mono.ledger.accounts.ContractAliases;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants;
import com.hedera.node.app.service.mono.store.contracts.precompile.InfrastructureFactory;
import com.hedera.node.app.service.mono.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.TokenUpdateExpiryInfoWrapper;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.KeyActivationUtils;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hederahashgraph.api.proto.java.TransactionBody.Builder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class UpdateTokenExpiryInfoPrecompile extends AbstractTokenUpdatePrecompile {
    private final int functionId;
    private TokenUpdateExpiryInfoWrapper updateExpiryInfoOp;

    public UpdateTokenExpiryInfoPrecompile(
            WorldLedgers ledgers,
            ContractAliases aliases,
            EvmSigsVerifier sigsVerifier,
            SideEffectsTracker sideEffectsTracker,
            SyntheticTxnFactory syntheticTxnFactory,
            InfrastructureFactory infrastructureFactory,
            PrecompilePricingUtils precompilePricingUtils,
            final int functionId) {
        super(
                KeyActivationUtils::validateKey,
                KeyActivationUtils::validateLegacyKey,
                ledgers,
                aliases,
                sigsVerifier,
                sideEffectsTracker,
                syntheticTxnFactory,
                infrastructureFactory,
                precompilePricingUtils);
        this.functionId = functionId;
    }

    @Override
    public Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver) {
        final var updateExpiryInfoAbi =
                switch (functionId) {
                    case AbiConstants.ABI_ID_UPDATE_TOKEN_EXPIRY_INFO -> SystemContractAbis.UPDATE_TOKEN_EXPIRY_INFO_V1;
                    case AbiConstants.ABI_ID_UPDATE_TOKEN_EXPIRY_INFO_V2 -> SystemContractAbis
                            .UPDATE_TOKEN_EXPIRY_INFO_V2;
                    default -> throw new IllegalArgumentException("invalid selector to updateExpiryInfo precompile");
                };

        updateExpiryInfoOp = getTokenUpdateExpiryInfoWrapper(input, aliasResolver, updateExpiryInfoAbi);
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

    public static TokenUpdateExpiryInfoWrapper getTokenUpdateExpiryInfoWrapper(
            Bytes input, UnaryOperator<byte[]> aliasResolver, @NonNull final SystemContractAbis abi) {
        final Tuple decodedArguments = decodeFunctionCall(input, abi.selector, abi.decoder);

        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));
        final Tuple tokenExpiryStruct = decodedArguments.get(1);
        final var tokenExpiry = decodeTokenExpiry(tokenExpiryStruct, aliasResolver);
        return new TokenUpdateExpiryInfoWrapper(tokenID, tokenExpiry);
    }
}
