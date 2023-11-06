package com.hedera.services.bdd.suites.hip796.operations;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

public class DesiredAccountTokenRelation {
    private boolean frozen;
    private boolean kycGranted;
    private boolean locked;
    private long balance;
    private List<Long> ownedSerialNos = new ArrayList<>();
    private Map<String, DesiredAccountTokenRelation> desiredPartitionRelations = new HashMap<>();

    public DesiredAccountTokenRelation frozen() {
        frozen = true;
        return this;
    }

    public DesiredAccountTokenRelation kycGranted() {
        kycGranted = true;
        return this;
    }

    public DesiredAccountTokenRelation locked() {
        locked = true;
        return this;
    }

    public DesiredAccountTokenRelation balance(long balance) {
        this.balance = balance;
        return this;
    }

    public DesiredAccountTokenRelation ownedSerialNos(@NonNull final Long... serialNos) {
        requireNonNull(serialNos);
        this.ownedSerialNos = Arrays.asList(serialNos);
        return this;
    }

    public DesiredAccountTokenRelation withPartitionRelation(
            @NonNull final String partition,
            @NonNull final Consumer<DesiredAccountTokenRelation> spec) {
        requireNonNull(spec);
        requireNonNull(partition);
        final var desiredPartitionRelation = new DesiredAccountTokenRelation();
        spec.accept(desiredPartitionRelation);
        desiredPartitionRelations.put(partition, desiredPartitionRelation);
        return this;
    }
}
