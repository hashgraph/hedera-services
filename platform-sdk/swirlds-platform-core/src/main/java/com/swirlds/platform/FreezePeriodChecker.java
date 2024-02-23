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

package com.swirlds.platform;

import java.time.Instant;

/**
 * Checks whether a timestamp is in freeze period
 */
public interface FreezePeriodChecker {
    /**
     * Checks whether the given instant is in the freeze period
     * Only when the timestamp is not before freezeTime, and freezeTime is after lastFrozenTime,
     * the timestamp is in the freeze period.
     *
     * @param timestamp
     * 		an Instant to check
     * @return true if it is in the freeze period, false otherwise
     */
    boolean isInFreezePeriod(Instant timestamp);
}
