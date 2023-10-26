/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.handlers;

import static com.hedera.node.app.service.token.impl.comparator.TokenComparators.TOKEN_TRANSFER_LIST_COMPARATOR;
import static com.hedera.node.app.service.token.impl.handlers.staking.StakingRewardsHelper.asAccountAmounts;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.RecordFinalizerBase;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableNftStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.handlers.staking.StakingRewardsHandler;
import com.hedera.node.app.service.token.records.ChildRecordBuilder;
import com.hedera.node.app.service.token.records.CryptoTransferRecordBuilder;
import com.hedera.node.app.service.token.records.FinalizeContext;
import com.hedera.node.app.service.token.records.ParentRecordFinalizer;
import com.hedera.node.config.data.StakingConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class is used to "finalize" hbar and token transfers for the parent transaction record.
 */
@Singleton
public class FinalizeParentRecordHandler extends RecordFinalizerBase implements ParentRecordFinalizer {
    private final StakingRewardsHandler stakingRewardsHandler;

    @Inject
    public FinalizeParentRecordHandler(@NonNull final StakingRewardsHandler stakingRewardsHandler) {
        this.stakingRewardsHandler = stakingRewardsHandler;
    }

    @Override
    public void finalizeParentRecord(@NonNull final AccountID payer, @NonNull final FinalizeContext context) {
        final var recordBuilder = context.userTransactionRecordBuilder(CryptoTransferRecordBuilder.class);

        // This handler won't ask the context for its transaction, but instead will determine the net hbar transfers and
        // token transfers based on the original value from writable state, and based on changes made during this
        // transaction via
        // any relevant writable stores
        final var writableAccountStore = context.writableStore(WritableAccountStore.class);
        final var writableTokenRelStore = context.writableStore(WritableTokenRelationStore.class);
        final var writableNftStore = context.writableStore(WritableNftStore.class);
        final var stakingConfig = context.configuration().getConfigData(StakingConfig.class);
        final var tokenStore = context.readableStore(ReadableTokenStore.class);

        if (stakingConfig.isEnabled()) {
            // staking rewards are triggered for any balance changes to account's that are staked to
            // a node. They are also triggered if staking related fields are modified
            // Calculate staking rewards and add them also to hbarChanges here, before assessing
            // net changes for transaction record
            final var rewardsPaid = stakingRewardsHandler.applyStakingRewards(context);
            if (!rewardsPaid.isEmpty()) {
                recordBuilder.paidStakingRewards(asAccountAmounts(rewardsPaid));
            }
        }

        /* ------------------------- Hbar changes from transaction including staking rewards ------------------------- */
        final var hbarChanges = hbarChangesFrom(writableAccountStore);
        // any hbar changes listed in child records should not be recorded again in parent record, so deduct them.
        deductChangesFromChildRecords(hbarChanges, context);
        if (!hbarChanges.isEmpty()) {
            // Save the modified hbar amounts so records can be written
            recordBuilder.transferList(TransferList.newBuilder()
                    .accountAmounts(asAccountAmounts(hbarChanges))
                    .build());
        }

        // Declare the top-level token transfer list, which list will include BOTH fungible and non-fungible token
        // transfers
        final ArrayList<TokenTransferList> tokenTransferLists;

        // ---------- fungible token transfers
        final var fungibleChanges = fungibleChangesFrom(writableTokenRelStore, tokenStore);
        // any fungible token changes listed in child records should not be considered while building
        // parent record, so don't deduct them.
        final var fungibleTokenTransferLists = asTokenTransferListFrom(fungibleChanges);
        tokenTransferLists = new ArrayList<>(fungibleTokenTransferLists);

        // ---------- nft transfers
        final var nftChanges = nftChangesFrom(writableNftStore, tokenStore);
        // any nft transfers listed in child records should not be considered while building
        // parent record, so don't deduct them.
        final var nftTokenTransferLists = asTokenTransferListFromNftChanges(nftChanges);
        tokenTransferLists.addAll(nftTokenTransferLists);

        // Record the modified fungible and non-fungible changes so records can be written
        if (!tokenTransferLists.isEmpty()) {
            tokenTransferLists.sort(TOKEN_TRANSFER_LIST_COMPARATOR);
            recordBuilder.tokenTransferLists(tokenTransferLists);
        }
    }

    private void deductChangesFromChildRecords(final Map<AccountID, Long> hbarChanges, final FinalizeContext context) {
        context.forEachChildRecord(ChildRecordBuilder.class, childRecord -> {
            final var childHbarChangesFromRecord = childRecord.transferList();
            for (final var childChange : childHbarChangesFromRecord.accountAmountsOrElse(List.of())) {
                final var childHbarChangeAccountId = childChange.accountID();
                final var childHbarChangeAmount = childChange.amount();
                if (hbarChanges.containsKey(childHbarChangeAccountId)) {
                    hbarChanges.merge(childHbarChangeAccountId, -childHbarChangeAmount, Long::sum);
                }
            }
        });
    }
}
