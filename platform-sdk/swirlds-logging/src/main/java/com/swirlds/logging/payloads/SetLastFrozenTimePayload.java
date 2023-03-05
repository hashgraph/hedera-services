/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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
 * This payload is logged when the platform sets last frozen time.
 */
public class SetLastFrozenTimePayload extends AbstractLogPayload {

    /** the last freezeTime based on which the nodes were frozen */
    private Instant lastFrozenTime;

    public SetLastFrozenTimePayload() {}

    public SetLastFrozenTimePayload(final Instant lastFrozenTime) {
        super("Set last frozen time");
        this.lastFrozenTime = lastFrozenTime;
    }

    public Instant getLastFrozenTime() {
        return lastFrozenTime;
    }

    public void setLastFrozenTime(Instant lastFrozenTime) {
        this.lastFrozenTime = lastFrozenTime;
    }
}
