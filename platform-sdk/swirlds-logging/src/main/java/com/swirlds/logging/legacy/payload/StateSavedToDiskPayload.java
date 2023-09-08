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

package com.swirlds.logging.legacy.payload;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.file.Path;
import java.util.Objects;

/**
 * This payload is logged when the platform save a saved state to disk.
 */
public class StateSavedToDiskPayload extends AbstractLogPayload {

    private long round;
    private boolean freezeState;
    /**
     * The reason for writing the state to disk
     */
    private String reason;

    /**
     * The directory where the state was written
     */
    private Path directory;

    public StateSavedToDiskPayload() {}

    /**
     * Constructor
     *
     * @param round       the round number of the state
     * @param freezeState true if the state is a freeze state, false otherwise
     * @param reason      the reason for writing the state to disk
     * @param directory   the directory where the state was written
     */
    public StateSavedToDiskPayload(
            final long round, final boolean freezeState, @NonNull final String reason, @NonNull final Path directory) {

        super("Finished writing state for round " + round + " to disk. Reason: " + reason + ", directory: "
                + directory);
        this.round = round;
        this.freezeState = freezeState;
        this.reason = Objects.requireNonNull(reason);
        this.directory = Objects.requireNonNull(directory);
    }

    public long getRound() {
        return round;
    }

    public void setRound(final long round) {
        this.round = round;
    }

    public boolean isFreezeState() {
        return freezeState;
    }

    public void setFreezeState(boolean freezeState) {
        this.freezeState = freezeState;
    }

    /**
     * Get the reason for writing the state to disk
     *
     * @return the reason for writing the state to disk
     */
    @Nullable
    public String getReason() {
        return reason;
    }

    /**
     * Get the directory where the state was written
     *
     * @return the directory where the state was written
     */
    @Nullable
    public Path getDirectory() {
        return directory;
    }
}
