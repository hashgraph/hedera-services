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

package com.hedera.node.app.service.token.impl.api;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.contract.ContractNonceInfo;
import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.spi.state.WritableStates;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Set;

/**
 * Implements {@link TokenServiceApi} via {@link WritableAccountStore} calls.
 */
public class TokenServiceApiImpl implements TokenServiceApi {
    private final WritableStates writableStates;

    public TokenServiceApiImpl(@NonNull final Configuration config, @NonNull final WritableStates writableStates) {
        requireNonNull(config);
        this.writableStates = requireNonNull(writableStates);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void markAsContract(@NonNull final AccountID accountId) {
        requireNonNull(accountId);
        final var store = new WritableAccountStore(writableStates);
        final var accountAsContract = requireNonNull(store.get(accountId))
                .copyBuilder()
                .smartContract(true)
                .build();
        store.put(accountAsContract);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteAndMaybeUnaliasContract(@NonNull final AccountID idToDelete) {
        requireNonNull(idToDelete);
        final var store = new WritableAccountStore(writableStates);
        if (idToDelete.hasAccountNum()) {
            store.remove(idToDelete);
        } else {
            final var alias = idToDelete.aliasOrThrow();
            final var contractAccountId = store.getAccountIDByAlias(alias);
            if (contractAccountId != null) {
                store.remove(contractAccountId);
                store.removeAlias(alias);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void incrementParentNonce(@NonNull final ContractID parentId) {
        requireNonNull(parentId);
        final var store = new WritableAccountStore(writableStates);
        final var contract = requireNonNull(store.getContractById(parentId));
        store.put(contract.copyBuilder()
                .ethereumNonce(contract.ethereumNonce() + 1)
                .build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void incrementSenderNonce(@NonNull final AccountID senderId) {
        requireNonNull(senderId);
        final var store = new WritableAccountStore(writableStates);
        final var sender = requireNonNull(store.get(senderId));
        store.put(sender.copyBuilder().ethereumNonce(sender.ethereumNonce() + 1).build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setNonce(@NonNull final AccountID accountId, final long nonce) {
        requireNonNull(accountId);
        final var store = new WritableAccountStore(writableStates);
        final var target = requireNonNull(store.get(accountId));
        store.put(target.copyBuilder().ethereumNonce(nonce).build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void transferFromTo(@NonNull AccountID fromId, @NonNull AccountID toId, long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException(
                    "Cannot transfer negative value (" + amount + " tinybars) from " + fromId + " to " + toId);
        }
        final var store = new WritableAccountStore(writableStates);
        final var from = requireNonNull(store.get(fromId));
        final var to = requireNonNull(store.get(toId));
        if (from.tinybarBalance() < amount) {
            throw new IllegalArgumentException(
                    "Insufficient balance to transfer " + amount + " tinybars from " + fromId + " to " + toId);
        }
        if (to.tinybarBalance() + amount < 0) {
            throw new IllegalArgumentException(
                    "Overflow on transfer of " + amount + " tinybars from " + fromId + " to " + toId);
        }
        store.put(from.copyBuilder()
                .tinybarBalance(from.tinybarBalance() - amount)
                .build());
        store.put(to.copyBuilder().tinybarBalance(to.tinybarBalance() + amount).build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<AccountID> modifiedAccountIds() {
        final var store = new WritableAccountStore(writableStates);
        return store.modifiedAccountsInState();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ContractNonceInfo> updatedContractNonces() {
        final var store = new WritableAccountStore(writableStates);
        return store.updatedContractNonces();
    }
}
