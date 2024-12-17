/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.service.contract.impl.exec.scope.HandleHederaOperations.ZERO_ENTROPY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.node.app.service.contract.impl.annotations.QueryScope;
import com.hedera.node.app.service.contract.impl.exec.gas.TinybarValues;
import com.hedera.node.app.service.contract.impl.state.ContractStateStore;
import com.hedera.node.app.service.token.api.ContractChangeSummary;
import com.hedera.node.app.spi.workflows.QueryContext;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import org.hyperledger.besu.datatypes.Address;

/**
 * A read-only {@link HederaOperations} implementation based on a {@link QueryContext}.
 */
@QueryScope
public class QueryHederaOperations implements HederaOperations {
    private final QueryContext context;
    private final HederaConfig hederaConfig;
    private final TinybarValues tinybarValues;

    @Inject
    public QueryHederaOperations(
            @NonNull final QueryContext context,
            @NonNull final HederaConfig hederaConfig,
            @NonNull final TinybarValues tinybarValues) {
        this.context = requireNonNull(context);
        this.hederaConfig = requireNonNull(hederaConfig);
        this.tinybarValues = requireNonNull(tinybarValues);
    }

    /**
     * Returns this, since there is no transactional context to manage.
     *
     * @return this
     */
    @Override
    public @NonNull HederaOperations begin() {
        return this;
    }

    /**
     * Does nothing, since there is no transactional context to manage.
     */
    @Override
    public void commit() {
        // No-op
    }

    /**
     * Does nothing, since there is no transactional context to manage.
     */
    @Override
    public void revert() {
        // No-op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull ContractStateStore getStore() {
        return context.createStore(ContractStateStore.class);
    }

    /**
     * Refuses to interact with entity numbers, since queries cannot change the state of the world.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public long peekNextEntityNumber() {
        throw new UnsupportedOperationException("Queries cannot peek at entity numbers");
    }

    /**
     * Refuses to interact with entity numbers, since queries cannot change the state of the world.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public long useNextEntityNumber() {
        throw new UnsupportedOperationException("Queries cannot use entity numbers");
    }

    @Override
    public long contractCreationLimit() {
        throw new UnsupportedOperationException("Queries should not be considering creations");
    }

    @Override
    public long accountCreationLimit() {
        throw new UnsupportedOperationException("Queries should not be considering creations");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull Bytes entropy() {
        final var entropy = context.blockRecordInfo().prngSeed();
        return (entropy == null || entropy.equals(Bytes.EMPTY)) ? ZERO_ENTROPY : entropy;
    }

    /**
     * Refuses to return the lazy creation cost in gas.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public long lazyCreationCostInGas(@NonNull final Address recipient) {
        throw new UnsupportedOperationException("Queries cannot get lazy creation cost");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long gasPriceInTinybars() {
        // TODO - implement correctly
        return 1L;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long valueInTinybars(final long tinycents) {
        return tinybarValues.asTinybars(tinycents);
    }

    /**
     * Refuses to collect a fee, since queries are paid asynchronously.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public void collectFee(@NonNull final AccountID payerId, final long amount) {
        throw new UnsupportedOperationException("Queries cannot collect fees");
    }

    /**
     * Refuses to refund a fee, since queries are paid asynchronously.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public void refundFee(@NonNull final AccountID payerId, final long amount) {
        throw new UnsupportedOperationException("Queries cannot refund fees");
    }

    /**
     * Refuses to charge storage rent.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public void chargeStorageRent(
            final ContractID contractID, final long amount, final boolean itemizeStoragePayments) {
        throw new UnsupportedOperationException("Queries cannot charge storage rent");
    }

    /**
     * Refuses to update storage metadata.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public void updateStorageMetadata(
            final ContractID contractID, @NonNull final Bytes firstKey, final int netChangeInSlotsUsed) {
        throw new UnsupportedOperationException("Queries cannot update storage metadata");
    }

    /**
     * Refuses to create a contract.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public void createContract(final long number, final long parentNumber, final @Nullable Bytes evmAddress) {
        throw new UnsupportedOperationException("Queries cannot create a contract");
    }

    /**
     * Refuses to create a contract.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public void createContract(
            long number, @NonNull final ContractCreateTransactionBody op, @Nullable Bytes evmAddress) {
        throw new UnsupportedOperationException("Queries cannot create a contract");
    }

    /**
     * Refuses to delete an aliased contract.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public void deleteAliasedContract(@NonNull Bytes evmAddress) {
        throw new UnsupportedOperationException("Queries cannot delete an contract");
    }

    /**
     * Refuses to delete an aliased contract.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public void deleteUnaliasedContract(final long number) {
        throw new UnsupportedOperationException("Queries cannot delete an contract");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Long> getModifiedAccountNumbers() {
        return Collections.emptyList();
    }

    @Override
    public ContractChangeSummary summarizeContractChanges() {
        throw new UnsupportedOperationException("Queries cannot summarize contract changes");
    }

    /**
     * Refuses to get original slot usage, as a query should never be changing storage slots.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public long getOriginalSlotsUsed(final ContractID contractID) {
        throw new UnsupportedOperationException("Queries cannot get original slot usage");
    }

    @Override
    public ContractID shardAndRealmValidated(@NonNull ContractID contractId) {
        return configValidated(contractId, hederaConfig);
    }

    public void externalizeHollowAccountMerge(@NonNull ContractID contractId, @Nullable Bytes evmAddress) {
        throw new UnsupportedOperationException("Queries cannot create accounts");
    }
}
