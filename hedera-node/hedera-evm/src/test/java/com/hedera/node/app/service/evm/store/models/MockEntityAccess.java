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
package com.hedera.node.app.service.evm.store.models;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmEntityAccess;
import java.util.HashMap;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

public class MockEntityAccess implements HederaEvmEntityAccess {
    private final Map<Address, Boolean> accounts = new HashMap<>();

    @Override
    public boolean isUsable(Address address) {
        return false;
    }

    @Override
    public long getBalance(Address address) {
        return 100;
    }

    @Override
    public boolean isTokenAccount(Address address) {
        final var isToken = accounts.get(address);
        return isToken != null ? isToken : false;
    }

    @Override
    public ByteString alias(Address address) {
        return null;
    }

    @Override
    public boolean isExtant(Address address) {
        return false;
    }

    @Override
    public Bytes getStorage(Address address, Bytes key) {
        return null;
    }

    @Override
    public Bytes fetchCodeIfPresent(Address address) {
        return null;
    }

    public void setIsTokenFor(final Address address) {
        accounts.put(address, true);
    }
}
