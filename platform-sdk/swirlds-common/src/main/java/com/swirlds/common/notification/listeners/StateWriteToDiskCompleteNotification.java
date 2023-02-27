/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.notification.listeners;

import com.swirlds.common.notification.AbstractNotification;
import com.swirlds.common.system.SwirldState;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Class that provides {@link com.swirlds.common.notification.Notification} when state is written to disk
 */
public class StateWriteToDiskCompleteNotification extends AbstractNotification {

    private final long roundNumber;
    private final Instant consensusTimestamp;
    private final SwirldState state;
    private final Path folder;
    private final boolean isFreezeState;

    public StateWriteToDiskCompleteNotification(
            final long roundNumber,
            final Instant consensusTimestamp,
            final SwirldState state,
            final Path folder,
            final boolean isFreezeState) {
        this.roundNumber = roundNumber;
        this.consensusTimestamp = consensusTimestamp;
        this.state = state;
        this.folder = folder;
        this.isFreezeState = isFreezeState;
    }

    /**
     * Gets round number from the state that is written to disk.
     *
     * @return the round number
     */
    public long getRoundNumber() {
        return roundNumber;
    }

    /**
     * Gets the consensus timestamp handled before the state is written to disk.
     *
     * @return the consensus timestamp
     */
    public Instant getConsensusTimestamp() {
        return consensusTimestamp;
    }

    /**
     * Gets the {@link SwirldState} instance that was sigend and saved to disk.
     *
     * @return the signed {@link SwirldState} instance
     */
    public SwirldState getState() {
        return state;
    }

    /**
     * Gets the path where the signed state was written to disk.
     *
     * @return the path containing the saved state
     */
    public Path getFolder() {
        return folder;
    }

    /**
     * Gets whether this is a freeze state
     *
     * @return whether this is a freeze state
     */
    public boolean isFreezeState() {
        return isFreezeState;
    }
}
