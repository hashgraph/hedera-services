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
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.contract.ContractNonceInfo;
import com.hedera.node.app.service.contract.impl.annotations.TransactionScope;
import com.hedera.node.app.service.contract.impl.state.ContractStateStore;
import com.hedera.node.app.service.contract.impl.state.WritableContractStateStore;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import javax.inject.Inject;

/**
 * A fully mutable {@link ExtWorldScope} implementation based on a {@link HandleContext}.
 */
@TransactionScope
public class HandleExtWorldScope implements ExtWorldScope {
    private final HandleContext context;

    @Inject
    public HandleExtWorldScope(@NonNull final HandleContext context) {
        this.context = context;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull HandleExtWorldScope begin() {
        context.savepointStack().createSavepoint();
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void commit() {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void revert() {
        context.savepointStack().rollback();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContractStateStore getStore() {
        return context.writableStore(WritableContractStateStore.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long peekNextEntityNumber() {
        throw new AssertionError("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long useNextEntityNumber() {
        throw new AssertionError("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull Bytes entropy() {
        throw new AssertionError("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long lazyCreationCostInGas() {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long gasPriceInTinybars() {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long valueInTinybars(final long tinycents) {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void collectFee(@NonNull final AccountID payerId, final long amount) {
        throw new AssertionError("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void refundFee(@NonNull final AccountID payerId, final long amount) {
        throw new AssertionError("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void chargeStorageRent(final long contractNumber, final long amount, final boolean itemizeStoragePayments) {
        throw new AssertionError("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateStorageMetadata(final long contractNumber, @Nullable final Bytes firstKey, final int slotsUsed) {
        throw new AssertionError("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createContract(
            final long number, final long parentNumber, final long nonce, @Nullable final Bytes evmAddress) {
        throw new AssertionError("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteAliasedContract(@NonNull final Bytes evmAddress) {
        throw new AssertionError("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteUnaliasedContract(final long number) {
        throw new AssertionError("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Long> getModifiedAccountNumbers() {
        throw new AssertionError("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ContractID> createdContractIds() {
        throw new AssertionError("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ContractNonceInfo> updatedContractNonces() {
        throw new AssertionError("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getOriginalSlotsUsed(final long contractNumber) {
        throw new AssertionError("Not implemented");
    }
}
