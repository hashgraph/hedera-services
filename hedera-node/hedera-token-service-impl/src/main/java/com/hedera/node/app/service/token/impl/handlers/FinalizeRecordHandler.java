/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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
import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static com.hedera.node.app.service.token.impl.handlers.staking.StakingRewardsHelper.asAccountAmounts;
import static com.hedera.node.app.service.token.impl.handlers.staking.StakingRewardsHelper.requiresExternalization;
import static java.util.Collections.emptyList;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.node.app.service.token.impl.RecordFinalizerBase;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableNftStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.service.token.impl.handlers.staking.StakingRewardsHandler;
import com.hedera.node.app.service.token.records.ChildStreamBuilder;
import com.hedera.node.app.service.token.records.CryptoTransferStreamBuilder;
import com.hedera.node.app.service.token.records.FinalizeContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.record.StreamBuilder;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.StakingConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class is used to "finalize" hbar and token transfers for the parent transaction record.
 */
@Singleton
public class FinalizeRecordHandler extends RecordFinalizerBase {
    private static final Logger logger = LogManager.getLogger(FinalizeRecordHandler.class);
    public static final long LEDGER_TOTAL_TINY_BAR_FLOAT = 5000000000000000000L;
    private static final List<AccountAmount> GENESIS_TREASURY_CREDIT = List.of(AccountAmount.newBuilder()
            .amount(LEDGER_TOTAL_TINY_BAR_FLOAT)
            .accountID(asAccount(2))
            .build());

    private final StakingRewardsHandler stakingRewardsHandler;

    /**
     * Constructs a {@link FinalizeRecordHandler} instance.
     * @param stakingRewardsHandler the {@link StakingRewardsHandler} instance
     */
    @Inject
    public FinalizeRecordHandler(@NonNull final StakingRewardsHandler stakingRewardsHandler) {
        this.stakingRewardsHandler = stakingRewardsHandler;
    }

    public void finalizeStakingRecord(
            @NonNull final FinalizeContext context,
            @NonNull final HederaFunctionality functionality,
            @NonNull final Set<AccountID> explicitRewardReceivers,
            @NonNull final Map<AccountID, Long> prePaidRewards) {
        finalizeRecord(context, functionality, explicitRewardReceivers, prePaidRewards);
    }

    public void finalizeNonStakingRecord(
            @NonNull final FinalizeContext context, @NonNull final HederaFunctionality functionality) {
        finalizeRecord(context, functionality, null, null);
    }

    private void finalizeRecord(
            @NonNull final FinalizeContext context,
            @NonNull final HederaFunctionality functionality,
            @Nullable final Set<AccountID> explicitRewardReceivers,
            @Nullable final Map<AccountID, Long> prePaidRewards) {
        final var recordBuilder = context.userTransactionRecordBuilder(CryptoTransferStreamBuilder.class);

        // This handler won't ask the context for its transaction, but instead will determine the net hbar transfers and
        // token transfers based on the original value from writable state, and based on changes made during this
        // transaction via
        // any relevant writable stores
        final var writableAccountStore = context.writableStore(WritableAccountStore.class);
        final var writableTokenRelStore = context.writableStore(WritableTokenRelationStore.class);
        final var writableNftStore = context.writableStore(WritableNftStore.class);
        final var stakingConfig = context.configuration().getConfigData(StakingConfig.class);
        final var writableTokenStore = context.writableStore(WritableTokenStore.class);

        if (stakingConfig.isEnabled() && explicitRewardReceivers != null && prePaidRewards != null) {
            // staking rewards are triggered for any balance changes to account's that are staked to
            // a node. They are also triggered if staking related fields are modified
            // Calculate staking rewards and add them also to hbarChanges here, before assessing
            // net changes for transaction record
            final var rewardsPaid =
                    stakingRewardsHandler.applyStakingRewards(context, explicitRewardReceivers, prePaidRewards);
            if (requiresExternalization(rewardsPaid)) {
                recordBuilder.paidStakingRewards(asAccountAmounts(rewardsPaid));
            }
        }

        // Hbar changes from transaction including staking rewards
        final var maxLegalBalance =
                context.configuration().getConfigData(LedgerConfig.class).totalTinyBarFloat();
        final Map<AccountID, Long> hbarChanges;
        try {
            hbarChanges = hbarChangesFrom(writableAccountStore, maxLegalBalance);
        } catch (HandleException e) {
            if (e.getStatus() == FAIL_INVALID) {
                logHbarFinalizationFailInvalid(
                        context.userTransactionRecordBuilder(StreamBuilder.class), writableAccountStore);
            }
            throw e;
        }
        // If the function is not a crypto transfer, then we filter all zero amounts from token transfer list.
        // To be compatible with mono-service records, we _don't_ filter zero token transfers in the record
        final var isCryptoTransfer = functionality == HederaFunctionality.CRYPTO_TRANSFER;
        // get all the token relation changes for fungible and non-fungible tokens
        final var tokenRelChanges = tokenRelChangesFrom(writableTokenRelStore, !isCryptoTransfer);
        // get all the NFT changes. Go through the nft changes and see if there are any token relation changes
        // for the sender and receiver of the NFTs. If there are, then reduce the balance change for that relation
        // by 1 for receiver and increment the balance change for sender by 1. This is to ensure that the NFT
        // transfer is not double counted in the token relation changes and the NFT changes. Also we don't need to
        // represent the changes for Mint or Wipe of NFTs in the token relation changes.
        final var nftChanges = nftChangesFrom(writableNftStore, writableTokenStore, tokenRelChanges);

        if (context.hasChildOrPrecedingRecords()) {
            // All the above changes maps are mutable
            deductChangesFromChildOrPrecedingRecords(context, tokenRelChanges, nftChanges, hbarChanges);
        }
        if (!hbarChanges.isEmpty()) {
            // Save the modified hbar amounts so records can be written
            recordBuilder.transferList(TransferList.newBuilder()
                    .accountAmounts(asAccountAmounts(hbarChanges))
                    .build());
        }
        final var hasTokenTransferLists = !tokenRelChanges.isEmpty() || !nftChanges.isEmpty();
        if (hasTokenTransferLists) {
            final var tokenTransferLists = asTokenTransferListFrom(tokenRelChanges, !isCryptoTransfer);
            final var nftTokenTransferLists = asTokenTransferListFromNftChanges(nftChanges);
            tokenTransferLists.addAll(nftTokenTransferLists);
            tokenTransferLists.sort(TOKEN_TRANSFER_LIST_COMPARATOR);
            recordBuilder.tokenTransferLists(tokenTransferLists);
        }
    }

