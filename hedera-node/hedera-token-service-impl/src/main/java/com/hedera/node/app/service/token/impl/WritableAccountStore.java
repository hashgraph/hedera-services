/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.mono.state.virtual.EntityNumValue;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.node.app.spi.state.WritableKVStateBase;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Provides write methods for modifying underlying data storage mechanisms for working with
 * accounts.
 *
 * <p>This class is not exported from the module. It is an internal implementation detail. This
 * class is not complete, it will be extended with other methods like remove, update etc.,
 */
public class WritableAccountStore extends ReadableAccountStore {
    /** The underlying data storage class that holds the account data. */
    private final WritableKVState<EntityNumVirtualKey, Account> accountState;
    /** The underlying data storage class that holds the aliases data built from the state. */
    private final WritableKVState<String, EntityNumValue> aliases;

    /**
     * Create a new {@link WritableAccountStore} instance.
     *
     * @param states The state to use.
     */
    public WritableAccountStore(@NonNull final WritableStates states) {
        super(states);
        requireNonNull(states);

        this.accountState = states.get("ACCOUNTS");
        this.aliases = states.get("ALIASES");
    }

    /**
     * Persists a new {@link Account} into the state, as well as exporting its ID to the transaction
     * receipt.
     *
     * @param account - the account to be added to modifications in state.
     */
    public void put(@NonNull final Account account) {
        Objects.requireNonNull(account);
        accountState.put(EntityNumVirtualKey.fromLong(account.accountNumber()), Objects.requireNonNull(account));
    }

    /**
     * Persists a new alias linked to the account persisted to state
     *
     * @param alias - the alias to be added to modifications in state.
     * @param accountNum - the account number to be added to modifications in state.
     */
    public void putAlias(@NonNull final String alias, @NonNull final long accountNum) {
        Objects.requireNonNull(alias);
        aliases.put(alias, new EntityNumValue(accountNum));
    }

    /** Commits the changes to the underlying data storage. */
    public void commit() {
        ((WritableKVStateBase) accountState).commit();
        ((WritableKVStateBase) aliases).commit();
    }

    /**
     * Returns the {@link Account} with the given number. If no such account exists, returns {@code
     * Optional.empty()}
     *
     * @param accountNum - the number of the Account to be retrieved.
     */
    @NonNull
    public Optional<Account> get(final long accountNum) {
        requireNonNull(accountNum);
        final var account =
                getAccountLeaf(AccountID.newBuilder().accountNum(accountNum).build());
        return Optional.ofNullable(account);
    }

    /**
     * Returns the {@link Account} with the given number using {@link
     * WritableKVState#getForModify(Comparable K)}. If no such account exists, returns {@code
     * Optional.empty()}
     *
     * @param id - the number of the account to be retrieved.
     */
    @NonNull
    public Optional<Account> getForModify(final AccountID id) {
        // Get the account number based on the account identifier. It may be null.
        final var accountOneOf = id.account();
        final Long accountNum =
                switch (accountOneOf.kind()) {
                    case ACCOUNT_NUM -> accountOneOf.as();
                    case ALIAS -> {
                        final Bytes alias = accountOneOf.as();
                        if (alias.length() == EVM_ADDRESS_LEN && isMirror(alias)) {
                            yield fromMirror(alias);
                        } else {
                            final var entityNum = aliases.getForModify(alias.asUtf8String());
                            yield entityNum == null ? EntityNumValue.DEFAULT.num() : entityNum.num();
                        }
                    }
                    case UNSET -> EntityNumValue.DEFAULT.num();
                };

        return accountNum == null
                ? null
                : Optional.ofNullable(accountState.getForModify(EntityNumVirtualKey.fromLong(accountNum)));
    }

    /**
     * Returns the number of accounts in the state. It also includes modifications in the {@link
     * WritableKVState}.
     *
     * @return the number of accounts in the state.
     */
    public long sizeOfAccountState() {
        return accountState.size();
    }

    /**
     * Returns the number of aliases in the state. It also includes modifications in the {@link
     * WritableKVState}.
     *
     * @return the number of aliases in the state.
     */
    public long sizeOfAliasesState() {
        return aliases.size();
    }

    /**
     * Returns the set of accounts modified in existing state.
     *
     * @return the set of accounts modified in existing state
     */
    @NonNull
    public Set<EntityNumVirtualKey> modifiedAccountsInAccountState() {
        return accountState.modifiedKeys();
    }
}
