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

package com.hedera.node.app.service.token.impl;

import static com.hedera.hapi.node.base.ResponseCodeEnum.FAIL_INVALID;
import static com.hedera.node.app.service.token.impl.comparator.TokenComparators.ACCOUNT_AMOUNT_COMPARATOR;
import static com.hedera.node.app.service.token.impl.handlers.staking.StakingRewardsHelper.asAccountAmounts;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.node.app.service.token.ReadableTokenStore;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for both {@link com.hedera.node.app.service.token.records.ParentRecordFinalizer} and {@link
 * com.hedera.node.app.service.token.records.ChildRecordFinalizer}. This contains methods that are common to both
 * classes.
 */
public class RecordFinalizerBase {
    private static final AccountID ZERO_ACCOUNT_ID =
            AccountID.newBuilder().accountNum(0).build();

    /**
     * Gets all hbar changes for all modified accounts from the given {@link WritableAccountStore}.
     * @param writableAccountStore the {@link WritableAccountStore} to get the hbar changes from
     * @return a {@link Map} of {@link AccountID} to {@link Long} representing the hbar changes for all modified
     */
    @NonNull
    protected Map<AccountID, Long> hbarChangesFrom(@NonNull final WritableAccountStore writableAccountStore) {
        final var hbarChanges = new HashMap<AccountID, Long>();
        var netHbarBalance = 0;
        for (final AccountID modifiedAcctId : writableAccountStore.modifiedAccountsInState()) {
            final var modifiedAcct = writableAccountStore.getAccountById(modifiedAcctId);
            final var persistedAcct = writableAccountStore.getOriginalValue(modifiedAcctId);
            // It's possible the modified account was created in this transaction, in which case the non-existent
            // persisted account effectively has no balance (i.e. its prior balance is 0)
            final var persistedBalance = persistedAcct != null ? persistedAcct.tinybarBalance() : 0;

            // Never allow an account's net hbar balance to be negative
            validateTrue(modifiedAcct.tinybarBalance() >= 0, FAIL_INVALID);

            final var netHbarChange = modifiedAcct.tinybarBalance() - persistedBalance;
            if (netHbarChange != 0) {
                netHbarBalance += netHbarChange;
                hbarChanges.put(modifiedAcctId, netHbarChange);
            }
        }
        // Since this is a finalization handler, we should have already succeeded in handling the transaction in a
        // handler before getting here. Therefore, if the sum is non-zero, something went wrong, and we'll respond with
        // FAIL_INVALID
        validateTrue(netHbarBalance == 0, FAIL_INVALID);

        return hbarChanges;
    }

    /**
     * Gets all fungible tokenRelation balances for all modified token relations from the given {@link WritableTokenRelationStore}.
     *
     * @param writableTokenRelStore the {@link WritableTokenRelationStore} to get the token relation balances from
     * @param tokenStore
     * @return a {@link Map} of {@link EntityIDPair} to {@link Long} representing the token relation balances for all
     * modified token relations
     */
    @NonNull
    protected Map<EntityIDPair, Long> fungibleChangesFrom(
            @NonNull final WritableTokenRelationStore writableTokenRelStore,
            @NonNull final ReadableTokenStore tokenStore) {
        final var fungibleChanges = new HashMap<EntityIDPair, Long>();
        for (final EntityIDPair modifiedRel : writableTokenRelStore.modifiedTokens()) {
            final var relAcctId = modifiedRel.accountId();
            final var relTokenId = modifiedRel.tokenId();
            final var token = tokenStore.get(relTokenId);
            // Add this to fungible token transfer list only if this token is a fungible token
            if (!token.tokenType().equals(TokenType.FUNGIBLE_COMMON)) {
                continue;
            }
            final var modifiedTokenRel = writableTokenRelStore.get(relAcctId, relTokenId);
            final var persistedTokenRel = writableTokenRelStore.getOriginalValue(relAcctId, relTokenId);

            // It's possible the modified token rel was created in this transaction. If so, use a persisted balance of 0
            // for the token rel that didn't exist
            final var persistedBalance = persistedTokenRel != null ? persistedTokenRel.balance() : 0;
            // It is possible that the account is dissociated with the token in this transaction. If so, use a
            // balance of 0 for the token rel that didn't exist
            final var modifiedTokenRelBalance = modifiedTokenRel != null ? modifiedTokenRel.balance() : 0;
            // Never allow a fungible token's balance to be negative
            validateTrue(modifiedTokenRelBalance >= 0, FAIL_INVALID);

            // If the token rel's balance has changed, add it to the list of changes
            final var netFungibleChange = modifiedTokenRelBalance - persistedBalance;
            if (netFungibleChange != 0) {
                fungibleChanges.put(modifiedRel, netFungibleChange);
            }
        }

        return fungibleChanges;
    }

