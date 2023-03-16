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

package com.swirlds.platform.bls.protocol;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/** An object to manage the current state of a protocol */
public class BlsStateManager {
    /** The number of rounds that have been started */
    private int roundsStarted;

    /** The number of rounds that have been completed */
    private int roundsCompleted;

    /** Enum representing the current state of the protocol */
    @NonNull
    private BlsProtocolState state;

    /** The BlsStateManager constructor */
    public BlsStateManager() {
        this.roundsStarted = 0;
        this.roundsCompleted = 0;
        this.state = BlsProtocolState.NOT_STARTED;
    }

    /** The finishing process is beginning */
    public void beginFinish() {
        if (state != BlsProtocolState.RUNNING) {
            throw new IllegalStateException(
                    String.format("A protocol cannot begin finishing while in [%s] state", state));
        }

        state = BlsProtocolState.FINISHING;
    }

    /** Marks the finish process as complete */
    public void finishComplete() {
        if (state != BlsProtocolState.FINISHING) {
            throw new IllegalStateException(String.format("A protocol cannot be finished while in [%s] state", state));
        }

        state = BlsProtocolState.FINISHED;
    }

    /** Increments {@link #roundsStarted} */
    public void roundStarted() {
        if (state != BlsProtocolState.NOT_STARTED && state != BlsProtocolState.RUNNING) {
            throw new IllegalStateException(String.format("A round cannot be started while in [%s] state", state));
        }

        if (state == BlsProtocolState.NOT_STARTED) {
            state = BlsProtocolState.RUNNING;
        }

        ++roundsStarted;
    }

    /** Increments {@link #roundsCompleted} */
    public void roundCompleted() {
        if (state != BlsProtocolState.RUNNING) {
            throw new IllegalStateException(String.format("A round cannot be completed while in [%s] state", state));
        }

        ++roundsCompleted;
    }

    /** Sets {@link #state} to {@link BlsProtocolState#ERROR ERROR} */
    public void errorOccurred() {
        state = BlsProtocolState.ERROR;
    }

    /**
     * Gets the number of rounds that the protocol has started
     *
     * @return {@link #roundsStarted}
     */
    public int getRoundsStarted() {
        return roundsStarted;
    }

    /**
     * Gets the number of rounds that the protocol has completed
     *
     * @return {@link #roundsCompleted}
     */
    public int getRoundsCompleted() {
        return roundsCompleted;
    }

    /**
     * Whether the protocol is in an error state
     *
     * @return true if the protocol is in an error state, otherwise false
     */
    public boolean isError() {
        return state == BlsProtocolState.ERROR;
    }

    /**
     * Gets the current protocol state
     *
     * @return the value of {@link #state}
     */
    @NonNull
    public BlsProtocolState getState() {
        return state;
    }
}
