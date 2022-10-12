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
package com.hedera.services.store.contracts;

import static com.hedera.services.utils.EntityIdUtils.accountIdFromEvmAddress;
import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;

import com.hederahashgraph.api.proto.java.AccountID;
import java.util.NavigableMap;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.AccountStorageEntry;

public class WorldStateAccount implements Account {
    private static final Code EMPTY_CODE =
            Code.createLegacyCode(Bytes.EMPTY, Hash.hash(Bytes.EMPTY));

    private final Wei balance;
    private final Address address;
    private final AccountID account;
    private final CodeCache codeCache;
    private final EntityAccess entityAccess;

    public WorldStateAccount(
            final Address address,
            final Wei balance,
            final CodeCache codeCache,
            final EntityAccess entityAccess) {
        this.balance = balance;
        this.address = address;
        this.codeCache = codeCache;
        this.entityAccess = entityAccess;

        this.account = accountIdFromEvmAddress(address);
    }

    @Override
    public Address getAddress() {
        return address;
    }

    @Override
    public Hash getAddressHash() {
        return Hash.EMPTY;
    }

    @Override
    public long getNonce() {
        return 0;
    }

    @Override
    public Wei getBalance() {
        return balance;
    }

    @Override
    public Bytes getCode() {
        return getCodeInternal().getBytes();
    }

    @Override
    public boolean hasCode() {
        return !getCode().isEmpty();
    }

    @Override
    public Hash getCodeHash() {
        return getCodeInternal().getCodeHash();
    }

    @Override
    public UInt256 getStorageValue(final UInt256 key) {
        return entityAccess.getStorage(asTypedEvmAddress(account), key);
    }

    @Override
    public UInt256 getOriginalStorageValue(final UInt256 key) {
        return getStorageValue(key);
    }

    @Override
    public NavigableMap<Bytes32, AccountStorageEntry> storageEntriesFrom(
            final Bytes32 startKeyHash, final int limit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return "AccountState"
                + "{"
                + "address="
                + getAddress()
                + ", "
                + "nonce="
                + getNonce()
                + ", "
                + "balance="
                + getBalance()
                + ", "
                + "codeHash="
                + getCodeHash()
                + ", "
                + "}";
    }

    private Code getCodeInternal() {
        final var code = codeCache.getIfPresent(address);
        return (code == null) ? EMPTY_CODE : code;
    }
}
