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

import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.IsApproveForAllWrapper;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.tuweni.bytes.Bytes;

import java.util.Set;
import java.util.function.UnaryOperator;

import static com.hedera.services.ledger.properties.AccountProperty.APPROVE_FOR_ALL_NFTS_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class IsApprovedForAllPrecompile extends AbstractReadOnlyPrecompile {
    private IsApproveForAllWrapper isApproveForAllWrapper;

    public IsApprovedForAllPrecompile(
            final TokenID tokenId,
            final SyntheticTxnFactory syntheticTxnFactory,
            final WorldLedgers ledgers,
            final EncodingFacade encoder,
            final DecodingFacade decoder,
            final PrecompilePricingUtils pricingUtils) {
        super(tokenId, syntheticTxnFactory, ledgers, encoder, decoder, pricingUtils);
    }

    public IsApprovedForAllPrecompile(
            final SyntheticTxnFactory syntheticTxnFactory,
            final WorldLedgers ledgers,
            final EncodingFacade encoder,
            final DecodingFacade decoder,
            final PrecompilePricingUtils pricingUtils) {
        this(null, syntheticTxnFactory, ledgers, encoder, decoder, pricingUtils);
    }

    @Override
    public TransactionBody.Builder body(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final var nestedInput = tokenId == null ? input : input.slice(24);
        isApproveForAllWrapper =
                decoder.decodeIsApprovedForAll(nestedInput, tokenId, aliasResolver);
        return super.body(input, aliasResolver);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
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
}
