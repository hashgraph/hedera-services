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
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.transaction.PendingAirdropRecord;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.spi.workflows.HandleContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

public class AirdropHandlerHelper {
    public record FungibleAirdropLists(
            @NonNull List<AccountAmount> transferFungibleAmounts,
            @NonNull List<AccountAmount> pendingFungibleAmounts) {}

    public record NftAirdropLists(
            @NonNull List<NftTransfer> transferNftList, @NonNull List<NftTransfer> pendingNftList) {}

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

    public static PendingAirdropId createFungibleTokenPendingAirdropId(
            TokenID tokenId, AccountID senderId, AccountID receiverId) {
        return PendingAirdropId.newBuilder()
                .receiverId(receiverId)
                .senderId(senderId)
                .fungibleTokenType(tokenId)
                .build();
    }

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

    public static PendingAirdropValue createPendingAirdropValue(long amount) {
        return PendingAirdropValue.newBuilder().amount(amount).build();
    }

    public static PendingAirdropRecord createPendingAirdropRecord(
            PendingAirdropId pendingAirdropId, PendingAirdropValue pendingAirdropValue) {
        return PendingAirdropRecord.newBuilder()
                .pendingAirdropId(pendingAirdropId)
                .pendingAirdropValue(pendingAirdropValue)
                .build();
    }
}
