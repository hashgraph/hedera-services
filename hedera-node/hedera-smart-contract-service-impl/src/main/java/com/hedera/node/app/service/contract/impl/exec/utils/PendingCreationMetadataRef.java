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

import com.hedera.node.app.service.contract.impl.annotations.TransactionScope;
import com.hedera.node.app.service.contract.impl.exec.processors.CustomContractCreationProcessor;
import com.hedera.node.app.service.contract.impl.records.ContractOperationRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Inject;

/**
 * Wrapper that holds a reference to the {@link ContractOperationRecordBuilder} that should receive
 * the bytecode sidecar in the next {@code codeSuccess()}  call of {@link CustomContractCreationProcessor}.
 */
@TransactionScope
public class PendingCreationMetadataRef {
    @Nullable
    private PendingCreationMetadata metadata = null;

    @Inject
    public PendingCreationMetadataRef() {
        // Dagger2
    }

    /**
     * Sets the pending creation's metadata to the given value.
     *
     * @param metadata the record builder to set
     */
    public void set(@NonNull final PendingCreationMetadata metadata) {
        this.metadata = requireNonNull(metadata);
    }

    /**
     * Returns the current metadata and resets the reference reset to {@code null}.
     * Throws if no metadata has been set for the pending creation.
     *
     * @return the metadata for the pending creation
     * @throws IllegalStateException if there is no pending creation
     */
    public @NonNull PendingCreationMetadata getAndClearOrThrow() {
        final var pendingMetadata = requireNonNull(metadata);
        metadata = null;
        return pendingMetadata;
    }
}
