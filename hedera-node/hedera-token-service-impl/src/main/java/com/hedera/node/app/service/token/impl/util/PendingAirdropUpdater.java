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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.PendingAirdropId;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.AccountPendingAirdrop;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableAirdropStore;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PendingAirdropUpdater {

    public static void removePendingAirdrops(
            @NonNull final List<PendingAirdropId> airdropsToRemove,
            @NonNull final WritableAirdropStore pendingAirdropStore,
            @NonNull final WritableAccountStore accountStore) {
        final Map<AccountID, Account> updatedSenders = new HashMap<>();
        final Map<PendingAirdropId, AccountPendingAirdrop> updatedAirdrops = new HashMap<>();
        // calculate state changes
        for (PendingAirdropId id : airdropsToRemove) {
            removePendingAirdropAndUpdateStores(id, pendingAirdropStore, accountStore, updatedSenders, updatedAirdrops);
        }

        // commit updates
        updatedSenders.forEach((accountID, account) -> accountStore.put(account));
        updatedAirdrops.forEach(pendingAirdropStore::patch);
        airdropsToRemove.forEach(pendingAirdropStore::remove);
    }

    private static void removePendingAirdropAndUpdateStores(
            PendingAirdropId airdropId,
            WritableAirdropStore pendingAirdropStore,
            WritableAccountStore accountStore,
            Map<AccountID, Account> updatedSenders,
            Map<PendingAirdropId, AccountPendingAirdrop> updatedAirdrops) {

        var senderId = airdropId.senderIdOrThrow();
        var senderAccount = updatedSenders.containsKey(senderId)
                ? updatedSenders.get(senderId)
                : requireNonNull(accountStore.getAccountById(senderId));

        final var airdrop = updatedAirdrops.containsKey(airdropId)
                ? updatedAirdrops.get(airdropId)
                : pendingAirdropStore.getForModify(airdropId);
        validateTrue(airdrop != null, INVALID_TRANSACTION_BODY);

        // updated sender's head of pending airdrops
        if (airdropId.equals(senderAccount.headPendingAirdropId())) {
            final var updatedSender = senderAccount
                    .copyBuilder()
                    .headPendingAirdropId(airdrop.nextAirdrop())
                    .build();
            updatedSenders.put(updatedSender.accountId(), updatedSender);
        }

        // update pending airdrops links
        final var prevAirdropId = airdrop.previousAirdrop();
        final var nextAirdropId = airdrop.nextAirdrop();
        if (prevAirdropId != null) {
            final var prevAccountAirdrop = updatedAirdrops.containsKey(prevAirdropId)
                    ? updatedAirdrops.get(prevAirdropId)
                    : pendingAirdropStore.getForModify(prevAirdropId);
            validateTrue(prevAccountAirdrop != null, INVALID_TRANSACTION_BODY);
            final var prevAirdropToUpdate =
                    prevAccountAirdrop.copyBuilder().nextAirdrop(nextAirdropId).build();
            updatedAirdrops.put(prevAirdropId, prevAirdropToUpdate);
        }
        if (nextAirdropId != null) {
            final var nextAccountAirdrop = updatedAirdrops.containsKey(nextAirdropId)
                    ? updatedAirdrops.get(nextAirdropId)
                    : pendingAirdropStore.getForModify(nextAirdropId);
            validateTrue(nextAccountAirdrop != null, INVALID_TRANSACTION_BODY);
            final var nextAirdropToUpdate = nextAccountAirdrop
                    .copyBuilder()
                    .previousAirdrop(prevAirdropId)
                    .build();
            updatedAirdrops.put(nextAirdropId, nextAirdropToUpdate);
        }

        // decrement the number of pending airdrops
        senderAccount = updatedSenders.containsKey(senderId)
                ? updatedSenders.get(senderId)
                : requireNonNull(accountStore.getAccountById(senderId));
        var updatedSenderAccount = senderAccount
                .copyBuilder()
                .numberPendingAirdrops(senderAccount.numberPendingAirdrops() - 1)
                .build();
        updatedSenders.put(updatedSenderAccount.accountId(), updatedSenderAccount);
    }
}
