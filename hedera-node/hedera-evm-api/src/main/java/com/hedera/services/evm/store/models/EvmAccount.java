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
package com.hedera.services.evm.store.models;

import java.util.NavigableMap;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.AccountStorageEntry;

public class EvmAccount implements Account {

    private final Address address;

    public EvmAccount(Address address) {
        this.address = address;
    }

    @Override
    public Address getAddress() {
        return address;
    }

    @Override
    public Hash getAddressHash() {
        return null;
    }

    @Override
    public long getNonce() {
        return 0;
    }

    @Override
    public Wei getBalance() {
        return null;
    }

    @Override
    public Bytes getCode() {
        return null;
    }

    @Override
    public Hash getCodeHash() {
        return null;
    }

    @Override
    public UInt256 getStorageValue(UInt256 key) {
        return null;
    }

    @Override
    public UInt256 getOriginalStorageValue(UInt256 key) {
        return null;
    }

    @Override
    public NavigableMap<Bytes32, AccountStorageEntry> storageEntriesFrom(
            Bytes32 startKeyHash, int limit) {
        return null;
    }
}
