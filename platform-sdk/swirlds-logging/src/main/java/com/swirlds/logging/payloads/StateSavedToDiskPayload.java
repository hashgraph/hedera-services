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

package com.swirlds.logging.payloads;

/**
 * This payload is logged when the platform save a saved state to disk.
 */
public class StateSavedToDiskPayload extends AbstractLogPayload {

    private long round;
    private boolean freezeState;

    public StateSavedToDiskPayload() {}

    public StateSavedToDiskPayload(final long round, final boolean freezeState) {
        super("Finished writing state for round " + round + " to disk.");
        this.round = round;
        this.freezeState = freezeState;
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
}
