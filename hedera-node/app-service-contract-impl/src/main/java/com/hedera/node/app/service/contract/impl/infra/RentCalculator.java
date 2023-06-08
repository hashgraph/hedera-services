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

import com.hedera.node.app.service.contract.impl.annotations.TransactionScope;
import com.hedera.node.config.data.ContractsConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import javax.inject.Inject;

/**
 * Calculates the rent in tinybars that should be charged to an allocating contract, given
 * the current consensus time and contract service configuration.
 */
@TransactionScope
public class RentCalculator {
    private final Instant consensusNow;
    private final ContractsConfig contractsConfig;

    @Inject
    public RentCalculator(@NonNull final Instant consensusNow, @NonNull final ContractsConfig contractsConfig) {
        this.consensusNow = consensusNow;
        this.contractsConfig = contractsConfig;
    }

    /**
     * Calculates the rent in tinybars that should be charged to an allocating contract, given a few facts
     * about what the contract's and the network's storage utilization would be after the allocation.
     *
     * @param totalSlotsUsed the total number of storage slots that would be used by all contracts after the allocation
     * @param contractSlotsAdded the number of storage slots being allocated
     * @param contractSlotsAlreadyUsed the number of storage slots already used by the allocating contract
     * @param expectedSlotExpiry the expected consensus second at which the allocated slots will expire
     * @return the rent in tinybars that should be charged to the allocating contract
     */
    public long computeFor(
            final long totalSlotsUsed,
            final int contractSlotsAdded,
            final int contractSlotsAlreadyUsed,
            final long expectedSlotExpiry) {
        throw new AssertionError("Not implemented");
    }
}
