/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.state;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.NavigableMap;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.AccountStorageEntry;

/**
 * An {@link Account} implementation that gives access to the contract or account with the
 * given entity number relative to the given {@link EvmFrameState}.
 *
 * <p>The {@link EvmFrameState} reflects all changes to the state of the world that have been
 * made up to and including the current {@link org.hyperledger.besu.evm.frame.MessageFrame}.
 *
 * <p>Since property access always delegates to the {@link EvmFrameState}, client code may
 * interleave usage of a {@link ProxyAccount} instance in the same dynamic context that it
 * uses a {@link MutableProxyAccount} instance for the same account; although it would be rare
 * for this to make sense.
 */
public class ProxyAccount implements Account {
    protected final long number;
    protected final EvmFrameState state;

    public ProxyAccount(final long number, @NonNull final EvmFrameState state) {
        this.state = state;
        this.number = number;
    }

    @Override
    public Address getAddress() {
        return state.getAddress(number);
    }

    /**
     * Unlike in Besu, we don't store the address hash in state trie (c.f. {@link Account#getAddressHash()} javadoc);
     * and also don't support {@link org.hyperledger.besu.evm.worldstate.WorldState#streamAccounts(Bytes32, int)}. So
     * there is actually no reason to implement this method in Hedera.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public Hash getAddressHash() {
        throw new UnsupportedOperationException("getAddressHash");
    }

    @Override
    public long getNonce() {
        return state.getNonce(number);
    }

    @Override
    public Wei getBalance() {
        return state.getBalance(number);
    }

    @Override
    public @NonNull Bytes getCode() {
        return state.getCode(number);
    }

    @Override
    public @NonNull Hash getCodeHash() {
        return state.getCodeHash(number);
    }

    @Override
    public @NonNull UInt256 getStorageValue(@NonNull final UInt256 key) {
        return state.getStorageValue(number, key);
    }

    @Override
    public @NonNull UInt256 getOriginalStorageValue(@NonNull final UInt256 key) {
        return state.getOriginalStorageValue(number, key);
    }

    /**
     * Besu uses this method for storage validation in test, but we don't have a practical way to implement it
     * at the moment, since this would require iterating through the storage linked list.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public NavigableMap<Bytes32, AccountStorageEntry> storageEntriesFrom(Bytes32 startKeyHash, int limit) {
        throw new UnsupportedOperationException("storageEntriesFrom");
    }
}
