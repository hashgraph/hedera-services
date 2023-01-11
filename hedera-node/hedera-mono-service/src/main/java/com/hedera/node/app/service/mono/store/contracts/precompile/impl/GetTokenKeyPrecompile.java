/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.ADDRESS_UINT256_RAW_TYPE;
import static com.hedera.node.app.service.mono.exceptions.ValidationUtils.validateTrue;
import static com.hedera.node.app.service.mono.state.merkle.MerkleToken.convertToEvmKey;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.decodeFunctionCall;
import static com.hedera.node.app.service.mono.utils.MiscUtils.asKeyUnchecked;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hedera.node.app.service.mono.ledger.properties.TokenProperty;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.GetTokenKeyWrapper;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.math.BigInteger;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;

public class GetTokenKeyPrecompile extends AbstractReadOnlyPrecompile {
    private static final Function GET_TOKEN_KEYS_FUNCTION =
            new Function("getTokenKey(address,uint256)");
    private static final Bytes GET_TOKEN_KEYS_SELECTOR =
            Bytes.wrap(GET_TOKEN_KEYS_FUNCTION.selector());
    private static final ABIType<Tuple> GET_TOKEN_KEYS_DECODER =
            TypeFactory.create(ADDRESS_UINT256_RAW_TYPE);
    private TokenProperty keyType;

    public GetTokenKeyPrecompile(
            final TokenID tokenId,
            final SyntheticTxnFactory syntheticTxnFactory,
            final WorldLedgers ledgers,
            final EncodingFacade encoder,
            final EvmEncodingFacade evmEncoder,
            final PrecompilePricingUtils pricingUtils) {
        super(tokenId, syntheticTxnFactory, ledgers, encoder, evmEncoder, pricingUtils);
    }

    @Override
    public TransactionBody.Builder body(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final var getTokenKeyWrapper = decodeGetTokenKey(input);
        tokenId = getTokenKeyWrapper.tokenID();
        keyType = getTokenKeyWrapper.tokenKeyType();
        return super.body(input, aliasResolver);
    }

    @Override
    public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
        validateTrue(ledgers.tokens().exists(tokenId), ResponseCodeEnum.INVALID_TOKEN_ID);
        Objects.requireNonNull(keyType);
        final JKey key = (JKey) ledgers.tokens().get(tokenId, keyType);
        validateTrue(key != null, ResponseCodeEnum.KEY_NOT_PROVIDED);
        final var evmKey = convertToEvmKey(asKeyUnchecked(key));
        return evmEncoder.encodeGetTokenKey(evmKey);
    }

    public static GetTokenKeyWrapper decodeGetTokenKey(final Bytes input) {
        final Tuple decodedArguments =
                decodeFunctionCall(input, GET_TOKEN_KEYS_SELECTOR, GET_TOKEN_KEYS_DECODER);
        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));
        final var tokenType = ((BigInteger) decodedArguments.get(1)).longValue();
        return new GetTokenKeyWrapper(tokenID, tokenType);
    }
}
