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

package com.hedera.node.app.service.contract.impl.infra;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.contract.impl.annotations.TransactionScope;
import com.hedera.node.app.service.contract.impl.state.StorageSizeChange;
import com.hedera.node.app.spi.meta.bni.Scope;
import com.hedera.node.config.data.ContractsConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import javax.inject.Inject;

/**
 * Validates that a set of storage size changes are valid, given the current contract service configuration.
 */
@TransactionScope
public class StorageSizeValidator {
    private final ContractsConfig contractsConfig;

    @Inject
    public StorageSizeValidator(@NonNull final ContractsConfig contractsConfig) {
        this.contractsConfig = requireNonNull(contractsConfig);
    }

    public void assertValid(
            final long totalSlotsUsed,
            @NonNull final Scope scope,
            @NonNull final List<StorageSizeChange> storageSizeChanges) {
        throw new AssertionError("Not implemented");
    }
}
