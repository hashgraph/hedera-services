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

package com.hedera.services.bdd.suites.hip796.operations;

import static java.util.Objects.requireNonNull;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Represents a desired relationship between an account and a token.
 */
public class DesiredAccountTokenRelation {
    private boolean frozen;
    private boolean kycGranted;
    private boolean locked;
    private long balance;
    private long autoRenewPeriod;
    private boolean receiverSigRequired = false;
    private boolean includingOnlyPartitionRelations = false;
    private List<Long> ownedSerialNos = new ArrayList<>();
    private String managingContract = null;
    private Map<String, DesiredAccountTokenRelation> desiredPartitionRelations = new HashMap<>();

    public DesiredAccountTokenRelation managedBy(@NonNull final String contract) {
        requireNonNull(contract);
        managingContract = contract;
        return this;
    }

    public DesiredAccountTokenRelation autoRenewPeriod(final long autoRenewPeriod) {
        this.autoRenewPeriod = autoRenewPeriod;
        return this;
    }

    public DesiredAccountTokenRelation onlyForPartition(@NonNull final String partition) {
        requireNonNull(partition);
        return alsoForPartition(partition, DesiredAccountTokenRelation::includingOnlyPartitionRelations);
    }

    public DesiredAccountTokenRelation onlyForPartition(
            @NonNull final String partition, @NonNull final Consumer<DesiredAccountTokenRelation> spec) {
        requireNonNull(partition);
        return alsoForPartition(partition, pr -> spec.accept(pr.includingOnlyPartitionRelations()));
    }

    public DesiredAccountTokenRelation andPartition(@NonNull final String partition) {
        requireNonNull(partition);
        return alsoForPartition(partition);
    }

    public DesiredAccountTokenRelation andPartition(
            @NonNull final String partition, @NonNull final Consumer<DesiredAccountTokenRelation> spec) {
        requireNonNull(partition);
        return alsoForPartition(partition, spec);
    }

    public DesiredAccountTokenRelation alsoForPartition(@NonNull final String partition) {
        requireNonNull(partition);
        return alsoForPartition(partition, pr -> {});
    }

    public DesiredAccountTokenRelation alsoForPartition(
            @NonNull final String partition, @NonNull final Consumer<DesiredAccountTokenRelation> spec) {
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

    public DesiredAccountTokenRelation kycRevoked() {
        kycGranted = false;
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

    public DesiredAccountTokenRelation receiverSigRequired() {
        receiverSigRequired = true;
        return this;
    }
}