    /**
     * Given a map of {@link EntityIDPair} to {@link Long} representing the changes to the balances of the token
     * relations, returns a list of {@link TokenTransferList} representing the changes to the token relations.
     * @param fungibleChanges the map of {@link EntityIDPair} to {@link Long} representing the changes to the balances
     * @return a list of {@link TokenTransferList} representing the changes to the token relations
     */
    @NonNull
    protected List<TokenTransferList> asTokenTransferListFrom(@NonNull final Map<EntityIDPair, Long> fungibleChanges) {
        final var fungibleTokenTransferLists = new ArrayList<TokenTransferList>();
        final var acctAmountsByTokenId = new HashMap<TokenID, HashMap<AccountID, Long>>();
        for (final var fungibleChange : fungibleChanges.entrySet()) {
            final var tokenIdOfAcctAmountChange = fungibleChange.getKey().tokenId();
            final var accountIdOfAcctAmountChange = fungibleChange.getKey().accountId();
            if (!acctAmountsByTokenId.containsKey(tokenIdOfAcctAmountChange)) {
                acctAmountsByTokenId.put(tokenIdOfAcctAmountChange, new HashMap<>());
            }
            if (fungibleChange.getValue() != 0) {
                final var tokenIdMap = acctAmountsByTokenId.get(tokenIdOfAcctAmountChange);
                tokenIdMap.merge(accountIdOfAcctAmountChange, fungibleChange.getValue(), Long::sum);
            }
        }
        // Mold the fungible changes into a transfer ordered by (token ID, account ID). The fungible pairs are ordered
        // by (accountId, tokenId), so we need to group by each token ID
        for (final var acctAmountsForToken : acctAmountsByTokenId.entrySet()) {
            final var singleTokenTransfers = acctAmountsForToken.getValue();
            if (!singleTokenTransfers.isEmpty()) {
                final var aaList = asAccountAmounts(singleTokenTransfers);
                aaList.sort(ACCOUNT_AMOUNT_COMPARATOR);
                fungibleTokenTransferLists.add(TokenTransferList.newBuilder()
                        .token(acctAmountsForToken.getKey())
                        .transfers(aaList)
                        .build());
            }
        }

        return fungibleTokenTransferLists;
    }

    /**
     * Gets all nft ownership changes for all modified nfts from the given {@link WritableNftStore}.
     * @param writableNftStore the {@link WritableNftStore} to get the nft ownership changes from
     * @return a {@link Map} of {@link TokenID} to {@link List} of {@link NftTransfer} representing the nft ownership
     */
    @NonNull
    protected Map<TokenID, List<NftTransfer>> nftChangesFrom(
            @NonNull final WritableNftStore writableNftStore, @NonNull final ReadableTokenStore readableTokenStore) {
        final var nftChanges = new HashMap<TokenID, List<NftTransfer>>();
        for (final NftID nftId : writableNftStore.modifiedNfts()) {
            final var modifiedNft = writableNftStore.get(nftId);
            final var persistedNft = writableNftStore.getOriginalValue(nftId);

            // The NFT may not have existed before, in which case we'll use a null sender account ID
            AccountID senderAccountId = null;
            if (persistedNft != null) {
                final var token = readableTokenStore.get(nftId.tokenId());
                final boolean hasOwnerId =
                        persistedNft.hasOwnerId() && !persistedNft.ownerId().equals(AccountID.DEFAULT);
                // If the NFT did not have an owner before set it to the treasury account
                senderAccountId = hasOwnerId ? persistedNft.ownerId() : token.treasuryAccountId();
            }

            // If the NFT has been burned or wiped, modifiedNft will be null. In that case the receiverId
            // will be explicitly set as 0.0.0
            final var builder = NftTransfer.newBuilder();
            if (modifiedNft != null) {
                builder.receiverAccountID(modifiedNft.ownerId());
            } else {
                builder.receiverAccountID(ZERO_ACCOUNT_ID);
            }
            final var nftTransfer = builder.serialNumber(nftId.serialNumber())
                    .senderAccountID(senderAccountId)
                    .build();

            if (!nftChanges.containsKey(nftId.tokenId())) {
                nftChanges.put(nftId.tokenId(), new ArrayList<>());
            }

            final var currentNftChanges = nftChanges.get(nftId.tokenId());
            currentNftChanges.add(nftTransfer);
            nftChanges.put(nftId.tokenId(), currentNftChanges);
        }
        return nftChanges;
    }

    /**
     * Given a {@link Map} of {@link TokenID} to {@link List} of {@link NftTransfer} representing the nft ownership
     * changes, returns a list of {@link TokenTransferList} representing the changes to the nft ownership.
     * @param nftChanges the {@link Map} of {@link TokenID} to {@link List} of {@link NftTransfer} representing the nft
     * @return a list of {@link TokenTransferList} representing the changes to the nft ownership
     */
    protected List<TokenTransferList> asTokenTransferListFromNftChanges(
            final Map<TokenID, List<NftTransfer>> nftChanges) {

        // Create a new transfer list for each token ID
        final var nftTokenTransferLists = new ArrayList<TokenTransferList>();
        for (final var nftsForTokenId : nftChanges.entrySet()) {
            if (!nftsForTokenId.getValue().isEmpty()) {
                // This var is the collection of all NFT transfers _for a single token ID_
                // NFT serial numbers will not be sorted, instead will be displayed in the order they were added in
                // transaction
                final var nftTransfersForTokenId = nftsForTokenId.getValue();
                nftTokenTransferLists.add(TokenTransferList.newBuilder()
                        .token(nftsForTokenId.getKey())
                        .nftTransfers(nftTransfersForTokenId)
                        .build());
            }
        }

        return nftTokenTransferLists;
    }
}
