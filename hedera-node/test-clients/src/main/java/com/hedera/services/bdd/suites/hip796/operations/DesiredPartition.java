package com.hedera.services.bdd.suites.hip796.operations;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Represents a desired partition of a token.
 */
public class DesiredPartition {
    private final String specRegistryName;
    private String name;
    private String memo;
    private long initialSupply;
    private List<Long> assignedSerialNos = new ArrayList<>();

    public DesiredPartition(@NonNull final String specRegistryName) {
        this.specRegistryName = specRegistryName;
    }

    public DesiredPartition name(@NonNull final String name) {
        this.name = name;
        return this;
    }

    public DesiredPartition assignedSerialNos(@NonNull final Long... serialNos) {
        requireNonNull(serialNos);
        this.assignedSerialNos = Arrays.asList(serialNos);
        return this;
    }

    public DesiredPartition memo(@NonNull final String memo) {
        this.memo = memo;
        return this;
    }

    public DesiredPartition initialSupply(final long initialSupply) {
        this.initialSupply = initialSupply;
        return this;
    }
}
