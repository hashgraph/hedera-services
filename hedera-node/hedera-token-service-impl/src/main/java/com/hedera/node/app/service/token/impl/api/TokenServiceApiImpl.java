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
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.spi.store.WritableStoreFactory;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Implements {@link TokenServiceApi} via {@link WritableAccountStore} calls.
 */
public class TokenServiceApiImpl implements TokenServiceApi {
    private final WritableStoreFactory storeFactory;

    public TokenServiceApiImpl(@NonNull final Configuration config, @NonNull final WritableStoreFactory storeFactory) {
        requireNonNull(config);
        this.storeFactory = requireNonNull(storeFactory);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createAndMaybeAliasContract(
            @NonNull final ContractID idToCreate, @NonNull final Consumer<Account.Builder> spec) {
        requireNonNull(spec);
        requireNonNull(idToCreate);
        final var builder = Account.newBuilder()
                .accountId(AccountID.newBuilder().accountNum(idToCreate.contractNumOrThrow()))
                .smartContract(true);
        spec.accept(builder);
        final var newContract = builder.build();
        if (newContract.tinybarBalance() > 0) {
            throw new IllegalArgumentException("Cannot create contract with non-zero balance");
        }
        final var store = storeFactory.getStore(WritableAccountStore.class);
        store.put(newContract);
        if (newContract.alias().length() > 0) {
            store.putAlias(newContract.alias(), newContract.accountId());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteAndMaybeUnaliasContract(@NonNull final ContractID idToDelete) {
        requireNonNull(idToDelete);
        final var store = storeFactory.getStore(WritableAccountStore.class);
        if (idToDelete.hasContractNum()) {
            store.remove(AccountID.newBuilder()
                    .accountNum(idToDelete.contractNumOrThrow())
                    .build());
        } else {
            final var contractAccountId = store.getAccountIDByAlias(idToDelete.evmAddressOrThrow());
            if (contractAccountId != null) {
                store.remove(contractAccountId);
                store.removeAlias(idToDelete.evmAddressOrThrow());
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void incrementParentNonce(@NonNull ContractID parentId) {
        final var store = storeFactory.getStore(WritableAccountStore.class);
        final var contract = requireNonNull(store.getContractById(parentId));
        store.put(contract.copyBuilder()
                .ethereumNonce(contract.ethereumNonce() + 1)
                .build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void incrementSenderNonce(@NonNull AccountID senderId) {
        final var store = storeFactory.getStore(WritableAccountStore.class);
        final var sender = requireNonNull(store.get(senderId));
        store.put(sender.copyBuilder().ethereumNonce(sender.ethereumNonce() + 1).build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void transferFromTo(@NonNull AccountID fromId, @NonNull AccountID toId, long amount) {
        final var store = storeFactory.getStore(WritableAccountStore.class);
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
        final var store = storeFactory.getStore(WritableAccountStore.class);
        return store.modifiedAccountsInState();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ContractNonceInfo> updatedContractNonces() {
        final var store = storeFactory.getStore(WritableAccountStore.class);
        return store.updatedContractNonces();
    }
}
