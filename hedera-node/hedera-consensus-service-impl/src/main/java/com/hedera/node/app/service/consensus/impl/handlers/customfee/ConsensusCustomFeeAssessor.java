/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.consensus.impl.handlers.customfee;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID_IN_CUSTOM_FEES;
import static com.hedera.node.app.service.token.impl.util.TokenHandlerHelper.TokenValidations.REQUIRE_NOT_PAUSED;
import static com.hedera.node.app.service.token.impl.util.TokenHandlerHelper.getIfUsable;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.AssessedCustomFee;
import com.hedera.hapi.node.transaction.FixedCustomFee;
import com.hedera.hapi.node.transaction.FixedFee;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.records.CryptoTransferStreamBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ConsensusCustomFeeAssessor {

    /**
     * Constructs a {@link ConsensusCustomFeeAssessor} instance.
     */
    @Inject
    public ConsensusCustomFeeAssessor() {
        // Needed for Dagger injection
    }

    /**
     * Build and return a list of synthetic crypto transfer transaction bodies, that represents custom fees payments.
     * It will return one body per topic custom fee.
     *
     * @param customFees List of custom fees to be charged
     * @param payer The payer Account ID
     * @return List of synthetic crypto transfer transaction bodies
     */
    public List<CryptoTransferTransactionBody> assessCustomFee(
            @NonNull final List<FixedCustomFee> customFees, @NonNull final AccountID payer) {
        final List<CryptoTransferTransactionBody> transactionBodies = new ArrayList<>();

        // build crypto transfer bodies for the first layer of custom fees,
        // if there is a second or third layer it will be assessed in crypto transfer handler
        for (FixedCustomFee fee : customFees) {
            final var tokenTransfers = new ArrayList<TokenTransferList>();
            List<AccountAmount> hbarTransfers = new ArrayList<>();

            final var fixedFee = fee.fixedFeeOrThrow();
            if (fixedFee.hasDenominatingTokenId()) {
                tokenTransfers.add(buildCustomFeeTokenTransferList(payer, fee.feeCollectorAccountId(), fixedFee));
            } else {
                hbarTransfers = buildCustomFeeHbarTransferList(payer, fee.feeCollectorAccountId(), fixedFee);
            }

            // build the synthetic body
            final var syntheticBodyBuilder =
                    CryptoTransferTransactionBody.newBuilder().tokenTransfers(tokenTransfers);
            transactionBodies.add(syntheticBodyBuilder
                    .transfers(TransferList.newBuilder().accountAmounts(hbarTransfers.toArray(AccountAmount[]::new)))
                    .build());
        }

        return transactionBodies;
    }

    private List<AccountAmount> buildCustomFeeHbarTransferList(AccountID payer, AccountID collector, FixedFee fee) {
        return List.of(
                AccountAmount.newBuilder()
                        .accountID(payer)
                        .amount(-fee.amount())
                        .build(),
                AccountAmount.newBuilder()
                        .accountID(collector)
                        .amount(fee.amount())
                        .build());
    }

    private TokenTransferList buildCustomFeeTokenTransferList(AccountID payer, AccountID collector, FixedFee fee) {
        return TokenTransferList.newBuilder()
                .token(fee.denominatingTokenId())
                .transfers(
                        AccountAmount.newBuilder()
                                .accountID(payer)
                                .amount(-fee.amount())
                                .build(),
                        AccountAmount.newBuilder()
                                .accountID(collector)
                                .amount(fee.amount())
                                .build())
                .build();
    }

    @VisibleForTesting
    public AccountID getTokenTreasury(TokenID tokenId, ReadableTokenStore tokenStore) {
        final var token = getIfUsable(tokenId, tokenStore, REQUIRE_NOT_PAUSED, INVALID_TOKEN_ID_IN_CUSTOM_FEES);
        return token.treasuryAccountIdOrThrow();
    }

    public List<AssessedCustomFee> assessedCustomFees(AccountID payer, CryptoTransferStreamBuilder streamBuilder) {

        final var assessedCustomFees = new ArrayList<AssessedCustomFee>();
        final var assessedFeeBuilder = AssessedCustomFee.newBuilder().effectivePayerAccountId(payer);
        final var body = streamBuilder.transactionBody().cryptoTransferOrThrow();

        // check for hbar transfers
        if (body.transfers() != null) {
            for (final var amount : body.transfers().accountAmounts()) {
                if (amount.amount() > 0) {
                    assessedFeeBuilder.amount(amount.amount());
                    assessedFeeBuilder.feeCollectorAccountId(amount.accountID());
                }
            }
        }

        // check for token transfers
        for (final var tokenTransferList : body.tokenTransfers()) {
            assessedFeeBuilder.tokenId(tokenTransferList.token());
            for (final var transfer : tokenTransferList.transfers()) {
                if (transfer.amount() > 0) {
                    assessedFeeBuilder.amount(transfer.amount());
                    assessedFeeBuilder.feeCollectorAccountId(transfer.accountID());
                }
            }
        }

        assessedCustomFees.add(assessedFeeBuilder.build());
        // check if any nested custom fees are charged during the transfer and add them in to the list
        assessedCustomFees.addAll(streamBuilder.getAssessedCustomFees());

        return assessedCustomFees;
    }
}
