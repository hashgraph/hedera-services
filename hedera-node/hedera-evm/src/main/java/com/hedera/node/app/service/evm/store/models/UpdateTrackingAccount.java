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

package com.hedera.node.app.service.evm.store.models;

import static com.hedera.node.app.service.evm.store.contracts.HederaEvmWorldStateTokenAccount.TOKEN_PROXY_ACCOUNT_NONCE;

import com.google.common.base.Preconditions;
import com.hedera.node.app.service.evm.store.UpdateAccountTracker;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmEntityAccess;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.AccountStorageEntry;
import org.hyperledger.besu.evm.account.MutableAccount;

/**
 * A mutable and updatable implementation of the {@link MutableAccount} interface, that tracks account updates since the
 * creation of the updated it's linked to.
 * <p>
 * Contains {@code updateAccountTracker} for immediate set of balance in the world state. Note that in practice this
 * only track the modified account values, but doesn't remind if they were modified or not.
 */
public class UpdateTrackingAccount<A extends Account> implements MutableAccount {
    @Nullable
    protected final A account;

    private final Address address;
    private final Hash addressHash;
    private final UpdateAccountTracker updateAccountTracker;
    private final NavigableMap<UInt256, UInt256> updatedStorage;

    @Nullable
    protected Bytes updatedCode;

    private long nonce;
    private Wei balance;
    private HederaEvmEntityAccess hederaEvmEntityAccess;
    private boolean storageWasCleared = false;

    @Nullable
    private Hash updatedCodeHash;

    public UpdateTrackingAccount(final Address address, @Nullable final UpdateAccountTracker updateAccountTracker) {
        Preconditions.checkNotNull(address);
        this.address = address;
        addressHash = Hash.hash(address);
        account = null;
        balance = Wei.ZERO;
        nonce = 0L;
        updatedCode = Bytes.EMPTY;
        updatedStorage = new TreeMap<>();
        this.updateAccountTracker = updateAccountTracker;
    }

    @SuppressWarnings("unchecked")
    public UpdateTrackingAccount(final A account, @Nullable final UpdateAccountTracker updateAccountTracker) {
        Preconditions.checkNotNull(account);
        this.account = account;
        address = account.getAddress();
        this.addressHash = account instanceof UpdateTrackingAccount
                ? ((UpdateTrackingAccount<A>) account).addressHash
                : Hash.hash(account.getAddress());
        this.updateAccountTracker = updateAccountTracker;
        balance = account.getBalance();
        nonce = account.getNonce();
        updatedStorage = new TreeMap<>();
    }

    /**
     * The original account over which this tracks updates.
     *
     * @return The original account over which this tracks updates, or {@code null} if this is a newly created account.
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

    /**
     * A map of the storage entries that were modified.
     *
     * @return a map containing all entries that have been modified. This <b>may</b> contain entries with a value of 0
     * to signify deletion.
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
    public void setNonce(final long nonce) {
        this.nonce = nonce;
        if (updateAccountTracker != null) {
            updateAccountTracker.setNonce(address, nonce);
        }
    }

    @Override
    public Wei getBalance() {
        return balance;
    }

    @Override
    public void setBalance(final Wei value) {
        balance = value;
        if (updateAccountTracker != null) {
            updateAccountTracker.setBalance(address, value.toLong());
        }
    }

    public void setBalanceFromPropertyChangeObserver(final Wei value) {
        balance = value;
    }

    @Override
    public Bytes getCode() {
        /* Since the constructor that omits account sets updatedCode to Bytes.ZERO, no risk of NPE here. */
        return updatedCode == null ? account.getCode() : updatedCode;
    }

    @Override
    public void setCode(Bytes code) {
        this.updatedCode = code;
        this.updatedCodeHash = null;
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
    public UInt256 getStorageValue(final UInt256 key) {
        final UInt256 value = updatedStorage.get(key);
        if (value != null) {
            return value;
        }
        if (storageWasCleared) {
            return UInt256.ZERO;
        }
        // Branching for the evm-module lib flow, where data source is Ledgers alternative
        if (hederaEvmEntityAccess != null) {
            return getStorageValueEvmFlow(key);
        }

        return account == null ? UInt256.ZERO : account.getStorageValue(key);
    }

    private UInt256 getStorageValueEvmFlow(UInt256 key) {
        final var value = UInt256.fromBytes(hederaEvmEntityAccess.getStorage(address, key.toBytes()));

        setStorageValue(key, value);
        return value;
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
    public NavigableMap<Bytes32, AccountStorageEntry> storageEntriesFrom(final Bytes32 startKeyHash, final int limit) {
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
                address, nonce, balance, updatedCode == null ? "[not updated]" : updatedCode, storage);
    }

    @Override
    public void becomeImmutable() {
        throw new UnsupportedOperationException("Not Implemented Yet");
    }

    public void setEvmEntityAccess(HederaEvmEntityAccess hederaEvmEntityAccess) {
        this.hederaEvmEntityAccess = hederaEvmEntityAccess;
    }

    public UpdateAccountTracker getAccountTracker() {
        return updateAccountTracker;
    }
}
