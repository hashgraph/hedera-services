package com.hedera.services.bdd.suites.hip796.operations;

import com.hedera.hapi.node.base.TokenType;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.hedera.services.bdd.suites.HapiSuite.FUNGIBLE_INITIAL_BALANCE;
import static java.util.Objects.requireNonNull;

/**
 * Represents a desired relationship between an account and a token.
 */
public class DesiredAccountTokenRelation {
    private boolean frozen;
    private boolean kycGranted;
    private boolean locked;
    private long balance;
    private boolean includingOnlyPartitionRelations = false;
    private List<Long> ownedSerialNos = new ArrayList<>();
    private Map<String, DesiredAccountTokenRelation> desiredPartitionRelations = new HashMap<>();

    public DesiredAccountTokenRelation onlyForPartition(@NonNull final String partition) {
        requireNonNull(partition);
        return alsoForPartition(partition, DesiredAccountTokenRelation::includingOnlyPartitionRelations);
    }

    public DesiredAccountTokenRelation alsoForPartition(@NonNull final String partition) {
        requireNonNull(partition);
        return alsoForPartition(partition, pr -> {});
    }

    public DesiredAccountTokenRelation alsoForPartition(
            @NonNull final String partition,
            @NonNull final Consumer<DesiredAccountTokenRelation> spec) {
        requireNonNull(spec);
        requireNonNull(partition);
        final var desiredPartitionRelation = new DesiredAccountTokenRelation();
        spec.accept(desiredPartitionRelation);
        desiredPartitionRelations.put(partition, desiredPartitionRelation);
        return this;
    }

    public DesiredAccountTokenRelation includingOnlyPartitionRelations() {
        includingOnlyPartitionRelations = true;
        return this;
    }

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
}
