/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.service.contract.impl.state.DispatchingEvmFrameState.HOLLOW_ACCOUNT_KEY;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

/**
 * An abstract {@link Account} implementation that reads and writes data for the given entity number
 * by proxying calls to the given {@link EvmFrameState}.
 *
 * <p>The {@link EvmFrameState} reflects all changes to the state of the world that have been
 * made up to and including the current {@link org.hyperledger.besu.evm.frame.MessageFrame}.
 *
 * <p>There is no implementation difference between mutable and immutable proxy accounts,
 * since all changes are made to the {@link EvmFrameState} and not to the proxy account.
 * So we just need one implementation, which the EVM will request from {@link WorldUpdater}s
 * as an immutable {@link Account} in read-only contexts; and as a mutable {@link MutableAccount}
 * in a context where writes are allowed.
 */
public abstract class AbstractProxyEvmAccount extends AbstractMutableEvmAccount {
    protected final AccountID accountID;
    protected final EvmFrameState state;

    protected AbstractProxyEvmAccount(final AccountID accountID, @NonNull final EvmFrameState state) {
        this.accountID = accountID;
        this.state = state;
    }

    @Override
    public Address getAddress() {
        return state.getAddress(accountID);
    }

    @Override
    public com.hedera.hapi.node.state.token.Account toNativeAccount() {
        return state.getNativeAccount(accountID);
    }

    @Override
    public long getNonce() {
        return state.getNonce(accountID);
    }

    @Override
    public Wei getBalance() {
        return state.getBalance(accountID);
    }

    @Override
    public @NonNull UInt256 getStorageValue(@NonNull final UInt256 key) {
        return state.getStorageValue(hederaContractId(), key);
    }

    @Override
    public @NonNull UInt256 getOriginalStorageValue(@NonNull final UInt256 key) {
        return state.getOriginalStorageValue(hederaContractId(), key);
    }

    @Override
    public void setNonce(final long value) {
        state.setNonce(accountID.accountNumOrThrow(), value);
    }

    @Override
    public void setCode(@NonNull final Bytes code) {
        state.setCode(hederaContractId(), code);
    }

    @Override
    public void setStorageValue(@NonNull final UInt256 key, @NonNull final UInt256 value) {
        state.setStorageValue(hederaContractId(), key, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isTokenFacade() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isRegularAccount() {
        return !isContract();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isScheduleTxnFacade() {
        return false;
    }

    @Override
    public @NonNull AccountID hederaId() {
        return accountID;
    }

    @Override
    public @NonNull ContractID hederaContractId() {
        return ContractID.newBuilder()
                .shardNum(1)
                .realmNum(2)
                .contractNum(accountID.accountNumOrThrow())
                .build();
    }

    @Override
    public void becomeImmutable() {
        throw new UnsupportedOperationException("Not Implemented Yet");
    }

    /**
     * Returns the number of treasury titles held by this account.
     *
     * @return the number of treasury titles held by this account
     */
    public int numTreasuryTitles() {
        return state.getNumTreasuryTitles(accountID);
    }

    /**
     * Returns the number of positive token balances held by this account.
     *
     * @return the number of positive token balances held by this account
     */
    public int numPositiveTokenBalances() {
        return state.getNumPositiveTokenBalances(accountID);
    }

    /**
     * Returns whether the account is a contract.
     *
     * @return if the account is a contract
     */
    public boolean isContract() {
        return state.isContract(accountID);
    }

    /**
     * Returns whether this account is a "hollow" account; i.e. an account created by sending
     * value to an EVM address that did not already have a corresponding Hedera account.
     *
     * @return whether this account is hollow
     */
    public boolean isHollow() {
        return HOLLOW_ACCOUNT_KEY.equals(toNativeAccount().key());
    }
}
