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
import com.hedera.node.app.service.contract.impl.annotations.QueryScope;
import com.hedera.node.app.service.contract.impl.state.ContractStateStore;
import com.hedera.node.app.spi.workflows.QueryContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Objects;
import javax.inject.Inject;

/**
 * A read-only {@link ExtWorldScope} implementation based on a {@link QueryContext}.
 */
@QueryScope
public class QueryExtWorldScope implements ExtWorldScope {
    private final QueryContext context;

    @Inject
    public QueryExtWorldScope(@NonNull final QueryContext context) {
        this.context = Objects.requireNonNull(context);
    }

    @NonNull
    @Override
    public ExtWorldScope begin() {
        return null;
    }

    @Override
    public void commit() {}

    @Override
    public void revert() {}

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull ContractStateStore getStore() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long peekNextEntityNumber() {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long useNextEntityNumber() {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull Bytes entropy() {
        return null;
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
    public long valueInTinybars(long tinycents) {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void collectFee(@NonNull AccountID payerId, long amount) {}

    /**
     * {@inheritDoc}
     */
    @Override
    public void refundFee(@NonNull AccountID payerId, long amount) {}

    /**
     * {@inheritDoc}
     */
    @Override
    public void chargeStorageRent(long contractNumber, long amount, boolean itemizeStoragePayments) {}

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateStorageMetadata(long contractNumber, @Nullable Bytes firstKey, int slotsUsed) {}

    /**
     * {@inheritDoc}
     */
    @Override
    public void createContract(long number, long parentNumber, long nonce, @Nullable Bytes evmAddress) {}

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteAliasedContract(@NonNull Bytes evmAddress) {}

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteUnaliasedContract(long number) {}

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
    public int getOriginalSlotsUsed(long contractNumber) {
        return 0;
    }
}
