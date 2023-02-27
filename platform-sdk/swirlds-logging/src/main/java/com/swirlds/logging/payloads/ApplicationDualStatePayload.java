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
 * This payload is logged when the swirld app receives a dual state instance
 */
public class ApplicationDualStatePayload extends AbstractLogPayload {

    /** the time when the freeze starts */
    private Instant freezeTime;

    /** the last freezeTime based on which the nodes were frozen */
    private Instant lastFrozenTime;

    public ApplicationDualStatePayload() {}

    public ApplicationDualStatePayload(final Instant freezeTime, final Instant lastFrozenTime) {
        super("App init with a dual state");
        this.freezeTime = freezeTime;
        this.lastFrozenTime = lastFrozenTime;
    }

    public Instant getFreezeTime() {
        return freezeTime;
    }

    public void setFreezeTime(Instant freezeTime) {
        this.freezeTime = freezeTime;
    }

    public Instant getLastFrozenTime() {
        return lastFrozenTime;
    }

    public void setLastFrozenTime(Instant lastFrozenTime) {
        this.lastFrozenTime = lastFrozenTime;
    }
}
