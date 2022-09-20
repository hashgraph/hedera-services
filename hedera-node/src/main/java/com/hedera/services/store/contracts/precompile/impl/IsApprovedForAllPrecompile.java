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
import static com.hedera.services.contracts.ParsingConstants.ADDRESS_TRIO_RAW_TYPE;
import static com.hedera.services.contracts.ParsingConstants.BOOL;
import static com.hedera.services.contracts.ParsingConstants.INT_BOOL_PAIR;
import static com.hedera.services.ledger.properties.AccountProperty.APPROVE_FOR_ALL_NFTS_ALLOWANCES;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertLeftPaddedAddressToAccountId;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.decodeFunctionCall;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.IsApproveForAllWrapper;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;

public class IsApprovedForAllPrecompile extends AbstractReadOnlyPrecompile {
    private static final Function ERC_IS_APPROVED_FOR_ALL =
            new Function("isApprovedForAll(address,address)", BOOL);
    private static final Bytes ERC_IS_APPROVED_FOR_ALL_SELECTOR =
            Bytes.wrap(ERC_IS_APPROVED_FOR_ALL.selector());
    private static final ABIType<Tuple> ERC_IS_APPROVED_FOR_ALL_DECODER =
            TypeFactory.create(ADDRESS_PAIR_RAW_TYPE);
    private static final Function HAPI_IS_APPROVED_FOR_ALL =
            new Function("isApprovedForAll(address,address,address)", INT_BOOL_PAIR);
    private static final Bytes HAPI_IS_APPROVED_FOR_ALL_SELECTOR =
            Bytes.wrap(HAPI_IS_APPROVED_FOR_ALL.selector());
    private static final ABIType<Tuple> HAPI_IS_APPROVED_FOR_ALL_DECODER =
            TypeFactory.create(ADDRESS_TRIO_RAW_TYPE);
    private IsApproveForAllWrapper isApproveForAllWrapper;

    public IsApprovedForAllPrecompile(
            final TokenID tokenId,
            final SyntheticTxnFactory syntheticTxnFactory,
            final WorldLedgers ledgers,
            final EncodingFacade encoder,
            final PrecompilePricingUtils pricingUtils) {
        super(tokenId, syntheticTxnFactory, ledgers, encoder, pricingUtils);
    }

    public IsApprovedForAllPrecompile(
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
        isApproveForAllWrapper = decodeIsApprovedForAll(nestedInput, tokenId, aliasResolver);
        return super.body(input, aliasResolver);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
        Objects.requireNonNull(
                isApproveForAllWrapper,
                "`body` method should be called before `getSuccessResultsFor`");

        final var accountsLedger = ledgers.accounts();
        var answer = true;
        final var ownerId = isApproveForAllWrapper.owner();
        answer &= accountsLedger.contains(ownerId);
        final var operatorId = isApproveForAllWrapper.operator();
        answer &= accountsLedger.contains(operatorId);
        if (answer) {
            final var allowances =
                    (Set<FcTokenAllowanceId>)
                            accountsLedger.get(ownerId, APPROVE_FOR_ALL_NFTS_ALLOWANCES);
            final var allowanceId =
                    FcTokenAllowanceId.from(isApproveForAllWrapper.tokenId(), operatorId);
            answer &= allowances.contains(allowanceId);
        }
        return tokenId == null
                ? encoder.encodeIsApprovedForAll(SUCCESS.getNumber(), answer)
                : encoder.encodeIsApprovedForAll(answer);
    }

    public static IsApproveForAllWrapper decodeIsApprovedForAll(
            final Bytes input,
            final TokenID impliedTokenId,
            final UnaryOperator<byte[]> aliasResolver) {
        final var offset = impliedTokenId == null ? 1 : 0;

        final Tuple decodedArguments =
                decodeFunctionCall(
                        input,
                        offset == 0
                                ? ERC_IS_APPROVED_FOR_ALL_SELECTOR
                                : HAPI_IS_APPROVED_FOR_ALL_SELECTOR,
                        offset == 0
                                ? ERC_IS_APPROVED_FOR_ALL_DECODER
                                : HAPI_IS_APPROVED_FOR_ALL_DECODER);

        final var tId =
                offset == 0
                        ? impliedTokenId
                        : convertAddressBytesToTokenID(decodedArguments.get(0));

        final var owner =
                convertLeftPaddedAddressToAccountId(decodedArguments.get(offset), aliasResolver);
        final var operator =
                convertLeftPaddedAddressToAccountId(
                        decodedArguments.get(offset + 1), aliasResolver);

        return new IsApproveForAllWrapper(tId, owner, operator);
    }
}
