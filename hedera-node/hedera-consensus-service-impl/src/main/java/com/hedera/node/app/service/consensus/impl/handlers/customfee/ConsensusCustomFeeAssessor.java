/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.ConsensusCustomFee;
import com.hedera.hapi.node.transaction.FixedFee;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.spi.workflows.HandleContext;
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
     * @param topic The topic
     * @param context The transaction handle context
     * @return List of synthetic crypto transfer transaction bodies
     */
    public List<CryptoTransferTransactionBody> assessCustomFee(
            @NonNull final Topic topic, @NonNull final HandleContext context) {
        final List<CryptoTransferTransactionBody> transactionBodies = new ArrayList<>();
        final var payer = context.payer();
        final var tokenStore = context.storeFactory().readableStore(ReadableTokenStore.class);

        // build crypto transfer bodies for the first layer of custom fees,
        // if there is a second or third layer it will be assessed in crypto transfer handler
        for (ConsensusCustomFee fee : topic.customFees()) {

            // check if payer is collector
            if (context.payer().equals(fee.feeCollectorAccountId())) {
                continue;
            }

            final var tokenTransfers = new ArrayList<TokenTransferList>();
            List<AccountAmount> hbarTransfers = new ArrayList<>();

            final var fixedFee = fee.fixedFeeOrThrow();
            if (fixedFee.hasDenominatingTokenId()) {
                final var tokenId = fixedFee.denominatingTokenIdOrThrow();
                final var tokenTreasury = getTokenTreasury(tokenId, tokenStore);
                // check if payer is treasury
                if (context.payer().equals(tokenTreasury)) {
                    continue;
                }
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
        final var token = tokenStore.get(tokenId);
        validateTrue(token != null, ResponseCodeEnum.INVALID_TOKEN_ID_IN_CUSTOM_FEES);
        return token.treasuryAccountIdOrThrow();
    }
}
