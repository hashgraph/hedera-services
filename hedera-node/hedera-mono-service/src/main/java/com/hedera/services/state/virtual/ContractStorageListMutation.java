/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.virtual;

import com.hedera.services.utils.MapValueListMutation;
import com.swirlds.virtualmap.VirtualMap;
import org.jetbrains.annotations.Nullable;

public class ContractStorageListMutation
        implements MapValueListMutation<ContractKey, IterableContractValue> {
    final long contractId;
    final VirtualMap<ContractKey, IterableContractValue> storage;

    public ContractStorageListMutation(
            final long contractId, final VirtualMap<ContractKey, IterableContractValue> storage) {
        this.contractId = contractId;
        this.storage = storage;
    }

    /** {@inheritDoc} */
    @Nullable
    @Override
    public IterableContractValue get(final ContractKey key) {
        return storage.get(key);
    }

    /** {@inheritDoc} */
    @Nullable
    @Override
    public IterableContractValue getForModify(final ContractKey key) {
        return storage.getForModify(key);
    }

    /** {@inheritDoc} */
    @Override
    public void put(final ContractKey key, final IterableContractValue value) {
        storage.put(key, value);
    }

    /** {@inheritDoc} */
    @Override
    public void remove(final ContractKey key) {
        storage.remove(key);
    }

    /** {@inheritDoc} */
    @Override
    public void markAsHead(final IterableContractValue contractValue) {
        contractValue.markAsRootMapping();
    }

    /** {@inheritDoc} */
    @Override
    public void markAsTail(final IterableContractValue contractValue) {
        contractValue.markAsLastMapping();
    }

    /** {@inheritDoc} */
    @Override
    public void updatePrev(final IterableContractValue contractValue, final ContractKey prev) {
        contractValue.setPrevKey(prev.getKey());
    }

    /** {@inheritDoc} */
    @Override
    public void updateNext(IterableContractValue contractValue, ContractKey next) {
        contractValue.setNextKey(next.getKey());
    }

    /** {@inheritDoc} */
    @Nullable
    @Override
    public ContractKey next(final IterableContractValue contractValue) {
        return contractValue.getNextKeyScopedTo(contractId);
    }

    /** {@inheritDoc} */
    @Nullable
    @Override
    public ContractKey prev(final IterableContractValue contractValue) {
        return contractValue.getPrevKeyScopedTo(contractId);
    }
}
