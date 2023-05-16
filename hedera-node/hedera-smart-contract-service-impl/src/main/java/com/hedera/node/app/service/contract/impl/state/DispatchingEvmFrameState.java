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

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.EVM_ADDRESS_LENGTH;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asLongZeroAddress;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.pbjToBesuAddress;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.pbjToBesuHash;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.pbjToTuweniBytes;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.pbjToTuweniUInt256;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.tuweniToPbjBytes;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.node.app.spi.meta.bni.Dispatch;
import com.hedera.node.app.spi.meta.bni.Scope;
import com.hedera.node.app.spi.state.WritableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.jetbrains.annotations.NotNull;

/**
 * An implementation of {@link EvmFrameState} that uses {@link WritableKVState}s to manage
 * contract storage and bytecode, and a {@link Dispatch} for additional influence over the
 * non-contract Hedera state in the current {@link Scope}.
 *
 * <p>Almost every access requires a conversion from a PBJ type to a Besu type. At some
 * point it might be necessary to cache the converted values and invalidate them when
 * the state changes.
 */
public class DispatchingEvmFrameState implements EvmFrameState {
    private static final int NUM_LONG_ZEROS = 12;
    private static final long MISSING_ENTITY_NUMBER = -1;

    private final Dispatch dispatch;
    private final WritableKVState<SlotKey, SlotValue> storage;
    private final WritableKVState<EntityNumber, Bytecode> bytecode;

    public DispatchingEvmFrameState(
            @NonNull final Dispatch dispatch,
            @NonNull final WritableKVState<SlotKey, SlotValue> storage,
            @NonNull final WritableKVState<EntityNumber, Bytecode> bytecode) {
        this.storage = requireNonNull(storage);
        this.bytecode = requireNonNull(bytecode);
        this.dispatch = requireNonNull(dispatch);
    }

    @Override
    public void setStorageValue(final long number, @NotNull final UInt256 key, @NotNull final UInt256 value) {
        final var slotKey = new SlotKey(number, tuweniToPbjBytes(requireNonNull(key)));
        // The previous and next keys in our iterable slot values are managed by logic that
        // runs ONLY when the storage is committed to "base" state; leave them empty here
        final var slotValue = new SlotValue(
                tuweniToPbjBytes(requireNonNull(value)),
                com.hedera.pbj.runtime.io.buffer.Bytes.EMPTY,
                com.hedera.pbj.runtime.io.buffer.Bytes.EMPTY);
        storage.put(slotKey, slotValue);
    }

    @Override
    public @NonNull UInt256 getStorageValue(final long number, @NonNull final UInt256 key) {
        final var slotKey = new SlotKey(number, tuweniToPbjBytes(requireNonNull(key)));
        final var slotValue = storage.get(slotKey);
        return (slotValue == null) ? UInt256.ZERO : pbjToTuweniUInt256(slotValue.value());
    }

    @Override
    public @NonNull UInt256 getOriginalStorageValue(long number, @NotNull UInt256 key) {
        // TODO - when WritableKVState supports getting the original value, use that here
        throw new AssertionError("Not implemented");
    }

    @Override
    public Bytes getCode(final long number) {
        final var numberedBytecode = bytecode.get(new EntityNumber(number));
        if (numberedBytecode == null) {
            return Bytes.EMPTY;
        } else {
            final var code = numberedBytecode.code();
            return code == null ? Bytes.EMPTY : pbjToTuweniBytes(code);
        }
    }

    @Override
    public Hash getCodeHash(final long number) {
        final var numberedBytecode = bytecode.get(new EntityNumber(number));
        if (numberedBytecode == null) {
            return Hash.EMPTY;
        } else {
            final var codeHash = numberedBytecode.codeHash();
            return codeHash == null ? Hash.EMPTY : pbjToBesuHash(codeHash);
        }
    }

    @Override
    public long getNonce(final long number) {
        return validatedAccount(number).ethereumNonce();
    }

    @Override
    public Wei getBalance(long number) {
        return Wei.of(validatedAccount(number).tinybarBalance());
    }

    /**
     * Returns the "priority" EVM address of the account with the given number. This is its
     * 20-byte address if it has one; or the "long-zero" address with the account number
     * as the last 8 bytes of the zero address.
     *
     * @param number the account number
     * @return the priority EVM address of the account
     * @throws IllegalArgumentException if the account does not exist
     */
    @Override
    public Address getAddress(final long number) {
        final var account = validatedAccount(number);
        final var alias = account.alias();
        if (alias.length() == EVM_ADDRESS_LENGTH) {
            return pbjToBesuAddress(alias);
        } else {
            return asLongZeroAddress(number);
        }
    }

    @Override
    @Nullable
    public Account getAccount(final Address address) {
        final var number = maybeMissingNumberOf(address);
        if (number == MISSING_ENTITY_NUMBER) {
            return null;
        }
        final var account = dispatch.getAccount(number);
        if (account == null) {
            return null;
        }
        if (account.deleted() || account.expiredAndPendingRemoval() || isNotPriority(address, account)) {
            return null;
        }
        return new ProxyAccount(number, this);
    }

    @Nullable
    @Override
    public EvmAccount getMutableAccount(Address address) {
        throw new AssertionError("Not implemented");
    }

    private boolean isNotPriority(
            final Address address, final @NonNull com.hedera.hapi.node.state.token.Account account) {
        final var alias = requireNonNull(account).alias();
        return alias != null && alias.length() == EVM_ADDRESS_LENGTH && !address.equals(pbjToBesuAddress(alias));
    }

    private long maybeMissingNumberOf(final Address address) {
        final var explicit = address.toArrayUnsafe();
        if (isLongZeroAddress(explicit)) {
            return longFrom(
                    explicit[12],
                    explicit[13],
                    explicit[14],
                    explicit[15],
                    explicit[16],
                    explicit[17],
                    explicit[18],
                    explicit[19]);
        } else {
            final var entityNumber = dispatch.resolveAlias(com.hedera.pbj.runtime.io.buffer.Bytes.wrap(explicit));
            return (entityNumber == null) ? MISSING_ENTITY_NUMBER : entityNumber.number();
        }
    }

    private boolean isLongZeroAddress(final byte[] explicit) {
        for (int i = 0; i < NUM_LONG_ZEROS; i++) {
            if (explicit[i] != 0) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("java:S107")
    private long longFrom(
            final byte b1,
            final byte b2,
            final byte b3,
            final byte b4,
            final byte b5,
            final byte b6,
            final byte b7,
            final byte b8) {
        return (b1 & 0xFFL) << 56
                | (b2 & 0xFFL) << 48
                | (b3 & 0xFFL) << 40
                | (b4 & 0xFFL) << 32
                | (b5 & 0xFFL) << 24
                | (b6 & 0xFFL) << 16
                | (b7 & 0xFFL) << 8
                | (b8 & 0xFFL);
    }

    private boolean isToken(final long number) {
        // If the token is deleted or expired, the system contract executed by the redirect
        // bytecode will fail with a more meaningful error message, so don't check that here
        return dispatch.getToken(number) != null;
    }

    private com.hedera.hapi.node.state.token.Account validatedAccount(final long number) {
        final var account = dispatch.getAccount(number);
        if (account == null) {
            throw new IllegalArgumentException("No account has number " + number);
        }
        return account;
    }
}
