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

import static com.hedera.hapi.node.base.AccountAmount.*;
import static com.hedera.hapi.node.base.ResponseCodeEnum.FAIL_INVALID;
import static com.hedera.node.app.service.token.impl.comparator.TokenComparators.ACCOUNT_AMOUNT_COMPARATOR;
import static com.hedera.node.app.service.token.impl.comparator.TokenComparators.NFT_TRANSFER_COMPARATOR;
import static com.hedera.node.app.service.token.impl.comparator.TokenComparators.TOKEN_TRANSFER_LIST_COMPARATOR;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.state.common.UniqueTokenId;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableNftStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableNftStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.records.CryptoTransferRecordBuilder;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;

/**
 * This is a special handler that is used to "finalize" hbar and token transfers, most importantly by writing the results of a transaction to record files. Finalization in this context means summing the net changes to make to each account's hbar balance and token balances, and assigning the final owner of an nft after an arbitrary number of ownership changes
 */
@Singleton
public class FinalizeRecordHandler implements TransactionHandler {
    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        // Intentionally empty. There are no pre-checks to do before finalizing the transfer lists
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        final var recordBuilder = context.recordBuilder(CryptoTransferRecordBuilder.class);

        // This handler won't ask the context for its transaction, but instead will determine the net hbar transfers and
        // token transfers based on the current readable state, and based on changes made during this transaction via
        // any relevant writable stores
        final var readableAccountStore = context.readableStore(ReadableAccountStore.class);
        final var writableAccountStore = context.writableStore(WritableAccountStore.class);
        final var readableTokenRelStore = context.readableStore(ReadableTokenRelationStore.class);
        final var writableTokenRelStore = context.writableStore(WritableTokenRelationStore.class);
        final var readableNftStore = context.readableStore(ReadableNftStore.class);
        final var writableNftStore = context.writableStore(WritableNftStore.class);

        // ---------- Hbar transfers
        final var hbarChanges = calculateHbarChanges(writableAccountStore, readableAccountStore);
        if (!hbarChanges.isEmpty()) {
            // Save the modified hbar amounts so records can be written
            recordBuilder.transferList(
                    TransferList.newBuilder().accountAmounts(hbarChanges).build());
        }

        // Declare the top-level token transfer list, which list will include BOTH fungible and non-fungible token
        // transfers
        final ArrayList<TokenTransferList> tokenTransferLists;

        // ---------- fungible token transfers
        final var fungibleChanges = calculateFungibleChanges(writableTokenRelStore, readableTokenRelStore);
        final var fungibleTokenTransferLists = moldFungibleChanges(fungibleChanges);
        tokenTransferLists = new ArrayList<>(fungibleTokenTransferLists);

        // ---------- nft transfers
        final var nftTokenTransferLists = calculateNftChanges(writableNftStore, readableNftStore);
        tokenTransferLists.addAll(nftTokenTransferLists);

