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

import static com.hedera.node.app.service.token.AliasUtils.isAlias;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.contract.ContractNonceInfo;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.token.api.ContractChangeSummary;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
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
    /**
     * Create a new {@link WritableAccountStore} instance.
     *
     * @param states The state to use.
     */
    public WritableAccountStore(@NonNull final WritableStates states) {
        super(states);
    }

    @Override
    protected WritableKVState<AccountID, Account> accountState() {
        return super.accountState();
    }

    @Override
    protected WritableKVState<ProtoBytes, AccountID> aliases() {
        return super.aliases();
    }

    /**
     * Persists a new {@link Account} into the state, as well as exporting its ID to the transaction
     * receipt.
     *
     * @param account - the account to be added to modifications in state.
     */
    public void put(@NonNull final Account account) {
        Objects.requireNonNull(account);
        accountState().put(account.accountIdOrThrow(), account);
    }

    /**
     * Persists a new alias linked to the account persisted to state
     *
     * @param alias - the alias to be added to modifications in state.
     * @param accountId - the account number to be added to modifications in state.
     */
    public void putAlias(@NonNull final Bytes alias, final AccountID accountId) {
        requireNonNull(alias);

        // The specified account ID must always have an account number, and not an alias. If it doesn't have
        // an account number, or if it has both an account number and alias, then we are going to throw an
        // exception. That should never happen.
        if (isAlias(accountId)) {
            throw new IllegalArgumentException("The account ID used with putAlias must have a number and not an alias");
        }

        // We should *never* see an empty alias. If we do, it is problem with the code.
        if (alias.length() == 0) {
            throw new IllegalArgumentException("Alias cannot be empty");
        }

        aliases().put(new ProtoBytes(alias), accountId);
    }

    /**
     * Removes an alias from the cache. This should only ever happen as the result of a delete operation.
     * @param alias The alias of the account to remove.
     */
    public void removeAlias(@NonNull final Bytes alias) {
        requireNonNull(alias);
        // We really shouldn't ever see an empty alias. But, if we do, we don't want to do any additional work.
        // FUTURE: It might be worth adding a log statement here if we see an empty alias, but maybe not.
        if (alias.length() > 0) {
            aliases().remove(new ProtoBytes(alias));
        }
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
        final var accountId = unaliasedAccountId(id);
        return accountId == null ? null : accountState().getForModify(accountId);
    }

    /**
     * Gets the original value associated with the given accountId before any modifications were made to
     * it. The returned value will be {@code null} if the accountId does not exist.
     *
     * @param id The accountId. Cannot be null, otherwise an exception is thrown.
     * @return The original value, or null if there is no such accountId in the state
     * @throws NullPointerException if the accountId is null.
     */
    @Nullable
    public Account getOriginalValue(@NonNull final AccountID id) {
        requireNonNull(id);
        final var accountId = unaliasedAccountId(id);
        return accountId == null ? null : accountState().getOriginalValue(accountId);
    }

    /**
     * Removes the {@link Account} with the given {@link AccountID} from the state.
     * This will add value of the accountId to num in the modifications in state.
     * @param accountID - the account id of the account to be removed.
     */
    public void remove(@NonNull final AccountID accountID) {
        accountState().remove(accountID);
    }

    /**
     * Returns the number of accounts in the state. It also includes modifications in the {@link
     * WritableKVState}.
     *
     * @return the number of accounts in the state.
     */
    public long sizeOfAccountState() {
        return accountState().size();
    }

    /**
     * Returns the number of aliases in the state. It also includes modifications in the {@link
     * WritableKVState}.
     *
     * @return the number of aliases in the state.
     */
    public long sizeOfAliasesState() {
        return aliases().size();
    }

    /**
     * Returns the set of accounts modified in existing state.
     *
     * @return the set of accounts modified in existing state
     */
    @NonNull
    public Set<AccountID> modifiedAccountsInState() {
        return accountState().modifiedKeys();
    }

    /**
     * Returns a summary of the changes made to contract state.
     *
     * @return a summary of the changes made to contract state
     */
    public @NonNull ContractChangeSummary summarizeContractChanges() {
        final List<ContractID> newContractIds = new ArrayList<>();
        final List<ContractNonceInfo> updates = new ArrayList<>();
        accountState().modifiedKeys().forEach(accountId -> {
            final var newAccount = accountState().get(accountId);
            if (newAccount != null && newAccount.smartContract()) {
                final var oldAccount = accountState().getOriginalValue(accountId);
                if (oldAccount == null || oldAccount.ethereumNonce() != newAccount.ethereumNonce()) {
                    final var contractId = ContractID.newBuilder()
                            .contractNum(accountId.accountNumOrThrow())
                            .build();
                    updates.add(new ContractNonceInfo(contractId, newAccount.ethereumNonce()));
                    if (oldAccount == null) {
                        newContractIds.add(contractId);
                    }
                }
            }
        });
        return new ContractChangeSummary(newContractIds, updates);
    }

    /**
     * Returns the set of aliases modified in existing state.
     *
     * @return the set of aliases modified in existing state
     */
    @NonNull
    public Set<ProtoBytes> modifiedAliasesInState() {
        return aliases().modifiedKeys();
    }
}
