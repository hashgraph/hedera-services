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

package com.hedera.node.app.service.evm.store.contracts;

import com.hedera.node.app.service.evm.accounts.AccountAccessor;
import com.hedera.node.app.service.evm.store.models.UpdateTrackingAccount;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.evm.worldstate.WorldView;

/**
 * This class implementation help for both "base" and "stacked" {@link WorldUpdater}s.
 *
 * <p>It tracks account changes in {@code deletedAccounts} set and {@code updatedAccounts} map.These
 * collections are used as follows:
 * <ol>
 *  <li>For services, the stored accounts are added or removed from the ledger accordingly.
 *
 *   <li>For evm-module (i.e. mirror-node flow) the ledger's persistence role is executed by a DB
 *       layer and {@code deletedAccounts} and {@code updatedAccounts} serve as a local cache for
 *       for optimization purposes.
 * </ol>
 *
 * @param <A> the most specialized account type to be updated
 * @param <W> the most specialized world updater to be used
 */
public abstract class AbstractLedgerEvmWorldUpdater<W extends WorldView, A extends Account> implements WorldUpdater {

    protected final W world;
    protected final AccountAccessor accountAccessor;
    protected Map<Address, UpdateTrackingAccount<A>> updatedAccounts = new HashMap<>();
    private HederaEvmEntityAccess hederaEvmEntityAccess;

    private TokenAccessor tokenAccessor;

    protected Set<Address> deletedAccounts = new HashSet<>();

    protected AbstractLedgerEvmWorldUpdater(final W world, final AccountAccessor accountAccessor) {
        this.world = world;
        this.accountAccessor = accountAccessor;
    }

    protected AbstractLedgerEvmWorldUpdater(
            final W world,
            final AccountAccessor accountAccessor,
            final TokenAccessor tokenAccessor,
            final HederaEvmEntityAccess hederaEvmEntityAccess) {
        this(world, accountAccessor);
        this.hederaEvmEntityAccess = hederaEvmEntityAccess;
        this.tokenAccessor = tokenAccessor;
    }

    /**
     * Given an address, returns an account that can be mutated <b>with the assurance</b> that these
     * mutations will be tracked in the change-set represented by this {@link WorldUpdater}; and
     * either committed or reverted atomically with all other mutations in the change-set.
     *
     * @param address the address of interest
     * @return a tracked mutable account for the given address
     */
    public abstract A getForMutation(Address address);

    protected W wrappedWorldView() {
        return world;
    }

    @Override
    public MutableAccount createAccount(Address address, long nonce, Wei balance) {
        return null;
    }

    @Override
    public void deleteAccount(Address address) {
        // The method is an intentionally-blank. If given implementation need it can be overridden
    }

    @Override
    public Collection<? extends Account> getTouchedAccounts() {
        return new ArrayList<>(updatedAccounts.values());
    }

    @Override
    public Collection<Address> getDeletedAccountAddresses() {
        return new ArrayList<>(deletedAccounts);
    }

    @Override
    public void revert() {
        // The method is an intentionally-blank. If given implementation need it can be overridden
    }

    @Override
    public void commit() {
        // The method is an intentionally-blank. If given implementation need it can be overridden
    }

    @Override
    public Optional<WorldUpdater> parentUpdater() {
        return Optional.empty();
    }

    @Override
    public WorldUpdater updater() {
        return this;
    }

    public boolean isTokenAddress(Address address) {
        return accountAccessor.isTokenAddress(address);
    }

    @Override
    public Account get(Address address) {
        if (!address.equals(accountAccessor.canonicalAddress(address))) {
            return null;
        }
        final var extantMutable = this.updatedAccounts.get(address);
        if (extantMutable != null) {
            return extantMutable;
        }

        return world.get(address);
    }

    @Override
    public MutableAccount getAccount(Address address) {
        final var extantMutable = this.updatedAccounts.get(address);
        if (extantMutable != null) {
            return extantMutable;
        }

        final var origin = getForMutation(address);
        if (origin == null) {
            return null;
        }
        final var trackedAccount = track(new UpdateTrackingAccount<>(origin, null));
        trackedAccount.setEvmEntityAccess(hederaEvmEntityAccess);

        return trackedAccount;
    }

    public Map<Address, UpdateTrackingAccount<A>> getUpdatedAccounts() {
        return updatedAccounts;
    }

    // FeatureWork public ContractAliases aliases()

    public UpdateTrackingAccount<A> track(final UpdateTrackingAccount<A> account) {
        final var address = account.getAddress();
        updatedAccounts.put(address, account);
        deletedAccounts.remove(address);
        return account;
    }

    public TokenAccessor tokenAccessor() {
        return this.tokenAccessor;
    }
}