        // Record the modified fungible and non-fungible changes so records can be written
        if (!tokenTransferLists.isEmpty()) {
            tokenTransferLists.sort(TOKEN_TRANSFER_LIST_COMPARATOR);
            recordBuilder.tokenTransferLists(tokenTransferLists);
        }
    }

    @NonNull
    private static List<AccountAmount> calculateHbarChanges(
            @NonNull final WritableAccountStore writableAccountStore,
            @NonNull final ReadableAccountStore readableAccountStore) {
        final var hbarChanges = new ArrayList<AccountAmount>();
        var netHbarBalance = 0;
        for (final AccountID modifiedAcctId : writableAccountStore.modifiedAccountsInState()) {
            final var modifiedAcct = writableAccountStore.getAccountById(modifiedAcctId);
            final var persistedAcct = readableAccountStore.getAccountById(modifiedAcctId);
            // It's possible the modified account was created in this transaction, in which case the non-existent
            // persisted account effectively has no balance (i.e. its prior balance is 0)
            final var persistedBalance = persistedAcct != null ? persistedAcct.tinybarBalance() : 0;

            // Never allow an account's net hbar balance to be negative
            validateTrue(modifiedAcct.tinybarBalance() >= 0, FAIL_INVALID);

            final var netHbarChange = modifiedAcct.tinybarBalance() - persistedBalance;
            if (netHbarChange != 0) {
                netHbarBalance += netHbarChange;
                final var netChange = newBuilder()
                        .accountID(modifiedAcctId)
                        .amount(netHbarChange)
                        .isApproval(false)
                        .build();
                hbarChanges.add(netChange);
            }
        }
        // Since this is a finalization handler, we should have already succeeded in handling the transaction in a
        // handler before getting here. Therefore, if the sum is non-zero, something went wrong, and we'll respond with
        // FAIL_INVALID
        validateTrue(netHbarBalance == 0, FAIL_INVALID);

        return hbarChanges;
    }

    @NonNull
    private static Map<EntityIDPair, AccountAmount> calculateFungibleChanges(
            @NonNull final WritableTokenRelationStore writableTokenRelStore,
            @NonNull final ReadableTokenRelationStore readableTokenRelStore) {
        final var fungibleChanges = new HashMap<EntityIDPair, AccountAmount>();
        for (final EntityIDPair modifiedRel : writableTokenRelStore.modifiedTokens()) {
            final var relAcctId = modifiedRel.accountId();
            final var relTokenId = modifiedRel.tokenId();
            final var modifiedTokenRel = writableTokenRelStore.get(relAcctId, relTokenId);
            final var persistedTokenRel = readableTokenRelStore.get(relAcctId, relTokenId);
            final var modifiedBalance = modifiedTokenRel.balance();
            // It's possible the modified token rel was created in this transaction. If so, use a persisted balance of 0
            // for the token rel that didn't exist
            final var persistedBalance = persistedTokenRel != null ? persistedTokenRel.balance() : 0;

            // Never allow a fungible token's balance to be negative
            validateTrue(modifiedTokenRel.balance() >= 0, FAIL_INVALID);

            // If the token rel's balance has changed, add it to the list of changes
            final var netFungibleChange = modifiedBalance - persistedBalance;
            if (netFungibleChange != 0) {
                final var netChange = newBuilder()
                        .accountID(relAcctId)
                        .amount(netFungibleChange)
                        .isApproval(false)
                        .build();
                fungibleChanges.put(modifiedRel, netChange);
            }
        }

        return fungibleChanges;
    }

    @NonNull
    private static List<TokenTransferList> moldFungibleChanges(
            @NonNull final Map<EntityIDPair, AccountAmount> fungibleChanges) {
        final var fungibleTokenTransferLists = new ArrayList<TokenTransferList>();
        final var acctAmountsByTokenId = new HashMap<TokenID, List<AccountAmount>>();
        for (final var fungibleChange : fungibleChanges.entrySet()) {
            final var tokenIdOfAcctAmountChange = fungibleChange.getKey().tokenId();
            if (!acctAmountsByTokenId.containsKey(tokenIdOfAcctAmountChange)) {
                acctAmountsByTokenId.put(tokenIdOfAcctAmountChange, new ArrayList<>());
            }
            if (fungibleChange.getValue().amount() != 0) {
                acctAmountsByTokenId.get(tokenIdOfAcctAmountChange).add(fungibleChange.getValue());
            }
        }
        // Mold the fungible changes into a transfer ordered by (token ID, account ID). The fungible pairs are ordered
        // by (accountId, tokenId), so we need to group by each token ID
        for (final var acctAmountsForToken : acctAmountsByTokenId.entrySet()) {
            final var singleTokenTransfers = acctAmountsForToken.getValue();
            if (!singleTokenTransfers.isEmpty()) {
                singleTokenTransfers.sort(ACCOUNT_AMOUNT_COMPARATOR);
                fungibleTokenTransferLists.add(TokenTransferList.newBuilder()
                        .token(acctAmountsForToken.getKey())
                        .transfers(singleTokenTransfers)
                        .build());
            }
        }

        return fungibleTokenTransferLists;
    }

    @NonNull
    private List<TokenTransferList> calculateNftChanges(
            @NonNull final WritableNftStore writableNftStore, @NonNull final ReadableNftStore readableNftStore) {
        final var nftChanges = new HashMap<TokenID, List<NftTransfer>>();
        for (final UniqueTokenId nftId : writableNftStore.modifiedNfts()) {
            final var modifiedNft = writableNftStore.get(nftId);
            final var persistedNft = readableNftStore.get(nftId);

            // The NFT may not have existed before, in which case we'll use a null sender account ID
            final var senderAccountId = persistedNft != null ? persistedNft.ownerId() : null;
            final var nftTransfer = NftTransfer.newBuilder()
                    .serialNumber(modifiedNft.id().serialNumber())
                    .senderAccountID(senderAccountId)
                    .receiverAccountID(modifiedNft.ownerId())
                    .isApproval(false)
                    .build();
            if (!nftChanges.containsKey(nftId.tokenId())) {
                nftChanges.put(nftId.tokenId(), new ArrayList<>());
            }

            final var currentNftChanges = nftChanges.get(nftId.tokenId());
            currentNftChanges.add(nftTransfer);
            nftChanges.put(nftId.tokenId(), currentNftChanges);
        }

        // Create a new transfer list for each token ID
        final var nftTokenTransferLists = new ArrayList<TokenTransferList>();
        for (final var nftsForTokenId : nftChanges.entrySet()) {
            if (!nftsForTokenId.getValue().isEmpty()) {
                // This var is the collection of all NFT transfers _for a single token ID_
                final var nftTransfersForTokenId = nftsForTokenId.getValue();
                nftTransfersForTokenId.sort(NFT_TRANSFER_COMPARATOR);
                nftTokenTransferLists.add(TokenTransferList.newBuilder()
                        .token(nftsForTokenId.getKey())
                        .nftTransfers(nftTransfersForTokenId)
                        .build());
            }
        }

        return nftTokenTransferLists;
    }
}
