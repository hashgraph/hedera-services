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

import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.decodeFunctionCall;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.ADDRESS_UINT256_RAW_TYPE;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrueOrRevert;
import static com.hedera.node.app.service.mono.ledger.properties.NftProperty.SPENDER;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.tokenIdFromEvmAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.GetApprovedWrapper;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmGetApprovedPrecompile;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.node.app.service.mono.store.models.NftId;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.math.BigInteger;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;

public class GetApprovedPrecompile extends AbstractReadOnlyPrecompile
        implements EvmGetApprovedPrecompile {

    private static final ABIType<Tuple> HAPI_GET_APPROVED_FUNCTION_DECODER =
            TypeFactory.create(ADDRESS_UINT256_RAW_TYPE);
    private static final Function HAPI_GET_APPROVED_FUNCTION =
            new Function("getApproved(address,uint256)", "(int,int)");
    private static final Bytes HAPI_GET_APPROVED_FUNCTION_SELECTOR =
            Bytes.wrap(HAPI_GET_APPROVED_FUNCTION.selector());

    GetApprovedWrapper<TokenID> getApprovedWrapper;

    public GetApprovedPrecompile(
            final TokenID tokenId,
            final SyntheticTxnFactory syntheticTxnFactory,
            final WorldLedgers ledgers,
            final EncodingFacade encoder,
            final EvmEncodingFacade evmEncoder,
            final PrecompilePricingUtils pricingUtils) {
        super(tokenId, syntheticTxnFactory, ledgers, encoder, evmEncoder, pricingUtils);
    }

    public GetApprovedPrecompile(
            final SyntheticTxnFactory syntheticTxnFactory,
            final WorldLedgers ledgers,
            final EncodingFacade encoder,
            final PrecompilePricingUtils pricingUtils) {
        this(null, syntheticTxnFactory, ledgers, encoder, null, pricingUtils);
    }

    @Override
    public TransactionBody.Builder body(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        getApprovedWrapper = decodeGetApproved(input, tokenId);
        return super.body(input, aliasResolver);
    }

    @Override
    public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
        Objects.requireNonNull(
                getApprovedWrapper, "`body` method should be called before `getSuccessResultsFor`");

        final var nftsLedger = ledgers.nfts();
        final var nftId = NftId.fromGrpc(getApprovedWrapper.token(), getApprovedWrapper.serialNo());
        validateTrueOrRevert(nftsLedger.contains(nftId), INVALID_TOKEN_NFT_SERIAL_NUMBER);
        final var spender = (EntityId) nftsLedger.get(nftId, SPENDER);
        final var canonicalSpender = ledgers.canonicalAddress(spender.toEvmAddress());
        return tokenId == null
                ? encoder.encodeGetApproved(SUCCESS.getNumber(), canonicalSpender)
                : evmEncoder.encodeGetApproved(canonicalSpender);
    }

    public static GetApprovedWrapper<TokenID> decodeGetApproved(
            final Bytes input, final TokenID impliedTokenId) {
        final var offset = impliedTokenId == null ? 1 : 0;
        if (offset == 1) {
            final Tuple decodedArguments =
                    decodeFunctionCall(
                            input,
                            HAPI_GET_APPROVED_FUNCTION_SELECTOR,
                            HAPI_GET_APPROVED_FUNCTION_DECODER);
            final var serialNo = (BigInteger) decodedArguments.get(offset);
            return new GetApprovedWrapper<>(
                    convertAddressBytesToTokenID(decodedArguments.get(0)),
                    serialNo.longValueExact());
        } else {
            final var rawGetApprovedWrapper = EvmGetApprovedPrecompile.decodeGetApproved(input);
            return new GetApprovedWrapper<>(
                    tokenIdFromEvmAddress(rawGetApprovedWrapper.token()),
                    rawGetApprovedWrapper.serialNo());
        }
    }
}
