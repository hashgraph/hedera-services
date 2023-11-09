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

import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_CONTRACT_STORAGE_EXCEEDED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_STORAGE_IN_PRICE_REGIME_HAS_BEEN_USED;
import static com.hedera.node.app.spi.workflows.ResourceExhaustedException.validateResource;

import com.hedera.node.app.service.contract.impl.annotations.TransactionScope;
import com.hedera.node.app.service.contract.impl.exec.scope.HederaOperations;
import com.hedera.node.app.service.contract.impl.state.StorageSizeChange;
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
        this.contractsConfig = contractsConfig;
    }

    /**
     * Validates that a set of storage size changes are valid, given the current contract service configuration.
     *
     * @param aggregateSlotsUsed the number of slots that would be used by all contracts combined after the transaction
     * @param hederaOperations the extended world scope used to create the size changes being validated
     * @param storageSizeChanges the summarized storage size changes to validate
     */
    public void assertValid(
            final long aggregateSlotsUsed,
            @NonNull final HederaOperations hederaOperations,
            @NonNull final List<StorageSizeChange> storageSizeChanges) {
        final var maxAggregateSlots = contractsConfig.maxKvPairsAggregate();
        validateResource(maxAggregateSlots >= aggregateSlotsUsed, MAX_STORAGE_IN_PRICE_REGIME_HAS_BEEN_USED);

        final var maxIndividualSlots = contractsConfig.maxKvPairsIndividual();
        storageSizeChanges.forEach(change -> {
            final var contractSlotsUsed =
                    change.numAdded() + hederaOperations.getOriginalSlotsUsed(change.contractNumber());
            validateResource(maxIndividualSlots >= contractSlotsUsed, MAX_CONTRACT_STORAGE_EXCEEDED);
        });
    }
}
