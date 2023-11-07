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

package com.hedera.services.bdd.spec.utilops.domain;

import static java.util.Objects.requireNonNull;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * A POJO for Jackson to use in storing all the {@link RecordSnapshot}'s for a given
 * {@link com.hedera.services.bdd.suites.HapiSuite}.
 */
public class SuiteSnapshots {
    /**
     * Maps from the name of a spec to its {@link RecordSnapshot}.
     */
    private Map<String, RecordSnapshot> specSnapshots = new HashMap<>();

    public Map<String, RecordSnapshot> getSpecSnapshots() {
        return specSnapshots;
    }

    public void setSpecSnapshots(@NonNull final Map<String, RecordSnapshot> specSnapshots) {
        this.specSnapshots = requireNonNull(specSnapshots);
    }

    /**
     * Adds a {@link RecordSnapshot} to the map of snapshots.
     *
     * @param name The name of the spec
     * @param snapshot The snapshot to add
     */
    public void addSnapshot(@NonNull final String name, @NonNull final RecordSnapshot snapshot) {
        requireNonNull(name);
        requireNonNull(snapshot);
        specSnapshots.put(name, snapshot);
    }

    /**
     * Gets the {@link RecordSnapshot} for the given spec name, or null if none exists.
     *
     * @param name the name of the spec
     * @return the snapshot, or null if none exists
     */
    public @Nullable RecordSnapshot getSnapshot(@NonNull final String name) {
        requireNonNull(name);
        return specSnapshots.get(name);
    }
}
