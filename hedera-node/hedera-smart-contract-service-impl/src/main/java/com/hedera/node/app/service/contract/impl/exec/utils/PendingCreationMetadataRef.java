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

package com.hedera.node.app.service.contract.impl.exec.utils;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.node.app.service.contract.impl.annotations.TransactionScope;
import com.hedera.node.app.service.contract.impl.exec.processors.CustomContractCreationProcessor;
import com.hedera.node.app.service.contract.impl.records.ContractOperationStreamBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;

/**
 * Wrapper that holds a reference to the {@link ContractOperationStreamBuilder} that should receive
 * the bytecode sidecar in the next {@code codeSuccess()}  call of {@link CustomContractCreationProcessor}.
 */
@TransactionScope
public class PendingCreationMetadataRef {
    private final Map<ContractID, PendingCreationMetadata> pendingMetadata = new LinkedHashMap<>();

    @Inject
    public PendingCreationMetadataRef() {
        // Dagger2
    }

    /**
     * Sets the given contract id's pending creation metadata to the given value.
     *
     * @param contractID the contract id to set pending creation metadata for
     * @param metadata the metadata to set
     */
    public void set(@NonNull final ContractID contractID, @NonNull final PendingCreationMetadata metadata) {
        requireNonNull(metadata);
        requireNonNull(contractID);
        pendingMetadata.put(contractID, metadata);
    }

    /**
     * Returns and forgets the pending creation metadata for the given contract id.
     * Throws if no metadata has been set for the pending creation.
     *
     * @param contractID the contract id to get metadata for
     * @return the metadata for the pending creation
     * @throws IllegalStateException if there is no pending creation
     */
    public @NonNull PendingCreationMetadata getAndClearOrThrowFor(@NonNull final ContractID contractID) {
        return requireNonNull(pendingMetadata.remove(contractID));
    }
}
