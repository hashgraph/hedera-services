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
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.ModificationNotAllowedException;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

/**
 * An {@link Account} implementation that reads and writes data for the given entity number
 * by proxying calls to the given {@link EvmFrameState}.
 *
 * <p>The {@link EvmFrameState} reflects all changes to the state of the world that have been
 * made up to and including the current {@link org.hyperledger.besu.evm.frame.MessageFrame}.
 *
 * <p>There is no implementation difference between mutable and immutable proxy accounts,
 * since all changes are made to the {@link EvmFrameState} and not to the proxy account.
 * So we just need one implementation, which the EVM will request from {@link WorldUpdater}s
 * as an immutable {@link Account} in read-only contexts; and as a mutable {@link EvmAccount}
 * in a context where writes are allowed.
 */
public class ProxyEvmAccount extends AbstractMutableEvmAccount {
    protected final long number;
    protected final EvmFrameState state;

    public ProxyEvmAccount(final long number, @NonNull final EvmFrameState state) {
        this.state = state;
        this.number = number;
    }

    @Override
    public Address getAddress() {
        return state.getAddress(number);
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

    @Override
    public @NonNull MutableAccount getMutable() throws ModificationNotAllowedException {
        return this;
    }

    @Override
    public void setNonce(final long value) {
        state.setNonce(number, value);
    }

    @Override
    public void setCode(@NonNull final Bytes code) {
        state.setCode(number, code);
    }

    @Override
    public void setStorageValue(@NonNull final UInt256 key, @NonNull final UInt256 value) {
        state.setStorageValue(number, key, value);
    }
}
