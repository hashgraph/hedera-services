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

import com.hedera.node.app.service.contract.impl.ContractServiceImpl;
import com.hedera.node.app.spi.meta.bni.Scope;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.EvmAccount;

/**
 * Exposes the full Hedera state that may be read and changed <b>directly </b> from an EVM frame,
 * using data types appropriate to the Besu EVM API.
 *
 * <p>Of course, implementations must still reflect the state changes made by any calls to
 * the {@code 0x167} system contract from within the EVM frame. But those changes are, in a
 * sense, the result of "escaping" the EVM; so they are not part of this API.
 *
 * <p>Since almost all return values require translating from Hedera data types to Besu data
 * types, implementations might need to do internal caching to avoid excessive conversions.
 */
public interface EvmFrameState {
    /**
     * Constructs an {@link EvmFrameState} from the given {@link Scope}.
     *
     * @param scope the scope
     * @return an {@link EvmFrameState} that client code can use to manipulate scoped state via Besu data types
     */
    static EvmFrameState from(@NonNull final Scope scope) {
        return new DispatchingEvmFrameState(
                scope.dispatch(),
                scope.writableContractState().get(ContractServiceImpl.STORAGE_KEY),
                scope.writableContractState().get(ContractServiceImpl.BYTECODE_KEY));
    }

    /**
     * Returns the read-only account with the given address, or {@code null} if the account is missing,
     * deleted, or expired; or if this get() used the account's "long zero" address and not is priority
     * EVM address.
     *
     * @param address the account address
     * @return the read-only account; or {@code null} if the account is missing, deleted, or expired
     */
    @Nullable
    Account getAccount(Address address);

    /**
     * Returns the mutable account with the given address, or {@code null} if the account is missing,
     * deleted, or expired; or if this get() used the account's "long zero" address and not is priority
     * EVM address.
     *
     * @param address the account address
     * @return the mutable account; or {@code null} if the account is missing, deleted, or expired
     */
    @Nullable
    EvmAccount getMutableAccount(Address address);

    @NonNull
    UInt256 getStorageValue(long number, @NonNull UInt256 key);

    void setStorageValue(long number, @NonNull UInt256 key, @NonNull UInt256 value);

    @NonNull
    UInt256 getOriginalStorageValue(long number, @NonNull UInt256 key);

    /**
     * Returns the code for the account with the given number, or empty code if no such code exists.
     *
     * @param number the account number
     * @return the code for the account
     */
    @NonNull
    Bytes getCode(long number);

    /**
     * Sets the code for the contract with the given number. Only used during contract creation.
     *
     * @param number the contract number
     * @param code the new code
     */
    void setCode(long number, @NonNull Bytes code);

    /**
     * Returns the redirect bytecode for the token with the given address, which must be a long-zero address.
     *
     * <p>Since a {@link TokenEvmAccount} never needs its Hedera entity number, we may as well use
     * the long-zero address there, and here.
     *
     * @param address the token long-zero address
     * @return the redirect code for the token
     */
    @NonNull
    Bytes getTokenRedirectCode(@NonNull Address address);

    @NonNull
    Hash getCodeHash(long number);

    /**
     * Returns the hash of the redirect bytecode for the token with the given address, which must be a
     * long-zero address.
     *
     * <p>Since a {@link TokenEvmAccount} never needs its Hedera entity number, we may as well use
     * the long-zero address there, and here.
     *
     * @param address the token long-zero address
     * @return the redirect code for the token
     */
    @NonNull
    Hash getTokenRedirectCodeHash(@NonNull Address address);

    /**
     * Returns the nonce for the account with the given number.
     *
     * @param number the account number
     * @return the nonce
     */
    long getNonce(long number);

    /**
     * Sets the nonce for the account with the given number.
     *
     * @param number the account number
     * @param nonce the new nonce
     */
    void setNonce(long number, long nonce);

    /**
     * Returns the balance of the account with the given number.
     *
     * @param number the account number
     * @return the balance
     */
    Wei getBalance(long number);

    /**
     * Returns the "priority" EVM address of the account with the given number, or null if the
     * account has been deleted.
     *
     * <p>The priority address is its 20-byte alias if applicable; or else the "long-zero" address
     * with the account number as the last 8 bytes of the zero address.
     *
     * @param number the account number
     * @return the priority EVM address of the account, or null if the account has been deleted
     * @throws IllegalArgumentException if the account does not exist
     */
    @Nullable
    Address getAddress(long number);

    /**
     * Returns the full list of account-scoped storage changes in the current scope.
     *
     * @return the full list of account-scoped storage changes
     */
    @NonNull
    List<StorageChanges> getPendingStorageChanges();

    /**
     * Returns the size of the underlying K/V state for contract storage.
     *
     * @return the size of the K/V state
     */
    long getKvStateSize();

    /**
     * Returns the rent factors for the account with the given number.
     *
     * @param number the account number
     * @return the rent factors
     */
    RentFactors getRentFactorsFor(long number);
}
