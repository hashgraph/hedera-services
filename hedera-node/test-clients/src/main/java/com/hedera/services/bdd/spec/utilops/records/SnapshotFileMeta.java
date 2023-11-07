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

package com.hedera.services.bdd.spec.utilops.records;

import com.hedera.services.bdd.spec.HapiSpec;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Specifies the storage location for a record snapshot.
 *
 * @param suiteName the name of the suite
 * @param specName
 */
record SnapshotFileMeta(String suiteName, String specName) {
    /**
     * Creates a new {@link SnapshotFileMeta} from the given {@link HapiSpec}.
     *
     * @param spec the spec to create the meta from
     * @return the created meta
     */
    static SnapshotFileMeta from(@NonNull final HapiSpec spec) {
        return new SnapshotFileMeta(spec.getSuitePrefix(), spec.getName());
    }

    @Override
    public String toString() {
        return suiteName + "-" + specName;
    }
}
