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

import static com.hedera.hapi.node.base.ResponseCodeEnum.FAIL_INVALID;
import static com.hedera.node.app.service.token.impl.comparator.TokenComparators.TOKEN_TRANSFER_LIST_COMPARATOR;
import static com.hedera.node.app.service.token.impl.handlers.staking.StakingRewardsHelper.asAccountAmounts;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Collections.emptyList;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.state.common.EntityIDPair;
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
import java.util.HashMap;
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

        // Hbar changes from transaction including staking rewards
        final var hbarChanges = hbarChangesFrom(writableAccountStore);
        final var fungibleChanges = fungibleChangesFrom(writableTokenRelStore, tokenStore);
        final var nftChanges = nftChangesFrom(writableNftStore, tokenStore);

        if (nftChanges.isEmpty()) {
            nonFungibleChangesFrom(writableTokenRelStore, tokenStore, fungibleChanges);
        }

        if (context.hasChildRecords()) {
            // All the above changes maps are mutable
            deductChangesFromChildRecords(context, fungibleChanges, nftChanges, hbarChanges);
            // In the current system a parent transaction that has child transactions cannot
            // *itself* cause any net fungible or NFT transfers; fail fast if that happens
            validateTrue(fungibleChanges.isEmpty(), FAIL_INVALID);
            validateTrue(nftChanges.isEmpty(), FAIL_INVALID);
        }
        if (!hbarChanges.isEmpty()) {
            // Save the modified hbar amounts so records can be written
            recordBuilder.transferList(TransferList.newBuilder()
                    .accountAmounts(asAccountAmounts(hbarChanges))
                    .build());
        }
        final var hasTokenTransferLists = !fungibleChanges.isEmpty() || !nftChanges.isEmpty();
        if (hasTokenTransferLists) {
            final var tokenTransferLists = asTokenTransferListFrom(fungibleChanges);
            final var nftTokenTransferLists = asTokenTransferListFromNftChanges(nftChanges);
            tokenTransferLists.addAll(nftTokenTransferLists);
            tokenTransferLists.sort(TOKEN_TRANSFER_LIST_COMPARATOR);
            recordBuilder.tokenTransferLists(tokenTransferLists);
        }
    }

    private void deductChangesFromChildRecords(
            @NonNull final FinalizeContext context,
            @NonNull final Map<EntityIDPair, Long> fungibleChanges,
            @NonNull final Map<TokenID, List<NftTransfer>> nftTransfers,
            @NonNull final Map<AccountID, Long> hbarChanges) {
        final Map<NftID, AccountID> finalNftOwners = new HashMap<>();
        context.forEachChildRecord(ChildRecordBuilder.class, childRecord -> {
            final var childHbarChangesFromRecord = childRecord.transferList();
            for (final var childChange : childHbarChangesFromRecord.accountAmountsOrElse(List.of())) {
                final var accountId = childChange.accountID();
                if (hbarChanges.containsKey(accountId)) {
                    final var newAdjust = hbarChanges.merge(accountId, -childChange.amount(), Long::sum);
                    if (newAdjust == 0) {
                        hbarChanges.remove(accountId);
                    }
                }
            }
            for (final var tokenTransfers : childRecord.tokenTransferLists()) {
                final var fungibleTransfers = tokenTransfers.transfersOrElse(emptyList());
                final var tokenId = tokenTransfers.tokenOrThrow();
                if (!fungibleTransfers.isEmpty()) {
                    for (final var unitAdjust : fungibleTransfers) {
                        final var accountId = unitAdjust.accountIDOrThrow();
                        final var amount = unitAdjust.amount();
                        final var key = new EntityIDPair(accountId, tokenId);
                        if (fungibleChanges.containsKey(key)) {
                            final var newAdjust = fungibleChanges.merge(key, -amount, Long::sum);
                            if (newAdjust == 0) {
                                fungibleChanges.remove(key);
                            }
                        }
                    }
                } else {
                    for (final var ownershipChange : tokenTransfers.nftTransfersOrElse(emptyList())) {
                        final var newOwnerId = ownershipChange.receiverAccountIDOrElse(ZERO_ACCOUNT_ID);
                        final var key = new NftID(tokenId, ownershipChange.serialNumber());
                        finalNftOwners.put(key, newOwnerId);
                    }
                }
            }
        });
        for (final var iter = nftTransfers.entrySet().iterator(); iter.hasNext(); ) {
            final var entry = iter.next();
            final var tokenId = entry.getKey();
            final var nftTransfersForToken = entry.getValue();
            nftTransfersForToken.removeIf(transfer -> {
                final var key = new NftID(tokenId, transfer.serialNumber());
                return finalNftOwners
                        .getOrDefault(key, ZERO_ACCOUNT_ID)
                        .equals(transfer.receiverAccountIDOrElse(ZERO_ACCOUNT_ID));
            });
            if (nftTransfersForToken.isEmpty()) {
                iter.remove();
            }
        }
    }
}
