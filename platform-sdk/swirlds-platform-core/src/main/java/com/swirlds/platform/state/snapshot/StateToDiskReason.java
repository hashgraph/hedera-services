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

package com.swirlds.platform.state.snapshot;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * The reason for writing the state to disk
 */
public enum StateToDiskReason {
    /**
     * The state was written to disk because the platform is starting up without a previous saved state
     */
    FIRST_ROUND_AFTER_GENESIS("first-round-after-genesis"),
    /**
     * The state was written to disk because it is a freeze state
     */
    FREEZE_STATE("freeze-state"),
    /**
     * The state was written to disk because it is time to take a periodic snapshot
     */
    PERIODIC_SNAPSHOT("periodic-snapshot"),
    /**
     * The state was written to disk because it is a reconnect state
     */
    RECONNECT("reconnect"),
    /**
     * The state was written to disk because an ISS was detected
     */
    ISS("iss"),
    /**
     * The state was written to disk because a fatal error was encountered
     */
    FATAL_ERROR("fatal"),
    /**
     * The state was written because the PCES recovery process has been completed
     */
    PCES_RECOVERY_COMPLETE("pces-recovery"),
    /**
     * If the reason at the point of saving is not known, this value will be used
     */
    UNKNOWN("unknown");

    /**
     * The description of the reason
     * <p>
     * This string will be used as part of file paths, so it should not contain any characters that are not suitable
     */
    private final String description;

    /**
     * Constructor
     *
     * @param description the description of the reason
     */
    StateToDiskReason(@NonNull final String description) {
        this.description = Objects.requireNonNull(description);
    }

    /**
     * Get the description of the reason
     *
     * @return the description of the reason
     */
    @NonNull
    public String getDescription() {
        return description;
    }
}
