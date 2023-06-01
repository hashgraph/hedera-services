package com.hedera.node.app.service.contract.impl.infra;

import com.hedera.node.app.service.contract.impl.annotations.TransactionScope;
import com.hedera.node.config.data.ContractsConfig;
import edu.umd.cs.findbugs.annotations.NonNull;

import javax.inject.Inject;

@TransactionScope
public class RentCalculator {
    private final ContractsConfig contractsConfig;

    @Inject
    public RentCalculator(@NonNull final ContractsConfig contractsConfig) {
        this.contractsConfig = contractsConfig;
    }

    /**
     * Calculates the rent in tinybars that should be charged to an allocating contract, given a few facts
     * about what the contract's and the network's storage utilization would be after the allocation.
     *
     * @param totalSlotsUsed the total number of storage slots that would be used by all contracts after the allocation
     * @param contractSlotsAdded the number of storage slots being allocated
     * @param contractSlotsUsed the number of storage slots that would be used by this contract after the allocation
     * @param expectedSlotLifetime the expected lifetime of a storage slot, in seconds
     * @return the rent in tinybars that should be charged to the allocating contract
     */
    public long rentInTinycentsGiven(
            final long totalSlotsUsed,
            final int contractSlotsAdded,
            final int contractSlotsUsed,
            final long expectedSlotLifetime) {
        throw new AssertionError("Not implemented");
    }
}
