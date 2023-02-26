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

import java.time.Instant;

/**
 * This payload is logged when the platform loads a saved state from disk.
 */
public class SavedStateLoadedPayload extends AbstractLogPayload {

    private long round;
    private Instant consensusTimestamp;
    private Instant willFreezeUntil;

    public SavedStateLoadedPayload(final long round, final Instant consensusTimestamp, final Instant willFreezeUntil) {
        super("Platform has loaded a saved state");
        this.round = round;
        this.consensusTimestamp = consensusTimestamp;
        this.willFreezeUntil = willFreezeUntil;
    }

    public long getRound() {
        return round;
    }

    public void setRound(final long round) {
        this.round = round;
    }

    public Instant getConsensusTimestamp() {
        return consensusTimestamp;
    }

    public void setConsensusTimestamp(final Instant consensusTimestamp) {
        this.consensusTimestamp = consensusTimestamp;
    }

    public Instant getWillFreezeUntil() {
        return willFreezeUntil;
    }

    public void setWillFreezeUntil(final Instant willFreezeUntil) {
        this.willFreezeUntil = willFreezeUntil;
    }
}
