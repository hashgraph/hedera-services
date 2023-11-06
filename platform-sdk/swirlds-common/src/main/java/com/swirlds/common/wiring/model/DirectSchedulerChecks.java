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

package com.swirlds.common.wiring.model;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collection;

/**
 * A utility for checking direct scheduler use.
 */
public final class DirectSchedulerChecks {

    private DirectSchedulerChecks() {}

    // TODO we need to ensure the following:
    // - at most one scheduler has edges leading into each direct scheduler
    // - concurrent schedulers never have edges leading into a direct scheduler
    // - it should be legal for wires travelling through direct schedulers and through transformers to have edges
    //   into a direct scheduler, as long as they all originate from the same scheduler and it is non-concurrent

    /**
     * Check for illegal direct scheduler use.
     *
     * @param vertices the vertices in the wiring model
     * @return true if there is illegal direct scheduler use
     */
    public static boolean checkForIllegalDirectSchedulerUse(@NonNull final Collection<ModelVertex> vertices) {
        return false; // TODO
    }
}
