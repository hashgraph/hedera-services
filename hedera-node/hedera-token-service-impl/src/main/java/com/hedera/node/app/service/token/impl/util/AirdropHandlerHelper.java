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

package com.hedera.node.app.service.token.impl.util;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.node.app.service.token.impl.util.TokenHandlerHelper.getIfUsableForAliasedId;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.PendingAirdropId;
import com.hedera.hapi.node.base.PendingAirdropValue;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.AccountPendingAirdrop;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.transaction.PendingAirdropRecord;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.spi.workflows.HandleContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class that provides static methods
 */
public class AirdropHandlerHelper {
    public record FungibleAirdropLists(
            @NonNull List<AccountAmount> transferFungibleAmounts,
            @NonNull List<AccountAmount> pendingFungibleAmounts) {}

    public record NftAirdropLists(
            @NonNull List<NftTransfer> transferNftList, @NonNull List<NftTransfer> pendingNftList) {}

    private AirdropHandlerHelper() {
        throw new UnsupportedOperationException("Utility class only");
    }

    /**
     * Checks every {@link AccountAmount} from given transfer list and separate it in to two lists.
     * One containing transfers that should be added to pending airdrop state and the other list - transfers that should
     * be executed. The check is done by account's available auto associations slots and the existence of account-token
     * relation {@link #shouldAddAirdropToPendingState(Account, TokenRelation)}
     *
     * @param context {@link HandleContext} used to obtain state stores
     * @param tokenId token id
     * @param transfers list of {@link AccountAmount}
     * @return {@link FungibleAirdropLists} a record containing two lists - transfers to be added in pending state and transfers to be executed
     */
    public static FungibleAirdropLists separateFungibleTransfers(
            HandleContext context, TokenID tokenId, List<AccountAmount> transfers) {
        List<AccountAmount> transferFungibleAmounts = new ArrayList<>();
        List<AccountAmount> pendingFungibleAmounts = new ArrayList<>();
        final var tokenRelStore = context.storeFactory().readableStore(ReadableTokenRelationStore.class);
        final var accountStore = context.storeFactory().readableStore(ReadableAccountStore.class);

        for (final var aa : transfers) {
            final var accountId = aa.accountIDOrElse(AccountID.DEFAULT);
            // if not existing account, create transfer
            if (!accountStore.contains(accountId)) {
                transferFungibleAmounts.add(aa);
                continue;
            }

            final var account =
                    getIfUsableForAliasedId(accountId, accountStore, context.expiryValidator(), INVALID_ACCOUNT_ID);
            final var tokenRel = tokenRelStore.get(accountId, tokenId);
            var shouldAddAirdropToPendingState = shouldAddAirdropToPendingState(account, tokenRel);

            if (shouldAddAirdropToPendingState) {
                pendingFungibleAmounts.add(aa);
            } else {
                transferFungibleAmounts.add(aa);
            }
        }

        return new FungibleAirdropLists(transferFungibleAmounts, pendingFungibleAmounts);
    }

    /**
     * Checks every {@link NftTransfer} from given transfer list and separate it in to two lists.
     * One containing transfers that should be added to pending airdrop state and the other list - transfers that should
     * be executed. The check is done by account's available auto associations slots and the existence of account-token
     * relation {@link #shouldAddAirdropToPendingState(Account, TokenRelation)}
     *
     * @param context context
     * @param tokenId token id
     * @param transfers list of nft transfers
     * @return {@link NftAirdropLists} a record containing two lists - transfers to be added in pending state and transfers to be executed
     */
    public static NftAirdropLists separateNftTransfers(
            HandleContext context, TokenID tokenId, List<NftTransfer> transfers) {
        List<NftTransfer> transferNftList = new ArrayList<>();
        List<NftTransfer> pendingNftList = new ArrayList<>();
        for (final var nftTransfer : transfers) {
            final var tokenRelStore = context.storeFactory().readableStore(ReadableTokenRelationStore.class);
            final var accountStore = context.storeFactory().readableStore(ReadableAccountStore.class);

            var receiverId = nftTransfer.receiverAccountIDOrElse(AccountID.DEFAULT);
            // if not existing account, create transfer
            if (!accountStore.contains(receiverId)) {
                transferNftList.add(nftTransfer);
                continue;
            }

            var account =
                    getIfUsableForAliasedId(receiverId, accountStore, context.expiryValidator(), INVALID_ACCOUNT_ID);
            var tokenRel = tokenRelStore.get(receiverId, tokenId);
            var shouldAddAirdropToPendingState = shouldAddAirdropToPendingState(account, tokenRel);

            if (shouldAddAirdropToPendingState) {
                pendingNftList.add(nftTransfer);
            } else {
                transferNftList.add(nftTransfer);
            }
        }
        return new NftAirdropLists(transferNftList, pendingNftList);
    }