    // invoke logger parameters conditionally
    @SuppressWarnings("java:S2629")
    private void logHbarFinalizationFailInvalid(
            @NonNull final StreamBuilder recordBuilder, @NonNull final WritableAccountStore accountStore) {
        logger.error(
                """
                        Non-zero net hbar change when handling body
                        {}
                        with fee {}; original/modified accounts claimed to be:
                        {}
                        """,
                recordBuilder.transactionBody(),
                recordBuilder.transactionFee(),
                accountStore.modifiedAccountsInState().stream()
                        .map(accountId -> String.format(
                                "\tOriginal : %s%n\tModified : %s",
                                accountStore.getOriginalValue(accountId), accountStore.get(accountId)))
                        .collect(Collectors.joining("\n")));
    }

    private void deductChangesFromChildOrPrecedingRecords(
            @NonNull final FinalizeContext context,
            @NonNull final Map<EntityIDPair, Long> fungibleChanges,
            @NonNull final Map<TokenID, List<NftTransfer>> nftTransfers,
            @NonNull final Map<AccountID, Long> hbarChanges) {
        final Map<NftID, AccountID> childFinalNftOwners = new HashMap<>();
        context.forEachChildRecord(ChildStreamBuilder.class, childRecord -> {
            final List<AccountAmount> childHbarChangesFromRecord = childRecord.transferList() == null
                    ? emptyList()
                    : childRecord.transferList().accountAmounts();
            if (childHbarChangesFromRecord.size() == 1) {
                if (!childHbarChangesFromRecord.equals(GENESIS_TREASURY_CREDIT)) {
                    throw new IllegalStateException("Invalid hbar changes from child record");
                }
                return;
            }
            for (final var childChange : childHbarChangesFromRecord) {
                final var accountId = childChange.accountID();
                final var newAdjust = hbarChanges.merge(accountId, -childChange.amount(), Long::sum);
                if (newAdjust == 0) {
                    hbarChanges.remove(accountId);
                }
            }
            for (final var tokenTransfers : childRecord.tokenTransferLists()) {
                final var fungibleTransfers = tokenTransfers.transfers();
                final var tokenId = tokenTransfers.tokenOrThrow();
                if (!fungibleTransfers.isEmpty()) {
                    for (final var unitAdjust : fungibleTransfers) {
                        final var accountId = unitAdjust.accountIDOrThrow();
                        final var amount = unitAdjust.amount();
                        final var key = new EntityIDPair(accountId, tokenId);
                        final var newAdjust = fungibleChanges.merge(key, -amount, Long::sum);
                        if (newAdjust == 0) {
                            fungibleChanges.remove(key);
                        }
                    }
                } else {
                    for (final var ownershipChange : tokenTransfers.nftTransfers()) {
                        final var newOwnerId = ownershipChange.receiverAccountIDOrElse(ZERO_ACCOUNT_ID);
                        final var key = new NftID(tokenId, ownershipChange.serialNumber());
                        childFinalNftOwners.put(key, newOwnerId);
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
                if (childFinalNftOwners.containsKey(key)) {
                    final var childFinalOwner = childFinalNftOwners.get(key);
                    final var ourFinalOwner = transfer.receiverAccountIDOrThrow();
                    // Remove this NFT transfer from our list if the child record's
                    // transfer list already shows it being transferred to the same
                    // final owner as in our list
                    return childFinalOwner.equals(ourFinalOwner);
                }
                return false;
            });
            if (nftTransfersForToken.isEmpty()) {
                iter.remove();
            }
        }
    }
}
