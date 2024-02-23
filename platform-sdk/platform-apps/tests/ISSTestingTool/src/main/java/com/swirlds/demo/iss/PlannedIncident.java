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

package com.swirlds.demo.iss;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;

/**
 * Describes an incident that is scheduled to occur at a specific time after genesis
 */
public interface PlannedIncident {
    /**
     * Get the amount of time after genesis when the incident should be triggered
     *
     * @return the amount of time after genesis when the incident should be triggered
     */
    @NonNull
    Duration getTimeAfterGenesis();

    /**
     * Get a brief descriptor of the incident for logging purposes
     *
     * @return a descriptor of the incident
     */
    @NonNull
    String getDescriptor();
}