    /**
     * Check if given airdrop should be pending or transfer will be executed.
     * The check is done by account's available auto associations slots and the existence of account-token relation.
     * If receiver's account is not existing, we should proceed with the transfer, this way {@link com.hedera.node.app.service.token.impl.handlers.CryptoTransferHandler}
     * will handle auto creation and auto association of the new receiver.
     *
     * @param receiver receivers account
     * @param tokenRelation token relation
     * @return if airdrop of given token to given receiver should be added to the airdrop pending state
     */
    private static boolean shouldAddAirdropToPendingState(
            @Nullable Account receiver, @Nullable TokenRelation tokenRelation) {
        if (receiver == null) {
            return false;
        }
        // check if we have existing association or free auto associations slots or unlimited auto associations
        return tokenRelation == null
                && receiver.maxAutoAssociations() <= receiver.usedAutoAssociations()
                && receiver.maxAutoAssociations() != -1;
    }

    /**
     * Creates a {@link PendingAirdropId} for a fungible token.
     *
     * @param tokenId the ID of the token
     * @param senderId the ID of sender's account
     * @param receiverId the ID of receiver's account
     * @return {@link PendingAirdropId} for storing in the state
     */
    public static PendingAirdropId createFungibleTokenPendingAirdropId(
            TokenID tokenId, AccountID senderId, AccountID receiverId) {
        return PendingAirdropId.newBuilder()
                .receiverId(receiverId)
                .senderId(senderId)
                .fungibleTokenType(tokenId)
                .build();
    }

    /**
     * Creates a {@link PendingAirdropId} for a non-fungible token.
     *
     * @param tokenId the ID of the token
     * @param serialNumber the serial number of the token
     * @param senderId the ID of the sender's account
     * @param receiverId the ID of the receiver's account
     * @return {@link PendingAirdropId} for storing in the state
     */
    public static PendingAirdropId createNftPendingAirdropId(
            TokenID tokenId, long serialNumber, AccountID senderId, AccountID receiverId) {
        var nftId =
                NftID.newBuilder().tokenId(tokenId).serialNumber(serialNumber).build();
        return PendingAirdropId.newBuilder()
                .receiverId(receiverId)
                .senderId(senderId)
                .nonFungibleToken(nftId)
                .build();
    }

    /**
     * Creates a {@link AccountPendingAirdrop} for a fungible token.
     *
     * @param pendingAirdropValue the amount of fungible token
     * @return {@link AccountPendingAirdrop} for storing in the state
     */
    public static AccountPendingAirdrop createAccountPendingAirdrop(PendingAirdropValue pendingAirdropValue) {
        return createAccountPendingAirdrop(pendingAirdropValue, null);
    }

    /**
     * Creates a {@link AccountPendingAirdrop} for a fungible token.
     *
     * @param pendingAirdropValue the amount of fungible token
     * @return {@link AccountPendingAirdrop} for storing in the state
     */
    public static AccountPendingAirdrop createAccountPendingAirdrop(
            PendingAirdropValue pendingAirdropValue, PendingAirdropId next) {
        return AccountPendingAirdrop.newBuilder()
                .pendingAirdropValue(pendingAirdropValue)
                .nextAirdrop(next)
                .build();
    }

    /**
     * Creates a {@link PendingAirdropRecord} for externalizing pending airdrop records.
     *
     * @param pendingAirdropId the ID of the pending airdrop
     * @param pendingAirdropValue the value of the pending airdrop
     * @return {@link PendingAirdropRecord}
     */
    public static PendingAirdropRecord createPendingAirdropRecord(
            PendingAirdropId pendingAirdropId, PendingAirdropValue pendingAirdropValue) {
        return PendingAirdropRecord.newBuilder()
                .pendingAirdropId(pendingAirdropId)
                .pendingAirdropValue(pendingAirdropValue)
                .build();
    }
}