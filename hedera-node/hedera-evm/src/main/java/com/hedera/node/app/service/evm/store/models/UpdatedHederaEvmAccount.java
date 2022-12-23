/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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

import static org.apache.tuweni.units.bigints.UInt256.ZERO;

import com.hedera.node.app.service.evm.store.contracts.HederaEvmEntityAccess;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.ModificationNotAllowedException;
import org.hyperledger.besu.evm.account.AccountStorageEntry;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.account.MutableAccount;

public class UpdatedHederaEvmAccount implements MutableAccount, EvmAccount {
    protected Hash addressHash;
    private Address address;
    private long nonce;
    private Wei balance;
    private HederaEvmEntityAccess hederaEvmEntityAccess;
    protected final NavigableMap<UInt256, UInt256> updatedStorage;

    @Nullable protected Bytes updatedCode;
    @Nullable private Hash updatedCodeHash;

    public UpdatedHederaEvmAccount(Address address) {
        this(address, 0L, Wei.ZERO);
        this.updatedCode = Bytes.EMPTY;
    }

    public UpdatedHederaEvmAccount(Address address, long nonce, Wei balance) {
        this.address = address;
        this.addressHash = Hash.hash(address);
        this.nonce = nonce;
        this.balance = balance;
        this.updatedStorage = new TreeMap<>();
        this.updatedCode = null;
    }

    @Override
    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    @Override
    public Hash getAddressHash() {
        return addressHash;
    }

    @Override
    public Wei getBalance() {
        return balance;
    }

    public void setBalance(final Wei amount) {
        this.balance = amount;
    }

    @Override
    public Bytes getCode() {
        return updatedCode;
    }

    @Override
    public Hash getCodeHash() {
        if (updatedCodeHash == null) {
            updatedCodeHash = Hash.hash(updatedCode);
        }
        return updatedCodeHash;
    }

    @Override
    public long getNonce() {
        return nonce;
    }

    public void setNonce(final long value) {
        this.nonce = value;
    }

    @Override
    public UInt256 getOriginalStorageValue(UInt256 key) {
        return getStorageValue(key);
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
    public UInt256 getStorageValue(UInt256 key) {
        UInt256 value = updatedStorage.get(key);
        if (value != null) {
            return value;
        } else if (hederaEvmEntityAccess != null) {
            value = UInt256.fromBytes(hederaEvmEntityAccess.getStorage(address, key.toBytes()));
        }
        if (value != null) {
            setStorageValue(key, value);
            return value;
        }
        return ZERO;
    }

    @Override
    public void setCode(Bytes code) {
        this.updatedCode = code;
        this.updatedCodeHash = null;
    }

    @Override
    public void setStorageValue(final UInt256 key, final UInt256 value) {
        updatedStorage.put(key, value);
    }

    @Override
    public void clearStorage() {
        updatedStorage.clear();
    }

    @Override
    public NavigableMap<Bytes32, AccountStorageEntry> storageEntriesFrom(
            Bytes32 startKeyHash, int limit) {
        return Collections.emptyNavigableMap();
    }

    @Override
    public MutableAccount getMutable() throws ModificationNotAllowedException {
        return this;
    }

    public void setEvmEntityAccess(HederaEvmEntityAccess hederaEvmEntityAccess) {
        this.hederaEvmEntityAccess = hederaEvmEntityAccess;
    }
}
