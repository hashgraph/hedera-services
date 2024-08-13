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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_PENDING_AIRDROP_ID;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.PendingAirdropId;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.AccountPendingAirdrop;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableAirdropStore;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

/**
 * Class that computes the expected results of a pending airdrops removal and then commit the needed changes.
 * This way it will save several state updates of pending airdrop's linked list pointers {@code previousAirdrop()}
 * and {@code nextAirdrop()}, also sender's account updates of {@code headPendingAirdropId()} and
 * {@code numberPendingAirdrops()}
 */
public class PendingAirdropUpdater {
    @Inject
    public PendingAirdropUpdater() {}

    /**
     * Removes provided pending airdrops from the state.
     * Updates sender accounts ({@code headPendingAirdropId()} and {@code numberPendingAirdrops()}).
     * Update neighbour pending airdrops linked list pointers ({@code previousAirdrop()} and {@code nextAirdrop()}).
     *
     * @param airdropsToRemove list of PendingAirdropId to be removed
     */
    public void removePendingAirdrops(
            @NonNull final List<PendingAirdropId> airdropsToRemove,
            @NonNull final WritableAirdropStore pendingAirdropStore,
            @NonNull final WritableAccountStore accountStore) {
        final var updatedSenders = new LinkedHashMap<AccountID, Account>();
        final var updatedAirdrops = new LinkedHashMap<PendingAirdropId, AccountPendingAirdrop>();
        // calculate state changes
        for (final var id : airdropsToRemove) {
            computeRemovalResults(id, updatedSenders, updatedAirdrops, pendingAirdropStore, accountStore);
        }

        // commit updates
        updatedSenders.forEach((accountID, account) -> accountStore.put(account));
        updatedAirdrops.forEach(pendingAirdropStore::put);
        airdropsToRemove.forEach(pendingAirdropStore::remove);
    }

    /**
     *  Compute updates needed to be commited, after removing a single pending airdrop.
     *  It populates maps {@code updatedSenders} and {@code updatedAirdrops} with updated entities, ready
     *  to be persisted in the state.
     *
     * <p>
     *  <b>Note:</b> this method don't persist any state changes.
     *
     * @param airdropId pending airdrop to remove
     * @param updatedSenders map containing previous changes of the senders accounts
     * @param updatedAirdrops map containing previous changes of the pending airdrops
     */
    private void computeRemovalResults(
            @NonNull final PendingAirdropId airdropId,
            @NonNull final Map<AccountID, Account> updatedSenders,
            @NonNull final Map<PendingAirdropId, AccountPendingAirdrop> updatedAirdrops,
            @NonNull final WritableAirdropStore pendingAirdropStore,
            @NonNull final WritableAccountStore accountStore) {
        final var senderId = airdropId.senderIdOrThrow();
        final var airdrop = getPendingAirdrop(updatedAirdrops, pendingAirdropStore, airdropId);
        validateTrue(airdrop != null, INVALID_PENDING_AIRDROP_ID);

        // update pending airdrops links
        final var prevAirdropId = airdrop.previousAirdrop();
        final var nextAirdropId = airdrop.nextAirdrop();
        if (prevAirdropId != null) {
            final var prevAccountAirdrop = getPendingAirdrop(updatedAirdrops, pendingAirdropStore, prevAirdropId);
            validateTrue(prevAccountAirdrop != null, INVALID_PENDING_AIRDROP_ID);
            final var prevAirdropToUpdate =
                    prevAccountAirdrop.copyBuilder().nextAirdrop(nextAirdropId).build();
            updatedAirdrops.put(prevAirdropId, prevAirdropToUpdate);
        }
        if (nextAirdropId != null) {
            final var nextAccountAirdrop = getPendingAirdrop(updatedAirdrops, pendingAirdropStore, nextAirdropId);
            validateTrue(nextAccountAirdrop != null, INVALID_PENDING_AIRDROP_ID);
            final var nextAirdropToUpdate = nextAccountAirdrop
                    .copyBuilder()
                    .previousAirdrop(prevAirdropId)
                    .build();
            updatedAirdrops.put(nextAirdropId, nextAirdropToUpdate);
        }

        // update sender
        var senderAccount = updatedSenders.containsKey(senderId)
                ? updatedSenders.get(senderId)
                : requireNonNull(accountStore.getAccountById(senderId));
        final var updatedSender =
                senderAccount.copyBuilder().numberPendingAirdrops(senderAccount.numberPendingAirdrops() - 1);
        if (airdropId.equals(senderAccount.headPendingAirdropId())) {
            updatedSender.headPendingAirdropId(airdrop.nextAirdrop());
        }
        updatedSenders.put(senderAccount.accountId(), updatedSender.build());
    }

    private AccountPendingAirdrop getPendingAirdrop(
            final @NonNull Map<PendingAirdropId, AccountPendingAirdrop> updatedAirdrops,
            final @NonNull WritableAirdropStore pendingAirdropStore,
            final PendingAirdropId prevAirdropId) {
        return updatedAirdrops.containsKey(prevAirdropId)
                ? updatedAirdrops.get(prevAirdropId)
                : pendingAirdropStore.getForModify(prevAirdropId);
    }
}
