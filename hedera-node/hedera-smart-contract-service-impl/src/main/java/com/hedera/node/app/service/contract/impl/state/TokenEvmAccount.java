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

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.numberOfLongZero;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.code.CodeFactory;

/**
 * An {@link Account} whose code proxies all calls to the {@code 0x167} system contract, and thus can
 * never change its storage or nonce.
 *
 * <p>It also cannot have a non-zero balance, as dispatching a {@code transferValue()} with a token
 * address as receiver will always fail.
 *
 * <p>Despite this inherent immutability, for convenience still implements {@link MutableAccount} so
 * that instances can be used anywhere the Besu EVM needs a <i>potentially</i> mutable account.
 * Mutability should always turn out to be unnecessary in these cases, however; so the mutator methods
 * on this class do throw {@code UnsupportedOperationException} .
 */
public class TokenEvmAccount extends AbstractMutableEvmAccount {
    private static final long TOKEN_PROXY_ACCOUNT_NONCE = -1;

    private final Address address;
    private final EvmFrameState state;

    public TokenEvmAccount(@NonNull final Address address, @NonNull final EvmFrameState state) {
        this.address = requireNonNull(address);
        this.state = requireNonNull(state);
    }

    @Override
    public Address getAddress() {
        return address;
    }

    @Override
    public long getNonce() {
        return TOKEN_PROXY_ACCOUNT_NONCE;
    }

    @Override
    public Wei getBalance() {
        return Wei.ZERO;
    }

    @Override
    public Bytes getCode() {
        return state.getTokenRedirectCode(address);
    }

    @Override
    public @NonNull Code getEvmCode() {
        return CodeFactory.createCode(getCode(), 0, false);
    }

    @Override
    public Hash getCodeHash() {
        return state.getTokenRedirectCodeHash(address);
    }

    @Override
    public @NonNull UInt256 getStorageValue(@NonNull final UInt256 key) {
        return UInt256.ZERO;
    }

    @Override
    public UInt256 getOriginalStorageValue(@NonNull final UInt256 key) {
        return UInt256.ZERO;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public void becomeImmutable() {
        throw new UnsupportedOperationException("Not Implemented Yet");
    }

    @Override
    public void setNonce(final long value) {
        throw new UnsupportedOperationException("setNonce");
    }

    @Override
    public void setCode(@NonNull final Bytes code) {
        throw new UnsupportedOperationException("setCode");
    }

    @Override
    public void setStorageValue(@NonNull final UInt256 key, @NonNull final UInt256 value) {
        throw new UnsupportedOperationException("setStorageValue");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isTokenFacade() {
        return true;
    }

    @Override
    public @NonNull AccountID hederaId() {
        throw new IllegalStateException("Token facade has no usable Hedera id");
    }

    @Override
    public @NonNull ContractID hederaContractId() {
        return ContractID.newBuilder().contractNum(numberOfLongZero(address)).build();
    }
}
