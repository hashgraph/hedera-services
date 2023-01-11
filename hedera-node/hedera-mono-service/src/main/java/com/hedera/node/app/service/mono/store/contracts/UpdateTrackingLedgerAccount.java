/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.store.contracts;

import static com.hedera.node.app.service.evm.store.contracts.HederaEvmWorldStateTokenAccount.TOKEN_PROXY_ACCOUNT_NONCE;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.BALANCE;

import com.google.common.base.Preconditions;
import com.hedera.node.app.service.evm.store.models.UpdatedHederaEvmAccount;
import com.hedera.node.app.service.mono.ledger.TransactionalLedger;
import com.hedera.node.app.service.mono.ledger.properties.AccountProperty;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.NavigableMap;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.ModificationNotAllowedException;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.AccountStorageEntry;
import org.hyperledger.besu.evm.account.MutableAccount;

public class UpdateTrackingLedgerAccount<A extends Account> extends UpdatedHederaEvmAccount {
    private final AccountID accountId;

    private TransactionalLedger<AccountID, AccountProperty, HederaAccount> trackingAccounts;

    @Nullable private final A account;
    private boolean storageWasCleared = false;

    public UpdateTrackingLedgerAccount(
            final Address address,
            @Nullable
                    final TransactionalLedger<AccountID, AccountProperty, HederaAccount>
                            trackingAccounts) {
        super(address);
        Preconditions.checkNotNull(address);
        this.accountId = EntityIdUtils.accountIdFromEvmAddress(address);
        this.addressHash = Hash.hash(super.getAddress());
        this.account = null;
        this.trackingAccounts = trackingAccounts;
    }

    @SuppressWarnings("unchecked")
    public UpdateTrackingLedgerAccount(
            final A account,
            @Nullable
                    final TransactionalLedger<AccountID, AccountProperty, HederaAccount>
                            trackingAccounts) {
        super(account.getAddress(), account.getNonce(), account.getBalance());
        Preconditions.checkNotNull(account);
        this.accountId = EntityIdUtils.accountIdFromEvmAddress(account.getAddress());
        this.addressHash =
                account instanceof UpdateTrackingLedgerAccount
                        ? ((UpdateTrackingLedgerAccount<A>) account).addressHash
                        : Hash.hash(account.getAddress());
        this.account = account;
        this.trackingAccounts = trackingAccounts;
    }

    /**
     * The original account over which this tracks updates.
     *
     * @return The original account over which this tracks updates, or {@code null} if this is a
     *     newly created account.
     */
    public A getWrappedAccount() {
        return account;
    }

    /**
     * Whether the code of the account was modified.
     *
     * @return {@code true} if the code was updated.
     */
    public boolean codeWasUpdated() {
        return updatedCode != null;
    }

    public boolean wrappedAccountIsTokenProxy() {
        return account != null && account.getNonce() == TOKEN_PROXY_ACCOUNT_NONCE;
    }

    @Override
    public void setBalance(final Wei value) {
        super.setBalance(value);
        if (trackingAccounts != null) {
            trackingAccounts.set(accountId, BALANCE, value.toLong());
        }
    }

    public void setBalanceFromPropertyChangeObserver(final Wei value) {
        super.setBalance(value);
    }

    @Override
    public Bytes getCode() {
        /* Since the constructor that omits account sets updatedCode to Bytes.ZERO, no risk of NPE here. */
        return updatedCode == null ? account.getCode() : updatedCode;
    }

    @Override
    public Hash getCodeHash() {
        if (updatedCode == null) {
            /* Since the constructor that omits account sets updatedCode to Bytes.ZERO, no risk of NPE here. */
            return account.getCodeHash();
        } else {
            return super.getCodeHash();
        }
    }

    @Override
    public boolean hasCode() {
        /* Since the constructor that omits account sets updatedCode to Bytes.ZERO, no risk of NPE here. */
        return updatedCode == null ? account.hasCode() : !updatedCode.isEmpty();
    }

    @Override
    public UInt256 getStorageValue(final UInt256 key) {
        final UInt256 value = updatedStorage.get(key);
        if (value != null) {
            return value;
        }
        if (storageWasCleared) {
            return UInt256.ZERO;
        }
        return account == null ? UInt256.ZERO : account.getStorageValue(key);
    }

    @Override
    public UInt256 getOriginalStorageValue(final UInt256 key) {
        if (storageWasCleared || account == null) {
            return UInt256.ZERO;
        } else {
            return account.getOriginalStorageValue(key);
        }
    }

    @Override
    public NavigableMap<Bytes32, AccountStorageEntry> storageEntriesFrom(
            final Bytes32 startKeyHash, final int limit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clearStorage() {
        storageWasCleared = true;
        updatedStorage.clear();
    }

    public boolean getStorageWasCleared() {
        return storageWasCleared;
    }

    @Override
    public String toString() {
        var storage = updatedStorage.isEmpty() ? "[not updated]" : updatedStorage.toString();
        if (updatedStorage.isEmpty() && storageWasCleared) {
            storage = "[cleared]";
        }
        return String.format(
                "%s -> {nonce:%s, balance:%s, code:%s, storage:%s }",
                super.getAddress(),
                super.getNonce(),
                super.getBalance(),
                updatedCode == null ? "[not updated]" : updatedCode,
                storage);
    }

    @Override
    public MutableAccount getMutable() throws ModificationNotAllowedException {
        return this;
    }

    public AccountID getAccountId() {
        return accountId;
    }

    public void updateTrackingAccounts(
            final TransactionalLedger<AccountID, AccountProperty, HederaAccount> trackingAccounts) {
        this.trackingAccounts = trackingAccounts;
    }
}
