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
import java.util.List;

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
