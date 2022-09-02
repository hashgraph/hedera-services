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

import static com.hedera.services.contracts.ParsingConstants.ARRAY_BRACKETS;
import static com.hedera.services.contracts.ParsingConstants.BYTES32;
import static com.hedera.services.contracts.ParsingConstants.TOKEN_KEY;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.TOKEN_KEY_DECODER;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.decodeFunctionCall;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.decodeTokenKeys;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.removeBrackets;
import static com.hedera.services.store.contracts.precompile.impl.AbstractTokenUpdatePrecompile.UpdateType.UPDATE_TOKEN_KEYS;
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
import com.hedera.services.store.contracts.precompile.codec.TokenUpdateKeysWrapper;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class TokenUpdateKeysPrecompile extends AbstractTokenUpdatePrecompile {
    private static final Function TOKEN_UPDATE_KEYS_FUNCTION =
            new Function("updateTokenKeys(address," + TOKEN_KEY + ARRAY_BRACKETS + ")");
    private static final Bytes TOKEN_UPDATE_KEYS_SELECTOR =
            Bytes.wrap(TOKEN_UPDATE_KEYS_FUNCTION.selector());
    private static final ABIType<Tuple> TOKEN_UPDATE_KEYS_DECODER =
            TypeFactory.create(
                    "(" + removeBrackets(BYTES32) + "," + TOKEN_KEY_DECODER + ARRAY_BRACKETS + ")");
    TokenUpdateKeysWrapper updateOp;

    public TokenUpdateKeysPrecompile(
            WorldLedgers ledgers,
            ContractAliases aliases,
            EvmSigsVerifier sigsVerifier,
            SideEffectsTracker sideEffectsTracker,
            SyntheticTxnFactory syntheticTxnFactory,
            InfrastructureFactory infrastructureFactory,
            PrecompilePricingUtils pricingUtils) {
        super(
                ledgers,
                aliases,
                sigsVerifier,
                sideEffectsTracker,
                syntheticTxnFactory,
                infrastructureFactory,
                pricingUtils);
    }

    @Override
    public TransactionBody.Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver) {
        updateOp = decodeUpdateTokenKeys(input, aliasResolver);
        transactionBody = syntheticTxnFactory.createTokenUpdateKeys(updateOp);
        return transactionBody;
    }

    @Override
    public void run(MessageFrame frame) {
        Objects.requireNonNull(updateOp);
        validateTrue(updateOp.tokenID() != null, INVALID_TOKEN_ID);
        tokenId = Id.fromGrpcToken(updateOp.tokenID());
        type = UPDATE_TOKEN_KEYS;
        super.run(frame);
    }

    public static TokenUpdateKeysWrapper decodeUpdateTokenKeys(
            Bytes input, UnaryOperator<byte[]> aliasResolver) {
        final Tuple decodedArguments =
                decodeFunctionCall(input, TOKEN_UPDATE_KEYS_SELECTOR, TOKEN_UPDATE_KEYS_DECODER);
        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));
        final var tokenKeys = decodeTokenKeys(decodedArguments.get(1), aliasResolver);
        return new TokenUpdateKeysWrapper(tokenID, tokenKeys);
    }
}
