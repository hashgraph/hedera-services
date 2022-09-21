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

import static com.hedera.services.contracts.ParsingConstants.ADDRESS_UINT256_RAW_TYPE;
import static com.hedera.services.contracts.ParsingConstants.INT;
import static com.hedera.services.contracts.ParsingConstants.UINT256;
import static com.hedera.services.exceptions.ValidationUtils.validateTrueOrRevert;
import static com.hedera.services.ledger.properties.NftProperty.SPENDER;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.decodeFunctionCall;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.GetApprovedWrapper;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.NftId;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.math.BigInteger;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;

public class GetApprovedPrecompile extends AbstractReadOnlyPrecompile {
    private static final Function ERC_GET_APPROVED_FUNCTION =
            new Function("getApproved(uint256)", INT);
    private static final Bytes ERC_GET_APPROVED_FUNCTION_SELECTOR =
            Bytes.wrap(ERC_GET_APPROVED_FUNCTION.selector());
    private static final ABIType<Tuple> ERC_GET_APPROVED_FUNCTION_DECODER =
            TypeFactory.create(UINT256);
    private static final Function HAPI_GET_APPROVED_FUNCTION =
            new Function("getApproved(address,uint256)", "(int,int)");
    private static final Bytes HAPI_GET_APPROVED_FUNCTION_SELECTOR =
            Bytes.wrap(HAPI_GET_APPROVED_FUNCTION.selector());
    private static final ABIType<Tuple> HAPI_GET_APPROVED_FUNCTION_DECODER =
            TypeFactory.create(ADDRESS_UINT256_RAW_TYPE);
    GetApprovedWrapper getApprovedWrapper;

    public GetApprovedPrecompile(
            final TokenID tokenId,
            final SyntheticTxnFactory syntheticTxnFactory,
            final WorldLedgers ledgers,
            final EncodingFacade encoder,
            final PrecompilePricingUtils pricingUtils) {
        super(tokenId, syntheticTxnFactory, ledgers, encoder, pricingUtils);
    }

    public GetApprovedPrecompile(
            final SyntheticTxnFactory syntheticTxnFactory,
            final WorldLedgers ledgers,
            final EncodingFacade encoder,
            final PrecompilePricingUtils pricingUtils) {
        this(null, syntheticTxnFactory, ledgers, encoder, pricingUtils);
    }

    @Override
    public TransactionBody.Builder body(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final var nestedInput = tokenId == null ? input : input.slice(24);
        getApprovedWrapper = decodeGetApproved(nestedInput, tokenId);
        return super.body(input, aliasResolver);
    }

    @Override
    public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
        Objects.requireNonNull(
                getApprovedWrapper, "`body` method should be called before `getSuccessResultsFor`");

        final var nftsLedger = ledgers.nfts();
        final var nftId =
                NftId.fromGrpc(getApprovedWrapper.tokenId(), getApprovedWrapper.serialNo());
        validateTrueOrRevert(nftsLedger.contains(nftId), INVALID_TOKEN_NFT_SERIAL_NUMBER);
        final var spender = (EntityId) nftsLedger.get(nftId, SPENDER);
        final var canonicalSpender = ledgers.canonicalAddress(spender.toEvmAddress());
        return tokenId == null
                ? encoder.encodeGetApproved(SUCCESS.getNumber(), canonicalSpender)
                : encoder.encodeGetApproved(canonicalSpender);
    }

    public static GetApprovedWrapper decodeGetApproved(
            final Bytes input, final TokenID impliedTokenId) {
        final var offset = impliedTokenId == null ? 1 : 0;

        final Tuple decodedArguments =
                decodeFunctionCall(
                        input,
                        offset == 0
                                ? ERC_GET_APPROVED_FUNCTION_SELECTOR
                                : HAPI_GET_APPROVED_FUNCTION_SELECTOR,
                        offset == 0
                                ? ERC_GET_APPROVED_FUNCTION_DECODER
                                : HAPI_GET_APPROVED_FUNCTION_DECODER);

        final var tId =
                offset == 0
                        ? impliedTokenId
                        : convertAddressBytesToTokenID(decodedArguments.get(0));

        final var serialNo = (BigInteger) decodedArguments.get(offset);
        return new GetApprovedWrapper(tId, serialNo.longValueExact());
    }
}
