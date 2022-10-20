/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.store.contracts;

import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.store.contracts.WorldStateTokenAccount.TOKEN_PROXY_ACCOUNT_NONCE;

import com.google.common.base.Preconditions;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.migration.HederaAccount;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import javax.annotation.Nullable;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.ModificationNotAllowedException;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.AccountStorageEntry;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.account.MutableAccount;

public class UpdateTrackingLedgerAccount<A extends Account> implements MutableAccount, EvmAccount {
    private final Hash addressHash;
    private final Address address;
    private final AccountID accountId;
    private final NavigableMap<UInt256, UInt256> updatedStorage;

    private TransactionalLedger<AccountID, AccountProperty, HederaAccount> trackingAccounts;

    @Nullable private A account;
    private long nonce;
    private Wei balance;
    @Nullable private Bytes updatedCode;
    @Nullable private Hash updatedCodeHash;
    private boolean storageWasCleared = false;

    public UpdateTrackingLedgerAccount(
            final Address address,
            @Nullable
                    final TransactionalLedger<AccountID, AccountProperty, HederaAccount>
                            trackingAccounts) {
        Preconditions.checkNotNull(address);
        this.address = address;
        this.accountId = EntityIdUtils.accountIdFromEvmAddress(address);
        this.addressHash = Hash.hash(this.address);
        this.account = null;
        this.nonce = 0L;
        this.balance = Wei.ZERO;
        this.updatedCode = Bytes.EMPTY;
        this.updatedStorage = new TreeMap<>();
        this.trackingAccounts = trackingAccounts;
    }

    @SuppressWarnings("unchecked")
    public UpdateTrackingLedgerAccount(
            final A account,
            @Nullable
                    final TransactionalLedger<AccountID, AccountProperty, HederaAccount>
                            trackingAccounts) {
        Preconditions.checkNotNull(account);
        this.address = account.getAddress();
        this.accountId = EntityIdUtils.accountIdFromEvmAddress(address);
        this.addressHash =
                account instanceof UpdateTrackingLedgerAccount
                        ? ((UpdateTrackingLedgerAccount<A>) account).addressHash
                        : Hash.hash(this.address);
        this.account = account;
        this.nonce = account.getNonce();
        this.balance = account.getBalance();
        this.updatedStorage = new TreeMap<>();
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

    /**
     * A map of the storage entries that were modified.
     *
     * @return a map containing all entries that have been modified. This <b>may</b> contain entries
     *     with a value of 0 to signify deletion.
     */
    @Override
    public Map<UInt256, UInt256> getUpdatedStorage() {
        return updatedStorage;
    }

    @Override
    public Address getAddress() {
        return address;
    }

    @Override
    public Hash getAddressHash() {
        return addressHash;
    }

    @Override
    public long getNonce() {
        return nonce;
    }

    @Override
    public void setNonce(final long value) {
        this.nonce = value;
    }

    public boolean wrappedAccountIsTokenProxy() {
        return account != null && account.getNonce() == TOKEN_PROXY_ACCOUNT_NONCE;
    }

    @Override
    public Wei getBalance() {
        return balance;
    }

    @Override
    public void setBalance(final Wei value) {
        this.balance = value;
        if (trackingAccounts != null) {
            trackingAccounts.set(accountId, BALANCE, value.toLong());
        }
    }

    public void setBalanceFromPropertyChangeObserver(final Wei value) {
        this.balance = value;
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
            /* This optimization is actually important to avoid DOS attacks that would otherwise force
             * frequent re-hashing of the updated code. */
            if (updatedCodeHash == null) {
                updatedCodeHash = Hash.hash(updatedCode);
            }
            return updatedCodeHash;
        }
    }

    @Override
    public boolean hasCode() {
        /* Since the constructor that omits account sets updatedCode to Bytes.ZERO, no risk of NPE here. */
        return updatedCode == null ? account.hasCode() : !updatedCode.isEmpty();
    }

    @Override
    public void setCode(final Bytes code) {
        this.updatedCode = code;
        this.updatedCodeHash = null;
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
    public void setStorageValue(final UInt256 key, final UInt256 value) {
        updatedStorage.put(key, value);
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
                address,
                nonce,
                balance,
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
