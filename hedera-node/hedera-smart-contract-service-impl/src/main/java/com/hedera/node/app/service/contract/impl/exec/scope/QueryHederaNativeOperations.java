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

package com.hedera.node.app.service.contract.impl.exec.scope;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.contract.impl.annotations.QueryScope;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.spi.workflows.QueryContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;
import javax.inject.Inject;

/**
 * A read-only {@link HederaNativeOperations} based on a {@link QueryContext}.
 */
@QueryScope
public class QueryHederaNativeOperations implements HederaNativeOperations {
    private final QueryContext context;

    @Inject
    public QueryHederaNativeOperations(@NonNull final QueryContext context) {
        this.context = Objects.requireNonNull(context);
    }

    @Override
    public @Nullable Account getAccount(final long number) {
        final var accountStore = context.createStore(ReadableAccountStore.class);
        return accountStore.getAccountById(
                AccountID.newBuilder().accountNum(number).build());
    }

    @Override
    public @Nullable Token getToken(final long number) {
        final var tokenStore = context.createStore(ReadableTokenStore.class);
        return tokenStore.get(TokenID.newBuilder().tokenNum(number).build());
    }

    @Override
    public long resolveAlias(@NonNull final Bytes evmAddress) {
        final var accountStore = context.createStore(ReadableAccountStore.class);
        final var account = accountStore.getAccountIDByAlias(evmAddress);
        return account == null ? MISSING_ENTITY_NUMBER : account.accountNumOrThrow();
    }

    /**
     * Refuses to set the nonce of a contract.
     *
     * @param contractNumber the contract number
     * @param nonce          the new nonce
     * @throws UnsupportedOperationException always
     */
    @Override
    public void setNonce(final long contractNumber, final long nonce) {
        throw new UnsupportedOperationException("Cannot set nonce in query context");
    }

    /**
     * Refuses to create a new hollow account.
     *
     * @param evmAddress the EVM address of the new hollow account
     * @throws UnsupportedOperationException always
     */
    @Override
    public ResponseCodeEnum createHollowAccount(@NonNull final Bytes evmAddress) {
        throw new UnsupportedOperationException("Cannot create hollow account in query context");
    }

    /**
     * Refuses to finalize a hollow account as a contract.
     *
     * @param evmAddress the EVM address of the hollow account to finalize as a contract
     * @throws UnsupportedOperationException always
     */
    @Override
    public void finalizeHollowAccountAsContract(@NonNull final Bytes evmAddress) {
        throw new UnsupportedOperationException("Cannot finalize hollow account as contract in query context");
    }

    /**
     * Refuses to transfer value.
     *
     * @param amount           the amount to transfer
     * @param fromNumber the number of the account to transfer from
     * @param toNumber   the number of the account to transfer to
     * @param strategy         the {@link VerificationStrategy} to use
     * @throws UnsupportedOperationException always
     */
    @Override
    public ResponseCodeEnum transferWithReceiverSigCheck(
            final long amount,
            final long fromNumber,
            final long toNumber,
            @NonNull final VerificationStrategy strategy) {
        throw new UnsupportedOperationException("Cannot transfer value in query context");
    }

    /**
     * Refuses to track a deletion.
     *
     * @param deletedNumber the number of the deleted contract
     * @param beneficiaryNumber the number of the beneficiary
     */
    @Override
    public void trackDeletion(final long deletedNumber, final long beneficiaryNumber) {
        throw new UnsupportedOperationException("Cannot track deletion in query context");
    }
}
