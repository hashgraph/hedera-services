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

import static com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases.EVM_ADDRESS_LEN;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.mono.state.virtual.EntityNumValue;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;
import java.util.Set;

/**
 * Provides write methods for modifying underlying data storage mechanisms for working with
 * accounts.
 *
 * <p>This class is not exported from the module. It is an internal implementation detail. This
 * class is not complete, it will be extended with other methods like remove, update etc.,
 */
public class WritableAccountStore extends ReadableAccountStoreImpl {
    /** The underlying data storage class that holds the account data. */
    private final WritableKVState<AccountID, Account> accountState;
    /** The underlying data storage class that holds the aliases data built from the state. */
    private final WritableKVState<String, AccountID> aliases;

    /**
     * Create a new {@link WritableAccountStore} instance.
     *
     * @param states The state to use.
     */
    public WritableAccountStore(@NonNull final WritableStates states) {
        super(states);
        requireNonNull(states);

        this.accountState = states.get(TokenServiceImpl.ACCOUNTS_KEY);
        this.aliases = states.get(TokenServiceImpl.ALIASES_KEY);
    }

    /**
     * Persists a new {@link Account} into the state, as well as exporting its ID to the transaction
     * receipt.
     *
     * @param account - the account to be added to modifications in state.
     */
    public void put(@NonNull final Account account) {
        Objects.requireNonNull(account);
        accountState.put(
                AccountID.newBuilder().accountNum(account.accountNumber()).build(), Objects.requireNonNull(account));
    }

    /**
     * Persists a new alias linked to the account persisted to state
     *
     * @param alias - the alias to be added to modifications in state.
     * @param accountNum - the account number to be added to modifications in state.
     */
    public void putAlias(@NonNull final String alias, final long accountNum) {
        Objects.requireNonNull(alias);
        aliases.put(alias, AccountID.newBuilder().accountNum(accountNum).build());
    }

    /**
     * Returns the {@link Account} with the given number. If no such account exists, returns {@code
     * null}
     *
     * @param accountID - the id of the Account to be retrieved.
     */
    @Nullable
    public Account get(@NonNull final AccountID accountID) {
        return getAccountLeaf(requireNonNull(accountID));
    }

    /**
     * Returns the {@link Account} with the given {@link AccountID}.
     * If no such account exists, returns {@code Optional.empty()}
     *
     * @param id - the number of the account to be retrieved.
     */
    @Nullable
    public Account getForModify(@NonNull final AccountID id) {
        requireNonNull(id);
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
                            final var accountID = aliases.get(alias.asUtf8String());
                            yield accountID == null ? AccountID.DEFAULT.accountNum() : accountID.accountNum();
                        }
                    }
                    case UNSET -> EntityNumValue.DEFAULT.num();
                };

        return accountNum == null
                ? null
                : accountState.getForModify(
                        AccountID.newBuilder().accountNum(accountNum).build());
    }

    /**
     * Removes the {@link Account} with the given {@link AccountID} from the state.
     * This will add value of the accountId to num in the modifications in state.
     * @param accountID - the account id of the account to be removed.
     */
    public void remove(@NonNull final AccountID accountID) {
        accountState.remove(accountID);
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
    public Set<AccountID> modifiedAccountsInState() {
        return accountState.modifiedKeys();
    }

    /**
     * Returns the set of aliases modified in existing state.
     *
     * @return the set of aliases modified in existing state
     */
    @NonNull
    public Set<String> modifiedAliasesInState() {
        return aliases.modifiedKeys();
    }
}
