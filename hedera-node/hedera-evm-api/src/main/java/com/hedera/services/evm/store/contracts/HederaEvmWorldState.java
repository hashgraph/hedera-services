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
package com.hedera.services.evm.store.contracts;

import com.hedera.services.evm.contracts.execution.EvmProperties;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;

public abstract class HederaEvmWorldState implements HederaEvmMutableWorldState {

    private final HederaEvmEntityAccess hederaEvmEntityAccess;
    private final EvmProperties evmProperties;
    private final AbstractCodeCache abstractCodeCache;

    public HederaEvmWorldState(
            HederaEvmEntityAccess hederaEvmEntityAccess,
            EvmProperties evmProperties,
            AbstractCodeCache abstractCodeCache) {
        this.hederaEvmEntityAccess = hederaEvmEntityAccess;
        this.evmProperties = evmProperties;
        this.abstractCodeCache = abstractCodeCache;
    }

    public Account get(final Address address) {
        if (address == null) {
            return null;
        }
        if (hederaEvmEntityAccess.isTokenAccount(address)
                && evmProperties.isRedirectTokenCallsEnabled()) {
            return new HederaEvmWorldStateTokenAccount(address);
        }
        if (!hederaEvmEntityAccess.isUsable(address)) {
            return null;
        }
        final long balance = hederaEvmEntityAccess.getBalance(address);
        return new WorldStateAccount(
                address, Wei.of(balance), abstractCodeCache, hederaEvmEntityAccess);
    }

    @Override
    public abstract HederaEvmWorldUpdater updater();

    @Override
    public Hash rootHash() {
        return Hash.EMPTY;
    }

    @Override
    public Hash frontierRootHash() {
        return rootHash();
    }

    @Override
    public Stream<StreamableAccount> streamAccounts(Bytes32 startKeyHash, int limit) {
        return Stream.empty();
    }
}
