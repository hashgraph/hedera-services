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

package com.hedera.node.app.service.consensus.impl;

import static com.hedera.hapi.node.base.ResponseCodeEnum.AMOUNT_EXCEEDS_ALLOWANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SPENDER_DOES_NOT_HAVE_ALLOWANCE;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.hapi.node.state.consensus.TopicCryptoAllowance;
import com.hedera.hapi.node.state.consensus.TopicFungibleTokenAllowance;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.ConsensusCustomFee;
import com.hedera.hapi.node.transaction.FixedFee;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.config.data.LedgerConfig;
import edu.umd.cs.findbugs.annotations.NonNull;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Singleton
public class ConsensusCustomFeeAssessor {

    private final ConsensusAllowanceUpdater allowanceUpdater;

    /**
     * Constructs a {@link ConsensusCustomFeeAssessor} instance.
     */
    @Inject
    public ConsensusCustomFeeAssessor(@NonNull final ConsensusAllowanceUpdater allowanceUpdater) {
        // Needed for Dagger injection
        this.allowanceUpdater = requireNonNull(allowanceUpdater);
    }

    public List<CryptoTransferTransactionBody> assessCustomFee(Topic topic, HandleContext context) {
        final List<CryptoTransferTransactionBody> transactionBodies = new ArrayList<>();

        final var payer = context.payer();
        final var topicStore = context.storeFactory().writableStore(WritableTopicStore.class);
        final var tokenStore = context.storeFactory().readableStore(ReadableTokenStore.class);
        final var ledgerConfig = context.configuration().getConfigData(LedgerConfig.class);

        // todo: allowance validation will be changed, when the storage situation is clear.

        // lookup for  hbar allowance
        TopicCryptoAllowance hbarAllowance = null;
        for (final var allowance : topic.cryptoAllowances()) {
            if (payer.equals(allowance.spenderId())) {
                hbarAllowance = allowance;
            }
        }
        // lookup for fungible token allowance
        Map<TokenID, TopicFungibleTokenAllowance> tokenAllowanceMap = new HashMap<>();
        for (final var allowance : topic.tokenAllowances()) {
            if (payer.equals(allowance.spenderId())) {
                tokenAllowanceMap.put(allowance.tokenId(), allowance);
            }
        }

        final var tokenTransfers = new ArrayList<TokenTransferList>();
        List<AccountAmount> hbarTransfers = new ArrayList<>();
        // we need to count the number of balance adjustments,
        // and if needed to split custom fee transfers in to two separate dispatches
        // todo: add explanation for maxTransfers
        final var maxTransfers = ledgerConfig.transfersMaxLen() / 3;
        var transferCounts = 0;

        // build crypto transfer body for the first layer of custom fees,
        // if there is a second layer it will be assessed in crypto transfer handler
        for (ConsensusCustomFee fee : topic.customFees()) {
            // check if payer is treasury or collector
            if(context.payer().equals(fee.feeCollectorAccountId())) {
                continue;
            }

            final var fixedFee = fee.fixedFeeOrThrow();
            if (fixedFee.hasDenominatingTokenId()) {
                final var tokenId = fixedFee.denominatingTokenIdOrThrow();
                final var tokenTreasury = tokenStore.get(tokenId).treasuryAccountIdOrThrow();
                if(context.payer().equals(tokenTreasury)) {
                    continue;
                }

                validateTokenAllowance(tokenAllowanceMap, fixedFee);
                tokenTransfers.add(buildCustomFeeTokenTransferList(payer, fee.feeCollectorAccountId(), fixedFee));
                // update allowance values
                allowanceUpdater.applyFungibleTokenAllowances(topic.topicIdOrThrow(), tokenAllowanceMap.get(tokenId), topicStore);
            } else {
                validateHbarAllowance(hbarAllowance, fixedFee);
                hbarTransfers = mergeTransfers(
                        hbarTransfers, buildCustomFeeHbarTransferList(payer, fee.feeCollectorAccountId(), fixedFee));
                // update allowance values
                allowanceUpdater.applyCryptoAllowances(topic.topicIdOrThrow(), hbarAllowance, topicStore);
            }
            transferCounts++;

            if (transferCounts == maxTransfers) {
                final var syntheticBodyBuilder = tokenTransfers(tokenTransfers.toArray(TokenTransferList[]::new));

                transactionBodies.add(syntheticBodyBuilder
                        .transfers(
                                TransferList.newBuilder().accountAmounts(hbarTransfers.toArray(AccountAmount[]::new)))
                        .build());

                // reset lists and counter
                transferCounts = 0;
                tokenTransfers.clear();
                hbarTransfers.clear();
            }
        }

        if(tokenTransfers.isEmpty() && hbarTransfers.isEmpty()) {
            return transactionBodies;
        }

        final var syntheticBodyBuilder = tokenTransfers(tokenTransfers.toArray(TokenTransferList[]::new));
        transactionBodies.add(syntheticBodyBuilder
                .transfers(TransferList.newBuilder().accountAmounts(hbarTransfers.toArray(AccountAmount[]::new)))
                .build());

        return transactionBodies;
    }

