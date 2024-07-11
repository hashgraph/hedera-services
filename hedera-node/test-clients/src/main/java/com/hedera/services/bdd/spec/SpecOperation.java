/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec;

import com.hederahashgraph.api.proto.java.HederaFunctionality;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Optional;
import java.util.Set;

/**
 * Defines the interface for a single operation in a {@link HapiSpec}.
 */
public interface SpecOperation {
    /**
     * Execute the operation for the given {@link HapiSpec}, returning an optional that
     * contains any failure (include assertion {@link Error}s) that were thrown.
     *
     * @param spec the {@link HapiSpec} to execute the operation for
     * @return an optional containing any failure that was thrown
     */
    Optional<Throwable> execFor(@NonNull HapiSpec spec);

    /**
     * Returns whether the operation should be skipped when auto-scheduling is enabled.
     *
     * @param beingAutoScheduled the set of {@link HederaFunctionality} that are being auto-scheduled
     * @return whether the operation should be skipped when auto-scheduling is enabled
     */
    default boolean shouldSkipWhenAutoScheduling(@NonNull Set<HederaFunctionality> beingAutoScheduled) {
        return false;
    }
}
