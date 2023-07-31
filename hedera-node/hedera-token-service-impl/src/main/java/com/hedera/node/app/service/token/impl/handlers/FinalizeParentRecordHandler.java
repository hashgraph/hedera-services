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
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.node.app.service.token.impl.RecordFinalizerBase;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableNftStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.handlers.staking.StakingRewardsHandler;
import com.hedera.node.app.service.token.impl.records.CryptoTransferRecordBuilder;
import com.hedera.node.app.service.token.records.ParentRecordFinalizer;
import com.hedera.node.app.spi.workflows.HandleContext;
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
    public void finalizeParentRecord(
            @NonNull final HandleContext context, @NonNull final List<TransactionRecord> childRecords) {
        final var recordBuilder = context.recordBuilder(CryptoTransferRecordBuilder.class);

        // This handler won't ask the context for its transaction, but instead will determine the net hbar transfers and
        // token transfers based on the original value from writable state, and based on changes made during this
        // transaction via
        // any relevant writable stores
        final var writableAccountStore = context.writableStore(WritableAccountStore.class);
        final var writableTokenRelStore = context.writableStore(WritableTokenRelationStore.class);
        final var writableNftStore = context.writableStore(WritableNftStore.class);
        final var stakingConfig = context.configuration().getConfigData(StakingConfig.class);

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
        deductChangesFromChildRecords(hbarChanges, childRecords);
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
        final var fungibleChanges = fungibleChangesFrom(writableTokenRelStore);
        // any fungible token changes listed in child records should not be recorded again in parent record, so deduct
        // them.
        deductFTChangesFromChildRecords(fungibleChanges, childRecords);
        final var fungibleTokenTransferLists = asTokenTransferListFrom(fungibleChanges);
        tokenTransferLists = new ArrayList<>(fungibleTokenTransferLists);

        // ---------- nft transfers
        final var nftChanges = nftChangesFrom(writableNftStore);
        // any nft transfers listed in child records should not be recorded again in parent record, so deduct them.
        deductNftChangesFromChildRecords(nftChanges, childRecords);
        final var nftTokenTransferLists = asTokenTransferListFromNftChanges(nftChanges);
        tokenTransferLists.addAll(nftTokenTransferLists);

        // Record the modified fungible and non-fungible changes so records can be written
        if (!tokenTransferLists.isEmpty()) {
            tokenTransferLists.sort(TOKEN_TRANSFER_LIST_COMPARATOR);
            recordBuilder.tokenTransferLists(tokenTransferLists);
        }
    }

    private void deductChangesFromChildRecords(
            final Map<AccountID, Long> hbarChanges, final List<TransactionRecord> childRecords) {
        for (final var childRecord : childRecords) {
            final var childHbarChangesFromRecord = childRecord.transferList();
            for (final var childChange : childHbarChangesFromRecord.accountAmountsOrElse(List.of())) {
                final var childHbarChangeAccountId = childChange.accountID();
                final var childHbarChangeAmount = childChange.amount();
                if (hbarChanges.containsKey(childHbarChangeAccountId)) {
                    hbarChanges.merge(childHbarChangeAccountId, -childHbarChangeAmount, Long::sum);
                }
            }
        }
    }

    private void deductFTChangesFromChildRecords(
            final Map<EntityIDPair, Long> fungibleTokenChanges, final List<TransactionRecord> childRecords) {
        for (final var childRecord : childRecords) {
            final var childTokenChanges = childRecord.tokenTransferListsOrElse(List.of());
            for (final var childChange : childTokenChanges) {
                final var childTokenId = childChange.token();
                final var fungibleTransfers = childChange.transfersOrElse(List.of());
                for (final var childFungibleTransfer : fungibleTransfers) {
                    final var childAccountId = childFungibleTransfer.accountID();
                    final var childAmount = childFungibleTransfer.amount();
                    final var childEntityIdPair = new EntityIDPair(childAccountId, childTokenId);
                    if (fungibleTokenChanges.containsKey(childEntityIdPair)) {
                        fungibleTokenChanges.merge(childEntityIdPair, -childAmount, Long::sum);
                    }
                }
            }
        }
    }

    private void deductNftChangesFromChildRecords(
            final Map<TokenID, List<NftTransfer>> nftChanges, final List<TransactionRecord> childRecords) {
        for (final var childRecord : childRecords) {
            final var childTokenChanges = childRecord.tokenTransferListsOrElse(List.of());
            for (final var childChange : childTokenChanges) {
                final var childTokenId = childChange.token();
                if (!nftChanges.containsKey(childTokenId)) {
                    continue;
                }
                final var nftTransfers = childChange.nftTransfersOrElse(List.of());
                for (final var childNftTransfer : nftTransfers) {
                    final var senderId = childNftTransfer.senderAccountID();
                    final var receiverId = childNftTransfer.receiverAccountID();
                    final var serial = childNftTransfer.serialNumber();
                    final var childNftTransferKey = new NftTransfer(senderId, receiverId, serial, false);
                    final var nftTransferList = nftChanges.get(childTokenId);
                    nftTransferList.remove(childNftTransferKey);
                }
            }
        }
    }
}