    private void validateTokenAllowance(
            Map<TokenID, TopicFungibleTokenAllowance> tokenAllowanceMap, FixedFee fixedFee) {
        final var allowance = tokenAllowanceMap.get(fixedFee.denominatingTokenId());
        validateTrue(allowance != null, SPENDER_DOES_NOT_HAVE_ALLOWANCE);
        validateTrue(allowance.amountPerMessage() >= fixedFee.amount(), AMOUNT_EXCEEDS_ALLOWANCE);
        validateTrue(allowance.amount() >= fixedFee.amount(), AMOUNT_EXCEEDS_ALLOWANCE);
    }

    private void validateHbarAllowance(TopicCryptoAllowance allowance, FixedFee fixedFee) {
        validateTrue(allowance != null, SPENDER_DOES_NOT_HAVE_ALLOWANCE);
        validateTrue(allowance.amountPerMessage() >= fixedFee.amount(), AMOUNT_EXCEEDS_ALLOWANCE);
        validateTrue(allowance.amount() >= fixedFee.amount(), AMOUNT_EXCEEDS_ALLOWANCE);
    }

    public void adjustAllowance(CryptoTransferTransactionBody syntheticBody) {
        // todo adjust allowance
        // extract the code for updating the allowance amounts from ConsensusApproveAllowanceHandler and reuse it here
    }

    private List<AccountAmount> buildCustomFeeHbarTransferList(
            AccountID payer, AccountID collector, FixedFee fee) {
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

    private TokenTransferList buildCustomFeeTokenTransferList(
            AccountID payer, AccountID collector, FixedFee fee) {
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

    private CryptoTransferTransactionBody.Builder tokenTransfers(
            @NonNull TokenTransferList... tokenTransferLists) {
        if (repeatsTokenId(tokenTransferLists)) {
            final Map<TokenID, TokenTransferList> consolidatedTokenTransfers = new LinkedHashMap<>();
            for (final var tokenTransferList : tokenTransferLists) {
                consolidatedTokenTransfers.merge(
                        tokenTransferList.tokenOrThrow(),
                        tokenTransferList,
                        ConsensusCustomFeeAssessor::mergeTokenTransferLists);
            }
            tokenTransferLists = consolidatedTokenTransfers.values().toArray(TokenTransferList[]::new);
        }
        return CryptoTransferTransactionBody.newBuilder().tokenTransfers(tokenTransferLists);
    }

    private static TokenTransferList mergeTokenTransferLists(
            @NonNull final TokenTransferList from, @NonNull final TokenTransferList to) {
        return from.copyBuilder()
                .transfers(mergeTransfers(from.transfers(), to.transfers()))
                .build();
    }

    private static List<AccountAmount> mergeTransfers(
            @NonNull final List<AccountAmount> from, @NonNull final List<AccountAmount> to) {
        requireNonNull(from);
        requireNonNull(to);
        final Map<AccountID, AccountAmount> consolidated = new LinkedHashMap<>();
        consolidateInto(consolidated, from);
        consolidateInto(consolidated, to);
        return new ArrayList<>(consolidated.values());
    }

    private static void consolidateInto(
            @NonNull final Map<AccountID, AccountAmount> consolidated, @NonNull final List<AccountAmount> transfers) {
        for (final var transfer : transfers) {
            consolidated.merge(transfer.accountID(), transfer, ConsensusCustomFeeAssessor::mergeAdjusts);
        }
    }

    private static AccountAmount mergeAdjusts(@NonNull final AccountAmount from, @NonNull final AccountAmount to) {
        return from.copyBuilder()
                .amount(from.amount() + to.amount())
                .isApproval(from.isApproval() || to.isApproval())
                .build();
    }

    private boolean repeatsTokenId(@NonNull final TokenTransferList[] tokenTransferList) {
        return tokenTransferList.length > 1
                && Arrays.stream(tokenTransferList)
                                .map(TokenTransferList::token)
                                .collect(Collectors.toSet())
                                .size()
                        < tokenTransferList.length;
    }
}
